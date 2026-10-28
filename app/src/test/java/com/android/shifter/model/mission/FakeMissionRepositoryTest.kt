// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the assignee rules of [FakeMissionRepository.createMission], and the contract of
 * [FakeMissionRepository.getMissionsAssignedTo] and [FakeMissionRepository.updateAssignees], so the
 * ViewModels tested with the fake behave as with the Firestore repository.
 */
class FakeMissionRepositoryTest {

  private val repository = FakeMissionRepository()

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
  fun getMissionsByTeams_returnsTheMissionsOfThoseTeamsOfThatEventOnly() = runTest {
    val logistics = repository.createMission(mission("event-1", startHour = 9, teamId = "log"))
    val bar = repository.createMission(mission("event-1", startHour = 10, teamId = "bar"))
    repository.createMission(mission("event-1", startHour = 11, teamId = "security"))
    repository.createMission(mission("event-1", startHour = 12))
    repository.createMission(mission("event-2", startHour = 13, teamId = "log"))

    assertEquals(listOf(logistics), repository.getMissionsByTeams("event-1", setOf("log")))
    assertEquals(
        listOf(logistics, bar),
        repository.getMissionsByTeams("event-1", setOf("log", "bar")),
    )
  }

  @Test
  fun getMissionsByTeams_withoutAnyTeam_returnsAnEmptyList() = runTest {
    repository.createMission(mission("event-1", startHour = 9, teamId = "log"))

    assertEquals(emptyList<Mission>(), repository.getMissionsByTeams("event-1", emptySet()))
  }

  @Test
  fun getMissionsByTeams_throwsTheConfiguredFailure() = runTest {
    val failure = IllegalStateException("backend down")
    repository.failure = failure

    assertSame(
        failure,
        runCatching { repository.getMissionsByTeams("event-1", setOf("log")) }.exceptionOrNull(),
    )
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

  /** A mission needing just enough volunteers for [assigneeIds], and at least one. */
  private fun mission(
      eventId: String,
      startHour: Int,
      vararg assigneeIds: String,
      teamId: String? = null,
  ) =
      Mission(
          eventId = eventId,
          title = "Mission at $startHour",
          description = "",
          teamId = teamId,
          volunteersNeeded = maxOf(1, assigneeIds.size),
          startAt = Instant.parse("2027-06-20T00:00:00Z").plusSeconds(startHour * 3600L),
          endAt = Instant.parse("2027-06-20T00:00:00Z").plusSeconds((startHour + 1) * 3600L),
          assigneeIds = assigneeIds.toList(),
          createdAt = Instant.parse("2027-06-01T00:00:00Z"),
      )
}
