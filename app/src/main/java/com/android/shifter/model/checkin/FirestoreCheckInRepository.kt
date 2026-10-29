// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.checkin

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.swent.shifter.model.event.EventSchema
import com.swent.shifter.model.mission.MissionSchema
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * [CheckInRepository] backed by Cloud Firestore, storing each check-in under its mission, keyed by
 * the volunteer's uid: `/events/{eventId}/missions/{missionId}/checkIns/{userId}`.
 *
 * The rules of [CheckInRepository] are enforced by `firestore.rules`, not here: this class only
 * translates what Firestore refuses into [CheckInRepositoryException]. [firestore] and [auth] are
 * injected so tests can point them at the emulators; [auth] gives the signed-in user recorded as
 * [CheckIn.checkedInBy].
 */
class FirestoreCheckInRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : CheckInRepository {

  private fun checkInsOf(eventId: String, missionId: String) =
      firestore
          .collection(EventSchema.COLLECTION)
          .document(eventId)
          .collection(MissionSchema.COLLECTION)
          .document(missionId)
          .collection(CheckInSchema.COLLECTION)

  /**
   * Writes the check-in, then waits for the backend to accept it. Offline, the write is kept and
   * sent once the connection is back: this call suspends until then, while [observeMyCheckIns]
   * already emits the pending check-in.
   *
   * The rules refuse a second check-in like any other forbidden write, with `PERMISSION_DENIED`.
   * Only then is the document read, to tell [CheckInRepositoryException.AlreadyCheckedIn] apart
   * from [CheckInRepositoryException.NotAllowed]. Reading after the write rather than before leaves
   * no gap in which another check-in could slip in unnoticed.
   */
  override suspend fun checkIn(
      eventId: String,
      missionId: String,
      userId: String,
      method: CheckInMethod,
  ) {
    val checkedInBy = auth.currentUser?.uid ?: throw CheckInRepositoryException.NotAllowed()
    val document = checkInsOf(eventId, missionId).document(userId)
    try {
      document.set(newCheckInFields(userId, method, checkedInBy)).await()
    } catch (e: FirebaseFirestoreException) {
      if (e.code != Code.PERMISSION_DENIED) throw toRepositoryException(e)
      // The cache may still hold the refused write: only the server knows what is stored.
      val existing = translatingErrors { document.get(Source.SERVER).await() }
      throw if (existing.exists()) CheckInRepositoryException.AlreadyCheckedIn(e)
      else CheckInRepositoryException.NotAllowed(e)
    }
  }

  override fun observeCheckIns(eventId: String, missionId: String): Flow<List<CheckIn>> =
      observe(checkInsOf(eventId, missionId)) { it }

  /**
   * Queries the `checkIns` collection group, the only way to reach every mission at once, then
   * keeps the check-ins of [eventId] here: filtering on the path in the query would need a
   * composite index, and a volunteer only has a handful of check-ins. `firestore.rules` only allow
   * this query for the signed-in user's own [userId].
   */
  override fun observeMyCheckIns(eventId: String, userId: String): Flow<List<CheckIn>> =
      observe(
          firestore
              .collectionGroup(CheckInSchema.COLLECTION)
              .whereEqualTo(CheckInSchema.USER_ID, userId)
      ) { checkIns ->
        checkIns.filter { it.eventId == eventId }
      }

  /**
   * Emits the check-ins of [query], passed through [select], each time its result changes. A
   * listener error or a malformed check-in ends the flow with a [CheckInRepositoryException].
   */
  private fun observe(query: Query, select: (List<CheckIn>) -> List<CheckIn>): Flow<List<CheckIn>> =
      callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
          when {
            error != null -> close(toRepositoryException(error))
            snapshot != null ->
                try {
                  trySend(select(snapshot.toCheckIns()))
                } catch (e: IllegalStateException) {
                  close(CheckInRepositoryException.Unknown(e))
                }
          }
        }
        // Keeps the flow open while collected, then removes the listener so Firestore stops it.
        awaitClose { registration.remove() }
      }

  private fun QuerySnapshot.toCheckIns(): List<CheckIn> = documents.map { it.toCheckIn() }

  companion object {
    /** Translates a Firestore error into the [CheckInRepositoryException] callers handle. */
    fun toRepositoryException(e: FirebaseFirestoreException): CheckInRepositoryException =
        when (e.code) {
          Code.PERMISSION_DENIED,
          Code.UNAUTHENTICATED -> CheckInRepositoryException.NotAllowed(e)
          Code.UNAVAILABLE,
          Code.DEADLINE_EXCEEDED -> CheckInRepositoryException.Unavailable(e)
          else -> CheckInRepositoryException.Unknown(e)
        }

    /** Runs [block], rethrowing its Firestore errors as [CheckInRepositoryException]. */
    private inline fun <T> translatingErrors(block: () -> T): T =
        try {
          block()
        } catch (e: FirebaseFirestoreException) {
          throw toRepositoryException(e)
        }
  }
}
