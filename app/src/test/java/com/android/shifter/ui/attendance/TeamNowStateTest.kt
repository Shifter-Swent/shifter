// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
package com.swent.shifter.ui.attendance

import com.swent.shifter.ui.attendance.TeamNowState.Arriving
import com.swent.shifter.ui.attendance.TeamNowState.Done
import com.swent.shifter.ui.attendance.TeamNowState.Missing
import com.swent.shifter.ui.attendance.TeamNowState.Quiet
import com.swent.shifter.ui.attendance.TeamNowState.Running
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TeamNowStateTest {

  private val now = Instant.parse("2027-06-20T10:00:00Z")
  private val utc = ZoneId.of("UTC")
  private val second = Duration.ofSeconds(1)
  private val window = TeamNowRules.UPCOMING_WINDOW
  private val shortGrace = TeamNowRules.SHORT_GRACE_PERIOD
  private val longGrace = TeamNowRules.LONG_GRACE_PERIOD
  private val shortMission = TeamNowRules.SHORT_MISSION_MAX_DURATION

  private val alice = present("alice")
  private val bob = present("bob")
  private val emma = absent("emma")
  private val noah = absent("noah")

  private fun present(id: String) =
      AttendanceVolunteer(id, id.replaceFirstChar { it.uppercase() }, now - Duration.ofMinutes(1))

  private fun absent(id: String) = AttendanceVolunteer(id, id.replaceFirstChar { it.uppercase() })

  /** A mission of two hours by default, with its attendance known and everyone needed assigned. */
  private fun mission(
      id: String,
      startAt: Instant,
      lasts: Duration = Duration.ofHours(2),
      assignees: List<AttendanceVolunteer> = emptyList(),
      needed: Int = assignees.size,
      known: Boolean = true,
      failed: Boolean = false,
      completed: Boolean = false,
  ) =
      AttendanceMission(
          id = id,
          title = id,
          teamId = "team",
          teamName = "Team",
          volunteersNeeded = needed,
          startAt = startAt,
          endAt = startAt + lasts,
          assignees = assignees,
          attendanceKnown = known,
          attendanceFailed = failed,
          completed = completed,
      )

  private fun ago(minutes: Long) = now - Duration.ofMinutes(minutes)

  private fun inMinutes(minutes: Long) = now + Duration.ofMinutes(minutes)

  // The rules

  @Test
  fun TeamNowRules_holdTheAgreedDelays() {
    assertEquals(Duration.ofMinutes(30), window)
    assertEquals(Duration.ofMinutes(30), shortMission)
    assertEquals(Duration.ofMinutes(3), shortGrace)
    assertEquals(Duration.ofMinutes(5), longGrace)
  }

  @Test
  fun gracePeriod_ofAMissionLastingExactlyThirtyMinutes_isTheShortOne() {
    assertEquals(shortGrace, TeamNowRules.gracePeriod(shortMission))
  }

  @Test
  fun gracePeriod_ofAMissionLastingJustOverThirtyMinutes_isTheLongOne() {
    assertEquals(longGrace, TeamNowRules.gracePeriod(shortMission + second))
  }

  // Each state

  @Test
  fun teamNowState_withoutMissions_isDone() {
    assertEquals(Done, teamNowState(emptyList(), now))
  }

  @Test
  fun teamNowState_withEveryMissionOver_isDone() {
    val over = mission("over", ago(180), assignees = listOf(alice, emma))
    val justOver = mission("justOver", ago(120), assignees = listOf(emma))

    assertEquals(Done, teamNowState(listOf(over, justOver), now))
  }

  @Test
  fun teamNowState_withMissionClosedByHandBeforeItsEnd_ignoresIt() {
    val closed = mission("closed", ago(60), assignees = listOf(alice, emma), completed = true)

    assertEquals(Done, teamNowState(listOf(closed), now))
  }

  @Test
  fun teamNowState_withAbsenteePastTheGracePeriod_isMissing() {
    val late = mission("late", ago(10), assignees = listOf(alice, emma))

    assertEquals(
        Missing(listOf(MissingAssignee(late, emma)), onDuty = 1),
        teamNowState(listOf(late), now),
    )
  }

  @Test
  fun teamNowState_withAbsenteeWithinTheGracePeriod_isArriving() {
    val handover = mission("handover", ago(2), assignees = listOf(alice, emma))

    assertEquals(
        Arriving(listOf(handover), next = emptyList()),
        teamNowState(listOf(handover), now),
    )
  }

  @Test
  fun teamNowState_withEveryonePresent_isRunning() {
    val running = mission("running", ago(30), assignees = listOf(alice, bob))

    assertEquals(
        Running(listOf(running), onDuty = 2, next = emptyList()),
        teamNowState(listOf(running), now),
    )
  }

  @Test
  fun teamNowState_withNothingUnderWayAndAMissionWithinTheWindow_isArriving() {
    val soon = mission("soon", inMinutes(20), assignees = listOf(emma))

    assertEquals(
        Arriving(handovers = emptyList(), next = listOf(soon)),
        teamNowState(listOf(soon), now),
    )
  }

  @Test
  fun teamNowState_withTheNextMissionBeyondTheWindow_isQuiet() {
    val later = mission("later", inMinutes(120), assignees = listOf(emma))

    assertEquals(Quiet(listOf(later)), teamNowState(listOf(later), now))
  }

  // Time boundaries

  @Test
  fun teamNowState_justBeforeTheStart_listsTheMissionAsNext() {
    val starting = mission("starting", now + second, assignees = listOf(emma))

    assertEquals(
        Arriving(emptyList(), next = listOf(starting)),
        teamNowState(listOf(starting), now),
    )
  }

  @Test
  fun teamNowState_atTheStart_listsTheMissionAsHandover() {
    val starting = mission("starting", now, assignees = listOf(emma))

    assertEquals(
        Arriving(listOf(starting), next = emptyList()),
        teamNowState(listOf(starting), now),
    )
  }

  @Test
  fun teamNowState_justBeforeTheEnd_stillReportsTheAbsentee() {
    val ending =
        mission(
            "ending",
            ago(60),
            lasts = Duration.ofMinutes(60) + second,
            assignees = listOf(emma),
        )

    assertEquals(
        Missing(listOf(MissingAssignee(ending, emma)), onDuty = 0),
        teamNowState(listOf(ending), now),
    )
  }

  @Test
  fun teamNowState_atTheEnd_isDone() {
    val ended = mission("ended", ago(60), lasts = Duration.ofMinutes(60), assignees = listOf(emma))

    assertEquals(Done, teamNowState(listOf(ended), now))
  }

  @Test
  fun teamNowState_withAMissionExactlyAtTheEndOfTheWindow_includesIt() {
    val atWindow = mission("atWindow", now + window, assignees = listOf(emma))

    assertEquals(
        Arriving(emptyList(), next = listOf(atWindow)),
        teamNowState(listOf(atWindow), now),
    )
  }

  @Test
  fun teamNowState_withAMissionJustAfterTheEndOfTheWindow_isQuiet() {
    val pastWindow = mission("pastWindow", now + window + second, assignees = listOf(emma))

    assertEquals(Quiet(listOf(pastWindow)), teamNowState(listOf(pastWindow), now))
  }

  @Test
  fun teamNowState_shortMissionExactlyAtTheEndOfItsGrace_isArriving() {
    val short = mission("short", now - shortGrace, lasts = shortMission, assignees = listOf(emma))

    assertEquals(Arriving(listOf(short), emptyList()), teamNowState(listOf(short), now))
  }

  @Test
  fun teamNowState_shortMissionJustAfterItsGrace_isMissing() {
    val short =
        mission("short", now - shortGrace - second, lasts = shortMission, assignees = listOf(emma))

    assertEquals(
        Missing(listOf(MissingAssignee(short, emma)), onDuty = 0),
        teamNowState(listOf(short), now),
    )
  }

  @Test
  fun teamNowState_longMissionJustAfterTheShortGrace_isStillArriving() {
    val long =
        mission(
            "long",
            now - shortGrace - second,
            lasts = shortMission + second,
            assignees = listOf(emma),
        )

    assertEquals(Arriving(listOf(long), emptyList()), teamNowState(listOf(long), now))
  }

  @Test
  fun teamNowState_longMissionExactlyAtTheEndOfItsGrace_isArriving() {
    val long = mission("long", now - longGrace, assignees = listOf(emma))

    assertEquals(Arriving(listOf(long), emptyList()), teamNowState(listOf(long), now))
  }

  @Test
  fun teamNowState_longMissionJustAfterItsGrace_isMissing() {
    val long = mission("long", now - longGrace - second, assignees = listOf(emma))

    assertEquals(
        Missing(listOf(MissingAssignee(long, emma)), onDuty = 0),
        teamNowState(listOf(long), now),
    )
  }

  // Several missions

  @Test
  fun teamNowState_withMissionsIn28And29And35Minutes_listsTheFirstTwo() {
    val in28 = mission("in28", inMinutes(28))
    val in29 = mission("in29", inMinutes(29))
    val in35 = mission("in35", inMinutes(35))

    assertEquals(
        Arriving(emptyList(), next = listOf(in28, in29)),
        teamNowState(listOf(in35, in29, in28), now),
    )
  }

  @Test
  fun teamNowState_withMissionsStartingTogether_keepsTheGivenOrderAmongThem() {
    val c = mission("c", inMinutes(20))
    val a = mission("a", inMinutes(10))
    val b = mission("b", inMinutes(20))

    assertEquals(Arriving(emptyList(), next = listOf(a, c, b)), teamNowState(listOf(c, a, b), now))
  }

  @Test
  fun teamNowState_withTwoHandovers_listsBoth() {
    val first = mission("first", ago(2), assignees = listOf(emma))
    val then = mission("then", ago(1), assignees = listOf(alice, noah))

    assertEquals(Arriving(listOf(first, then), emptyList()), teamNowState(listOf(then, first), now))
  }

  @Test
  fun teamNowState_withAMissionUnderWayAndTheNextIn20Minutes_isRunningWithTheNext() {
    val running = mission("running", ago(60), assignees = listOf(alice))
    val next = mission("next", inMinutes(20), assignees = listOf(emma))

    assertEquals(
        Running(listOf(running), onDuty = 1, next = listOf(next)),
        teamNowState(listOf(running, next), now),
    )
  }

  @Test
  fun teamNowState_running_listsTheMissionsWithinTheWindowByStart() {
    val running = mission("running", ago(60), assignees = listOf(alice))
    val in10 = mission("in10", inMinutes(10))
    val in25 = mission("in25", inMinutes(25))
    val in35 = mission("in35", inMinutes(35))

    assertEquals(
        Running(listOf(running), onDuty = 1, next = listOf(in10, in25)),
        teamNowState(listOf(in35, in25, running, in10), now),
    )
  }

  @Test
  fun teamNowState_withAHandoverBesideASettledMission_isArriving() {
    val settled = mission("settled", ago(60), assignees = listOf(alice))
    val handover = mission("handover", ago(2), assignees = listOf(bob, emma))

    assertEquals(
        Arriving(listOf(handover), emptyList()),
        teamNowState(listOf(settled, handover), now),
    )
  }

  @Test
  fun teamNowState_withAHandoverAndAMissionWithinTheWindow_keepsTheNext() {
    val handover = mission("handover", ago(2), assignees = listOf(emma))
    val soon = mission("soon", inMinutes(25), assignees = listOf(noah))

    assertEquals(
        Arriving(listOf(handover), next = listOf(soon)),
        teamNowState(listOf(handover, soon), now),
    )
  }

  @Test
  fun teamNowState_withALateAbsenteeAndAHandover_isMissing() {
    val late = mission("late", ago(30), assignees = listOf(alice, emma))
    val handover = mission("handover", ago(2), assignees = listOf(bob, noah))

    assertEquals(
        Missing(listOf(MissingAssignee(late, emma)), onDuty = 2),
        teamNowState(listOf(late, handover), now),
    )
  }

  @Test
  fun teamNowState_quiet_listsTheMissionsAtTheEarliestStartOnly() {
    val x = mission("x", inMinutes(120))
    val y = mission("y", inMinutes(120))
    val z = mission("z", inMinutes(180))

    assertEquals(Quiet(listOf(x, y)), teamNowState(listOf(z, x, y), now))
  }

  // Attendance semantics

  @Test
  fun teamNowState_withUnknownAttendancePastTheGrace_reportsNobodyMissing() {
    val unknown = mission("unknown", ago(60), assignees = listOf(emma), known = false)

    assertEquals(
        Running(listOf(unknown), onDuty = 0, emptyList(), attendanceUnknown = listOf(unknown)),
        teamNowState(listOf(unknown), now),
    )
  }

  @Test
  fun teamNowState_withFailedAttendancePastTheGrace_reportsNobodyMissing() {
    val failed = mission("failed", ago(60), assignees = listOf(emma), known = false, failed = true)

    assertEquals(
        Running(listOf(failed), onDuty = 0, emptyList(), attendanceUnknown = listOf(failed)),
        teamNowState(listOf(failed), now),
    )
  }

  @Test
  fun teamNowState_withUnknownAttendanceWithinTheGrace_isNotAHandover() {
    val unknown = mission("unknown", ago(1), assignees = listOf(emma), known = false)

    assertEquals(
        Running(listOf(unknown), onDuty = 0, emptyList(), attendanceUnknown = listOf(unknown)),
        teamNowState(listOf(unknown), now),
    )
  }

  @Test
  fun teamNowState_missing_isByMissionStartThenAsTheMissionListsThem() {
    val later = mission("later", ago(10), assignees = listOf(noah, alice, emma))
    val earlier = mission("earlier", ago(20), assignees = listOf(emma, noah))

    val expected =
        listOf(
            MissingAssignee(earlier, emma),
            MissingAssignee(earlier, noah),
            MissingAssignee(later, noah),
            MissingAssignee(later, emma),
        )
    assertEquals(Missing(expected, onDuty = 1), teamNowState(listOf(later, earlier), now))
  }

  @Test
  fun teamNowState_withAVolunteerMissingFromTwoMissions_listsThemUnderEach() {
    val first = mission("first", ago(20), assignees = listOf(emma))
    val then = mission("then", ago(10), assignees = listOf(emma))

    assertEquals(
        Missing(listOf(MissingAssignee(first, emma), MissingAssignee(then, emma)), onDuty = 0),
        teamNowState(listOf(first, then), now),
    )
  }

  @Test
  fun teamNowState_onDuty_countsAVolunteerOnTwoMissionsOnce() {
    val first = mission("first", ago(30), assignees = listOf(alice, bob))
    val then = mission("then", ago(10), assignees = listOf(alice))

    assertEquals(
        Running(listOf(first, then), onDuty = 2, emptyList()),
        teamNowState(listOf(first, then), now),
    )
  }

  @Test
  fun teamNowState_understaffedWithEveryonePresent_isRunning() {
    val short = mission("short", ago(30), assignees = listOf(alice), needed = 3)

    assertEquals(Running(listOf(short), onDuty = 1, emptyList()), teamNowState(listOf(short), now))
  }

  @Test
  fun teamNowState_staffedWithAnAbsentee_isStillMissing() {
    val staffed = mission("staffed", ago(10), assignees = listOf(alice, emma), needed = 1)

    assertEquals(AttendanceStatus.STAFFED, staffed.status(now))
    assertEquals(
        Missing(listOf(MissingAssignee(staffed, emma)), onDuty = 1),
        teamNowState(listOf(staffed), now),
    )
  }

  @Test
  fun teamNowState_withEveryonePresentWithinTheGrace_isRunning() {
    val arrived = mission("arrived", ago(1), assignees = listOf(alice, bob))

    assertEquals(
        Running(listOf(arrived), onDuty = 2, emptyList()),
        teamNowState(listOf(arrived), now),
    )
  }

  // Attendance that is not known, kept apart from nobody being there

  @Test
  fun teamNowState_withAnUnknownMissionBesideAStaffedOne_countsOnlyTheKnownOne() {
    val staffed = mission("staffed", ago(60), assignees = listOf(alice))
    val unknown = mission("unknown", ago(30), assignees = listOf(emma), known = false)

    assertEquals(
        Running(listOf(staffed, unknown), onDuty = 1, emptyList(), listOf(unknown)),
        teamNowState(listOf(unknown, staffed), now),
    )
  }

  @Test
  fun teamNowState_withAFailedMissionBesideAStaffedOne_countsOnlyTheKnownOne() {
    val staffed = mission("staffed", ago(60), assignees = listOf(alice))
    val failed = mission("failed", ago(30), assignees = listOf(emma), known = false, failed = true)

    assertEquals(
        Running(listOf(staffed, failed), onDuty = 1, emptyList(), listOf(failed)),
        teamNowState(listOf(failed, staffed), now),
    )
  }

  @Test
  fun teamNowState_withAnUnknownMissionBesideALateOne_isMissingAndListsItApart() {
    val unknown = mission("unknown", ago(30), assignees = listOf(noah), known = false)
    val late = mission("late", ago(10), assignees = listOf(alice, emma))

    assertEquals(
        Missing(
            listOf(MissingAssignee(late, emma)),
            onDuty = 1,
            attendanceUnknown = listOf(unknown),
        ),
        teamNowState(listOf(late, unknown), now),
    )
  }

  @Test
  fun teamNowState_withAnUnknownMissionBesideAHandover_isArrivingAndListsItApart() {
    val unknown = mission("unknown", ago(30), assignees = listOf(noah), known = false)
    val handover = mission("handover", ago(2), assignees = listOf(alice, emma))

    assertEquals(
        Arriving(listOf(handover), emptyList(), attendanceUnknown = listOf(unknown)),
        teamNowState(listOf(handover, unknown), now),
    )
  }

  // Overlaps and handovers

  @Test
  fun teamNowState_onDuty_leavesOutAVolunteerMissingFromAnotherMission() {
    val absentHere = mission("absentHere", ago(10), assignees = listOf(emma))
    val presentThere =
        mission("presentThere", ago(30), assignees = listOf(emma.copy(checkedInAt = ago(1)), bob))

    // Emma is missing from one mission and checked in to the other: she is missing, not an "other".
    assertEquals(
        Missing(listOf(MissingAssignee(absentHere, emma)), onDuty = 1),
        teamNowState(listOf(absentHere, presentThere), now),
    )
  }

  @Test
  fun teamNowState_withBackToBackMissions_isArrivingThenMissing() {
    val handoverAt = now
    val ending = mission("ending", ago(120), assignees = listOf(alice))
    val starting =
        mission("starting", handoverAt, assignees = listOf(alice.copy(checkedInAt = null)))
    val both = listOf(ending, starting)

    // Four minutes in, the mission that ended is gone and the one taking over is still arriving.
    assertEquals(
        Arriving(listOf(starting), emptyList()),
        teamNowState(both, handoverAt + Duration.ofMinutes(4)),
    )
    // Two minutes later its grace period is over, and Alice is missing from it.
    assertEquals(
        Missing(listOf(MissingAssignee(starting, alice.copy(checkedInAt = null))), onDuty = 0),
        teamNowState(both, handoverAt + Duration.ofMinutes(6)),
    )
  }

  @Test
  fun teamNowState_shortMission_isOverBeforeItsGraceEnds() {
    val running = mission("brief", ago(2), lasts = shortGrace, assignees = listOf(emma))

    assertEquals(Arriving(listOf(running), emptyList()), teamNowState(listOf(running), now))

    // A minute later it has ended, so its absentee is never reported missing.
    val ended = mission("brief", ago(3), lasts = shortGrace, assignees = listOf(emma))

    assertEquals(Done, teamNowState(listOf(ended), now))
  }

  @Test
  fun teamNowState_withAnUpcomingMissionClosedByHand_ignoresIt() {
    val closed = mission("closed", inMinutes(20), assignees = listOf(emma), completed = true)
    val later = mission("later", inMinutes(120), assignees = listOf(emma))

    assertEquals(Quiet(listOf(later)), teamNowState(listOf(closed, later), now))
  }

  // The event day the card speaks for

  @Test
  fun onEventDayOf_keepsTheDaysMissionsAndLeavesTheNextDaysOut() {
    // 20:00 on the first day: one mission is left tonight, the next is at 09:00 tomorrow.
    val evening = Instant.parse("2027-06-20T20:00:00Z")
    val tonight = mission("tonight", Instant.parse("2027-06-20T22:00:00Z"))
    val tomorrow = mission("tomorrow", Instant.parse("2027-06-21T09:00:00Z"))

    val day = listOf(tonight, tomorrow).onEventDayOf(evening, utc)

    assertEquals(listOf(tonight), day)
    assertEquals(Quiet(listOf(tonight)), teamNowState(day, evening))
    // With nothing left tonight, the filter is what keeps the card from announcing tomorrow's
    // mission as if it were tonight's: it says the day is over instead.
    assertEquals(Quiet(listOf(tomorrow)), teamNowState(listOf(tomorrow), evening))
    assertEquals(Done, teamNowState(listOf(tomorrow).onEventDayOf(evening, utc), evening))
  }

  @Test
  fun onEventDayOf_beforeFourInTheMorning_isStillTheEveningsDay() {
    // The day runs from 04:00 to 04:00, so at 03:00 the evening's missions are still the day's
    // and the morning's are not.
    val tonight = mission("tonight", Instant.parse("2027-06-20T21:00:00Z"))
    val morning = mission("morning", Instant.parse("2027-06-21T09:00:00Z"))
    val threeInTheMorning = Instant.parse("2027-06-21T03:00:00Z")

    assertEquals(
        listOf(tonight),
        listOf(tonight, morning).onEventDayOf(threeInTheMorning, utc),
    )
  }

  @Test
  fun onEventDayOf_keepsAMissionRunningAcrossTheTurnOfTheDay() {
    // A night shift from 02:00 to 05:00 belongs to the evening before and to the day it ends on,
    // so a team at work at 04:00 keeps its mission on the card.
    val night = mission("night", Instant.parse("2027-06-21T02:00:00Z"), lasts = Duration.ofHours(3))

    assertEquals(
        listOf(night),
        listOf(night).onEventDayOf(Instant.parse("2027-06-21T03:00:00Z"), utc),
    )
    assertEquals(
        listOf(night),
        listOf(night).onEventDayOf(Instant.parse("2027-06-21T04:30:00Z"), utc),
    )
  }
}
