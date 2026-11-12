// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.membership

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import com.swent.shifter.model.event.EventSchema
import com.swent.shifter.model.location.LiveLocationSchema
import com.swent.shifter.model.mission.MissionSchema
import com.swent.shifter.model.mission.isCompleted
import com.swent.shifter.model.team.TeamSchema
import com.swent.shifter.model.team.indexManagerChange
import java.time.Instant
import kotlinx.coroutines.tasks.await

/**
 * [MembershipRequestRepository] backed by `events/{eventId}/membershipRequests/{userId}`.
 *
 * The document id is the volunteer's user id, so a volunteer cannot send two requests to the same
 * event and the security rules can check they only write their own.
 */
class MembershipRequestRepositoryFirestore(private val db: FirebaseFirestore) :
    MembershipRequestRepository {

  override suspend fun apply(eventId: String, request: MembershipRequest): MembershipRequest =
      translatingErrors {
        val ref = requests(eventId).document(request.userId)
        val pending = request.copy(id = request.userId, status = MembershipRequestStatus.PENDING)
        // A transaction makes check-then-create atomic, so a double tap cannot overwrite a
        // decision. It returns the existing document, or null once it wrote the pending request.
        val existing =
            db.runTransaction { transaction ->
                  transaction.get(ref).takeIf { it.exists() }
                      ?: null.also { transaction.set(ref, pending.toFirestoreMap()) }
                }
                .await()
        existing?.toRequestOrThrow() ?: pending
      }

  override suspend fun accept(eventId: String, userId: String) {
    translatingErrors {
      val team = requestedTeam(eventId, userId)
      // The three writes commit atomically: a failed write prevents the others from being applied.
      val batch = db.batch()
      batch.update(
          requests(eventId).document(userId),
          MembershipRequestSchema.STATUS,
          MembershipRequestStatus.ACCEPTED.name,
      )
      batch.set(
          participants(eventId),
          mapOf(PARTICIPANT_IDS to FieldValue.arrayUnion(userId)),
          SetOptions.merge(),
      )
      batch.update(team, TeamSchema.MEMBER_IDS, FieldValue.arrayUnion(userId))
      batch.commit().await()
    }
  }

  override suspend fun reject(eventId: String, userId: String) {
    translatingErrors {
      val request = requests(eventId).document(userId)
      // A transaction, so the team is left alone only if it is really gone when the write lands. It
      // returns the request when it cannot be decided, or null once the decision is written.
      val undecided =
          db.runTransaction { transaction ->
                val snapshot = transaction.get(request)
                val teamId =
                    (snapshot.get(MembershipRequestSchema.TEAM_ID) as? String)?.takeIf {
                      it.isNotBlank()
                    }
                if (!snapshot.exists() || teamId == null) return@runTransaction snapshot
                val team = team(eventId, teamId)
                val teamSnapshot = transaction.get(team)
                transaction.update(
                    request,
                    MembershipRequestSchema.STATUS,
                    MembershipRequestStatus.REJECTED.name,
                )
                // Merge also handles rejection before a participants document exists.
                transaction.set(
                    participants(eventId),
                    mapOf(PARTICIPANT_IDS to FieldValue.arrayRemove(userId)),
                    SetOptions.merge(),
                )
                // Rejecting an accepted request takes the volunteer out of their team as well,
                // unless the team was deleted meanwhile: then there is nothing to leave.
                if (teamSnapshot.exists()) transaction.leaveTeam(team, teamSnapshot, userId)
                null
              }
              .await()
      undecided?.let { throw undecidable(it, eventId, userId) }
    }
  }

  override suspend fun withdraw(eventId: String, userId: String) {
    translatingErrors {
      val request = requests(eventId).document(userId)
      // The rules keep an ACCEPTED request and participation in step, so the volunteer's own
      // request says whether they are a participant: they need not read the participants document.
      // A pending request may have no participants document yet, and a volunteer may not create it.
      db.runTransaction { transaction ->
            val snapshot = transaction.get(request)
            val accepted =
                snapshot.getString(MembershipRequestSchema.STATUS) ==
                    MembershipRequestStatus.ACCEPTED.name
            val team =
                snapshot
                    .getString(MembershipRequestSchema.TEAM_ID)
                    ?.takeIf { accepted }
                    ?.let { team(eventId, it) }
            // Read before any write, as a transaction requires.
            val teamSnapshot = team?.let { transaction.get(it) }
            if (accepted) {
              transaction.update(
                  participants(eventId),
                  PARTICIPANT_IDS,
                  FieldValue.arrayRemove(userId),
              )
            }
            if (team != null && teamSnapshot != null) {
              transaction.leaveTeam(team, teamSnapshot, userId)
            }
            transaction.delete(request)
            null
          }
          .await()
    }
  }

  /**
   * The missions are found before the transaction, which cannot run a query: the open missions of
   * the old team that [userId] is assigned to. A mission assigned to them meanwhile keeps them, as
   * it would if it were assigned right after the move. If their request changed team meanwhile,
   * that list is outdated, so the move is refused rather than leaving them on the missions of their
   * old team.
   *
   * A refusal is returned by the transaction and thrown after it: thrown inside, a
   * [FirebaseFirestoreException] would make the SDK run the transaction again, up to five times.
   */
  override suspend fun moveToTeam(eventId: String, userId: String, teamId: String) {
    require(teamId.isNotBlank()) { "The team to move '$userId' to is blank" }
    translatingErrors {
      val now = Instant.now()
      val request = requests(eventId).document(userId)
      val previousTeamId = request.get().await().getString(MembershipRequestSchema.TEAM_ID)
      val missions =
          db.collection(EventSchema.COLLECTION)
              .document(eventId)
              .collection(MissionSchema.COLLECTION)
              .whereArrayContains(MissionSchema.ASSIGNEE_IDS, userId)
              .get()
              .await()
              .documents
              .filter { it.isOpenMissionOf(previousTeamId, now) }
              .map { it.reference }
      val refusal: String? =
          db.runTransaction { transaction ->
                val snapshot = transaction.get(request)
                if (
                    snapshot.getString(MembershipRequestSchema.STATUS) !=
                        MembershipRequestStatus.ACCEPTED.name
                ) {
                  return@runTransaction "'$userId' is not an accepted volunteer of event '$eventId'"
                }
                val target = team(eventId, teamId)
                val targetSnapshot = transaction.get(target)
                if (!targetSnapshot.exists()) {
                  return@runTransaction "Team '$teamId' of event '$eventId' does not exist"
                }
                val from = snapshot.getString(MembershipRequestSchema.TEAM_ID)
                if (from == teamId) {
                  transaction.rejoin(target, targetSnapshot, userId)
                  return@runTransaction null
                }
                if (from != previousTeamId) {
                  return@runTransaction "The team of '$userId' changed meanwhile, try again"
                }
                val previous = from?.let { team(eventId, it) }
                val previousSnapshot = previous?.let { transaction.get(it) }
                if (previousSnapshot?.getString(TeamSchema.MANAGER_ID) == userId) {
                  return@runTransaction "'$userId' manages their team and cannot be moved"
                }
                // Read again, so a mission deleted, given to another team or closed meanwhile is
                // left alone.
                val assigned = missions.filter { transaction.get(it).isOpenMissionOf(from, now) }
                if (previous != null && previousSnapshot?.exists() == true) {
                  transaction.leaveTeam(previous, previousSnapshot, userId)
                }
                transaction.update(target, TeamSchema.MEMBER_IDS, FieldValue.arrayUnion(userId))
                transaction.update(request, MembershipRequestSchema.TEAM_ID, teamId)
                assigned.forEach {
                  transaction.update(it, MissionSchema.ASSIGNEE_IDS, FieldValue.arrayRemove(userId))
                }
                null
              }
              .await()
      refusal?.let { throw MembershipRequestRepositoryException.Unknown(IllegalStateException(it)) }
    }
  }

  override suspend fun getMembershipRequest(eventId: String, userId: String): MembershipRequest? =
      translatingErrors {
        requests(eventId).document(userId).get().await().takeIf { it.exists() }?.toRequestOrThrow()
      }

  override suspend fun getMembershipRequestsByEId(eventId: String): List<MembershipRequest> =
      translatingErrors {
        requests(eventId).get().await().documents.map { it.toRequestOrThrow() }
      }

  override suspend fun getMembershipRequestsByUId(userId: String): Map<String, MembershipRequest> =
      translatingErrors {
        db.collectionGroup(MembershipRequestSchema.COLLECTION)
            .whereEqualTo(MembershipRequestSchema.USER_ID, userId)
            .get()
            .await()
            .documents
            .associate { document ->
              val eventId =
                  document.reference.parent.parent?.id
                      ?: throw MembershipRequestRepositoryException.Unknown(
                          IllegalStateException(
                              "Membership request '${document.id}' has no parent event"
                          )
                      )
              eventId to document.toRequestOrThrow()
            }
      }

  private fun requests(eventId: String): CollectionReference =
      db.collection(EventSchema.COLLECTION)
          .document(eventId)
          .collection(MembershipRequestSchema.COLLECTION)

  private fun participants(eventId: String): DocumentReference =
      db.collection(PARTICIPANTS_COLLECTION).document(eventId)

  private fun team(eventId: String, teamId: String): DocumentReference =
      db.collection(EventSchema.COLLECTION)
          .document(eventId)
          .collection(TeamSchema.COLLECTION)
          .document(teamId)

  /**
   * Takes [userId] out of [team], read as [snapshot] earlier in this transaction: out of its
   * members, out of its management when they lead it, or they would keep managing a team of an
   * event they no longer take part in, with the index of the event's managers, and out of the
   * positions shared with its manager. Nothing is written to the team itself when they are in
   * neither: the rules refuse a withdrawal that changes nothing on the team, which would fail the
   * whole transaction.
   *
   * The position goes in the same commit as the membership, so the manager never keeps reading the
   * position of someone who has left (#305). It is deleted whether one is stored or not: a delete
   * needs no read, which a transaction would otherwise have to do before every write, and deleting
   * a document that is not there is a no-op in Firestore. That also takes out a position left
   * behind by a `memberIds` written directly, outside this repository.
   */
  private fun Transaction.leaveTeam(
      team: DocumentReference,
      snapshot: DocumentSnapshot,
      userId: String,
  ) {
    val memberIds = snapshot.get(TeamSchema.MEMBER_IDS) as? List<*> ?: emptyList<Any>()
    if (userId in memberIds) update(team, TeamSchema.MEMBER_IDS, FieldValue.arrayRemove(userId))
    if (snapshot.getString(TeamSchema.MANAGER_ID) == userId) {
      update(team, TeamSchema.MANAGER_ID, null)
      indexManagerChange(team, previous = userId, next = null)
    }
    delete(team.collection(LiveLocationSchema.COLLECTION).document(userId))
  }

  /**
   * Puts [userId] back among the members of [team], read as [snapshot] earlier in this transaction,
   * when their request names it but they are missing from its `memberIds`. Nothing is written when
   * they are in it already, or when they manage it, since a manager is never one of its members.
   */
  private fun Transaction.rejoin(
      team: DocumentReference,
      snapshot: DocumentSnapshot,
      userId: String,
  ) {
    val memberIds = snapshot.get(TeamSchema.MEMBER_IDS) as? List<*> ?: emptyList<Any>()
    if (userId in memberIds || snapshot.getString(TeamSchema.MANAGER_ID) == userId) return
    update(team, TeamSchema.MEMBER_IDS, FieldValue.arrayUnion(userId))
  }

  /**
   * The team [userId] applied to in [eventId], read from their request. Only an accepted request
   * changes team, when [moveToTeam] moves the volunteer, so it can be read before the batch that
   * decides the request.
   */
  private suspend fun requestedTeam(eventId: String, userId: String): DocumentReference {
    val snapshot = requests(eventId).document(userId).get().await()
    if (!snapshot.exists()) throw undecidable(snapshot, eventId, userId)
    return team(eventId, snapshot.toRequestOrThrow().teamId)
  }

  /**
   * Why the request in [snapshot] cannot be decided: it does not exist, or it does not match the
   * schema. Both are reported as [MembershipRequestRepositoryException.Unknown].
   */
  private fun undecidable(
      snapshot: DocumentSnapshot,
      eventId: String,
      userId: String,
  ): MembershipRequestRepositoryException {
    if (!snapshot.exists()) {
      return MembershipRequestRepositoryException.Unknown(
          NoSuchElementException("No request from '$userId' for event '$eventId'")
      )
    }
    snapshot.toRequestOrThrow()
    return MembershipRequestRepositoryException.Unknown(
        IllegalStateException("Membership request '${snapshot.id}' has no team")
    )
  }

  companion object {
    private const val PARTICIPANTS_COLLECTION = "eventParticipants"
    private const val PARTICIPANT_IDS = "participantIds"

    /**
     * Translates a Firestore error into the [MembershipRequestRepositoryException] callers handle.
     */
    fun toRepositoryException(e: FirebaseFirestoreException): MembershipRequestRepositoryException =
        when (e.code) {
          Code.PERMISSION_DENIED,
          Code.UNAUTHENTICATED -> MembershipRequestRepositoryException.PermissionDenied(e)
          Code.UNAVAILABLE,
          Code.DEADLINE_EXCEEDED -> MembershipRequestRepositoryException.Unavailable(e)
          else -> MembershipRequestRepositoryException.Unknown(e)
        }

    /**
     * Whether this mission exists, belongs to [teamId] and is still open at [now]: neither closed
     * by hand nor over. A move leaves the volunteer on the others, whose attendance is built from
     * their assignees, so the missions they already worked keep showing their check-in. A mission
     * without an end is kept open, as the mapper would reject it anyway.
     */
    private fun DocumentSnapshot.isOpenMissionOf(teamId: String?, now: Instant): Boolean {
      if (!exists() || getString(MissionSchema.TEAM_ID) != teamId) return false
      val endAt = getTimestamp(MissionSchema.END_AT)?.toInstant() ?: return true
      return !isCompleted(getBoolean(MissionSchema.COMPLETED) == true, endAt, now)
    }

    private inline fun <T> translatingErrors(block: () -> T): T =
        try {
          block()
        } catch (e: FirebaseFirestoreException) {
          throw toRepositoryException(e)
        }

    /**
     * Maps a stored document, reporting one that does not match the schema as
     * [MembershipRequestRepositoryException.Unknown]. The mapper signals it with an
     * [IllegalStateException], which is only caught around this pure call: catching it around an
     * `await()` would also swallow coroutine cancellation, a subclass of it.
     */
    private fun DocumentSnapshot.toRequestOrThrow(): MembershipRequest =
        try {
          toMembershipRequest()
        } catch (e: IllegalStateException) {
          throw MembershipRequestRepositoryException.Unknown(e)
        }
  }
}
