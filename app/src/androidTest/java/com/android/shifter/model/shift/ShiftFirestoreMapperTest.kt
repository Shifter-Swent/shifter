// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.model.event.EventSchema
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mapping tests for [toShift] and [toFirestoreMap], run against the Firestore emulator.
 *
 * There is no shift repository yet, so each test writes a raw document and reads it back: a
 * [DocumentSnapshot] cannot be constructed outside Firebase. Every instance works under a freshly
 * generated event id, so tests cannot observe each other whatever order they run in.
 */
@RunWith(AndroidJUnit4::class)
class ShiftFirestoreMapperTest {

  private val auth = FirestoreEmulator.auth
  private val firestore = FirestoreEmulator.firestore

  /** The parent event of every document this test writes. Never created: only its path is used. */
  private val eventId = "event-" + UUID.randomUUID()

  private val shifts
    get() =
        firestore
            .collection(EventSchema.COLLECTION)
            .document(eventId)
            .collection(ShiftSchema.COLLECTION)

  /** Ids of the documents this test created, deleted in [tearDown]. */
  private val createdShiftIds = mutableListOf<String>()

  /** `firestore.rules` only lets signed-in users touch the shifts of an event. */
  @Before fun signIn() = emulatorTest { auth.signInAnonymously().await() }

  @After
  fun tearDown() = emulatorTest {
    createdShiftIds.forEach { shifts.document(it).delete().await() }
    createdShiftIds.clear()
    auth.signOut()
  }

  @Test
  fun toShift_mapsEveryFieldOfAStoredShift() = emulatorTest {
    val stored = richShift()
    val shiftId = writeRawShift(stored.toFirestoreMap())

    val mapped = read(shiftId).toShift()

    assertEquals(shiftId, mapped.id)
    assertEquals(TEAM_ID, mapped.teamId)
    assertEquals(ASSIGNEES, mapped.assigneeIds)
    assertEquals(Instant.parse("2026-07-21T16:00:00Z"), mapped.startAt)
    assertEquals(Instant.parse("2026-07-22T02:00:00Z"), mapped.endAt)
    assertEquals(Instant.parse("2026-01-15T09:00:00Z"), mapped.createdAt)

    // The id is the only field the document cannot carry, so the rest must round-trip.
    assertEquals(stored.copy(id = shiftId), mapped)
  }

  @Test
  fun toShift_takesTheIdFromTheDocumentAndNeverFromAField() = emulatorTest {
    val shiftId = writeRawShift(richShift().toFirestoreMap())

    val snapshot = read(shiftId)

    assertEquals("the shift id must be the document id", shiftId, snapshot.toShift().id)
    assertFalse("the id must not be duplicated as a field", snapshot.contains("id"))
  }

  @Test
  fun toShift_doesNotPersistTheEventItBelongsTo() = emulatorTest {
    // The parent event is the path: a field could contradict it.
    val shiftId = writeRawShift(richShift().toFirestoreMap())

    assertFalse("the event id must not be a field", read(shiftId).contains("eventId"))
  }

  @Test
  fun toShift_failsLoudlyOnAMissingTeamId() = emulatorTest {
    val shiftId = writeRawShift(richShift().toFirestoreMap() - ShiftSchema.TEAM_ID)

    assertFailsOnField(shiftId, ShiftSchema.TEAM_ID)
  }

  @Test
  fun toShift_failsLoudlyOnATeamIdWithAWrongType() = emulatorTest {
    val shiftId = writeRawShift(richShift().toFirestoreMap() + (ShiftSchema.TEAM_ID to 42))

    assertFailsOnField(shiftId, ShiftSchema.TEAM_ID)
  }

  @Test
  fun toShift_failsLoudlyOnAMissingTimestamp() = emulatorTest {
    val shiftId = writeRawShift(richShift().toFirestoreMap() - ShiftSchema.START_AT)

    assertFailsOnField(shiftId, ShiftSchema.START_AT)
  }

  @Test
  fun toShift_failsLoudlyOnATimestampWithAWrongType() = emulatorTest {
    val shiftId =
        writeRawShift(richShift().toFirestoreMap() + (ShiftSchema.END_AT to "2026-07-22T02:00:00Z"))

    assertFailsOnField(shiftId, ShiftSchema.END_AT)
  }

  @Test
  fun toShift_roundTripsSeveralAssigneeIdsInOrder() = emulatorTest {
    // Belonging to the team and working a given slot are separate: these are only the volunteers
    // scheduled for this shift.
    val assignees = listOf("user-c", "user-a", "user-b")
    val stored = richShift().copy(assigneeIds = assignees)
    val shiftId = writeRawShift(stored.toFirestoreMap())

    val snapshot = read(shiftId)
    val mapped = snapshot.toShift()

    assertEquals(
        "the field must be stored as a Firestore list of strings, in order",
        assignees,
        snapshot.get(ShiftSchema.ASSIGNEE_IDS),
    )
    assertEquals("the stored order must be preserved", assignees, mapped.assigneeIds)
    assertEquals(stored.copy(id = shiftId), mapped)
  }

