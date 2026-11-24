// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.DocumentReference
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.firebase.ScratchEvents
import com.swent.shifter.firebase.emulatorTest
import com.swent.shifter.firebase.signInForFirestore
import com.swent.shifter.firebase.step
import com.swent.shifter.model.event.EventSchema
import com.swent.shifter.model.firestore.toFirestoreTimestamp
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [FirestoreShiftRepository] against the Firestore emulator and its security rules, signed in as
 * the organizer of a scratch event from 09:00 to 23:00 UTC. The slots are taken in UTC, where the
 * even hours of `slotOf` are the same whatever the device's zone.
 *
 * A slot volunteers chose is written as an admin: only #594 lets volunteers write the shifts. A
 * test that needs someone else signs in with a new account, deleted in [tearDown].
 */
@RunWith(AndroidJUnit4::class)
class FirestoreShiftRepositoryTest {

  private val auth = FirestoreEmulator.auth
  private val firestore = FirestoreEmulator.firestore
  private val repository = FirestoreShiftRepository(firestore)

  private lateinit var event: DocumentReference

  private val eventId
    get() = event.id

  @Before
  fun signInAndCreateTheEvent() = emulatorTest {
    auth.signInForFirestore { auth.signInAnonymously().await() }
    event =
        ScratchEvents.create(
            mapOf(
                EventSchema.START_AT to EVENT_START.toFirestoreTimestamp(),
                EventSchema.END_AT to EVENT_END.toFirestoreTimestamp(),
            )
        )
  }

  @After
  fun tearDown() = emulatorTest {
    // Children first, then the event: once it is gone, the rules refuse to delete them.
    step("cleanup") {
      SLOTS.forEach { FirestoreEmulatorAdmin.deleteDocument(shiftPath(it)) }
      FirestoreEmulatorAdmin.deleteDocument(event.path)
    }
    auth.currentUser?.takeUnless { it.isAnonymous }?.delete()?.await()
    auth.signOut()
  }

  @Test
  fun openSlot_createsTheShiftOfTheSlot() = emulatorTest {
    val before = Instant.now()

    val opened = repository.openSlot(eventId, TEN)

    assertEquals(TEN.shiftId, opened.id)
    assertEquals(eventId, opened.eventId)
    assertEquals(TEN.startAt, opened.startAt)
    assertEquals(TEN.endAt, opened.endAt)
    assertEquals(emptyList<String>(), opened.volunteerIds)
    // The server's time, read back: within a minute of the device's, whatever their clocks.
    assertTrue(
        "createdAt ${opened.createdAt} should be close to $before",
        opened.createdAt > before.minusSeconds(60) &&
            opened.createdAt < Instant.now().plusSeconds(60),
    )
    assertEquals(listOf(opened), repository.getShifts(eventId))
  }

  @Test
  fun openSlot_twice_leavesTheOneShiftAsItWas() = emulatorTest {
    val first = repository.openSlot(eventId, TEN)

    val second = repository.openSlot(eventId, TEN)

    assertEquals(first, second)
    assertEquals(listOf(first), repository.getShifts(eventId))
  }

  @Test
  fun openSlot_ofASlotVolunteersChose_returnsItWithItsVolunteers() = emulatorTest {
    writeChosenSlot(TEN, listOf("user-volunteer"))

    val opened = repository.openSlot(eventId, TEN)

    assertEquals(listOf("user-volunteer"), opened.volunteerIds)
  }

  @Test
  fun openSlot_ofAnOpenSlotBySomeoneElse_returnsItUnchanged() = emulatorTest {
    val opened = repository.openSlot(eventId, TEN)
    signInAsSomeoneElse()

    // Only a read: any signed-in user may get a slot already open.
    assertEquals(opened, repository.openSlot(eventId, TEN))
  }

  @Test
  fun openSlot_ofAClosedSlotBySomeoneElse_isRefused() = emulatorTest {
    signInAsSomeoneElse()

    val failure = runCatching { repository.openSlot(eventId, TEN) }.exceptionOrNull()

    assertTrue(
        "expected PermissionDenied, but was: $failure",
        failure is ShiftRepositoryException.PermissionDenied,
    )
    assertEquals(emptyList<Shift>(), repository.getShifts(eventId))
  }

  @Test
  fun getShifts_sortsTheShiftsByStart() = emulatorTest {
    listOf(FOURTEEN, TEN, TWELVE).forEach { repository.openSlot(eventId, it) }

    val shifts = repository.getShifts(eventId)

    assertEquals(listOf(TEN, TWELVE, FOURTEEN).map { it.startAt }, shifts.map { it.startAt })
  }

