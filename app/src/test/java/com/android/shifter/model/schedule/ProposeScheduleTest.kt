// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.schedule

import com.swent.shifter.model.mission.Mission
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers what [proposeSchedule] never proposes: nothing without a mission, nobody for a mission in
 * progress or completed, even for a candidate who fits it, and that it refuses two candidates with
 * the same user id.
 */
class ProposeScheduleTest {

  private val now = Instant.parse("2026-11-21T12:00:00Z")

  /** A member of the team who can work all day, so they fit every mission of these tests. */
  private val alice =
      ScheduleCandidate(
          userId = "alice",
          teamIds = setOf("team"),
          slots =
              listOf(
                  TimeSlot(
                      Instant.parse("2026-11-21T06:00:00Z"),
                      Instant.parse("2026-11-21T22:00:00Z"),
                  )
              ),
      )

  private fun mission(startAt: String, endAt: String, completed: Boolean = false) =
      Mission(
          id = "mission",
          eventId = "event",
          title = "Sort the food",
          description = "",
          teamId = "team",
          volunteersNeeded = 1,
          startAt = Instant.parse(startAt),
          endAt = Instant.parse(endAt),
          createdAt = Instant.parse("2026-11-01T00:00:00Z"),
          completed = completed,
      )

  @Test
  fun proposeSchedule_withNoMission_proposesNothing() {
    assertEquals(ScheduleProposal(), proposeSchedule(emptyList(), listOf(alice), now))
  }

  @Test
  fun proposeSchedule_withAMissionInProgress_proposesNobody() {
    val inProgress = mission("2026-11-21T11:00:00Z", "2026-11-21T13:00:00Z")

    val proposal = proposeSchedule(listOf(inProgress), listOf(alice), now)

    assertEquals(emptyMap<String, List<String>>(), proposal.proposed)
  }

  @Test(expected = IllegalArgumentException::class)
  fun proposeSchedule_withTwiceTheSameUserId_throws() {
    val upcoming = mission("2026-11-21T14:00:00Z", "2026-11-21T16:00:00Z")

    proposeSchedule(listOf(upcoming), listOf(alice, alice.copy(teamIds = emptySet())), now)
  }

  @Test
  fun proposeSchedule_withACompletedMission_skipsIt() {
    val completed = mission("2026-11-21T14:00:00Z", "2026-11-21T16:00:00Z", completed = true)

    assertEquals(ScheduleProposal(), proposeSchedule(listOf(completed), listOf(alice), now))
  }
}