  @Test
  fun toShift_roundTripsAnUnstaffedShift() = emulatorTest {
    // A shift exists before anybody is scheduled for it, so an empty list is valid.
    val stored = richShift().copy(assigneeIds = emptyList())
    val shiftId = writeRawShift(stored.toFirestoreMap())

    val snapshot = read(shiftId)
    val mapped = snapshot.toShift()

    assertTrue(
        "an empty list must be stored rather than omitted",
        snapshot.contains(ShiftSchema.ASSIGNEE_IDS),
    )
    assertEquals(emptyList<String>(), mapped.assigneeIds)
    assertEquals(stored.copy(id = shiftId), mapped)
  }

  @Test
  fun toShift_mapsAnAbsentAssigneeIdsToAnEmptyList() = emulatorTest {
    // The key is removed rather than written as null, which is what a shift document saved before
    // the field existed looks like. "Nobody scheduled yet" cannot be told apart from a shift that
    // is legitimately unstaffed, so it maps to an empty list rather than failing.
    val shiftId = writeRawShift(richShift().toFirestoreMap() - ShiftSchema.ASSIGNEE_IDS)

    val snapshot = read(shiftId)

    assertFalse("the field must really be absent", snapshot.contains(ShiftSchema.ASSIGNEE_IDS))
    assertEquals(emptyList<String>(), snapshot.toShift().assigneeIds)
  }

  @Test
  fun toShift_failsLoudlyOnAnExplicitlyNullAssigneeIds() = emulatorTest {
    // A non-nullable list never serializes to null, so a null is a schema problem rather than an
    // unstaffed shift.
    val shiftId = writeRawShift(richShift().toFirestoreMap() + (ShiftSchema.ASSIGNEE_IDS to null))

    assertFailsOnField(shiftId, ShiftSchema.ASSIGNEE_IDS)
  }

  @Test
  fun toShift_failsLoudlyOnAnAssigneeIdsThatIsNotAList() = emulatorTest {
    val shiftId =
        writeRawShift(
            richShift().toFirestoreMap() + (ShiftSchema.ASSIGNEE_IDS to "user-volunteer-1")
        )

    assertFailsOnField(shiftId, ShiftSchema.ASSIGNEE_IDS)
  }

  @Test
  fun toShift_failsLoudlyOnAnAssigneeIdsEntryThatIsNotAString() = emulatorTest {
    // Skipping the malformed entry would quietly take a volunteer off a shift they are due to work.
    val shiftId =
        writeRawShift(
            richShift().toFirestoreMap() +
                (ShiftSchema.ASSIGNEE_IDS to listOf("user-volunteer-1", 7))
        )

    assertFailsOnField(shiftId, ShiftSchema.ASSIGNEE_IDS)
  }

  /** Whole-second instants on purpose: Firestore keeps microseconds, not nanoseconds. */
  private fun richShift() =
      Shift(
          teamId = TEAM_ID,
          assigneeIds = ASSIGNEES,
          startAt = Instant.parse("2026-07-21T16:00:00Z"),
          endAt = Instant.parse("2026-07-22T02:00:00Z"),
          createdAt = Instant.parse("2026-01-15T09:00:00Z"),
      )

  /** Writes [body] into the event's shifts subcollection and remembers it for cleanup. */
  private suspend fun writeRawShift(body: Map<String, Any?>): String {
    val document = shifts.document()
    createdShiftIds += document.id
    document.set(body).await()
    return document.id
  }

  /** [Source.SERVER] bypasses the local cache, so the read really goes through the emulator. */
  private suspend fun read(shiftId: String): DocumentSnapshot =
      shifts.document(shiftId).get(Source.SERVER).await()

  /** Asserts that mapping [shiftId] fails loudly and that the failure names [field]. */
  private suspend fun assertFailsOnField(shiftId: String, field: String) {
    val snapshot = read(shiftId)
    val failure = runCatching { snapshot.toShift() }.exceptionOrNull()

    assertTrue(
        "a malformed document must fail with IllegalStateException, but was: $failure",
        failure is IllegalStateException,
    )
    assertTrue(
        "the failure must name the offending field, but said: ${failure?.message}",
        failure?.message?.contains(field) == true,
    )
  }

  /** Fails the test instead of hanging when the emulator cannot be reached. */
  private fun emulatorTest(block: suspend () -> Unit) = runBlocking {
    withTimeout(TIMEOUT_MILLIS) { block() }
  }

  private companion object {
    const val TIMEOUT_MILLIS = 20_000L

    const val TEAM_ID = "team-logistics"

    val ASSIGNEES = listOf("user-volunteer-1", "user-volunteer-2")
  }
}
