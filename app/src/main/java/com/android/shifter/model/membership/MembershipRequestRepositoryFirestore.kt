// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.membership

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.swent.shifter.model.event.EventSchema
import kotlinx.coroutines.tasks.await

/**
 * [MembershipRequestRepository] backed by `events/{eventId}/membershipRequests/{userId}`.
 *
 * The document id is the volunteer's user id, so a volunteer cannot send two requests to the same
 * event and the security rules can check they only write their own.
 */
class MembershipRequestRepositoryFirestore(private val db: FirebaseFirestore) :
    MembershipRequestRepository {

  override suspend fun getRequest(eventId: String, userId: String): MembershipRequest? =
      translatingErrors {
        requests(eventId).document(userId).get().await().takeIf { it.exists() }?.toRequestOrThrow()
      }

  override suspend fun requestToJoin(
      eventId: String,
      request: MembershipRequest,
  ): MembershipRequest = translatingErrors {
    val ref = requests(eventId).document(request.userId)
    val pending = request.copy(id = request.userId, status = MembershipRequestStatus.PENDING)
    // A transaction makes check-then-create atomic, so a double tap cannot overwrite a decision.
    // It returns the existing document, or null once it has written the pending request.
    val existing =
        db.runTransaction { transaction ->
              transaction.get(ref).takeIf { it.exists() }
                  ?: null.also { transaction.set(ref, pending.toFirestoreMap()) }
            }
            .await()
    existing?.toRequestOrThrow() ?: pending
  }

  private fun requests(eventId: String): CollectionReference =
      db.collection(EventSchema.COLLECTION)
          .document(eventId)
          .collection(MembershipRequestSchema.COLLECTION)

  companion object {
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
