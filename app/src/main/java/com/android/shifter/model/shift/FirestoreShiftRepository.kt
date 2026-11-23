// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.swent.shifter.model.event.EventSchema
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/**
 * [ShiftRepository] backed by Cloud Firestore, storing each shift in the subcollection of its
 * event, under the id of its start: `/events/{eventId}/shifts/{shiftId}`.
 *
 * All Firebase types stay inside this class and the shift mappers: callers only ever see domain
 * models. [firestore] is injected so tests can point it at the emulator. Firestore errors are
 * rethrown as [ShiftRepositoryException].
 */
class FirestoreShiftRepository(private val firestore: FirebaseFirestore) : ShiftRepository {

  private fun shiftsOf(eventId: String) =
      firestore
          .collection(EventSchema.COLLECTION)
          .document(eventId)
          .collection(ShiftSchema.COLLECTION)

  private fun shiftsByStart(eventId: String): Query =
      shiftsOf(eventId).orderBy(ShiftSchema.START_AT)

  override suspend fun getShifts(eventId: String): List<Shift> = translatingErrors {
    shiftsByStart(eventId).get().await().documents.map { it.toShiftOrThrow() }
  }

  /** A listener error or a malformed shift ends the flow with a [ShiftRepositoryException]. */
  override fun observeShifts(eventId: String): Flow<List<Shift>> = callbackFlow {
    val registration =
        shiftsByStart(eventId).addSnapshotListener { snapshot, error ->
          when {
            error != null -> close(toRepositoryException(error))
            snapshot != null ->
                try {
                  trySend(snapshot.documents.map { it.toShiftOrThrow() })
                } catch (e: ShiftRepositoryException) {
                  close(e)
                }
          }
        }
    // Keeps the flow open while collected, then removes the listener so Firestore stops it.
    awaitClose { registration.remove() }
  }
      .distinctUntilChanged()

  /**
   * Creates the shift of [slot] in a transaction, unless it exists: the security rules refuse any
   * update, so a plain write of a slot already open would fail rather than leave it as it is. A
   * transaction needs the backend, so a slot cannot be opened offline.
   *
   * The shift is then read back from the server, the only one to know its [Shift.createdAt]. Being
   * a transaction, the write never reaches the local cache before the server has set that time, so
   * a listener never sees a shift without it.
   */
  override suspend fun openSlot(eventId: String, slot: ShiftSlot): Shift = translatingErrors {
    val document = shiftsOf(eventId).document(slot.shiftId)
    firestore
        .runTransaction { transaction ->
          if (!transaction.get(document).exists()) {
            transaction.set(document, slot.toNewShiftFields())
          }
          null
        }
        .await()
    document.get(Source.SERVER).await().toShiftOrThrow()
  }

  /**
   * Deletes the shift. The security rules refuse it while volunteers chose the slot, and accept it
   * when the shift is already gone, so closing again after a lost answer succeeds.
   */
  override suspend fun closeSlot(eventId: String, shiftId: String) {
    translatingErrors { shiftsOf(eventId).document(shiftId).delete().await() }
  }

  /** Maps this document, reporting a malformed one as [ShiftRepositoryException.Unknown]. */
  private fun DocumentSnapshot.toShiftOrThrow(): Shift =
      try {
        toShift()
      } catch (e: IllegalStateException) {
        throw ShiftRepositoryException.Unknown(e)
      }

  companion object {
    /** Translates a Firestore error into the [ShiftRepositoryException] callers handle. */
    internal fun toRepositoryException(e: FirebaseFirestoreException): ShiftRepositoryException =
        when (e.code) {
          Code.PERMISSION_DENIED,
          Code.UNAUTHENTICATED -> ShiftRepositoryException.PermissionDenied(e)
          Code.UNAVAILABLE,
          Code.DEADLINE_EXCEEDED -> ShiftRepositoryException.Unavailable(e)
          else -> ShiftRepositoryException.Unknown(e)
        }

    /** Runs [block], rethrowing its Firestore errors as [ShiftRepositoryException]. */
    private inline fun <T> translatingErrors(block: () -> T): T =
        try {
          block()
        } catch (e: FirebaseFirestoreException) {
          throw toRepositoryException(e)
        }
  }
}
