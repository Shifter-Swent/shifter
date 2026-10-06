// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.team

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mapping tests for [toTeam] and [toFirestoreMap], run against the Firestore emulator.
 *
 * There is no team repository yet, so each test writes a raw document and reads it back: a
 * [DocumentSnapshot] cannot be constructed outside Firebase. Every instance works under a freshly
 * generated event id, so tests cannot observe each other whatever order they run in.
 */
@RunWith(AndroidJUnit4::class)
class TeamFirestoreMapperTest {

  private val firestore = FirestoreEmulator.firestore

  /** The parent event of every document this test writes. Never created: only its path is used. */
  private val eventId = "event-" + UUID.randomUUID()

  private val teams
    get() =
        firestore
            .collection(EventSchema.COLLECTION)
            .document(eventId)
            .collection(TeamSchema.COLLECTION)

  /** Ids of the documents this test created, deleted in [tearDown]. */
  private val createdTeamIds = mutableListOf<String>()

  @After
  fun tearDown() = emulatorTest {
    createdTeamIds.forEach { teams.document(it).delete().await() }
    createdTeamIds.clear()
  }

  @Test
  fun toTeam_mapsEveryFieldOfAStoredTeam() = emulatorTest {
    val stored = richTeam()
    val teamId = writeRawTeam(stored.toFirestoreMap())

    val mapped = read(teamId).toTeam()

    assertEquals(teamId, mapped.id)
    assertEquals("Logistics", mapped.name)
    assertEquals("truck", mapped.icon)
    assertEquals(MANAGER_ID, mapped.managerId)
    assertEquals(12, mapped.volunteersNeeded)
    assertEquals(CheckInZone(46.3869, 6.2228, 75.0), mapped.checkInZone)
    assertEquals(Instant.parse("2026-01-15T09:00:00Z"), mapped.createdAt)

    // The id is the only field the document cannot carry, so the rest must round-trip.
    assertEquals(stored.copy(id = teamId), mapped)
  }

  @Test
  fun toTeam_mapsAnAbsentManagerIdToNull() = emulatorTest {
    val teamId = writeRawTeam(richTeam().copy(managerId = null).toFirestoreMap())

    assertNull("a team without a manager must stay without one", read(teamId).toTeam().managerId)
  }

  @Test
  fun toTeam_failsLoudlyOnAManagerIdWithAWrongType() = emulatorTest {
    // Mapping a wrong type to null would silently unassign the manager.
    val teamId = writeRawTeam(richTeam().toFirestoreMap() + (TeamSchema.MANAGER_ID to 7))

    assertFailsOnField(teamId, TeamSchema.MANAGER_ID)
  }

  @Test
  fun toTeam_mapsAnAbsentCheckInZoneToNull() = emulatorTest {
    val teamId = writeRawTeam(richTeam().copy(checkInZone = null).toFirestoreMap())

    assertNull("a team without a zone must stay without one", read(teamId).toTeam().checkInZone)
  }

  @Test
  fun toTeam_mapsACheckInZoneWrittenWithWholeNumbers() = emulatorTest {
    // Firestore gives whole numbers back as Longs, not Doubles.
    val teamId =
        writeRawTeam(
            richTeam().toFirestoreMap() +
                (TeamSchema.CHECK_IN_ZONE to
                    mapOf(
                        TeamSchema.CheckInZoneFields.LATITUDE to 46L,
                        TeamSchema.CheckInZoneFields.LONGITUDE to 6L,
                        TeamSchema.CheckInZoneFields.RADIUS_METERS to 50L,
                    ))
        )

    assertEquals(CheckInZone(46.0, 6.0, 50.0), read(teamId).toTeam().checkInZone)
  }

  @Test
  fun toTeam_failsLoudlyOnACheckInZoneMissingAValue() = emulatorTest {
    // A zone without a radius would check volunteers in over the whole event.
    val teamId =
        writeRawTeam(
            richTeam().toFirestoreMap() +
                (TeamSchema.CHECK_IN_ZONE to
                    mapOf(
                        TeamSchema.CheckInZoneFields.LATITUDE to 46.3869,
                        TeamSchema.CheckInZoneFields.LONGITUDE to 6.2228,
                    ))
        )

    assertFailsOnField(teamId, TeamSchema.CHECK_IN_ZONE)
  }

  @Test
  fun toTeam_failsLoudlyOnACheckInZoneThatIsNotAMap() = emulatorTest {
    val teamId =
        writeRawTeam(richTeam().toFirestoreMap() + (TeamSchema.CHECK_IN_ZONE to "46.3869,6.2228"))

    assertFailsOnField(teamId, TeamSchema.CHECK_IN_ZONE)
  }

  @Test
  fun toTeam_failsLoudlyOnACheckInZoneValueThatIsNotANumber() = emulatorTest {
    val teamId =
        writeRawTeam(
            richTeam().toFirestoreMap() +
                (TeamSchema.CHECK_IN_ZONE to
                    mapOf(
                        TeamSchema.CheckInZoneFields.LATITUDE to "46.3869",
                        TeamSchema.CheckInZoneFields.LONGITUDE to 6.2228,
                        TeamSchema.CheckInZoneFields.RADIUS_METERS to 75.0,
                    ))
        )

    assertFailsOnField(teamId, TeamSchema.CHECK_IN_ZONE)
  }

