// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.firebase.ScratchEvents
import com.swent.shifter.firebase.emulatorTest
import com.swent.shifter.firebase.signInForFirestore
import com.swent.shifter.firebase.step
import com.swent.shifter.model.event.EventSchema
import com.swent.shifter.model.firestore.toFirestoreTimestamp
import java.time.Instant
import kotlinx.coroutines.tasks.await
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
 * The shift repository is #593, so each test writes a raw document and reads it back: a
 * [DocumentSnapshot] cannot be constructed outside Firebase. Every instance works under a freshly
 * generated event id, so tests cannot observe each other whatever order they run in.
 */
@RunWith(AndroidJUnit4::class)
class ShiftFirestoreMapperTest {

  private val auth = FirestoreEmulator.auth
  private val firestore = FirestoreEmulator.firestore

  /**
   * The parent event of every document this test writes, owned by the signed-in user as
   * `firestore.rules` requires.
   */
  private lateinit var event: DocumentReference

  private val eventId
    get() = event.id

  private val shifts
    get() =
        firestore
            .collection(EventSchema.COLLECTION)
            .document(eventId)
            .collection(ShiftSchema.COLLECTION)

  /**
   * The documents this test created, deleted in [tearDown]. References rather than ids, because
   * [toShift_derivesTheEventIdFromTheParentDocumentPath] writes under a second event.
   */
  private val createdDocuments = mutableListOf<DocumentReference>()

  /** `firestore.rules` only lets the organizer of an existing event write its shifts. */
  @Before
  fun signInAndCreateTheEvent() = emulatorTest {
    auth.signInForFirestore { auth.signInAnonymously().await() }
    event = ScratchEvents.create()
  }

  @After
  fun tearDown() = emulatorTest {
    // As an admin: the rules refuse to delete a shift volunteers chose, as most of these are.
    step("cleanup") {
      (createdDocuments.asReversed() + event).forEach {
        FirestoreEmulatorAdmin.deleteDocument(it.path)
      }
    }
    createdDocuments.clear()
    auth.signOut()
  }

  @Test
  fun toShift_mapsEveryFieldOfAStoredShift() = emulatorTest {
    val stored = richShift()
    val shiftId = writeRawShift(stored.toFirestoreMap())

    val mapped = read(shiftId).toShift()

    assertEquals(shiftIdOf(START), mapped.id)
    assertEquals(eventId, mapped.eventId)
    assertEquals(VOLUNTEERS, mapped.volunteerIds)
    assertEquals(START, mapped.startAt)
    assertEquals(END, mapped.endAt)
    assertEquals(Instant.parse("2026-01-15T09:00:00Z"), mapped.createdAt)

    assertEquals(stored, mapped)
  }

  @Test
  fun toShift_neverStoresTheIdNorTheEventAsFields() = emulatorTest {
    // Both are the path: a field could contradict it.
    val shiftId = writeRawShift(richShift().toFirestoreMap())

    val snapshot = read(shiftId)

    assertFalse("the id must not be duplicated as a field", snapshot.contains("id"))
    assertFalse("the event id must not be a field", snapshot.contains("eventId"))
    // Absent from the document, yet present on the model: it can only come from the path.
    assertEquals("the event id must still be mapped", eventId, snapshot.toShift().eventId)
  }

  @Test
  fun toShift_derivesTheEventIdFromTheParentDocumentPath() = emulatorTest {
    // A second event, so the mapped id has to follow the document it was read from rather than
    // anything this test class holds: a volunteer's schedule spans several events.
    val otherEventId = ScratchEvents.create().also { createdDocuments += it }.id
    val document = writeRawShiftUnder(otherEventId, shiftIdOf(START), richShift().toFirestoreMap())

    val mapped = document.get(Source.SERVER).await().toShift()

    assertEquals("the event id must be the parent event document id", otherEventId, mapped.eventId)
  }

  @Test
  fun toShift_withADocumentIdThatIsNotItsStart_failsLoudly() = emulatorTest {
    // Another id would let the same slot be stored twice.
    val document = writeRawShiftUnder(eventId, "shift-1", richShift().toFirestoreMap())

    val failure = runCatching { document.get(Source.SERVER).await().toShift() }.exceptionOrNull()

    assertTrue(
        "a document not keyed by its start must fail with IllegalStateException, but was: $failure",
        failure is IllegalStateException,
    )
  }

  @Test
  fun toShift_ignoresTheFieldsOfTheOldModel() = emulatorTest {
    // Shifts were once staffed per team; nothing wrote them, but a document of that time may stay.
    val body =
        richShift().toFirestoreMap() +
            mapOf("teamId" to "team-logistics", "assigneeIds" to listOf("user-volunteer-1"))
    val shiftId = writeRawShift(body)

    assertEquals(richShift(), read(shiftId).toShift())
  }

  @Test
  fun toShift_withASlotThatIsNotOneToThreeHours_failsLoudly() = emulatorTest {
    val shiftId =
        writeRawShift(
            richShift().toFirestoreMap() +
                (ShiftSchema.END_AT to Instant.parse("2026-07-18T18:00:00Z").toFirestoreTimestamp())
        )

    assertFailsOnField(shiftId, ShiftSchema.END_AT)
  }