  @Test
  fun closeSlot_deletesTheShift() = emulatorTest {
    repository.openSlot(eventId, TEN)

    repository.closeSlot(eventId, TEN.shiftId)

    assertEquals(emptyList<Shift>(), repository.getShifts(eventId))
  }

  @Test
  fun closeSlot_ofASlotAlreadyClosed_doesNothing() = emulatorTest {
    // A retry after a lost answer: the rules accept deleting what is already gone.
    repository.closeSlot(eventId, TEN.shiftId)

    assertEquals(emptyList<Shift>(), repository.getShifts(eventId))
  }

  @Test
  fun closeSlot_ofASlotVolunteersChose_isRefused() = emulatorTest {
    writeChosenSlot(TEN, listOf("user-volunteer"))

    val failure = runCatching { repository.closeSlot(eventId, TEN.shiftId) }.exceptionOrNull()

    assertTrue(
        "expected PermissionDenied, but was: $failure",
        failure is ShiftRepositoryException.PermissionDenied,
    )
    assertEquals(1, repository.getShifts(eventId).size)
  }

  @Test
  fun observeShifts_emitsAgainOnEveryChange() = emulatorTest {
    coroutineScope {
      val emissions = repository.observeShifts(eventId).produceIn(this)
      try {
        assertEquals(emptyList<Shift>(), emissions.receive())

        repository.openSlot(eventId, TEN)
        emissions.receiveUntil { it.map(Shift::id) == listOf(TEN.shiftId) }

        repository.openSlot(eventId, TWELVE)
        emissions.receiveUntil { it.map(Shift::id) == listOf(TEN.shiftId, TWELVE.shiftId) }

        // A volunteer chooses the slot, written as an admin until #594 lets them.
        FirestoreEmulatorAdmin.deleteDocument(shiftPath(TEN))
        writeChosenSlot(TEN, listOf("user-volunteer"))
        emissions.receiveUntil { shifts ->
          shifts.any { it.id == TEN.shiftId && it.volunteerIds == listOf("user-volunteer") }
        }

        repository.closeSlot(eventId, TWELVE.shiftId)
        emissions.receiveUntil { it.map(Shift::id) == listOf(TEN.shiftId) }
      } finally {
        emissions.cancel()
      }
    }
  }

  /** Signs in with a new account, which is not the organizer of the event. */
  private suspend fun signInAsSomeoneElse() {
    val email = "shift-repository-${UUID.randomUUID()}@example.test"
    // Creating the account signs it in, so Firestore switches to it too.
    auth.signInForFirestore { auth.createUserWithEmailAndPassword(email, PASSWORD).await() }
  }

  /** Stores the shift of [slot] with [volunteerIds], as #594 will once they apply. */
  private suspend fun writeChosenSlot(slot: ShiftSlot, volunteerIds: List<String>) {
    step("write a chosen slot") {
      FirestoreEmulatorAdmin.createDocument(
          shiftPath(slot),
          mapOf(
              ShiftSchema.VOLUNTEER_IDS to volunteerIds,
              ShiftSchema.START_AT to slot.startAt,
              ShiftSchema.END_AT to slot.endAt,
              ShiftSchema.CREATED_AT to EVENT_START,
          ),
      )
    }
  }

  private fun shiftPath(slot: ShiftSlot) =
      "${EventSchema.COLLECTION}/$eventId/${ShiftSchema.COLLECTION}/${slot.shiftId}"

  /** Receives emissions until one matches [predicate], the listener may skip or merge some. */
  private suspend fun ReceiveChannel<List<Shift>>.receiveUntil(
      predicate: (List<Shift>) -> Boolean
  ): List<Shift> {
    while (true) {
      val emission = receive()
      if (predicate(emission)) return emission
    }
  }

  private companion object {
    const val PASSWORD = "test-password-123"

    // In the future, in UTC: the rules only accept a slot during the event.
    val EVENT_START: Instant = Instant.parse("2036-07-18T09:00:00Z")
    val EVENT_END: Instant = Instant.parse("2036-07-18T23:00:00Z")

    fun utcSlot(hour: Int) =
        ShiftSlot(
            Instant.parse("2036-07-18T%02d:00:00Z".format(hour)),
            Instant.parse("2036-07-18T%02d:00:00Z".format(hour + 2)),
        )

    val TEN = utcSlot(10)
    val TWELVE = utcSlot(12)
    val FOURTEEN = utcSlot(14)

    val SLOTS = listOf(TEN, TWELVE, FOURTEEN)
  }
}
