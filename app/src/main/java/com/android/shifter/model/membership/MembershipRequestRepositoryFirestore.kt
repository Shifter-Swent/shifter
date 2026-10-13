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
      val team = requestedTeam(eventId, userId)
      val batch = db.batch()
      batch.update(
          requests(eventId).document(userId),
          MembershipRequestSchema.STATUS,
          MembershipRequestStatus.REJECTED.name,
      )
      // Merge also handles rejection before a participants document exists.
      batch.set(
          participants(eventId),
          mapOf(PARTICIPANT_IDS to FieldValue.arrayRemove(userId)),
          SetOptions.merge(),
      )
      // Rejecting an accepted request takes the volunteer out of their team as well.
      batch.update(team, TeamSchema.MEMBER_IDS, FieldValue.arrayRemove(userId))
      batch.commit().await()
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
            val status = snapshot.getString(MembershipRequestSchema.STATUS)
            if (status == MembershipRequestStatus.ACCEPTED.name) {
              transaction.update(
                  participants(eventId),
                  PARTICIPANT_IDS,
                  FieldValue.arrayRemove(userId),
              )
              snapshot.getString(MembershipRequestSchema.TEAM_ID)?.let { teamId ->
                transaction.update(
                    team(eventId, teamId),
                    TeamSchema.MEMBER_IDS,
                    FieldValue.arrayRemove(userId),
                )
              }
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
    if (!snapshot.exists()) {
      throw MembershipRequestRepositoryException.Unknown(
          NoSuchElementException("No request from '$userId' for event '$eventId'")
      )
    }
    return team(eventId, snapshot.toRequestOrThrow().teamId)
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
