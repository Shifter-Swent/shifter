// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.nowcard

import com.swent.shifter.model.checkin.CheckInRules
import com.swent.shifter.model.checkin.checkInWindow
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionLocation
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NowStateTest {

  @Test
  fun nowState_withoutMissions_isDone() {
    assertEquals(NowState.DoneForToday, nowState(emptyList(), START, emptySet()))
  }

  @Test
  fun nowState_withOnlyEndedOrClosedMissions_isDone() {
    val ended = mission("ended", START.minus(hours(2)), START.minus(hours(1)))
    val closed = mission("closed").copy(completed = true)
    assertEquals(NowState.DoneForToday, nowState(listOf(ended, closed), START, setOf(ended.id)))
  }

  @Test
  fun nowState_moreThan30MinutesBeforeStart_isLater() {
    val next = mission()
    assertEquals(NowState.Later(next), nowState(listOf(next), START.minus(minutes(31)), emptySet()))
  }

  @Test
  fun nowState_atThe30MinuteBoundary_becomesSoon() {
    val next = mission()
    val boundary = START.minus(minutes(30))
    assertEquals(NowState.Later(next), nowState(listOf(next), boundary.minusNanos(1), emptySet()))
    assertEquals(NowState.Soon(next), nowState(listOf(next), boundary, emptySet()))
    assertEquals(NowState.Soon(next), nowState(listOf(next), boundary.plusNanos(1), emptySet()))
  }

  @Test
  fun nowState_justBeforeStart_isSoonRatherThanLate() {
    val next = mission()
    assertEquals(NowState.Soon(next), nowState(listOf(next), START.minusNanos(1), emptySet()))
  }

  @Test
  fun nowState_atStartWithoutCheckIn_isLate() {
    val current = mission()
    assertEquals(NowState.Late(current, null), nowState(listOf(current), START, emptySet()))
  }

  @Test
  fun nowState_atStartWithCheckIn_isOngoing() {
    val current = mission()
    assertEquals(
        NowState.Ongoing(current, null),
        nowState(listOf(current), START, setOf(current.id)),
    )
  }

  @Test
  fun nowState_untilItsEnd_keepsTheRunningMission() {
    val current = mission()
    val beforeEnd = current.endAt.minusNanos(1)
    assertEquals(NowState.Late(current, null), nowState(listOf(current), beforeEnd, emptySet()))
    assertEquals(
        NowState.Ongoing(current, null),
        nowState(listOf(current), beforeEnd, setOf(current.id)),
    )
  }

  @Test
  fun nowState_fromItsEnd_discardsTheMissionEvenWithACheckIn() {
    val current = mission()
    for (now in listOf(current.endAt, current.endAt.plusNanos(1))) {
      assertEquals(NowState.DoneForToday, nowState(listOf(current), now, emptySet()))
      assertEquals(NowState.DoneForToday, nowState(listOf(current), now, setOf(current.id)))
    }
  }

  @Test
  fun nowState_withACheckInBeforeStart_staysSoonUntilStart() {
    val current = mission()
    // Inside the check-in window, which opens before the start, but not started yet.
    val inWindow = current.checkInWindow().start.plus(minutes(1))
    assertEquals(NowState.Soon(current), nowState(listOf(current), inWindow, setOf(current.id)))
    assertEquals(
        NowState.Ongoing(current, null),
        nowState(listOf(current), START, setOf(current.id)),
    )
  }

  @Test
  fun nowState_withAClosedCurrentMission_selectsTheFollowingOne() {
    val closed = mission().copy(completed = true)
    val next = mission("next", START.plus(minutes(10)))
    assertEquals(NowState.Soon(next), nowState(listOf(closed, next), START, setOf(closed.id)))
  }

  @Test
  fun nowState_withAClosedFutureMission_skipsIt() {
    val closed = mission("closed", START.plus(minutes(10))).copy(completed = true)
    val next = mission("next", START.plus(minutes(90)))
    assertEquals(NowState.Later(next), nowState(listOf(closed, next), START, emptySet()))
  }

  @Test
  fun nowState_withAClosedMissionComingNext_leavesItOutOfTheNextStrip() {
    val current = mission("current", START.minus(minutes(10)))
    val closed = mission("closed", START.plus(minutes(10))).copy(completed = true)
    val next = mission("next", START.plus(minutes(20)))
    val missions = listOf(current, closed, next)
    assertEquals(NowState.Late(current, next), nowState(missions, START, emptySet()))
    assertEquals(NowState.Ongoing(current, next), nowState(missions, START, setOf(current.id)))
  }

  @Test
  fun nowState_withAStartedMission_showsTheNextOneWithin30Minutes() {
    val current = mission("current", START.minus(minutes(10)))
    val next = mission("next", START.plus(minutes(20)))
    val after = mission("after", START.plus(minutes(25)))
    val missions = listOf(after, next, current)
    assertEquals(NowState.Late(current, next), nowState(missions, START, emptySet()))
    assertEquals(NowState.Ongoing(current, next), nowState(missions, START, setOf(current.id)))
  }

  @Test
  fun nowState_withTheNextMissionAtThe30MinuteBoundary_includesIt() {
    val current = mission("current", START.minus(minutes(10)))
    val next = mission("next", START.plus(minutes(30)))
    val missions = listOf(current, next)
    assertEquals(NowState.Late(current, next), nowState(missions, START, emptySet()))
    assertEquals(NowState.Ongoing(current, next), nowState(missions, START, setOf(current.id)))
  }

  @Test
  fun nowState_withTheNextMissionBeyond30Minutes_omitsIt() {
    val current = mission("current", START.minus(minutes(10)))
    val next = mission("next", START.plus(minutes(30)).plusNanos(1))
    val missions = listOf(current, next)
    assertEquals(NowState.Late(current, null), nowState(missions, START, emptySet()))
    assertEquals(NowState.Ongoing(current, null), nowState(missions, START, setOf(current.id)))
  }

  @Test
  fun nowState_withBackToBackMissions_switchesAtTheSharedBoundary() {
    val first = mission("first", START.minus(hours(1)), START)
    val second = mission("second")
    val missions = listOf(second, first)
    assertEquals(
        NowState.Ongoing(first, second),
        nowState(missions, START.minusNanos(1), setOf(first.id)),
    )
    assertEquals(NowState.Late(second, null), nowState(missions, START, setOf(first.id)))
    assertEquals(
        NowState.Ongoing(second, null),
        nowState(missions, START, setOf(first.id, second.id)),
    )
  }

  @Test
  fun nowState_lateForTheCurrentMissionAndCheckedInToTheNext_staysLateWithTheNextShown() {
    val first = mission("first", START.minus(hours(1)), START)
    val second = mission("second")
    val earlyCheckIn = START.minus(minutes(10))
    assertEquals(
        NowState.Late(first, second),
        nowState(listOf(first, second), earlyCheckIn, setOf(second.id)),
    )
  }

  @Test
  fun nowState_withOverlappingRunningMissions_prefersTheOneCheckedInTo() {
    val first = mission("first", START.minus(minutes(20)), START.plus(hours(1)))
    val second = mission("second", START.minus(minutes(10)), START.plus(minutes(30)))
    val next = mission("next", START.plus(minutes(5)))
    for (missions in listOf(listOf(first, second, next), listOf(next, second, first))) {
      assertEquals(NowState.Ongoing(second, next), nowState(missions, START, setOf(second.id)))
      assertEquals(NowState.Ongoing(first, next), nowState(missions, START, setOf(first.id)))
    }
  }

  @Test
  fun nowState_withOverlappingRunningMissionsAndNoCheckIn_isLateForTheEarliest() {
    val first = mission("first", START.minus(minutes(20)), START.plus(hours(1)))
    val second = mission("second", START.minus(minutes(10)), START.plus(minutes(30)))
    assertEquals(NowState.Late(first, null), nowState(listOf(second, first), START, emptySet()))
  }

  @Test
  fun nowState_withEqualStarts_selectsTheEarliestEnd() {
    val shorter = mission("z-shorter", START, START.plus(minutes(30)))
    val longer = mission("a-longer")
    assertEquals(NowState.Late(shorter, null), nowState(listOf(longer, shorter), START, emptySet()))
  }

  @Test
  fun nowState_withEqualSlots_usesEventThenMissionIdRegardlessOfTitle() {
    val first = mission("a").copy(eventId = "event-a", title = "Z title")
    val sameEvent = mission("b").copy(eventId = "event-a", title = "A title")
    val otherEvent = mission("0").copy(eventId = "event-b")
    for (missions in
        listOf(listOf(first, sameEvent, otherEvent), listOf(otherEvent, sameEvent, first))) {
      assertEquals(NowState.Late(first, null), nowState(missions, START, emptySet()))
    }
  }

  @Test
  fun nowState_withUnsortedFutureMissions_selectsTheEarliest() {
    val first = mission("first", START.plus(minutes(10)))
    val later = mission("later", START.plus(minutes(20)))
    assertEquals(NowState.Soon(first), nowState(listOf(later, first), START, emptySet()))
  }

  @Test
  fun nowState_withoutALocation_usesTheSameTimeAndPresenceRules() {
    val current = mission().copy(location = null)
    assertEquals(
        NowState.Soon(current),
        nowState(listOf(current), START.minus(minutes(1)), emptySet()),
    )
    assertEquals(NowState.Late(current, null), nowState(listOf(current), START, emptySet()))
    assertEquals(
        NowState.Ongoing(current, null),
        nowState(listOf(current), START, setOf(current.id)),
    )
  }

  @Test
  fun nowState_withAnUnrelatedCheckIn_keepsTheVolunteerLate() {
    val current = mission()
    assertEquals(NowState.Late(current, null), nowState(listOf(current), START, setOf("another")))
  }

  @Test
  fun nowState_withAMissionAcrossMidnight_keepsItUntilItsEnd() {
    val midnight = Instant.parse("2026-11-15T00:00:00Z")
    val current = mission("night", midnight.minus(minutes(30)), midnight.plus(minutes(30)))
    assertEquals(
        NowState.Ongoing(current, null),
        nowState(listOf(current), midnight, setOf(current.id)),
    )
  }

  @Test
  fun nowState_withASuppliedMissionTomorrow_keepsItInTheHorizon() {
    val next = mission("tomorrow", START.plus(Duration.ofDays(1)))
    assertEquals(NowState.Later(next), nowState(listOf(next), START, emptySet()))
  }

  @Test
  fun soonBeforeStart_coversTheCheckInWindow() {
    // The card must show a mission before its check-in opens, or the volunteer could miss it.
    assertTrue(NowCardRules.SOON_BEFORE_START >= CheckInRules.OPENS_BEFORE_START)
  }

  private fun mission(
      id: String = "mission",
      start: Instant = START,
      end: Instant = start.plus(hours(1)),
  ) =
      Mission(
          id = id,
          eventId = "event",
          title = id,
          description = "Briefing",
          volunteersNeeded = 1,
          startAt = start,
          endAt = end,
          location = MissionLocation("Welcome desk", GeoPosition(46.5191, 6.5668)),
          createdAt = START.minus(Duration.ofDays(1)),
      )

  private companion object {
    val START: Instant = Instant.parse("2026-11-14T12:00:00Z")

    fun minutes(count: Long): Duration = Duration.ofMinutes(count)

    fun hours(count: Long): Duration = Duration.ofHours(count)
  }
}
