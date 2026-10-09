// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mapping tests for [toTeam] and [toFirestoreMap], run against the Firestore emulator.
 *
 * Each test writes a raw document and reads it back, bypassing [FirestoreTeamRepository]: no
 * repository call can write a malformed document, and a [DocumentSnapshot] cannot be constructed
 * outside Firebase. Every instance works under a freshly generated event id, so tests cannot
 * observe each other whatever order they run in.
 */
@RunWith(AndroidJUnit4::class)
class TeamFirestoreMapperTest {

  private val auth = FirestoreEmulator.auth
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

  /** `firestore.rules` only lets signed-in users touch the teams of an event. */
  @Before fun signIn() = emulatorTest { auth.signInAnonymously().await() }

  @After
  fun tearDown() = emulatorTest {
    createdTeamIds.forEach { teams.document(it).delete().await() }
    createdTeamIds.clear()
    auth.signOut()
  }

  @Test
  fun toTeam_mapsEveryFieldOfAStoredTeam() = emulatorTest {
    val stored = richTeam()
    val teamId = writeRawTeam(stored.toFirestoreMap())

    val mapped = read(teamId).toTeam()

    assertEquals(teamId, mapped.id)
    assertEquals(eventId, mapped.eventId)
    assertEquals("Logistics", mapped.name)
    assertEquals("truck", mapped.icon)
    assertEquals(MANAGER_ID, mapped.managerId)
    assertEquals(MEMBERS, mapped.memberIds)
    assertEquals(12, mapped.volunteersNeeded)
    assertEquals(CheckInZone(46.3869, 6.2228, 75.0), mapped.checkInZone)
    assertEquals(Instant.parse("2026-01-15T09:00:00Z"), mapped.createdAt)

    // The ids come from the path, so the rest of the team must round-trip through the fields.
    assertEquals(stored.copy(id = teamId), mapped)
  }

  @Test
  fun toTeam_mapsAnAbsentManagerIdToNull() = emulatorTest {
    // The key is removed rather than written as null, which is what a document saved before the
    // field existed looks like.
    val teamId = writeRawTeam(richTeam().toFirestoreMap() - TeamSchema.MANAGER_ID)

    val snapshot = read(teamId)

    assertFalse("the field must really be absent", snapshot.contains(TeamSchema.MANAGER_ID))
    assertNull("a team without a manager must stay without one", snapshot.toTeam().managerId)
  }

  @Test
  fun toTeam_failsLoudlyOnAManagerIdWithAWrongType() = emulatorTest {
    // Mapping a wrong type to null would silently unassign the manager.
    val teamId = writeRawTeam(richTeam().toFirestoreMap() + (TeamSchema.MANAGER_ID to 7))

    assertFailsOnField(teamId, TeamSchema.MANAGER_ID)
  }

  @Test
  fun toTeam_roundTripsSeveralMemberIds() = emulatorTest {
    // A volunteer may belong to several teams, so nothing here is exclusive: the list is simply the
    // ids of the volunteers of this team, stored and read back as written.
    val stored = richTeam().copy(memberIds = listOf("user-a", "user-b", "user-c"))
    val teamId = writeRawTeam(stored.toFirestoreMap())

    val snapshot = read(teamId)
    val mapped = snapshot.toTeam()

    assertEquals(
        "the field must be stored as a Firestore list of strings",
        listOf("user-a", "user-b", "user-c"),
        snapshot.get(TeamSchema.MEMBER_IDS),
    )
    assertEquals(listOf("user-a", "user-b", "user-c"), mapped.memberIds)
    assertEquals(stored.copy(id = teamId), mapped)
  }

  @Test
  fun toTeam_roundTripsATeamWithoutMembers() = emulatorTest {
    // An empty team is valid: an organizer creates it before assigning anyone to it.
    val stored = richTeam().copy(memberIds = emptyList())
    val teamId = writeRawTeam(stored.toFirestoreMap())

    val snapshot = read(teamId)
    val mapped = snapshot.toTeam()

    assertTrue(
        "an empty list must be stored rather than omitted",
        snapshot.contains(TeamSchema.MEMBER_IDS),
    )
    assertEquals(emptyList<String>(), mapped.memberIds)
    assertEquals(stored.copy(id = teamId), mapped)
  }

