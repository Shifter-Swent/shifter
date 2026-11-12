// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.team

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.swent.shifter.model.chat.ChatMessageSchema
import com.swent.shifter.model.checkin.CheckInSchema
import com.swent.shifter.model.event.EventSchema
import com.swent.shifter.model.location.LiveLocationSchema
import com.swent.shifter.model.membership.MembershipRequestSchema
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.mission.MissionSchema
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

  private fun eventDocument(eventId: String) =
      firestore.collection(EventSchema.COLLECTION).document(eventId)

  private fun teamsOf(eventId: String) = eventDocument(eventId).collection(TeamSchema.COLLECTION)

  private fun teamDocument(eventId: String, teamId: String) = teamsOf(eventId).document(teamId)

  /**
   * Writes [team] under a freshly generated document id in the subcollection of its event, and
   * indexes its manager, if it has one, in the same commit.
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
      val batch = firestore.batch().set(document, persisted.toFirestoreMap())
      persisted.managerId?.let { batch.indexManager(document, it) }
      batch.commit().await()
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
   *
   * The index of the event's managers ([ManagerIndexSchema]) changes in the same commit, so the
   * previous manager loses what managing gives access to as soon as the new one gets it.
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
            transaction.indexManagerChange(document, team.managerId, managerId)
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
   * A plain update of the three fields, which Firestore refuses with `NOT_FOUND` when the team is
   * gone. Offline, it is queued like any write.
   */
  override suspend fun updateTeam(
      eventId: String,
      teamId: String,
      name: String,
      description: String,
      icon: String,
  ) {
    translatingErrors {
      teamDocument(eventId, teamId)
          .update(
              mapOf(
                  TeamSchema.NAME to name,
                  TeamSchema.DESCRIPTION to description,
                  TeamSchema.ICON to icon,
              )
          )
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
   * Several commits, since a batch holds at most [MAX_BATCH_WRITES] writes, ordered so that an
   * interrupted deletion is completed by calling it again:
   * 1. The team is marked [TeamSchema.DELETING], which the rules require to delete its messages,
   *    and which stops new messages and new missions, so none arrives after they were read.
   * 2. Its missions, each in one commit with its check-ins, which the rules only let go with it.
   * 3. Its chat messages.
   * 4. The team itself last, in one batch with its manager's index entry, the positions its members
   *    shared with it and the pending applications to it, all taken from the team as it is then:
   *    the rules read the team to let the organizer list its messages and to delete those
   *    applications, so nothing under it could be found once it is gone.
   *
   * Everything is read from the server: offline, the cache could miss documents, which would be
   * left behind once the team is gone.
   *
   * Pending applications sent after they were read, and check-ins recorded after a mission's were
   * read, are left behind: the window lasts a few round trips and needs someone to act in it,
   * unlike a member's app, which republishes its position on its own. The final batch holds one
   * write per member and per pending application, plus two, so a team with more than 498 of them
   * together cannot be deleted. The same for a position of a former member, who left the team
   * outside these repositories without deleting it: positions are deleted by the uids of the
   * members, since the rules only let the organizer list them during the event.
   */
  override suspend fun deleteTeam(eventId: String, teamId: String) {
    val document = teamDocument(eventId, teamId)
    translatingErrors {
      if (!document.get(Source.SERVER).await().exists()) return
      document.update(TeamSchema.DELETING, true).await()
      deleteMissionsOf(eventId, teamId)
      val messages = document.collection(ChatMessageSchema.COLLECTION).get(Source.SERVER).await()
      deleteInBatches(messages.documents.map { it.reference })
      val pendingApplications =
          eventDocument(eventId)
              .collection(MembershipRequestSchema.COLLECTION)
              .whereEqualTo(MembershipRequestSchema.TEAM_ID, teamId)
              .whereEqualTo(MembershipRequestSchema.STATUS, MembershipRequestStatus.PENDING.name)
              .get(Source.SERVER)
              .await()
      // Read last, as the manager or the members may have changed since the start: what goes with
      // the team is what it has when it goes. Gone meanwhile: another deletion finished it.
      val current = document.get(Source.SERVER).await()
      if (!current.exists()) return
      val batch = firestore.batch().delete(document)
      current.teamManagerId()?.let { batch.unindexManager(document, it) }
      // In this commit rather than before it: a member's app republishes its position every few
      // minutes, which the rules refuse only once the team is gone.
      current.teamMemberIds().forEach { batch.delete(liveLocationOf(document, it)) }
      pendingApplications.documents.forEach { batch.delete(it.reference) }
      batch.commit().await()
    }
  }

  /** Deletes the missions of the team [teamId], each in one commit with its check-ins. */
  private suspend fun deleteMissionsOf(eventId: String, teamId: String) {
    val missions =
        eventDocument(eventId)
            .collection(MissionSchema.COLLECTION)
            .whereEqualTo(MissionSchema.TEAM_ID, teamId)
            .get(Source.SERVER)
            .await()
    for (mission in missions.documents) {
      val checkIns =
          mission.reference.collection(CheckInSchema.COLLECTION).get(Source.SERVER).await()
      val batch = firestore.batch()
      checkIns.documents.forEach { batch.delete(it.reference) }
      batch.delete(mission.reference).commit().await()
    }
  }

  /** Deletes [documents] in as few batches as Firestore allows. */
  private suspend fun deleteInBatches(documents: List<DocumentReference>) {
    for (chunk in documents.chunked(MAX_BATCH_WRITES)) {
      val batch = firestore.batch()
      chunk.forEach { batch.delete(it) }
      batch.commit().await()
    }
  }

  /**
   * Where [userId] last shared their position with the manager of [team]. Only the path is taken
   * from [LiveLocationSchema], which names that collection once for the whole module: this
   * repository does not depend on the live location repository, and `docs/firestore-schema.md`
   * records the path.
   */
  private fun liveLocationOf(team: DocumentReference, userId: String) =
      team.collection(LiveLocationSchema.COLLECTION).document(userId)

  private suspend fun Query.fetch(): List<Team> = translatingErrors {
    get().await().documents.map { it.toTeam() }
  }

  companion object {
    /** The most writes Firestore accepts in one batch. */
    private const val MAX_BATCH_WRITES = 500

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
