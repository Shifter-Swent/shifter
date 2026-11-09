// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.DocumentReference
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.ScratchEvents
import com.swent.shifter.firebase.emulatorTest
import com.swent.shifter.firebase.signInForFirestore
import com.swent.shifter.firebase.step
import com.swent.shifter.model.event.EventSchema
import com.swent.shifter.model.location.GeoPosition
import java.time.Instant
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [FirestoreMissionRepository.updateMission], running the real Firestore SDK
 * against the Firestore emulator through [FirestoreEmulator].
 *
 * Every test builds its missions under a fresh event owned by the signed-in user, since
 * `firestore.rules` only lets an event's organizer write all its missions, and every document
 * created is deleted in [tearDown]. Who else may update a mission is covered by
 * [MissionManagerRulesTest].
 */
@RunWith(AndroidJUnit4::class)
class FirestoreMissionUpdateTest {

  private val auth = FirestoreEmulator.auth
  private val firestore = FirestoreEmulator.firestore
  private val repository = FirestoreMissionRepository(firestore)

  /** The documents this test created, deleted in [tearDown]. */
  private val createdDocuments = mutableListOf<DocumentReference>()

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
  fun updateMission_savesTheChangesWithoutCreatingAMission() = emulatorTest {
    val created = create(mission(newEvent()))
    val edited =
        created.copy(
            title = "Accueillir les bénévoles",
            description = "À l'entrée principale.",
            volunteersNeeded = 5,
            startAt = Instant.parse("2030-06-20T14:00:00Z"),
            endAt = Instant.parse("2030-06-20T16:00:00Z"),
            location = ENTRANCE,
        )

    repository.updateMission(edited)

    assertEquals(listOf(edited), step("read") { repository.getMissionsByEvent(created.eventId) })
  }

  @Test
  fun updateMission_leavesTheTeamAssigneesAndCompletionUntouched() = emulatorTest {
    val created = create(mission(newEvent(), "user-1"))

    repository.updateMission(
        created.copy(
            title = "Renamed",
            teamId = "team-other",
            assigneeIds = emptyList(),
            createdAt = Instant.EPOCH,
            completed = true,
        )
    )

    assertEquals(created.copy(title = "Renamed"), stored(created))
  }

  @Test
  fun updateMission_withAnotherTeam_stillChecksTheOverlapOfItsTeam() = emulatorTest {
    val eventId = newEvent()
    val other = create(mission(eventId, at = HALL))
    val created =
        create(
            mission(eventId, at = HALL)
                .copy(
                    startAt = Instant.parse("2030-06-20T15:00:00Z"),
                    endAt = Instant.parse("2030-06-20T16:00:00Z"),
                )
        )

    val failure = runCatching {
      repository.updateMission(
          created.copy(teamId = "team-other", startAt = other.startAt, endAt = other.endAt)
      )
    }
        .exceptionOrNull()

    assertTrue(
        "expected MissionOverlapException, but was: $failure",
        failure is MissionOverlapException,
    )
  }

  @Test
  fun updateMission_withAnInvalidMission_isRejectedBeforeAnyRead() = emulatorTest {
    val created = create(mission(newEvent()))

    listOf(
            created.copy(id = ""),
            created.copy(endAt = created.startAt),
            created.copy(volunteersNeeded = 0),
            // Invalid and missing: the input is checked first, so this is not a NotFound.
            created.copy(id = "missing", volunteersNeeded = 0),
        )
        .forEach { invalid ->
          val failure = runCatching { repository.updateMission(invalid) }.exceptionOrNull()
          assertTrue(
              "expected IllegalArgumentException for $invalid, but was: $failure",
              failure is IllegalArgumentException,
          )
        }
    assertEquals(created, stored(created))
  }

  @Test
  fun updateMission_canRemoveTheLocation() = emulatorTest {
    val created = create(mission(newEvent(), at = HALL))

    repository.updateMission(created.copy(location = null))

    assertEquals(null, stored(created).location)
  }

