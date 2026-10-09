// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.ScratchEvents
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
  @Before fun signIn() = emulatorTest { auth.signInAnonymously().await() }

  @After
  fun tearDown() = emulatorTest {
    // Children first: once its event is gone, the rules refuse to delete them.
    createdDocuments.asReversed().forEach { it.delete().await() }
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
  fun getMissionFailsLoudlyOnExplicitlyNullAssigneeIds() = emulatorTest {
    // Absent means nobody is assigned yet, but the mapper never writes a null: one is malformed.
    val (eventId, missionId) = writeRawMission { it + (MissionSchema.ASSIGNEE_IDS to null) }

    assertFailsOnField(eventId, missionId, MissionSchema.ASSIGNEE_IDS)
  }

  @Test
  fun getMissionMapsAbsentAssigneeIdsToAnEmptyList() = emulatorTest {
    val (eventId, missionId) = writeRawMission { it - MissionSchema.ASSIGNEE_IDS }

    assertEquals(emptyList<String>(), repository.getMission(eventId, missionId)?.assigneeIds)
  }

  @Test
  fun getMissionMapsAVolunteerCountWrittenAsAWholeDouble() = emulatorTest {
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

  /** Creates [mission] through the repository and remembers it for cleanup. */
  private suspend fun create(mission: Mission): Mission =
      repository.createMission(mission).also {
        createdDocuments += missionDocument(it.eventId, it.id)
      }

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
    document.set(body(richMission(eventId).toFirestoreMap())).await()
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

  /** Fails the test instead of hanging when the emulator cannot be reached. */
  private fun emulatorTest(block: suspend () -> Unit) = runBlocking {
    withTimeout(TIMEOUT_MILLIS) { block() }
  }

  private companion object {
    const val TIMEOUT_MILLIS = 20_000L
  }
}