  @Test
  fun toTeam_mapsAVolunteersNeededWrittenAsAWholeDouble() = emulatorTest {
    // 12.0 is the same count as 12, so narrowing it loses nothing and is accepted.
    val teamId = writeRawTeam(richTeam().toFirestoreMap() + (TeamSchema.VOLUNTEERS_NEEDED to 12.0))

    assertEquals(12, read(teamId).toTeam().volunteersNeeded)
  }

  @Test
  fun toTeam_failsLoudlyOnAFractionalVolunteersNeeded() = emulatorTest {
    // Truncating 3.5 to 3 would staff the team with a number nobody wrote.
    val teamId = writeRawTeam(richTeam().toFirestoreMap() + (TeamSchema.VOLUNTEERS_NEEDED to 3.5))

    assertFailsOnField(teamId, TeamSchema.VOLUNTEERS_NEEDED)
  }

  @Test
  fun toTeam_failsLoudlyOnAVolunteersNeededBeyondIntRange() = emulatorTest {
    // Narrowing this Long to an Int would wrap it around to a negative count.
    val teamId =
        writeRawTeam(
            richTeam().toFirestoreMap() +
                (TeamSchema.VOLUNTEERS_NEEDED to Int.MAX_VALUE.toLong() + 1L)
        )

    assertFailsOnField(teamId, TeamSchema.VOLUNTEERS_NEEDED)
  }

  @Test
  fun toTeam_failsLoudlyOnAVolunteersNeededWithAWrongType() = emulatorTest {
    val teamId =
        writeRawTeam(richTeam().toFirestoreMap() + (TeamSchema.VOLUNTEERS_NEEDED to "twelve"))

    assertFailsOnField(teamId, TeamSchema.VOLUNTEERS_NEEDED)
  }

  @Test
  fun toTeam_failsLoudlyOnAMissingRequiredField() = emulatorTest {
    val teamId = writeRawTeam(richTeam().toFirestoreMap() - TeamSchema.NAME)

    assertFailsOnField(teamId, TeamSchema.NAME)
  }

  @Test
  fun toTeam_failsLoudlyOnARequiredFieldWithAWrongType() = emulatorTest {
    val teamId = writeRawTeam(richTeam().toFirestoreMap() + (TeamSchema.ICON to 42))

    assertFailsOnField(teamId, TeamSchema.ICON)
  }

  @Test
  fun toTeam_failsLoudlyOnACreatedAtWithAWrongType() = emulatorTest {
    val teamId =
        writeRawTeam(
            richTeam().toFirestoreMap() + (TeamSchema.CREATED_AT to "2026-01-15T09:00:00Z")
        )

    assertFailsOnField(teamId, TeamSchema.CREATED_AT)
  }

  @Test
  fun toTeam_takesTheIdFromTheDocumentAndNeverFromAField() = emulatorTest {
    val teamId = writeRawTeam(richTeam().toFirestoreMap())

    val snapshot = read(teamId)

    assertEquals("the team id must be the document id", teamId, snapshot.toTeam().id)
    assertFalse("the id must not be duplicated as a field", snapshot.contains("id"))
  }

  @Test
  fun toTeam_doesNotPersistTheEventItBelongsTo() = emulatorTest {
    // The parent event is the path: a field could contradict it.
    val teamId = writeRawTeam(richTeam().toFirestoreMap())

    assertFalse("the event id must not be a field", read(teamId).contains("eventId"))
  }

  @Test
  fun toTeam_doesNotPersistTheManagerProfile() = emulatorTest {
    // A copied display name would go stale the moment the manager renames themselves.
    val snapshot = read(writeRawTeam(richTeam().toFirestoreMap()))

    assertEquals(MANAGER_ID, snapshot.get(TeamSchema.MANAGER_ID))
    assertFalse("the manager's name must not be copied here", snapshot.contains("displayName"))
    assertFalse("the manager's email must not be copied here", snapshot.contains("email"))
  }

  /** Whole-second instants on purpose: Firestore keeps microseconds, not nanoseconds. */
  private fun richTeam() =
      Team(
          name = "Logistics",
          icon = "truck",
          managerId = MANAGER_ID,
          volunteersNeeded = 12,
          checkInZone = CheckInZone(latitude = 46.3869, longitude = 6.2228, radiusMeters = 75.0),
          createdAt = Instant.parse("2026-01-15T09:00:00Z"),
      )

  /** Writes [body] into the event's teams subcollection and remembers it for cleanup. */
  private suspend fun writeRawTeam(body: Map<String, Any?>): String {
    val document = teams.document()
    createdTeamIds += document.id
    document.set(body).await()
    return document.id
  }

  /** [Source.SERVER] bypasses the local cache, so the read really goes through the emulator. */
  private suspend fun read(teamId: String): DocumentSnapshot =
      teams.document(teamId).get(Source.SERVER).await()

  /** Asserts that mapping [teamId] fails loudly and that the failure names [field]. */
  private suspend fun assertFailsOnField(teamId: String, field: String) {
    val snapshot = read(teamId)
    val failure = runCatching { snapshot.toTeam() }.exceptionOrNull()

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

    const val MANAGER_ID = "user-manager-1"
  }
}
