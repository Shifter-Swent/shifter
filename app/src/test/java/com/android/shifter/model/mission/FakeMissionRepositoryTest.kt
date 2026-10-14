// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Covers the contract of [FakeMissionRepository.getMissionsAssignedTo], so the ViewModels tested
 * with the fake see the same missions, in the same order, as with the Firestore repository.
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
