// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.membership

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mapping tests for [toMembershipRequest] and [toFirestoreMap], run against the Firestore emulator.
 *
 * There is no membership request repository yet, so each test writes a raw document and reads it
 * back: a [DocumentSnapshot] cannot be constructed outside Firebase. Every instance works under a
 * freshly generated event id, so tests cannot observe each other whatever order they run in.
 */
@RunWith(AndroidJUnit4::class)
class MembershipRequestFirestoreMapperTest {

  private val firestore = FirestoreEmulator.firestore

  /** The parent event of every document this test writes. Never created: only its path is used. */
  private val eventId = "event-" + UUID.randomUUID()

  private val requests
    get() =
        firestore
            .collection(EventSchema.COLLECTION)
            .document(eventId)
            .collection(MembershipRequestSchema.COLLECTION)

  /** Ids of the documents this test created, deleted in [tearDown]. */
  private val createdRequestIds = mutableListOf<String>()

  @After
  fun tearDown() = emulatorTest {
    createdRequestIds.forEach { requests.document(it).delete().await() }
    createdRequestIds.clear()
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
    // The parent event is the path, and a copied display name would go stale.
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

  /** Writes [body] into the event's requests subcollection and remembers it for cleanup. */
  private suspend fun writeRawRequest(body: Map<String, Any?>): String {
    val document = requests.document()
    createdRequestIds += document.id
    document.set(body).await()
    return document.id
  }

  /** [Source.SERVER] bypasses the local cache, so the read really goes through the emulator. */
  private suspend fun read(requestId: String): DocumentSnapshot =
      requests.document(requestId).get(Source.SERVER).await()

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

    const val USER_ID = "user-volunteer-1"

    const val FIRST_TEAM_ID = "team-bar"
    const val SECOND_TEAM_ID = "team-logistics"

    val START_OF_SLOT: Instant = Instant.parse("2026-07-21T08:00:00Z")
  }
}
