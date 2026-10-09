// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.model.membership

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.model.event.EventSchema
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mapping tests for [toMembershipRequest] and [toFirestoreMap], run against the Firestore emulator.
 *
 * Each test writes a raw document and reads it back, because a [DocumentSnapshot] cannot be
 * constructed outside Firebase. The mapper only ever reads a document's id and its fields, never
 * the path it sits at, so these documents are written as scratch documents in the top-level events
 * collection rather than under an event's `membershipRequests`. That production path is guarded by
 * rules which reject the malformed bodies these tests write on purpose, and the mapper behaves the
 * same wherever the document lives. Every document gets a freshly generated id, so tests cannot
 * observe each other whatever order they run in.
 */
@RunWith(AndroidJUnit4::class)
class MembershipRequestFirestoreMapperTest {

  private val auth = FirestoreEmulator.auth
  private val firestore = FirestoreEmulator.firestore

  /** Where the raw documents of these tests are written. See the note on the class. */
  private val scratch
    get() = firestore.collection(EventSchema.COLLECTION)

  /** Ids of the documents this test created, deleted in [tearDown]. */
  private val createdRequestIds = mutableListOf<String>()

  /** Scratch events belong to the signed-in test account, as required by `firestore.rules`. */
  @Before fun signIn() = emulatorTest { auth.signInAnonymously().await() }

  @After
  fun tearDown() = emulatorTest {
    try {
      createdRequestIds.forEach { FirestoreEmulatorAdmin.deleteDocument(scratch.document(it).path) }
      createdRequestIds.clear()
    } finally {
      auth.signOut()
    }
  }

  @Test
  fun toMembershipRequest_mapsEveryFieldOfAStoredRequest() = emulatorTest {
    val stored = richRequest()
    val requestId = writeRawRequest(stored.toFirestoreMap())

    val mapped = read(requestId).toMembershipRequest()

    assertEquals(requestId, mapped.id)
    assertEquals(USER_ID, mapped.userId)
    assertEquals(listOf(SECOND_TEAM_ID, FIRST_TEAM_ID), mapped.preferredTeamIds)
    assertEquals(2, mapped.availability.size)
    assertEquals(
        AvailabilitySlot(
            Instant.parse("2026-07-21T08:00:00Z"),
            Instant.parse("2026-07-21T16:00:00Z"),
        ),
        mapped.availability[0],
    )
    assertEquals(
        AvailabilitySlot(
            Instant.parse("2026-07-22T08:00:00Z"),
            Instant.parse("2026-07-22T12:00:00Z"),
        ),
        mapped.availability[1],
    )
    assertEquals(MembershipRequestStatus.ACCEPTED, mapped.status)
    assertEquals(Instant.parse("2026-01-15T09:00:00Z"), mapped.createdAt)

    // The id is the only field the document cannot carry, so the rest must round-trip.
    assertEquals(stored.copy(id = requestId), mapped)
  }

  @Test
  fun toMembershipRequest_keepsThePreferenceOrderOfTheTeams() = emulatorTest {
    // Not alphabetical: the first entry is the team the volunteer wants most.
    val ordered = listOf("team-zulu", "team-alpha", "team-mike")
    val requestId = writeRawRequest(richRequest().copy(preferredTeamIds = ordered).toFirestoreMap())

    assertEquals(ordered, read(requestId).toMembershipRequest().preferredTeamIds)
  }

  @Test
  fun toMembershipRequest_mapsAbsentPreferredTeamIdsToAnEmptyList() = emulatorTest {
    val requestId =
        writeRawRequest(richRequest().toFirestoreMap() - MembershipRequestSchema.PREFERRED_TEAM_IDS)

    assertTrue(
        "a missing preference list must map to an empty list",
        read(requestId).toMembershipRequest().preferredTeamIds.isEmpty(),
    )
  }

  @Test
  fun toMembershipRequest_failsLoudlyOnAPreferredTeamIdsFieldThatIsNull() = emulatorTest {
    // The write side always stores an array, so a null is a malformed document rather than a
    // volunteer with no preference.
    val requestId =
        writeRawRequest(
            richRequest().toFirestoreMap() + (MembershipRequestSchema.PREFERRED_TEAM_IDS to null)
        )

    assertFailsOnField(requestId, MembershipRequestSchema.PREFERRED_TEAM_IDS)
  }

