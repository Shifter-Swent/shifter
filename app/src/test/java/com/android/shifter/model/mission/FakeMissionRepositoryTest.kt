// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the contract of [FakeMissionRepository.getMissionsAssignedTo] and
 * [FakeMissionRepository.updateAssignees], so the ViewModels tested with the fake behave as with
 * the Firestore repository.
 */
class FakeMissionRepositoryTest {

  private val repository = FakeMissionRepository()

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
  fun updateAssignees_withAnEmptyListLeavesNobodyAssigned() = runTest {
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

    assertTrue(failure is IllegalStateException)
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

  private fun mission(eventId: String, startHour: Int, vararg assigneeIds: String) =
      Mission(
          eventId = eventId,
          title = "Mission at $startHour",
          description = "",
          volunteersNeeded = 1,
          startAt = Instant.parse("2027-06-20T00:00:00Z").plusSeconds(startHour * 3600L),
          endAt = Instant.parse("2027-06-20T00:00:00Z").plusSeconds((startHour + 1) * 3600L),
          assigneeIds = assigneeIds.toList(),
          createdAt = Instant.parse("2027-06-01T00:00:00Z"),
      )
}