  @Test
  fun updateMission_refusesAnOverlapAndWritesNothing() = emulatorTest {
    val eventId = newEvent()
    val other = create(mission(eventId, at = HALL))
    val created =
        create(
            mission(eventId, at = HALL)
                .copy(
                    startAt = Instant.parse("2030-06-20T15:00:00Z"),
                    endAt = Instant.parse("2030-06-20T16:00:00Z"),
                )
        )

    val failure = runCatching {
      repository.updateMission(created.copy(startAt = other.startAt, endAt = other.endAt))
    }
        .exceptionOrNull()

    assertEquals(
        "expected an overlap with the other mission, but was: $failure",
        other.id,
        (failure as? MissionOverlapException)?.conflictingMission?.id,
    )
    assertEquals(created, stored(created))
  }

  @Test
  fun updateMission_keepingItsOwnSlot_isAccepted() = emulatorTest {
    val created = create(mission(newEvent(), at = HALL))

    repository.updateMission(created.copy(title = "Renamed"))

    assertEquals("Renamed", stored(created).title)
  }

  @Test
  fun updateMission_refusesFewerVolunteersThanAssignedAndWritesNothing() = emulatorTest {
    val created = create(mission(newEvent(), "user-1", "user-2"))

    val failure = runCatching {
      repository.updateMission(created.copy(volunteersNeeded = 1))
    }
        .exceptionOrNull()

    assertEquals(
        "expected VolunteersBelowAssigneesException, but was: $failure",
        2,
        (failure as? VolunteersBelowAssigneesException)?.assigneeCount,
    )
    assertEquals(created, stored(created))
  }

  @Test
  fun updateMission_ontoTheSlotOfAnotherTeam_isAccepted() = emulatorTest {
    val eventId = newEvent()
    val other = create(mission(eventId, at = HALL))
    val created =
        create(
            mission(eventId, at = HALL)
                .copy(
                    teamId = "team-bar",
                    startAt = Instant.parse("2030-06-20T15:00:00Z"),
                    endAt = Instant.parse("2030-06-20T16:00:00Z"),
                )
        )

    repository.updateMission(created.copy(startAt = other.startAt, endAt = other.endAt))

    assertEquals(other.startAt, stored(created).startAt)
  }

  @Test
  fun updateMission_failsOnAMissingMission() = emulatorTest {
    val failure = runCatching {
      repository.updateMission(mission(newEvent()).copy(id = "missing"))
    }
        .exceptionOrNull()

    assertTrue(
        "a missing mission must fail with NotFound, but was: $failure",
        failure is MissionRepositoryException.NotFound,
    )
  }

  private suspend fun create(mission: Mission): Mission =
      step("create") { repository.createMission(mission) }
          .also {
            createdDocuments +=
                firestore
                    .collection(EventSchema.COLLECTION)
                    .document(it.eventId)
                    .collection(MissionSchema.COLLECTION)
                    .document(it.id)
          }

  private suspend fun stored(mission: Mission): Mission =
      checkNotNull(step("read") { repository.getMission(mission.eventId, mission.id) })

  private suspend fun newEvent(): String = ScratchEvents.create().also { createdDocuments += it }.id

  /**
   * A mission of the logistics team in the event [eventId], taking place [at] and needing just
   * enough people for [assigneeIds], and at least two.
   */
  private fun mission(eventId: String, vararg assigneeIds: String, at: MissionLocation? = null) =
      Mission(
          eventId = eventId,
          title = "Trier les dons alimentaires",
          description = "Trier les produits collectés.",
          teamId = "team-logistics",
          volunteersNeeded = maxOf(2, assigneeIds.size),
          startAt = Instant.parse("2030-06-20T12:00:00Z"),
          endAt = Instant.parse("2030-06-20T13:30:00Z"),
          location = at,
          assigneeIds = assigneeIds.toList(),
          createdAt = Instant.parse("2030-05-02T08:15:00Z"),
      )

  private companion object {
    val HALL = MissionLocation("Salle polyvalente", GeoPosition(46.52, 6.57))
    val ENTRANCE = MissionLocation("Entrée principale", GeoPosition(46.521, 6.571))
  }
}
