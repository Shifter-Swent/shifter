// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.ScratchEvents
import com.swent.shifter.firebase.emulatorTest
import com.swent.shifter.firebase.signInForFirestore
import com.swent.shifter.firebase.step
import com.swent.shifter.model.event.EventSchema
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [FirestoreMissionRepository], running the real Firestore SDK against the
 * Firestore emulator through [FirestoreEmulator].
 *
 * Isolation: every test builds its missions under a fresh event of its own, so no test can observe
 * another test's data and the order they run in does not matter. Every document created is
 * remembered and deleted in [tearDown], which JUnit runs even when an assertion fails.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreMissionRepositoryTest {

  private val auth = FirestoreEmulator.auth
  private val firestore = FirestoreEmulator.firestore
  private val repository = FirestoreMissionRepository(firestore)

  /** The documents this test created, deleted in [tearDown]. */
  private val createdDocuments = mutableListOf<DocumentReference>()

  /** `firestore.rules` only lets signed-in users touch missions. */
  @Before
  fun signIn() = emulatorTest { auth.signInForFirestore { auth.signInAnonymously().await() } }

  @After
  fun tearDown() = emulatorTest {
    // Children first: once its event is gone, the rules refuse to delete them.
    step("cleanup") { createdDocuments.asReversed().forEach { it.delete().await() } }
    createdDocuments.clear()
    auth.signOut()
  }

  @Test
  fun createMission_generatesANonEmptyIdAndStoresTheMissionUnderItsEvent() = emulatorTest {
    val created = create(richMission(newEvent()))

    assertTrue("the generated id must not be empty", created.id.isNotEmpty())
    val document = missionDocument(created.eventId, created.id).get(Source.SERVER).await()
    assertTrue("the mission must be stored in its event's subcollection", document.exists())
  }

  @Test
  fun getMission_returnsTheStoredMissionWithEveryDomainField() = emulatorTest {
    val eventId = newEvent()
    val created = create(richMission(eventId))

    val found = repository.getMission(eventId, created.id)

    // Data-class equality covers every field at once, including the generated id.
    assertEquals(created, found)

    // Spelled out as well, so a mapping regression says which field broke.
    checkNotNull(found)
    assertEquals(created.id, found.id)
    assertEquals(eventId, found.eventId)
    assertEquals("Trier les dons alimentaires", found.title)
    assertEquals(
        "Trier les produits collectés et vérifier les dates de péremption.",
        found.description,
    )
    assertEquals("team-logistics", found.teamId)
    assertEquals(3, found.volunteersNeeded)
    assertEquals(Instant.parse("2026-06-20T12:00:00Z"), found.startAt)
    assertEquals(Instant.parse("2026-06-20T13:30:00Z"), found.endAt)
    // Order matters: the team is listed in the order people were assigned.
    assertEquals(listOf("$eventId-staff-1", "$eventId-staff-2"), found.assigneeIds)
    assertEquals(Instant.parse("2026-05-02T08:15:00Z"), found.createdAt)
  }

  @Test
  fun getMission_roundTripsAGeneralMissionWithoutAssignees() = emulatorTest {
    // What the creation screen produces: no assignee yet, and possibly the "General" team. A null
    // team id must come back as a null rather than as an empty string, and an empty list must not
    // come back as a missing field.
    val eventId = newEvent()
    val created = create(richMission(eventId).copy(teamId = null, assigneeIds = emptyList()))

    val found = repository.getMission(eventId, created.id)

    assertEquals(created, found)
    checkNotNull(found)
    assertNull("a General mission must keep a null team id", found.teamId)
    assertTrue("an empty assignee list must stay empty", found.assigneeIds.isEmpty())
  }

  @Test
  fun createMission_doesNotDuplicateTheIdsAsDocumentFields() = emulatorTest {
    val created = create(richMission(newEvent()))

    val document = missionDocument(created.eventId, created.id).get(Source.SERVER).await()

    assertEquals("the mission id must be the document id", created.id, document.id)
    assertFalse("the id must not be duplicated as a field", document.contains("id"))
    assertFalse("the event id must not be duplicated as a field", document.contains("eventId"))
  }

  @Test
  fun createMission_rejectsAMissionWithoutAnEvent() = emulatorTest {
    val failure = runCatching {
      repository.createMission(richMission(eventId = ""))
    }
        .exceptionOrNull()

    assertTrue(
        "a mission without an event must be rejected, but was: $failure",
        failure is IllegalArgumentException,
    )
  }

  @Test
  fun getMission_returnsNullForAnUnknownId() = emulatorTest {
    assertNull(repository.getMission(newEvent(), "missing-" + UUID.randomUUID()))
  }

  @Test
  fun getMission_returnsNullForAMissionOfAnotherEvent() = emulatorTest {
    // Mission ids are only unique within their event, so the event id is part of the lookup.
    val created = create(richMission(newEvent()))

    assertNull(repository.getMission(newEvent(), created.id))
  }

  @Test
  fun getMission_failsLoudlyOnADocumentMissingARequiredField() = emulatorTest {
    val (eventId, missionId) = writeRawMission { it - MissionSchema.TITLE }

    // A document that cannot be mapped is a schema problem, not "no mission": returning null, or a
    // Mission with a blank title, would hide it.
    assertFailsOnField(eventId, missionId, MissionSchema.TITLE)
  }

  @Test
  fun getMission_failsLoudlyOnARequiredFieldWithAWrongType() = emulatorTest {
    val (eventId, missionId) = writeRawMission { it + (MissionSchema.TITLE to 42) }

    assertFailsOnField(eventId, missionId, MissionSchema.TITLE)
  }

  @Test
  fun getMission_failsLoudlyOnARequiredTimestampWithAWrongType() = emulatorTest {
    // An instant stored as the string an older tool might have written, rather than as a Timestamp.
    val (eventId, missionId) =
        writeRawMission { it + (MissionSchema.START_AT to "2026-06-20T12:00:00Z") }

    assertFailsOnField(eventId, missionId, MissionSchema.START_AT)
  }

  @Test
  fun getMission_failsLoudlyOnAFractionalVolunteerCount() = emulatorTest {
    // Rounding 2.5 people to 2 or 3 would show the organizer a count they never entered.
    val (eventId, missionId) = writeRawMission { it + (MissionSchema.VOLUNTEERS_NEEDED to 2.5) }

    assertFailsOnField(eventId, missionId, MissionSchema.VOLUNTEERS_NEEDED)
  }

  @Test
  fun getMission_failsLoudlyOnAVolunteerCountOutOfTheIntRange() = emulatorTest {
    val (eventId, missionId) =
        writeRawMission { it + (MissionSchema.VOLUNTEERS_NEEDED to Int.MAX_VALUE + 1L) }

    assertFailsOnField(eventId, missionId, MissionSchema.VOLUNTEERS_NEEDED)
  }

  @Test
  fun getMission_failsLoudlyOnATeamIdWithAWrongType() = emulatorTest {
    // teamId is optional, so absent and null both mean "General". A value of another type does
    // not: mapping it to null would silently move the mission out of its team.
    val (eventId, missionId) = writeRawMission { it + (MissionSchema.TEAM_ID to 7) }

    assertFailsOnField(eventId, missionId, MissionSchema.TEAM_ID)
  }

  @Test
  fun getMission_failsLoudlyOnMalformedAssigneeIds() = emulatorTest {
    val (eventId, missionId) = writeRawMission { it + (MissionSchema.ASSIGNEE_IDS to listOf(1, 2)) }

    assertFailsOnField(eventId, missionId, MissionSchema.ASSIGNEE_IDS)
  }

  @Test
  fun getMission_withExplicitlyNullAssigneeIds_failsLoudly() = emulatorTest {
    // Absent means nobody is assigned yet, but the mapper never writes a null: one is malformed.
    val (eventId, missionId) = writeRawMission { it + (MissionSchema.ASSIGNEE_IDS to null) }

    assertFailsOnField(eventId, missionId, MissionSchema.ASSIGNEE_IDS)
  }

  @Test
  fun getMission_withAbsentAssigneeIds_mapsThemToAnEmptyList() = emulatorTest {
    val (eventId, missionId) = writeRawMission { it - MissionSchema.ASSIGNEE_IDS }

    assertEquals(emptyList<String>(), repository.getMission(eventId, missionId)?.assigneeIds)
  }

  @Test
  fun getMission_withVolunteerCountWrittenAsAWholeDouble_mapsIt() = emulatorTest {
    // A whole number written by another client as 3.0 is still exactly three people.
    val (eventId, missionId) = writeRawMission { it + (MissionSchema.VOLUNTEERS_NEEDED to 3.0) }

    assertEquals(3, repository.getMission(eventId, missionId)?.volunteersNeeded)
  }

  @Test
  fun getMissionsByEvent_returnsOnlyTheMissionsOfThatEvent() = emulatorTest {
    val eventId = newEvent()
    val mine =
        listOf(
            create(richMission(eventId).copy(title = "Trier les dons alimentaires")),
            create(richMission(eventId).copy(title = "Tenir le stand d'accueil")),
        )
    val theirs = create(richMission(newEvent()))

    val found = repository.getMissionsByEvent(eventId)

    assertEquals(mine.toSet(), found.toSet())
    assertTrue("another event's mission leaked in", found.none { it.id == theirs.id })
  }

  @Test
  fun getMissionsByEvent_returnsAnEmptyListForAnEventWithoutMissions() = emulatorTest {
    // A fresh id no mission was ever created under, so "no missions" cannot be confused with "the
    // other tests' missions were cleaned up".
    assertEquals(emptyList<Mission>(), repository.getMissionsByEvent(newEvent()))
  }

  @Test
  fun getMissionsByTeams_returnsTheMissionsOfThoseTeamsOfThatEventOnly() = emulatorTest {
    val eventId = newEvent()
    val logistics = create(richMission(eventId).copy(teamId = "team-logistics"))
    val bar = create(richMission(eventId).copy(teamId = "team-bar"))
    create(richMission(eventId).copy(teamId = "team-security"))
    // A "General" mission of no team, which a manager must never be served.
    create(richMission(eventId).copy(teamId = null))
    val theirs = create(richMission(newEvent()).copy(teamId = "team-logistics"))

    val found = repository.getMissionsByTeams(eventId, setOf("team-logistics", "team-bar"))

    assertEquals(setOf(logistics, bar), found.toSet())
    assertTrue("another event's mission leaked in", found.none { it.id == theirs.id })
  }

  @Test
  fun getMissionsByTeams_withoutAnyTeam_returnsAnEmptyList() = emulatorTest {
    val eventId = newEvent()
    create(richMission(eventId).copy(teamId = "team-logistics"))

    assertEquals(emptyList<Mission>(), repository.getMissionsByTeams(eventId, emptySet()))
  }

  @Test
  fun getMissionsByTeams_withManyTeams_returnsEachMissionOnceAndNothingForTheEmptyOnes() =
      emulatorTest {
        val eventId = newEvent()
        val mine = create(richMission(eventId).copy(teamId = "team-logistics"))
        // One query per team, so more teams than an `in` filter would take is not a limit, and the
        // 30 teams without a mission must each contribute nothing rather than fail the call.
        val teamIds = setOf("team-logistics") + (1..30).map { "team-$it" }

        val found = repository.getMissionsByTeams(eventId, teamIds)

        assertEquals(listOf(mine), found)
      }

  @Test
  fun getMissionsAssignedTo_returnsTheUsersMissionsOfEveryEventEarliestFirst() = emulatorTest {
    val uid = signedInUid()
    val firstEvent = newEvent()
    val secondEvent = newEvent()
    val late = create(assignedMission(firstEvent, startHour = 15, uid))
    val early = create(assignedMission(secondEvent, startHour = 9, "someone-else", uid))
    create(assignedMission(firstEvent, startHour = 12, "someone-else"))
    create(assignedMission(secondEvent, startHour = 10))

    val found = repository.getMissionsAssignedTo(uid)

    // Equality covers the event id, which the query takes from each document's path.
    assertEquals(listOf(early, late), found)
  }

  @Test
  fun getMissionsAssignedTo_returnsAnEmptyListForAUserWithoutMissions() = emulatorTest {
    create(assignedMission(newEvent(), startHour = 9, "someone-else"))

    assertEquals(emptyList<Mission>(), repository.getMissionsAssignedTo(signedInUid()))
  }

  @Test
  fun getMissionsAssignedTo_isDeniedForTheMissionsOfAnotherUser() = emulatorTest {
    // The collection-group rule only lets a user list the missions they are assigned to.
    val other = "someone-else-" + UUID.randomUUID()
    create(assignedMission(newEvent(), startHour = 9, other))

    val failure = runCatching { repository.getMissionsAssignedTo(other) }.exceptionOrNull()

    assertTrue(
        "listing another user's missions must be denied, but was: $failure",
        failure is MissionRepositoryException.PermissionDenied,
    )
  }

  @Test
  fun getMission_roundTripsAGeoTimedMission() = emulatorTest {
    val eventId = newEvent()
    val created = create(geoTimed(eventId, startHour = 12, endHour = 13))

    val found = repository.getMission(eventId, created.id)

    assertEquals(created, found)
    assertEquals(LOCATION, found?.location)
  }

  @Test
  fun getMission_mapsAnAbsentLocationToNoLocation() = emulatorTest {
    // Every mission written before locations existed has no such field.
    val (eventId, missionId) = writeRawMission { it - MissionSchema.LOCATION }

    assertNull(repository.getMission(eventId, missionId)?.location)
  }

  @Test
  fun getMission_failsLoudlyOnAHalfFilledLocation() = emulatorTest {
    val halfFilled =
        mapOf(
            MissionSchema.LocationFields.ADDRESS to LOCATION.address,
            MissionSchema.LocationFields.LATITUDE to LOCATION.latitude,
        )
    val (eventId, missionId) = writeRawMission { it + (MissionSchema.LOCATION to halfFilled) }

    assertFailsOnField(eventId, missionId, MissionSchema.LOCATION)
  }

  @Test
  fun getMission_failsLoudlyOnACoordinateOutOfRange() = emulatorTest {
    val outOfRange =
        mapOf(
            MissionSchema.LocationFields.ADDRESS to LOCATION.address,
            MissionSchema.LocationFields.LATITUDE to 91.0,
            MissionSchema.LocationFields.LONGITUDE to LOCATION.longitude,
        )
    val (eventId, missionId) = writeRawMission { it + (MissionSchema.LOCATION to outOfRange) }

    assertFailsOnField(eventId, missionId, MissionSchema.LOCATION)
  }

  @Test
  fun createMission_refusesAGeoTimedMissionOverlappingOneOfTheSameTeam() = emulatorTest {
    val eventId = newEvent()
    val existing = create(geoTimed(eventId, startHour = 12, endHour = 14))

    val failure = runCatching {
      create(geoTimed(eventId, startHour = 13, endHour = 15))
    }
        .exceptionOrNull()

    assertTrue(
        "an overlap must fail with MissionOverlapException, but was: $failure",
        failure is MissionOverlapException,
    )
    assertEquals(existing, (failure as MissionOverlapException).conflictingMission)
    assertEquals(
        "nothing must have been written",
        listOf(existing),
        repository.getMissionsByEvent(eventId),
    )
  }

  @Test
  fun createMission_acceptsMissionsThatDoNotConflict() = emulatorTest {
    val eventId = newEvent()
    val existing = create(geoTimed(eventId, startHour = 12, endHour = 14))

    val accepted =
        listOf(
            existing,
            // Starts exactly when the existing mission ends.
            create(geoTimed(eventId, startHour = 14, endHour = 15)),
            // Same slot, but another team, no team, or no location.
            create(geoTimed(eventId, startHour = 12, endHour = 14).copy(teamId = "team-bar")),
            create(geoTimed(eventId, startHour = 12, endHour = 14).copy(teamId = null)),
            create(geoTimed(eventId, startHour = 12, endHour = 14).copy(location = null)),
        )

    assertEquals(accepted.toSet(), repository.getMissionsByEvent(eventId).toSet())
  }

  /** Creates [mission] through the repository and remembers it for cleanup. */
  private suspend fun create(mission: Mission): Mission =
      step("create") { repository.createMission(mission) }
          .also { createdDocuments += missionDocument(it.eventId, it.id) }

  /**
   * Writes a mission document straight to Firestore, bypassing the repository, and returns its
   * event id and mission id.
   *
   * No repository call can write a malformed document, but a schema change or a hand edit in the
   * console can, so the mapping tests derive theirs from [richMission]'s valid document body.
   */
  private suspend fun writeRawMission(
      body: (Map<String, Any?>) -> Map<String, Any?>
  ): Pair<String, String> {
    val eventId = newEvent()
    val document = missionsOf(eventId).document()
    createdDocuments += document
    step("write") { document.set(body(richMission(eventId).toFirestoreMap())).await() }
    return eventId to document.id
  }

  /** Asserts that reading the mission fails loudly and that the failure names [field]. */
  private suspend fun assertFailsOnField(eventId: String, missionId: String, field: String) {
    val failure = runCatching { repository.getMission(eventId, missionId) }.exceptionOrNull()

    assertTrue(
        "a malformed document must fail with IllegalStateException, but was: $failure",
        failure is IllegalStateException,
    )
    assertTrue(
        "the failure must name the offending field, but said: ${failure?.message}",
        failure?.message?.contains(field) == true,
    )
  }

  private fun missionsOf(eventId: String) =
      firestore
          .collection(EventSchema.COLLECTION)
          .document(eventId)
          .collection(MissionSchema.COLLECTION)

  private fun missionDocument(eventId: String, missionId: String) =
      missionsOf(eventId).document(missionId)

  /**
   * Creates a fresh event owned by the signed-in user, which `firestore.rules` requires before any
   * mission can be written under it, and remembers it for cleanup.
   */
  private suspend fun newEvent(): String = ScratchEvents.create().also { createdDocuments += it }.id

  /**
   * A mission exercising every non-trivial mapping: a team, a volunteer count above one, two
   * assignees and three instants.
   *
   * The instants are whole seconds on purpose: Firestore keeps microseconds, so a sub-microsecond
   * [Instant] would not survive the round trip. The assignees are derived from [eventId] so that
   * they are unique to the test that created the mission.
   */
  private fun richMission(eventId: String) =
      Mission(
          eventId = eventId,
          title = "Trier les dons alimentaires",
          description = "Trier les produits collectés et vérifier les dates de péremption.",
          teamId = "team-logistics",
          volunteersNeeded = 3,
          startAt = Instant.parse("2026-06-20T12:00:00Z"),
          endAt = Instant.parse("2026-06-20T13:30:00Z"),
          assigneeIds = listOf("$eventId-staff-1", "$eventId-staff-2"),
          createdAt = Instant.parse("2026-05-02T08:15:00Z"),
      )

  /** A mission of [eventId] from [startHour] to the next hour, assigned to [assigneeIds]. */
  private fun assignedMission(eventId: String, startHour: Int, vararg assigneeIds: String) =
      richMission(eventId)
          .copy(
              startAt = Instant.parse("2026-06-20T00:00:00Z").plusSeconds(startHour * 3600L),
              endAt = Instant.parse("2026-06-20T00:00:00Z").plusSeconds((startHour + 1) * 3600L),
              assigneeIds = assigneeIds.toList(),
          )

  /** Each test signs in anonymously, so this uid is new and no other test assigns it. */
  private fun signedInUid(): String = checkNotNull(auth.currentUser).uid

  /** A GeoTimed version of [richMission], from [startHour] to [endHour] on the same day (UTC). */
  private fun geoTimed(eventId: String, startHour: Int, endHour: Int): Mission {
    val day = Instant.parse("2026-06-20T00:00:00Z")
    return richMission(eventId)
        .copy(
            startAt = day.plusSeconds(startHour * 3600L),
            endAt = day.plusSeconds(endHour * 3600L),
            location = LOCATION,
            assigneeIds = emptyList(),
        )
  }

  private companion object {
    val LOCATION = MissionLocation("Rue de Lausanne 1, 1201 Genève", 46.21, 6.14)
  }
}
