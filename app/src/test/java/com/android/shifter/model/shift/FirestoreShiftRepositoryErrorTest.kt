// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import com.swent.shifter.model.event.EventSchema
import com.swent.shifter.model.firestore.toFirestoreTimestamp
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Error translation and the reopening of a slot closed as it opens, with Firestore mocked; the
 * emulator tests cover the real SDK and the rules.
 */
class FirestoreShiftRepositoryErrorTest {

  private val firestore = mockk<FirebaseFirestore>()
  private val shiftsByStart = mockk<Query>()
  private val shiftDocument = mockk<DocumentReference>()

  init {
    val events = mockk<CollectionReference>()
    val event = mockk<DocumentReference>()
    val shifts = mockk<CollectionReference>()
    every { firestore.collection(EventSchema.COLLECTION) } returns events
    every { events.document(EVENT_ID) } returns event
    every { event.collection(ShiftSchema.COLLECTION) } returns shifts
    every { shifts.orderBy(ShiftSchema.START_AT) } returns shiftsByStart
    every { shifts.document(SLOT.shiftId) } returns shiftDocument
    // The transaction itself is the emulator tests' concern: here it always succeeds.
    every { firestore.runTransaction(any<Transaction.Function<Any?>>()) } returns
        Tasks.forResult(null)
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
  fun openSlot_whenClosedBeforeTheReadBack_opensItAgain() = runTest {
    // Another device closes the slot between the transaction and the read: it is opened again.
    every { shiftDocument.get(Source.SERVER) } returnsMany
        listOf(Tasks.forResult(absent()), Tasks.forResult(openedShift()))

    val opened = repository.openSlot(EVENT_ID, SLOT)

    assertEquals(SLOT.shiftId, opened.id)
    verify(exactly = 2) { firestore.runTransaction(any<Transaction.Function<Any?>>()) }
  }

  @Test
  fun openSlot_whenClosedEachTime_throwsUnknownSayingSo() = runTest {
    every { shiftDocument.get(Source.SERVER) } returns Tasks.forResult(absent())

    val thrown = runCatching { repository.openSlot(EVENT_ID, SLOT) }.exceptionOrNull()

    assertTrue("expected Unknown, but was: $thrown", thrown is ShiftRepositoryException.Unknown)
    assertTrue(thrown!!.cause?.message.orEmpty().contains("closed"))
    verify(exactly = 2) { firestore.runTransaction(any<Transaction.Function<Any?>>()) }
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

  private fun absent(): DocumentSnapshot =
      mockk<DocumentSnapshot>().also { every { it.exists() } returns false }

  /** The shift of [SLOT], as the server stores it once opened. */
  private fun openedShift(): DocumentSnapshot =
      mockk<DocumentSnapshot>().also {
        every { it.exists() } returns true
        every { it.id } returns SLOT.shiftId
        every { it.reference.parent.parent?.id } returns EVENT_ID
        every { it.contains(ShiftSchema.VOLUNTEER_IDS) } returns false
        every { it.get(ShiftSchema.START_AT) } returns SLOT.startAt.toFirestoreTimestamp()
        every { it.get(ShiftSchema.END_AT) } returns SLOT.endAt.toFirestoreTimestamp()
        every { it.get(ShiftSchema.CREATED_AT) } returns SLOT.startAt.toFirestoreTimestamp()
      }

  /** A shift document whose every field is missing. */
  private fun malformedShift(): DocumentSnapshot =
      mockk<DocumentSnapshot>().also {
        every { it.id } returns "shift-1"
        every { it.get(any<String>()) } returns null
      }

  private companion object {
    const val EVENT_ID = "event-1"

    val SLOT =
        ShiftSlot(Instant.parse("2036-07-18T10:00:00Z"), Instant.parse("2036-07-18T12:00:00Z"))
  }
}