  @Test
  fun toMembershipRequest_roundTripsARequestWithoutAPreferredTeam() = emulatorTest {
    // A volunteer who applied without picking a team, which is valid, unlike one who declared no
    // availability.
    val stored =
        richRequest().copy(preferredTeamIds = emptyList(), status = MembershipRequestStatus.PENDING)
    val requestId = writeRawRequest(stored.toFirestoreMap())

    val mapped = read(requestId).toMembershipRequest()

    assertEquals(stored.copy(id = requestId), mapped)
    assertTrue("an empty preference list must stay empty", mapped.preferredTeamIds.isEmpty())
  }

  @Test
  fun membershipRequest_cannotBeConstructedWithoutAvailability() {
    // The domain invariant, so no caller can build a request an organizer could not schedule.
    assertThrows(
        "a request with no availability slot must be rejected",
        IllegalArgumentException::class.java,
    ) {
      MembershipRequest(
          userId = USER_ID,
          availability = emptyList(),
          createdAt = Instant.parse("2026-01-15T09:00:00Z"),
      )
    }
  }

  @Test
  fun toMembershipRequest_failsLoudlyOnAnAbsentAvailability() = emulatorTest {
    val requestId =
        writeRawRequest(richRequest().toFirestoreMap() - MembershipRequestSchema.AVAILABILITY)

    assertFailsOnField(requestId, MembershipRequestSchema.AVAILABILITY)
  }

  @Test
  fun toMembershipRequest_failsLoudlyOnANullAvailability() = emulatorTest {
    val requestId =
        writeRawRequest(
            richRequest().toFirestoreMap() + (MembershipRequestSchema.AVAILABILITY to null)
        )

    assertFailsOnField(requestId, MembershipRequestSchema.AVAILABILITY)
  }

  @Test
  fun toMembershipRequest_failsLoudlyOnAnEmptyAvailability() = emulatorTest {
    // Written raw: the domain invariant makes an empty list impossible through the model, but a
    // hand edit in the console can still produce one.
    val requestId =
        writeRawRequest(
            richRequest().toFirestoreMap() +
                (MembershipRequestSchema.AVAILABILITY to emptyList<Any>())
        )

    assertFailsOnField(requestId, MembershipRequestSchema.AVAILABILITY)
  }

  @Test
  fun toMembershipRequest_roundTripsASingleAvailabilitySlot() = emulatorTest {
    val slot = AvailabilitySlot(START_OF_SLOT, Instant.parse("2026-07-21T16:00:00Z"))
    val stored = richRequest().copy(availability = listOf(slot))
    val requestId = writeRawRequest(stored.toFirestoreMap())

    val mapped = read(requestId).toMembershipRequest()

    assertEquals(listOf(slot), mapped.availability)
    assertEquals(stored.copy(id = requestId), mapped)
  }

  @Test
  fun toMembershipRequest_mapsEveryStatusItPersists() = emulatorTest {
    // Every supported status is persisted by name and must round-trip.
    MembershipRequestStatus.entries.forEach { status ->
      val requestId = writeRawRequest(richRequest().copy(status = status).toFirestoreMap())

      assertEquals(
          "status $status must round-trip",
          status,
          read(requestId).toMembershipRequest().status,
      )
    }
  }

  @Test
  fun toMembershipRequest_failsLoudlyOnAnUnknownStatus() = emulatorTest {
    // Strict on purpose: MembershipRequestStatus has no catch-all, so guessing one would enrol
    // someone an organizer rejected, or drop someone they accepted.
    val requestId =
        writeRawRequest(
            richRequest().toFirestoreMap() + (MembershipRequestSchema.STATUS to "MAYBE")
        )

    assertFailsOnField(requestId, MembershipRequestSchema.STATUS)
  }

