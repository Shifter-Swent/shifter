// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
package com.swent.shifter.ui.attendance

import com.swent.shifter.model.checkin.CheckInRules
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The boundaries of the shared classification, which the mission list and the team's now state both
 * read. What each status looks like on a card is covered by `ManagerOverviewContentTest`.
 */
class AttendanceStatusTest {

  private val start = Instant.parse("2027-06-20T14:00:00Z")
  private val end = start + Duration.ofHours(2)
  private val opens = start - CheckInRules.OPENS_BEFORE_START
  private val second = Duration.ofSeconds(1)

  private val present = AttendanceVolunteer("present", "Present", start)
  private val absent = AttendanceVolunteer("absent", "Absent")

  private fun mission(
      assignees: List<AttendanceVolunteer> = listOf(present),
      needed: Int = assignees.size,
      known: Boolean = true,
      failed: Boolean = false,
      completed: Boolean = false,
  ) =
      AttendanceMission(
          id = "m",
          title = "Food sorting",
          teamId = "team",
          teamName = "Team",
          volunteersNeeded = needed,
          startAt = start,
          endAt = end,
          assignees = assignees,
          attendanceKnown = known,
          attendanceFailed = failed,
          completed = completed,
      )

  @Test
  fun status_justBeforeTheCheckInOpens_isNotOpen() {
    assertEquals(AttendanceStatus.NOT_OPEN, mission().status(opens - second))
  }

  @Test
  fun status_whenTheCheckInOpens_isEarly() {
    assertEquals(AttendanceStatus.EARLY, mission().status(opens))
  }

  @Test
  fun status_beforeTheCheckInOpens_isNotOpenEvenWhenItFailed() {
    assertEquals(AttendanceStatus.NOT_OPEN, mission(failed = true).status(opens - second))
  }

  @Test
  fun status_whenTheObservationFailed_isFailedOverWaiting() {
    assertEquals(AttendanceStatus.FAILED, mission(known = false, failed = true).status(start))
  }

  @Test
  fun status_whileTheCheckInsHaveNotArrived_isWaiting() {
    assertEquals(AttendanceStatus.WAITING, mission(known = false).status(start))
  }

  @Test
  fun status_justBeforeTheStart_isEarly() {
    val short = mission(assignees = listOf(absent), needed = 2)

    assertEquals(AttendanceStatus.EARLY, short.status(start - second))
  }

  @Test
  fun status_atTheStart_isUnderstaffed() {
    val short = mission(assignees = listOf(absent), needed = 2)

    assertEquals(AttendanceStatus.UNDERSTAFFED, short.status(start))
  }

  @Test
  fun status_withEveryoneNeededPresent_isStaffed() {
    assertEquals(AttendanceStatus.STAFFED, mission().status(start))
  }

  @Test
  fun status_withAnAbsenteeButEnoughPresent_isStaffed() {
    val enough = mission(assignees = listOf(present, absent), needed = 1)

    assertEquals(AttendanceStatus.STAFFED, enough.status(start))
  }

  @Test
  fun status_justBeforeTheEnd_isStillStaffed() {
    assertEquals(AttendanceStatus.STAFFED, mission().status(end - second))
  }

  @Test
  fun status_atTheEnd_isCompleted() {
    assertEquals(AttendanceStatus.COMPLETED, mission().status(end))
  }

  @Test
  fun status_whenClosedByHand_isCompletedBeforeItsEnd() {
    assertEquals(AttendanceStatus.COMPLETED, mission(completed = true).status(start))
  }
}
