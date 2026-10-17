// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.swent.shifter.model.event.EventSchema
import kotlinx.coroutines.tasks.await

/**
 * [MissionRepository] backed by Cloud Firestore, storing each mission in the subcollection of its
 * event: `/events/{eventId}/missions/{missionId}`.
 *
 * All Firebase types stay inside this class and the [toMission] / [toFirestoreMap] mappers: callers
 * only ever see domain models. [firestore] is injected so tests can point it at the emulator.
 */
class FirestoreMissionRepository(private val firestore: FirebaseFirestore) : MissionRepository {

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
  override suspend fun createMission(mission: Mission): Mission {
    // Firestore would also reject an empty id, but with a message that does not say which one.
    require(mission.eventId.isNotEmpty()) { "A mission must belong to an event" }
    findConflict(mission)?.let { throw MissionOverlapException(it) }
    val document = missionsOf(mission.eventId).document()
    val persisted = mission.copy(id = document.id)
    document.set(persisted.toFirestoreMap()).await()
    return persisted
  }

  override suspend fun getMission(eventId: String, missionId: String): Mission? =
      missionsOf(eventId).document(missionId).get().await().takeIf { it.exists() }?.toMission()

  override suspend fun getMissionsByEvent(eventId: String): List<Mission> =
      missionsOf(eventId).get().await().documents.map { it.toMission() }

  /**
   * Queries the `missions` collection group, so one request covers every event. The missions are
   * sorted here rather than by Firestore: ordering an array-contains query on another field would
   * need a composite index, and a volunteer only has a handful of missions.
   */
  override suspend fun getMissionsAssignedTo(userId: String): List<Mission> =
      firestore
          .collectionGroup(MissionSchema.COLLECTION)
          .whereArrayContains(MissionSchema.ASSIGNEE_IDS, userId)
          .get()
          .await()
          .documents
          .map { it.toMission() }
          .sortedBy { it.startAt }

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
    require(assigneeIds.size == assigneeIds.toSet().size) {
      "A mission cannot list the same assignee twice"
    }
    val document = missionsOf(eventId).document(missionId)
    firestore
        .runTransaction { transaction ->
          val snapshot = transaction.get(document)
          if (!snapshot.exists()) {
            throw FirebaseFirestoreException(
                "Mission '$missionId' of event '$eventId' does not exist",
                FirebaseFirestoreException.Code.NOT_FOUND,
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

  /**
   * The first existing mission [mission] conflicts with, or null. Only a GeoTimed mission of a team
   * can conflict, so only then are that team's missions read.
   */
  private suspend fun findConflict(mission: Mission): Mission? {
    if (!mission.isGeoTimed || mission.teamId == null) return null
    return missionsOf(mission.eventId)
        .whereEqualTo(MissionSchema.TEAM_ID, mission.teamId)
        .get()
        .await()
        .documents
        .map { it.toMission() }
        .firstOrNull { mission.conflictsWith(it) }
  }
}
