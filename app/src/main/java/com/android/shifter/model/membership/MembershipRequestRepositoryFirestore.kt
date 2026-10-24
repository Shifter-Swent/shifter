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
import com.swent.shifter.model.event.EventSchema
import com.swent.shifter.model.team.TeamSchema
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
                val teamExists = transaction.get(team).exists()
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
                // unless
                // the team was deleted meanwhile: then there is nothing to leave.
                if (teamExists) {
                  transaction.update(team, TeamSchema.MEMBER_IDS, FieldValue.arrayRemove(userId))
                }
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
            // Read before any write, as a transaction requires: a manager who leaves leaves the
            // team without one, or they would keep managing an event they no longer take part in.
            val managesTeam =
                team != null && transaction.get(team).getString(TeamSchema.MANAGER_ID) == userId
            if (accepted) {
              transaction.update(
                  participants(eventId),
                  PARTICIPANT_IDS,
                  FieldValue.arrayRemove(userId),
              )
            }
            team?.let {
              transaction.update(it, TeamSchema.MEMBER_IDS, FieldValue.arrayRemove(userId))
              if (managesTeam) transaction.update(it, TeamSchema.MANAGER_ID, null)
            }
            transaction.delete(request)
            null
          }
          .await()
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
   * The team [userId] applied to in [eventId], read from their request. The team of a request never
   * changes once it is sent, so it can be read before the batch that decides the request.
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
