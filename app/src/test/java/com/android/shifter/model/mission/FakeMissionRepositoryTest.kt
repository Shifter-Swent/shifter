// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import com.swent.shifter.model.location.GeoPosition
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the assignee rules of [FakeMissionRepository.createMission], and the contract of
 * [FakeMissionRepository.getMissionsAssignedTo], [FakeMissionRepository.observeMissionsAssignedTo],
 * [FakeMissionRepository.updateAssignees] and [FakeMissionRepository.updateMission], so the
 * ViewModels tested with the fake behave as with the Firestore repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeMissionRepositoryTest {

  /** Before every mission of these tests, so none is past its end. */
  private val repository =
      FakeMissionRepository(Clock.fixed(Instant.parse("2027-06-01T00:00:00Z"), ZoneOffset.UTC))

  @Test
  fun createMission_withAnAssigneeListedTwice_isRejected() = runTest {
    val failure = runCatching {
      repository.createMission(
          mission("event-1", startHour = 9, "user-1", "user-1").copy(volunteersNeeded = 2)
      )
    }
        .exceptionOrNull()

    assertTrue(failure is IllegalArgumentException)
    assertEquals("A mission cannot list the same assignee twice", failure?.message)
    assertTrue(repository.created.isEmpty())
  }

  @Test
  fun createMission_withMoreAssigneesThanNeeded_isRejected() = runTest {
    val failure = runCatching {
      repository.createMission(
          mission("event-1", startHour = 9, "user-1", "user-2").copy(volunteersNeeded = 1)
      )
    }
        .exceptionOrNull()

    assertTrue(failure is IllegalArgumentException)
    assertEquals("A mission needing 1 volunteers cannot have 2 assignees", failure?.message)
    assertTrue(repository.created.isEmpty())
  }

  @Test
  fun getMissionsAssignedTo_returnsTheUsersMissionsOfEveryEventEarliestFirst() = runTest {
    val late = repository.createMission(mission("event-1", startHour = 15, "user-1"))
    val early = repository.createMission(mission("event-2", startHour = 9, "user-1", "user-2"))
    repository.createMission(mission("event-1", startHour = 12, "user-2"))
    repository.createMission(mission("event-2", startHour = 10))

    assertEquals(listOf(early, late), repository.getMissionsAssignedTo("user-1"))
  }

  @Test
  fun getMissionsAssignedTo_returnsAnEmptyListForAUserWithoutMissions() = runTest {
    repository.createMission(mission("event-1", startHour = 9, "user-2"))

    assertEquals(emptyList<Mission>(), repository.getMissionsAssignedTo("user-1"))
  }

  @Test
  fun getMissionsAssignedTo_throwsTheConfiguredFailure() = runTest {
    val failure = IllegalStateException("backend down")
    repository.failure = failure

    assertSame(
        failure,
        runCatching { repository.getMissionsAssignedTo("user-1") }.exceptionOrNull(),
    )
  }

  @Test
  fun observeMissionsAssignedTo_emitsThenFollowsTheAssignments() = runTest {
    val first = repository.createMission(mission("event-1", startHour = 9, "user-1"))
    val emitted = mutableListOf<List<Mission>>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeMissionsAssignedTo("user-1").toList(emitted)
    }

    val second = repository.createMission(mission("event-2", startHour = 8, "user-1"))
    // Someone else's mission changes nothing for user-1: no emission.
    repository.createMission(mission("event-1", startHour = 12, "user-2"))
    repository.updateAssignees("event-1", first.id, emptyList())

    assertEquals(listOf(listOf(first), listOf(second, first), listOf(second)), emitted)
  }

  @Test
  fun observeMissionsAssignedTo_throwsTheConfiguredFailure() = runTest {
    val failure = IllegalStateException("backend down")
    repository.failure = failure

    val thrown = runCatching {
      repository.observeMissionsAssignedTo("user-1").first()
    }
        .exceptionOrNull()

    assertSameFailure(failure, thrown)
  }

  @Test
  fun observeMissionsAssignedTo_failureWhileObserved_endsTheFlow() = runTest {
    val failure = IllegalStateException("listener closed")
    var ended: Throwable? = null
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      ended =
          runCatching { repository.observeMissionsAssignedTo("user-1").collect {} }
              .exceptionOrNull()
    }

    repository.failure = failure

    assertSameFailure(failure, ended)
  }

  @Test
  fun updateAssignees_replacesTheAssigneesInTheGivenOrder() = runTest {
    val created =
        repository.createMission(
            mission("event-1", startHour = 9, "user-1").copy(volunteersNeeded = 2)
        )

    repository.updateAssignees("event-1", created.id, listOf("user-3", "user-2"))

    assertEquals(
        listOf("user-3", "user-2"),
        repository.getMission("event-1", created.id)?.assigneeIds,
    )
  }

  @Test
  fun updateAssignees_withAnEmptyList_leavesNobodyAssigned() = runTest {
    val created = repository.createMission(mission("event-1", startHour = 9, "user-1"))

    repository.updateAssignees("event-1", created.id, emptyList())

    assertEquals(emptyList<String>(), repository.getMission("event-1", created.id)?.assigneeIds)
    assertEquals(emptyList<Mission>(), repository.getMissionsAssignedTo("user-1"))
  }

  @Test
  fun updateAssignees_refusesMorePeopleThanTheMissionNeeds() = runTest {
    val created = repository.createMission(mission("event-1", startHour = 9, "user-1"))

    val failure = runCatching {
      repository.updateAssignees("event-1", created.id, listOf("a", "b"))
    }
        .exceptionOrNull()

    assertTrue(failure is IllegalArgumentException)
    assertEquals(listOf("user-1"), repository.getMission("event-1", created.id)?.assigneeIds)
  }

  @Test
  fun updateAssignees_refusesAnAssigneeListedTwice() = runTest {
    val created =
        repository.createMission(mission("event-1", startHour = 9).copy(volunteersNeeded = 3))

    val failure = runCatching {
      repository.updateAssignees("event-1", created.id, listOf("a", "a"))
    }
        .exceptionOrNull()

    assertTrue(failure is IllegalArgumentException)
    assertEquals(emptyList<String>(), repository.getMission("event-1", created.id)?.assigneeIds)
  }

  @Test
  fun updateAssignees_failsOnAMissingMission() = runTest {
    repository.createMission(mission("event-1", startHour = 9))

    val failure = runCatching {
      repository.updateAssignees("event-2", "mission-1", emptyList())
    }
        .exceptionOrNull()

    assertTrue(failure is MissionRepositoryException.NotFound)
  }

  @Test
  fun updateAssignees_throwsTheConfiguredFailure() = runTest {
    val created = repository.createMission(mission("event-1", startHour = 9))
    val failure = IllegalStateException("backend down")
    repository.failure = failure

    assertSame(
        failure,
        runCatching { repository.updateAssignees("event-1", created.id, emptyList()) }
            .exceptionOrNull(),
    )
  }

  @Test
  fun markCompleted_closesTheMission() = runTest {
    val created = repository.createMission(mission("event-1", startHour = 9))

    repository.markCompleted("event-1", created.id)

    assertEquals(true, repository.getMission("event-1", created.id)?.completed)
  }

  @Test
  fun markCompleted_ofAnUnknownMission_throwsNotFound() = runTest {
    val thrown = runCatching { repository.markCompleted("event-1", "nope") }.exceptionOrNull()

    assertTrue(thrown is MissionRepositoryException.NotFound)
  }

  @Test
  fun updateMission_savesTheNewValuesOnTheSameMission() = runTest {
    val created = repository.createMission(mission("event-1", startHour = 9))
    val edited =
        created.copy(
            title = "Sort the food",
            description = "In the hall",
            volunteersNeeded = 4,
            startAt = created.startAt.plusSeconds(3600),
            endAt = created.endAt.plusSeconds(7200),
            location = HALL,
        )

    repository.updateMission(edited)

    assertEquals(listOf(edited), repository.created)
  }

  @Test
  fun updateMission_keepsTheTeamAssigneesAndCompletion() = runTest {
    val created =
        repository.createMission(
            mission("event-1", startHour = 9, "user-1", teamId = "team-1")
                .copy(volunteersNeeded = 2)
        )

    repository.updateMission(
        created.copy(
            title = "Renamed",
            teamId = "team-2",
            assigneeIds = emptyList(),
            createdAt = Instant.EPOCH,
            completed = true,
        )
    )

    assertEquals(created.copy(title = "Renamed"), repository.getMission("event-1", created.id))
  }

  @Test
  fun updateMission_overlappingWithTooFewVolunteers_reportsTheOverlapFirst() = runTest {
    val other =
        repository.createMission(mission("event-1", startHour = 9, teamId = "team-1", at = HALL))
    val created =
        repository.createMission(
            mission("event-1", startHour = 11, "user-1", "user-2", teamId = "team-1", at = HALL)
        )

    val failure = runCatching {
      repository.updateMission(
          created.copy(startAt = other.startAt, endAt = other.endAt, volunteersNeeded = 1)
      )
    }
        .exceptionOrNull()

    assertTrue(failure is MissionOverlapException)
  }

  @Test
  fun updateMission_withAnotherTeam_stillChecksTheOverlapOfItsTeam() = runTest {
    val other =
        repository.createMission(mission("event-1", startHour = 9, teamId = "team-1", at = HALL))
    val created =
        repository.createMission(mission("event-1", startHour = 11, teamId = "team-1", at = HALL))

    val failure = runCatching {
      repository.updateMission(
          created.copy(teamId = "team-2", startAt = other.startAt, endAt = other.endAt)
      )
    }
        .exceptionOrNull()

    assertTrue(failure is MissionOverlapException)
  }

  @Test
  fun updateMission_withAnInvalidMission_isRejected() = runTest {
    val created = repository.createMission(mission("event-1", startHour = 9))

    listOf(
            created.copy(id = ""),
            created.copy(endAt = created.startAt),
            created.copy(volunteersNeeded = 0),
            // Invalid and missing: the input is checked first, so this is not a NotFound.
            created.copy(id = "missing", volunteersNeeded = 0),
        )
        .forEach { invalid ->
          val failure = runCatching { repository.updateMission(invalid) }.exceptionOrNull()
          assertTrue("$invalid: $failure", failure is IllegalArgumentException)
        }
    assertEquals(listOf(created), repository.created)
  }

  @Test
  fun updateMission_throwsTheConfiguredFailure() = runTest {
    val created = repository.createMission(mission("event-1", startHour = 9))
    val failure = IllegalStateException("backend down")
    repository.failure = failure

    assertSame(failure, runCatching { repository.updateMission(created) }.exceptionOrNull())
  }

  @Test
  fun updateMission_overlappingAnotherMissionOfTheTeam_isRejected() = runTest {
    val other =
        repository.createMission(mission("event-1", startHour = 9, teamId = "team-1", at = HALL))
    val created =
        repository.createMission(mission("event-1", startHour = 11, teamId = "team-1", at = HALL))

    val failure = runCatching {
      repository.updateMission(created.copy(startAt = other.startAt, endAt = other.endAt))
    }
        .exceptionOrNull()

    assertEquals(other, (failure as? MissionOverlapException)?.conflictingMission)
    assertEquals(created, repository.getMission("event-1", created.id))
  }

  @Test
  fun updateMission_keepingItsOwnSlot_isAccepted() = runTest {
    val created =
        repository.createMission(mission("event-1", startHour = 9, teamId = "team-1", at = HALL))

    repository.updateMission(created.copy(title = "Renamed"))

    assertEquals("Renamed", repository.getMission("event-1", created.id)?.title)
  }

  @Test
  fun updateMission_withFewerVolunteersThanAssigned_isRejected() = runTest {
    val created = repository.createMission(mission("event-1", startHour = 9, "user-1", "user-2"))

    val failure = runCatching {
      repository.updateMission(created.copy(volunteersNeeded = 1))
    }
        .exceptionOrNull()

    assertEquals(2, (failure as? VolunteersBelowAssigneesException)?.assigneeCount)
    assertEquals(created, repository.getMission("event-1", created.id))
  }

  @Test
  fun updateMission_ontoTheSlotOfAnotherTeam_isAccepted() = runTest {
    val other =
        repository.createMission(mission("event-1", startHour = 9, teamId = "team-1", at = HALL))
    val created =
        repository.createMission(mission("event-1", startHour = 11, teamId = "team-2", at = HALL))

    repository.updateMission(created.copy(startAt = other.startAt, endAt = other.endAt))

    assertEquals(other.startAt, repository.getMission("event-1", created.id)?.startAt)
  }

  @Test
  fun updateMission_withoutLocation_ontoAnOccupiedSlot_isAccepted() = runTest {
    val other =
        repository.createMission(mission("event-1", startHour = 9, teamId = "team-1", at = HALL))
    val created = repository.createMission(mission("event-1", startHour = 11, teamId = "team-1"))

    repository.updateMission(created.copy(startAt = other.startAt, endAt = other.endAt))

    assertEquals(other.startAt, repository.getMission("event-1", created.id)?.startAt)
  }

  @Test
  fun updateMission_failsOnAMissingMission() = runTest {
    val created = repository.createMission(mission("event-1", startHour = 9))

    val failure = runCatching {
      repository.updateMission(created.copy(id = "nope"))
    }
        .exceptionOrNull()

    assertTrue(failure is MissionRepositoryException.NotFound)
  }

  @Test
  fun updateMission_ofAMissionClosedByHand_isRejected() = runTest {
    val created = repository.createMission(mission("event-1", startHour = 9))
    repository.markCompleted("event-1", created.id)

    val failure = runCatching {
      repository.updateMission(created.copy(title = "Too late"))
    }
        .exceptionOrNull()

    assertTrue(failure is MissionCompletedException)
    assertEquals(created.copy(completed = true), repository.getMission("event-1", created.id))
  }

  @Test
  fun updateMission_ofAMissionPastItsEnd_isRejected() = runTest {
    val created = repository.createMission(mission("event-1", startHour = 9))
    val afterItsEnd = FakeMissionRepository(Clock.fixed(created.endAt, ZoneOffset.UTC))
    val copy = afterItsEnd.createMission(created.copy(id = ""))

    val failure = runCatching {
      afterItsEnd.updateMission(copy.copy(title = "Too late"))
    }
        .exceptionOrNull()

    assertTrue(failure is MissionCompletedException)
  }

  @Test
  fun updateMission_ofACompletedMissionThatOverlaps_reportsTheCompletionFirst() = runTest {
    val other =
        repository.createMission(mission("event-1", startHour = 9, teamId = "team-1", at = HALL))
    val created =
        repository.createMission(mission("event-1", startHour = 11, teamId = "team-1", at = HALL))
    repository.markCompleted("event-1", created.id)

    val failure = runCatching {
      repository.updateMission(created.copy(startAt = other.startAt, endAt = other.endAt))
    }
        .exceptionOrNull()

    assertTrue(failure is MissionCompletedException)
  }

  /** A mission needing just enough volunteers for [assigneeIds], and at least one. */
  private fun mission(
      eventId: String,
      startHour: Int,
      vararg assigneeIds: String,
      teamId: String? = null,
      at: MissionLocation? = null,
  ) =
      Mission(
          eventId = eventId,
          title = "Mission at $startHour",
          description = "",
          teamId = teamId,
          volunteersNeeded = maxOf(1, assigneeIds.size),
          startAt = Instant.parse("2027-06-20T00:00:00Z").plusSeconds(startHour * 3600L),
          endAt = Instant.parse("2027-06-20T00:00:00Z").plusSeconds((startHour + 1) * 3600L),
          location = at,
          assigneeIds = assigneeIds.toList(),
          createdAt = Instant.parse("2027-06-01T00:00:00Z"),
      )

  private companion object {
    val HALL = MissionLocation("Hall 1", GeoPosition(46.52, 6.57))
  }

  /**
   * [actual] is [expected], or the copy coroutines make of it when it crosses a coroutine (its
   * stack trace recovery, in debug mode): same type and message.
   */
  private fun assertSameFailure(expected: Throwable, actual: Throwable?) {
    assertEquals(expected::class, actual?.let { it::class })
    assertEquals(expected.message, actual?.message)
  }
}