  @Test
  fun toMembershipRequest_failsLoudlyOnAPreferredTeamIdThatIsNotAString() = emulatorTest {
    val requestId =
        writeRawRequest(
            richRequest().toFirestoreMap() +
                (MembershipRequestSchema.PREFERRED_TEAM_IDS to listOf(FIRST_TEAM_ID, 42))
        )

    assertFailsOnField(requestId, MembershipRequestSchema.PREFERRED_TEAM_IDS)
  }

  @Test
  fun toMembershipRequest_failsLoudlyOnAPreferredTeamIdsFieldThatIsNotAList() = emulatorTest {
    val requestId =
        writeRawRequest(
            richRequest().toFirestoreMap() +
                (MembershipRequestSchema.PREFERRED_TEAM_IDS to FIRST_TEAM_ID)
        )

    assertFailsOnField(requestId, MembershipRequestSchema.PREFERRED_TEAM_IDS)
  }

  @Test
  fun toMembershipRequest_failsLoudlyOnAnAvailabilityEntryThatIsNotAMap() = emulatorTest {
    val requestId =
        writeRawRequest(
            richRequest().toFirestoreMap() +
                (MembershipRequestSchema.AVAILABILITY to listOf("2026-07-21T08:00:00Z"))
        )

    assertFailsOnField(requestId, MembershipRequestSchema.AVAILABILITY)
  }

  @Test
  fun toMembershipRequest_failsLoudlyOnAnAvailabilitySlotMissingATimestamp() = emulatorTest {
    // Half a slot would make the volunteer look available until some hour nobody wrote.
    val requestId =
        writeRawRequest(
            richRequest().toFirestoreMap() +
                (MembershipRequestSchema.AVAILABILITY to
                    listOf(
                        mapOf(
                            MembershipRequestSchema.SlotFields.START_AT to
                                Timestamp(START_OF_SLOT.epochSecond, START_OF_SLOT.nano)
                        )
                    ))
        )

    assertFailsOnField(requestId, MembershipRequestSchema.AVAILABILITY)
  }

  @Test
  fun toMembershipRequest_failsLoudlyOnAnAvailabilitySlotTimestampWithAWrongType() = emulatorTest {
    val requestId =
        writeRawRequest(
            richRequest().toFirestoreMap() +
                (MembershipRequestSchema.AVAILABILITY to
                    listOf(
                        mapOf(
                            MembershipRequestSchema.SlotFields.START_AT to "2026-07-21T08:00:00Z",
                            MembershipRequestSchema.SlotFields.END_AT to "2026-07-21T16:00:00Z",
                        )
                    ))
        )

    assertFailsOnField(requestId, MembershipRequestSchema.AVAILABILITY)
  }

  @Test
  fun toMembershipRequest_failsLoudlyOnAMissingUserId() = emulatorTest {
    val requestId =
        writeRawRequest(richRequest().toFirestoreMap() - MembershipRequestSchema.USER_ID)

    assertFailsOnField(requestId, MembershipRequestSchema.USER_ID)
  }

  @Test
  fun toMembershipRequest_failsLoudlyOnAUserIdWithAWrongType() = emulatorTest {
    val requestId =
        writeRawRequest(richRequest().toFirestoreMap() + (MembershipRequestSchema.USER_ID to 42))

    assertFailsOnField(requestId, MembershipRequestSchema.USER_ID)
  }

  @Test
  fun toMembershipRequest_failsLoudlyOnACreatedAtWithAWrongType() = emulatorTest {
    val requestId =
        writeRawRequest(
            richRequest().toFirestoreMap() +
                (MembershipRequestSchema.CREATED_AT to "2026-01-15T09:00:00Z")
        )

    assertFailsOnField(requestId, MembershipRequestSchema.CREATED_AT)
  }