  @Test
  fun toTeam_mapsAnAbsentMemberIdsToAnEmptyList() = emulatorTest {
    // The key is removed rather than written as null, which is what a team document saved before
    // the field existed looks like. "Nobody yet" is indistinguishable from a legitimately empty
    // team, so it maps to an empty list rather than failing.
    val teamId = writeRawTeam(richTeam().toFirestoreMap() - TeamSchema.MEMBER_IDS)

    val snapshot = read(teamId)

    assertFalse("the field must really be absent", snapshot.contains(TeamSchema.MEMBER_IDS))
    assertEquals(emptyList<String>(), snapshot.toTeam().memberIds)
  }

  @Test
  fun toTeam_keepsTheManagerOutOfMemberIds() = emulatorTest {
    // managerId already says who leads the team: adding them to the members would double-count them
    // against volunteersNeeded.
    val teamId = writeRawTeam(richTeam().toFirestoreMap())

    val mapped = read(teamId).toTeam()

    assertEquals(MANAGER_ID, mapped.managerId)
    assertFalse(
        "the manager must not be implicitly a member",
        mapped.memberIds.contains(MANAGER_ID),
    )
  }

  @Test
  fun toTeam_failsLoudlyOnAMemberIdsThatIsNotAList() = emulatorTest {
    val teamId =
        writeRawTeam(richTeam().toFirestoreMap() + (TeamSchema.MEMBER_IDS to "user-volunteer-1"))

    assertFailsOnField(teamId, TeamSchema.MEMBER_IDS)
  }

  @Test
  fun toTeam_failsLoudlyOnAMemberIdsEntryThatIsNotAString() = emulatorTest {
    // Skipping the malformed entry would quietly drop a volunteer out of their team.
    val teamId =
        writeRawTeam(
            richTeam().toFirestoreMap() + (TeamSchema.MEMBER_IDS to listOf("user-volunteer-1", 7))
        )

    assertFailsOnField(teamId, TeamSchema.MEMBER_IDS)
  }

  @Test
  fun toTeam_failsLoudlyOnAnExplicitlyNullMemberIds() = emulatorTest {
    // Unlike managerId and checkInZone, null is not a value this mapper ever writes here: a
    // non-nullable list always serializes to a list, so a null is a schema problem.
    val teamId = writeRawTeam(richTeam().toFirestoreMap() + (TeamSchema.MEMBER_IDS to null))

    assertFailsOnField(teamId, TeamSchema.MEMBER_IDS)
  }

  @Test
  fun toTeam_mapsAnAbsentCheckInZoneToNull() = emulatorTest {
    val teamId = writeRawTeam(richTeam().toFirestoreMap() - TeamSchema.CHECK_IN_ZONE)

    val snapshot = read(teamId)

    assertFalse("the field must really be absent", snapshot.contains(TeamSchema.CHECK_IN_ZONE))
    assertNull("a team without a zone must stay without one", snapshot.toTeam().checkInZone)
  }

  @Test
  fun toTeam_roundTripsATeamWithoutAManagerOrAZone() = emulatorTest {
    // The other half of the optional contract: toFirestoreMap stores both as explicit nulls, which
    // must read back as null just like an absent field.
    val stored = richTeam().copy(managerId = null, checkInZone = null)
    val teamId = writeRawTeam(stored.toFirestoreMap())

    val snapshot = read(teamId)
    val mapped = snapshot.toTeam()

    assertTrue(
        "both fields must be stored as explicit nulls rather than omitted",
        snapshot.contains(TeamSchema.MANAGER_ID) && snapshot.contains(TeamSchema.CHECK_IN_ZONE),
    )
    assertNull("an explicit null manager must stay null", mapped.managerId)
    assertNull("an explicit null zone must stay null", mapped.checkInZone)
    assertEquals(stored.copy(id = teamId), mapped)
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
  fun toTeam_failsLoudlyOnANonFiniteVolunteersNeeded() = emulatorTest {
    // Firestore stores doubles, so these are all storable, and none of them narrows to a count:
    // NaN compares unequal to itself and the infinities saturate rather than convert.
    listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { value ->
      val teamId =
          writeRawTeam(richTeam().toFirestoreMap() + (TeamSchema.VOLUNTEERS_NEEDED to value))

      assertFailsOnField(teamId, TeamSchema.VOLUNTEERS_NEEDED)
    }
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

    val snapshot = read(teamId)

    assertFalse("the event id must not be a field", snapshot.contains("eventId"))
    // Absent from the document, yet present on the model: it can only come from the path.
    assertEquals("the event id must still be mapped", eventId, snapshot.toTeam().eventId)
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
          eventId = eventId,
          name = "Logistics",
          icon = "truck",
          managerId = MANAGER_ID,
          memberIds = MEMBERS,
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

    val MEMBERS = listOf("user-volunteer-1", "user-volunteer-2")
  }
}
