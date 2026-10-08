// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.team

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.swent.shifter.model.event.EventSchema
import kotlinx.coroutines.tasks.await

/**
 * [TeamRepository] backed by Cloud Firestore, storing each team in the subcollection of its event:
 * `/events/{eventId}/teams/{teamId}`.
 *
 * All Firebase types stay inside this class and the [toTeam] / [toFirestoreMap] mappers: callers
 * only ever see domain models. [firestore] is injected so tests can point it at the emulator.
 *
 * Members are added and removed with `arrayUnion` / `arrayRemove` rather than by rewriting the
 * list, so two organizers editing the same team at once cannot overwrite each other's change.
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
    val document = teamsOf(team.eventId).document()
    val persisted = team.copy(id = document.id)
    document.set(persisted.toFirestoreMap()).await()
    return persisted
  }

  override suspend fun getTeam(eventId: String, teamId: String): Team? =
      teamDocument(eventId, teamId).get().await().takeIf { it.exists() }?.toTeam()

  override suspend fun getTeamsByEvent(eventId: String): List<Team> = teamsOf(eventId).fetch()

  override suspend fun getTeamsOfMember(eventId: String, userId: String): List<Team> =
      teamsOf(eventId).whereArrayContains(TeamSchema.MEMBER_IDS, userId).fetch()

  override suspend fun getTeamsManagedBy(eventId: String, userId: String): List<Team> =
      teamsOf(eventId).whereEqualTo(TeamSchema.MANAGER_ID, userId).fetch()

  /** `update` rather than `set`, so a missing team fails instead of being created half-filled. */
  override suspend fun setManager(eventId: String, teamId: String, managerId: String?) {
    val changes =
        if (managerId == null) mapOf(TeamSchema.MANAGER_ID to null)
        else
            mapOf(
                TeamSchema.MANAGER_ID to managerId,
                TeamSchema.MEMBER_IDS to FieldValue.arrayRemove(managerId),
            )
    teamDocument(eventId, teamId).update(changes).await()
  }

  /**
   * A transaction, because whether [userId] may join depends on the current manager: checking it
   * before a plain write would let a concurrent [setManager] slip in between. Like every
   * transaction, it needs a connection.
   */
  override suspend fun addMember(eventId: String, teamId: String, userId: String) {
    val document = teamDocument(eventId, teamId)
    firestore
        .runTransaction { transaction ->
          val team = transaction.get(document)
          if (!team.exists()) {
            throw FirebaseFirestoreException(
                "Team '$teamId' of event '$eventId' does not exist",
                FirebaseFirestoreException.Code.NOT_FOUND,
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

  override suspend fun removeMember(eventId: String, teamId: String, userId: String) {
    teamDocument(eventId, teamId)
        .update(TeamSchema.MEMBER_IDS, FieldValue.arrayRemove(userId))
        .await()
  }

  private suspend fun Query.fetch(): List<Team> = get().await().documents.map { it.toTeam() }
}