  @Test
  fun toShift_failsLoudlyOnAMissingTimestamp() = emulatorTest {
    val shiftId = writeRawShift(richShift().toFirestoreMap() - ShiftSchema.START_AT)

    assertFailsOnField(shiftId, ShiftSchema.START_AT)
  }

  @Test
  fun toShift_failsLoudlyOnATimestampWithAWrongType() = emulatorTest {
    val shiftId =
        writeRawShift(richShift().toFirestoreMap() + (ShiftSchema.END_AT to "2026-07-18T10:00:00Z"))

    assertFailsOnField(shiftId, ShiftSchema.END_AT)
  }

  @Test
  fun toShift_roundTripsSeveralVolunteerIdsInOrder() = emulatorTest {
    val volunteers = listOf("user-c", "user-a", "user-b")
    val stored = richShift().copy(volunteerIds = volunteers)
    val shiftId = writeRawShift(stored.toFirestoreMap())

    val snapshot = read(shiftId)
    val mapped = snapshot.toShift()

    assertEquals(
        "the field must be stored as a Firestore list of strings, in order",
        volunteers,
        snapshot.get(ShiftSchema.VOLUNTEER_IDS),
    )
    assertEquals("the stored order must be preserved", volunteers, mapped.volunteerIds)
    assertEquals(stored, mapped)
  }

  @Test
  fun toShift_roundTripsAShiftNobodyChose() = emulatorTest {
    // A slot is opened before anybody chooses it, so an empty list is valid.
    val stored = richShift().copy(volunteerIds = emptyList())
    val shiftId = writeRawShift(stored.toFirestoreMap())

    val snapshot = read(shiftId)
    val mapped = snapshot.toShift()

    assertTrue(
        "an empty list must be stored rather than omitted",
        snapshot.contains(ShiftSchema.VOLUNTEER_IDS),
    )
    assertEquals(emptyList<String>(), mapped.volunteerIds)
    assertEquals(stored, mapped)
  }

  @Test
  fun toShift_mapsAnAbsentVolunteerIdsToAnEmptyList() = emulatorTest {
    // The key is removed rather than written as null, which is what a shift document saved before
    // the field existed looks like.
    val shiftId = writeRawShift(richShift().toFirestoreMap() - ShiftSchema.VOLUNTEER_IDS)

    val snapshot = read(shiftId)

    assertFalse("the field must really be absent", snapshot.contains(ShiftSchema.VOLUNTEER_IDS))
    assertEquals(emptyList<String>(), snapshot.toShift().volunteerIds)
  }

  @Test
  fun toShift_failsLoudlyOnAnExplicitlyNullVolunteerIds() = emulatorTest {
    // A non-nullable list never serializes to null, so a null is a schema problem rather than a
    // shift nobody chose.
    val shiftId = writeRawShift(richShift().toFirestoreMap() + (ShiftSchema.VOLUNTEER_IDS to null))

    assertFailsOnField(shiftId, ShiftSchema.VOLUNTEER_IDS)
  }

  @Test
  fun toShift_failsLoudlyOnAVolunteerIdsEntryThatIsNotAString() = emulatorTest {
    // Skipping the malformed entry would quietly take a volunteer off a shift they chose.
    val shiftId =
        writeRawShift(
            richShift().toFirestoreMap() +
                (ShiftSchema.VOLUNTEER_IDS to listOf("user-volunteer-1", 7))
        )

    assertFailsOnField(shiftId, ShiftSchema.VOLUNTEER_IDS)
  }

  /** Whole-second instants on purpose: Firestore keeps microseconds, not nanoseconds. */
  private fun richShift() =
      Shift(
          eventId = eventId,
          volunteerIds = VOLUNTEERS,
          startAt = START,
          endAt = END,
          createdAt = Instant.parse("2026-01-15T09:00:00Z"),
      )

  /**
   * Writes [body] into the event's shifts subcollection, under the id of [START] as every shift is,
   * and remembers it for cleanup.
   */
  private suspend fun writeRawShift(body: Map<String, Any?>): String =
      writeRawShiftUnder(eventId, shiftIdOf(START), body).id

  /** Writes [body] as the shift [shiftId] of [parentEventId] and remembers it for cleanup. */
  private suspend fun writeRawShiftUnder(
      parentEventId: String,
      shiftId: String,
      body: Map<String, Any?>,
  ): DocumentReference {
    val document =
        firestore
            .collection(EventSchema.COLLECTION)
            .document(parentEventId)
            .collection(ShiftSchema.COLLECTION)
            .document(shiftId)
    createdDocuments += document
    // As an admin: the rules refuse the malformed documents these tests store.
    step("write") { FirestoreEmulatorAdmin.createDocument(document.path, body) }
    return document
  }

  /** [Source.SERVER] bypasses the local cache, so the read really goes through the emulator. */
  private suspend fun read(shiftId: String): DocumentSnapshot =
      step("read") { shifts.document(shiftId).get(Source.SERVER).await() }

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

  private companion object {
    val START: Instant = Instant.parse("2026-07-18T08:00:00Z")
    val END: Instant = Instant.parse("2026-07-18T10:00:00Z")

    val VOLUNTEERS = listOf("user-volunteer-1", "user-volunteer-2")
  }
}
