// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.team

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.Query
import com.swent.shifter.model.event.EventSchema
import kotlinx.coroutines.tasks.await

/**
 * [TeamRepository] backed by Cloud Firestore, storing each team in the subcollection of its event:
 * `/events/{eventId}/teams/{teamId}`.
 *
 * All Firebase types stay inside this class and the [toTeam] / [toFirestoreMap] mappers: callers
 * only ever see domain models. [firestore] is injected so tests can point it at the emulator.
 * Firestore errors are rethrown as [TeamRepositoryException].
 *
 * Members are added and removed with `arrayUnion` / `arrayRemove` rather than by rewriting the
 * list, so two organizers editing the same team at once cannot overwrite each other's change. The
 * one exception is [setManager], which rewrites the list, since the previous manager goes back to
 * it: it does so in a transaction, which Firestore retries when the team changes meanwhile.
 */
class FirestoreTeamRepository(private val firestore: FirebaseFirestore) : TeamRepository {

  private fun teamsOf(eventId: String) =
      firestore
          .collection(EventSchema.COLLECTION)
          .document(eventId)
          .collection(TeamSchema.COLLECTION)

  private fun teamDocument(eventId: String, teamId: String) = teamsOf(eventId).document(teamId)

  /**
   * Writes [team] under a freshly generated document id in the subcollection of its event.
   *
   * [Team.createdAt] is persisted as the caller supplied it: the repository does not invent time,
   * which keeps writes deterministic and testable.
   */
  override suspend fun createTeam(team: Team): Team {
    // Firestore would also reject an empty id, but with a message that does not say which one.
    require(team.eventId.isNotEmpty()) { "A team must belong to an event" }
    require(team.managerId == null || team.managerId !in team.memberIds) {
      "The manager of a team cannot also be one of its members"
    }
    return translatingErrors {
      val document = teamsOf(team.eventId).document()
      val persisted = team.copy(id = document.id)
      document.set(persisted.toFirestoreMap()).await()
      persisted
    }
  }

  override suspend fun getTeam(eventId: String, teamId: String): Team? = translatingErrors {
    teamDocument(eventId, teamId).get().await().takeIf { it.exists() }?.toTeam()
  }

  override suspend fun getTeamsByEvent(eventId: String): List<Team> = teamsOf(eventId).fetch()

  override suspend fun getTeamsOfMember(eventId: String, userId: String): List<Team> =
      teamsOf(eventId).whereArrayContains(TeamSchema.MEMBER_IDS, userId).fetch()

  override suspend fun getTeamsManagedBy(eventId: String, userId: String): List<Team> =
      teamsOf(eventId).whereEqualTo(TeamSchema.MANAGER_ID, userId).fetch()

  /**
   * A transaction, because the members written depend on the current manager and members: the
   * previous manager goes back to the members while the new one leaves them, which a single
   * `arrayUnion` or `arrayRemove` cannot do. Like every transaction, it needs a connection.
   */
  override suspend fun setManager(eventId: String, teamId: String, managerId: String?) {
    val document = teamDocument(eventId, teamId)
    translatingErrors {
      firestore
          .runTransaction { transaction ->
            val snapshot = transaction.get(document)
            if (!snapshot.exists()) {
              throw FirebaseFirestoreException(
                  "Team '$teamId' of event '$eventId' does not exist",
                  Code.NOT_FOUND,
              )
            }
            val team = snapshot.toTeam()
            require(
                managerId == null || managerId == team.managerId || managerId in team.memberIds
            ) {
              "Only a member of the team can be appointed its manager"
            }
            transaction.update(
                document,
                mapOf(
                    TeamSchema.MANAGER_ID to managerId,
                    TeamSchema.MEMBER_IDS to team.membersAfterAppointing(managerId),
                ),
            )
            // Appointing a member leaves them out of the members, so the position they shared as a
            // volunteer goes with it, in the same commit: a manager is not a member and cannot
            // share one, so nothing would replace or remove it afterwards (#305). Nothing to clean
            // up when the team loses its manager instead: the previous one goes back to the
            // members, and a manager has no position stored.
            if (managerId != null) transaction.delete(liveLocationOf(document, managerId))
            null
          }
          .await()
    }
  }

  /**
   * A transaction, because whether [userId] may join depends on the current manager: checking it
   * before a plain write would let a concurrent [setManager] slip in between. Like every
   * transaction, it needs a connection.
   */
  override suspend fun addMember(eventId: String, teamId: String, userId: String) {
    val document = teamDocument(eventId, teamId)
    translatingErrors {
      firestore
          .runTransaction { transaction ->
            val team = transaction.get(document)
            if (!team.exists()) {
              throw FirebaseFirestoreException(
                  "Team '$teamId' of event '$eventId' does not exist",
                  Code.NOT_FOUND,
              )
            }
            require(team.get(TeamSchema.MANAGER_ID) != userId) {
              "The manager of a team cannot also be one of its members"
            }
            transaction.update(document, TeamSchema.MEMBER_IDS, FieldValue.arrayUnion(userId))
            null
          }
          .await()
    }
  }

  /**
   * Firestore refuses to update a missing document with `NOT_FOUND`, reported as
   * [TeamRepositoryException.NotFound], the batch below included: a removal from a team that is
   * gone still fails, and deletes nothing.
   *
   * A batch rather than a plain update, so the position the volunteer shared with this team's
   * manager goes out together with their membership (#305) instead of in a second write that an
   * offline device or a killed app would never make. It needs no read, so no transaction either.
   */
  override suspend fun removeMember(eventId: String, teamId: String, userId: String) {
    translatingErrors {
      val document = teamDocument(eventId, teamId)
      val batch = firestore.batch()
      batch.update(document, TeamSchema.MEMBER_IDS, FieldValue.arrayRemove(userId))
      batch.delete(liveLocationOf(document, userId))
      batch.commit().await()
    }
  }

  /**
   * Where [userId] last shared their position with the manager of [team]. Spelled out from
   * [TeamSchema] rather than taken from the live location repository, which this repository does
   * not depend on: only the path is needed, and `docs/firestore-schema.md` records it.
   */
  private fun liveLocationOf(team: DocumentReference, userId: String) =
      team.collection(TeamSchema.LIVE_LOCATIONS_COLLECTION).document(userId)

  private suspend fun Query.fetch(): List<Team> = translatingErrors {
    get().await().documents.map { it.toTeam() }
  }

  companion object {
    /** Translates a Firestore error into the [TeamRepositoryException] callers handle. */
    fun toRepositoryException(e: FirebaseFirestoreException): TeamRepositoryException =
        when (e.code) {
          Code.NOT_FOUND -> TeamRepositoryException.NotFound(e)
          Code.PERMISSION_DENIED,
          Code.UNAUTHENTICATED -> TeamRepositoryException.PermissionDenied(e)
          Code.UNAVAILABLE,
          Code.DEADLINE_EXCEEDED -> TeamRepositoryException.Unavailable(e)
          else -> TeamRepositoryException.Unknown(e)
        }

    /**
     * Runs [block], rethrowing its Firestore errors as [TeamRepositoryException]. Other errors,
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
