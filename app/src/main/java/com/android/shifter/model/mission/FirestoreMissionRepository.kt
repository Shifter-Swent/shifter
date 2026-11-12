// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.swent.shifter.model.event.EventSchema
import java.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * [MissionRepository] backed by Cloud Firestore, storing each mission in the subcollection of its
 * event: `/events/{eventId}/missions/{missionId}`.
 *
 * All Firebase types stay inside this class and the [toMission] / [toFirestoreMap] mappers: callers
 * only ever see domain models. [firestore] is injected so tests can point it at the emulator.
 * Firestore errors are rethrown as [MissionRepositoryException]. [clock] tells [updateMission]
 * whether a mission is past its end, and is injected so tests can fix it.
 */
class FirestoreMissionRepository(
    private val firestore: FirebaseFirestore,
    private val clock: Clock = Clock.systemUTC(),
) : MissionRepository {

  private fun missionsOf(eventId: String) =
      firestore
          .collection(EventSchema.COLLECTION)
          .document(eventId)
          .collection(MissionSchema.COLLECTION)

  /**
   * Writes [mission] under a freshly generated document id in the subcollection of its event.
   *
   * [Mission.createdAt] is persisted as the caller supplied it: the repository does not invent
   * time, which keeps writes deterministic and testable.
   *
   * The overlap check reads the team's missions, then writes: it is not atomic, because the client
   * SDK cannot run a query inside a transaction. Two organizers creating conflicting missions at
   * the same moment could both succeed.
   */
  override suspend fun createMission(mission: Mission): Mission = translatingErrors {
    // Before the overlap check, so an invalid mission is refused without any read.
    requireValidCreation(mission)
    findConflict(mission)?.let { throw MissionOverlapException(it) }
    val document = missionsOf(mission.eventId).document()
    val persisted = mission.copy(id = document.id)
    document.set(persisted.toFirestoreMap()).await()
    persisted
  }

  override suspend fun getMission(eventId: String, missionId: String): Mission? =
      translatingErrors {
        missionsOf(eventId).document(missionId).get().await().takeIf { it.exists() }?.toMission()
      }

  override suspend fun getMissionsByEvent(eventId: String): List<Mission> = translatingErrors {
    missionsOf(eventId).get().await().documents.map { it.toMission() }
  }

  /**
   * One query per team on [MissionSchema.TEAM_ID], inside the event's subcollection, so the server
   * returns the missions of those teams only and the others are never downloaded.
   *
   * One equality query per team rather than a single `in` filter over all of them: a rule
   * restricting a manager to their own teams then has a single team id to check per request, and no
   * request grows past the document limits such a rule is subject to. Each team is a separate
   * request, but they are sent concurrently, so the call takes about as long as the slowest of them
   * rather than their sum. A mission belongs to one team, so none comes back twice, and no team
   * means no request at all, which is why an empty [teamIds] never reaches Firestore.
   */
  override suspend fun getMissionsByTeams(eventId: String, teamIds: Set<String>): List<Mission> =
      translatingErrors {
        coroutineScope {
          teamIds
              .map { teamId ->
                async {
                  missionsOf(eventId)
                      .whereEqualTo(MissionSchema.TEAM_ID, teamId)
                      .get()
                      .await()
                      .documents
                      .map { it.toMission() }
                }
              }
              .awaitAll()
              .flatten()
        }
      }

  /**
   * Queries the `missions` collection group, so one request covers every event. The missions are
   * sorted here rather than by Firestore: ordering an array-contains query on another field would
   * need a composite index, and a volunteer only has a handful of missions.
   */
  override suspend fun getMissionsAssignedTo(userId: String): List<Mission> = translatingErrors {
    assignedTo(userId).get().await().toAssignedMissions()
  }

  /**
   * Listens to the query of [getMissionsAssignedTo], so the same rules and indexes apply. A
   * listener error or a malformed mission ends the flow with a [MissionRepositoryException].
   */
  override fun observeMissionsAssignedTo(userId: String): Flow<List<Mission>> = callbackFlow {
    val registration =
        assignedTo(userId).addSnapshotListener { snapshot, error ->
          when {
            error != null -> close(toRepositoryException(error))
            snapshot != null ->
                try {
                  trySend(snapshot.toAssignedMissions())
                } catch (e: IllegalStateException) {
                  close(MissionRepositoryException.Unknown(e))
                }
          }
        }
    // Keeps the flow open while collected, then removes the listener so Firestore stops it.
    awaitClose { registration.remove() }
  }

  private fun assignedTo(userId: String): Query =
      firestore
          .collectionGroup(MissionSchema.COLLECTION)
          .whereArrayContains(MissionSchema.ASSIGNEE_IDS, userId)

  private fun QuerySnapshot.toAssignedMissions(): List<Mission> =
      documents.map { it.toMission() }.sortedBy { it.startAt }

  /**
   * A transaction, because the limit depends on the stored [Mission.volunteersNeeded]: reading it
   * before a plain write would check a value that may have changed in between. Like every
   * transaction, it needs a connection.
   */
  override suspend fun updateAssignees(
      eventId: String,
      missionId: String,
      assigneeIds: List<String>,
  ) {
    requireDistinctAssignees(assigneeIds)
    val document = missionsOf(eventId).document(missionId)
    translatingErrors {
      firestore
          .runTransaction { transaction ->
            val snapshot = transaction.get(document)
            if (!snapshot.exists()) {
              throw FirebaseFirestoreException(
                  "Mission '$missionId' of event '$eventId' does not exist",
                  Code.NOT_FOUND,
              )
            }
            val volunteersNeeded = snapshot.toMission().volunteersNeeded
            require(assigneeIds.size <= volunteersNeeded) {
              "A mission needing $volunteersNeeded volunteers cannot have ${assigneeIds.size} assignees"
            }
            transaction.update(document, MissionSchema.ASSIGNEE_IDS, assigneeIds)
            null
          }
          .await()
    }
  }

  /**
   * Updates the editable fields only, so the document keeps its id, team, assignees, creation time
   * and completion, and its check-ins are left as they are.
   *
   * The overlap check reads the team's missions before the write, so like in [createMission] it is
   * not atomic. The limit on [Mission.volunteersNeeded] depends on the stored mission, so it is
   * checked again in a transaction, as in [updateAssignees]. Like every transaction, it needs a
   * connection.
   *
   * `firestore.rules` is what refuses the change of a completed mission, measured by the server's
   * clock. The completion is checked here only to report it as a [MissionCompletedException] rather
   * than a denied write: before the overlap check, and again in the transaction, for a mission
   * completed in between.
   */
  override suspend fun updateMission(mission: Mission) {
    requireValidUpdate(mission)
    val document = missionsOf(mission.eventId).document(mission.id)
    translatingErrors {
      val stored = document.get().await().toStoredMission(mission)
      requireNotCompleted(stored, clock.instant())
      findConflict(stored.withEditableFieldsOf(mission))?.let { throw MissionOverlapException(it) }
      firestore
          .runTransaction { transaction ->
            val current = transaction.get(document).toStoredMission(mission)
            requireNotCompleted(current, clock.instant())
            requireRoomForAssignees(current, mission)
            transaction.update(document, mission.toEditableFieldsMap())
            null
          }
          .await()
    }
  }

  override suspend fun markCompleted(eventId: String, missionId: String): Unit = translatingErrors {
    missionsOf(eventId).document(missionId).update(MissionSchema.COMPLETED, true).await()
  }

  /**
   * The first other existing mission [mission] conflicts with, or null. Only a GeoTimed mission of
   * a team can conflict, so only then are that team's missions read. The stored version of
   * [mission] itself is skipped, so an update never conflicts with its own former slot.
   */
  private suspend fun findConflict(mission: Mission): Mission? {
    if (!mission.isGeoTimed || mission.teamId == null) return null
    return missionsOf(mission.eventId)
        .whereEqualTo(MissionSchema.TEAM_ID, mission.teamId)
        .get()
        .await()
        .documents
        .map { it.toMission() }
        .firstOrNull { it.id != mission.id && mission.conflictsWith(it) }
  }

  /** The mission stored in this snapshot, failing with NOT_FOUND when [mission] does not exist. */
  private fun DocumentSnapshot.toStoredMission(mission: Mission): Mission {
    if (!exists()) {
      throw FirebaseFirestoreException(
          "Mission '${mission.id}' of event '${mission.eventId}' does not exist",
          Code.NOT_FOUND,
      )
    }
    return toMission()
  }

  companion object {
    /** Translates a Firestore error into the [MissionRepositoryException] callers handle. */
    fun toRepositoryException(e: FirebaseFirestoreException): MissionRepositoryException =
        when (e.code) {
          Code.NOT_FOUND -> MissionRepositoryException.NotFound(e)
          Code.PERMISSION_DENIED,
          Code.UNAUTHENTICATED -> MissionRepositoryException.PermissionDenied(e)
          Code.UNAVAILABLE,
          Code.DEADLINE_EXCEEDED -> MissionRepositoryException.Unavailable(e)
          else -> MissionRepositoryException.Unknown(e)
        }

    /**
     * Runs [block], rethrowing its Firestore errors as [MissionRepositoryException]. Other errors,
     * such as an [IllegalArgumentException] for invalid input, pass through unchanged.
     */
    private inline fun <T> translatingErrors(block: () -> T): T =
        try {
          block()
        } catch (e: FirebaseFirestoreException) {
          throw toRepositoryException(e)
        }
  }
}