  @Test
  fun toMembershipRequest_failsWithNoSuchElementWhenTheDocumentDoesNotExist() = emulatorTest {
    // Nothing is written: reading a document that was never created still yields a real snapshot,
    // one whose exists() is false and whose every field is absent.
    val missingId = SCRATCH_ID_PREFIX + UUID.randomUUID()
    val snapshot = read(missingId)
    assertFalse("the document must not exist for this test to mean anything", snapshot.exists())

    val failure = runCatching { snapshot.toMembershipRequest() }.exceptionOrNull()

    assertTrue(
        "an absent document must fail with NoSuchElementException, but was: $failure",
        failure is NoSuchElementException,
    )
    assertFalse(
        "an absent document must not be reported as a malformed one",
        failure is IllegalStateException,
    )
    assertTrue(
        "the failure must say the document does not exist, but said: ${failure?.message}",
        failure?.message?.contains("does not exist") == true,
    )
    assertTrue(
        "the failure must name the document, but said: ${failure?.message}",
        failure?.message?.contains(missingId) == true,
    )
  }

  @Test
  fun toMembershipRequest_takesTheIdFromTheDocumentAndNeverFromAField() = emulatorTest {
    val requestId = writeRawRequest(richRequest().toFirestoreMap())

    val snapshot = read(requestId)

    assertEquals(
        "the request id must be the document id",
        requestId,
        snapshot.toMembershipRequest().id,
    )
    assertFalse("the id must not be duplicated as a field", snapshot.contains("id"))
  }

  @Test
  fun toMembershipRequest_doesNotPersistTheEventOrTheVolunteerProfile() = emulatorTest {
    // In production the event a request belongs to is its parent document, never a field of its
    // own, and a copied display name would go stale.
    val snapshot = read(writeRawRequest(richRequest().toFirestoreMap()))

    assertFalse("the event id must not be a field", snapshot.contains("eventId"))
    assertEquals(USER_ID, snapshot.get(MembershipRequestSchema.USER_ID))
    assertFalse("the volunteer's name must not be copied here", snapshot.contains("displayName"))
    assertFalse("the volunteer's email must not be copied here", snapshot.contains("email"))
  }

  /** Whole-second instants on purpose: Firestore keeps microseconds, not nanoseconds. */
  private fun richRequest() =
      MembershipRequest(
          userId = USER_ID,
          preferredTeamIds = listOf(SECOND_TEAM_ID, FIRST_TEAM_ID),
          availability =
              listOf(
                  AvailabilitySlot(START_OF_SLOT, Instant.parse("2026-07-21T16:00:00Z")),
                  AvailabilitySlot(
                      Instant.parse("2026-07-22T08:00:00Z"),
                      Instant.parse("2026-07-22T12:00:00Z"),
                  ),
              ),
          status = MembershipRequestStatus.ACCEPTED,
          createdAt = Instant.parse("2026-01-15T09:00:00Z"),
      )

  /**
   * Writes [body] into a fresh scratch document and remembers it for cleanup.
   *
   * The id is prefixed rather than auto-generated: these documents share the events collection, so
   * one left behind by a crashed run is recognisable as test scratch rather than a real event.
   */
  private suspend fun writeRawRequest(body: Map<String, Any?>): String {
    val document = scratch.document(SCRATCH_ID_PREFIX + UUID.randomUUID())
    createdRequestIds += document.id
    // The mapper ignores this event field; malformed request fields remain untouched.
    document.set(body + ("organizerId" to checkNotNull(auth.currentUser).uid)).await()
    return document.id
  }

  /** [Source.SERVER] bypasses the local cache, so the read really goes through the emulator. */
  private suspend fun read(requestId: String): DocumentSnapshot =
      scratch.document(requestId).get(Source.SERVER).await()

  /** Asserts that mapping [requestId] fails loudly and that the failure names [field]. */
  private suspend fun assertFailsOnField(requestId: String, field: String) {
    val snapshot = read(requestId)
    val failure = runCatching { snapshot.toMembershipRequest() }.exceptionOrNull()

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

    /** Marks the scratch documents these tests write into the events collection. */
    const val SCRATCH_ID_PREFIX = "membership-mapper-scratch-"

    const val USER_ID = "user-volunteer-1"

    const val FIRST_TEAM_ID = "team-bar"
    const val SECOND_TEAM_ID = "team-logistics"

    val START_OF_SLOT: Instant = Instant.parse("2026-07-21T08:00:00Z")
  }
}
