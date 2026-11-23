// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.swent.shifter.model.event.EventSchema
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Error translation with Firestore mocked; the emulator tests cover the real SDK and the rules. */
class FirestoreShiftRepositoryErrorTest {

  private val firestore = mockk<FirebaseFirestore>()
  private val shiftsByStart = mockk<Query>()

  init {
    every {
      firestore
          .collection(EventSchema.COLLECTION)
          .document(EVENT_ID)
          .collection(ShiftSchema.COLLECTION)
          .orderBy(ShiftSchema.START_AT)
    } returns shiftsByStart
  }

  private val repository = FirestoreShiftRepository(firestore)

  @Test
  fun toRepositoryException_translatesFirestoreErrors() {
    fun map(code: Code) =
        FirestoreShiftRepository.toRepositoryException(FirebaseFirestoreException("", code))
    assertTrue(map(Code.PERMISSION_DENIED) is ShiftRepositoryException.PermissionDenied)
    assertTrue(map(Code.UNAUTHENTICATED) is ShiftRepositoryException.PermissionDenied)
    assertTrue(map(Code.UNAVAILABLE) is ShiftRepositoryException.Unavailable)
    assertTrue(map(Code.DEADLINE_EXCEEDED) is ShiftRepositoryException.Unavailable)
    assertTrue(map(Code.ABORTED) is ShiftRepositoryException.Unknown)
  }

  @Test
  fun getShifts_onFirestoreError_throwsTheDomainError() = runTest {
    val error = FirebaseFirestoreException("offline", Code.UNAVAILABLE)
    every { shiftsByStart.get() } returns Tasks.forException(error)

    val thrown = runCatching { repository.getShifts(EVENT_ID) }.exceptionOrNull()

    assertTrue(thrown is ShiftRepositoryException.Unavailable)
    assertEquals(error, thrown!!.cause)
  }

  @Test
  fun getShifts_withAMalformedShift_throwsUnknown() = runTest {
    every { shiftsByStart.get() } returns Tasks.forResult(snapshotOf(malformedShift()))

    val thrown = runCatching { repository.getShifts(EVENT_ID) }.exceptionOrNull()

    assertTrue("expected Unknown, but was: $thrown", thrown is ShiftRepositoryException.Unknown)
  }

  @Test
  fun observeShifts_onAListenerError_endsWithTheDomainError() = runTest {
    val error = FirebaseFirestoreException("denied", Code.PERMISSION_DENIED)
    listenWith(null, error)

    val thrown = runCatching { repository.observeShifts(EVENT_ID).first() }.exceptionOrNull()

    assertTrue(thrown is ShiftRepositoryException.PermissionDenied)
    // Through a flow, coroutines may wrap the exception in a copy, keeping it as the cause.
    assertTrue(generateSequence(thrown) { it.cause }.any { it === error })
  }

  @Test
  fun observeShifts_withAMalformedShift_endsWithUnknown() = runTest {
    listenWith(snapshotOf(malformedShift()), null)

    val thrown = runCatching { repository.observeShifts(EVENT_ID).first() }.exceptionOrNull()

    assertTrue("expected Unknown, but was: $thrown", thrown is ShiftRepositoryException.Unknown)
  }

  /** Answers the listener at once with [snapshot] or [error]. */
  private fun listenWith(snapshot: QuerySnapshot?, error: FirebaseFirestoreException?) {
    every { shiftsByStart.addSnapshotListener(any<EventListener<QuerySnapshot>>()) } answers
        {
          firstArg<EventListener<QuerySnapshot>>().onEvent(snapshot, error)
          mockk<ListenerRegistration>(relaxed = true)
        }
  }

  private fun snapshotOf(vararg documents: DocumentSnapshot): QuerySnapshot =
      mockk<QuerySnapshot>().also { every { it.documents } returns documents.toList() }

  /** A shift document whose every field is missing. */
  private fun malformedShift(): DocumentSnapshot =
      mockk<DocumentSnapshot>().also {
        every { it.id } returns "shift-1"
        every { it.get(any<String>()) } returns null
      }

  private companion object {
    const val EVENT_ID = "event-1"
  }
}
