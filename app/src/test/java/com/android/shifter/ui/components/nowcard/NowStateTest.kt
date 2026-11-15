// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.ui.components.nowcard

import com.swent.shifter.model.checkin.checkInWindow
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionLocation
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class NowStateTest {

  @Test
  fun nowState_withoutMissions_isDone() {
    assertEquals(NowState.DoneForToday, nowState(emptyList(), START, emptySet()))
  }

  @Test
  fun nowState_withOnlyEndedOrClosedMissions_isDone() {
    val ended = mission("ended", START.minusSeconds(7_200), START.minusSeconds(3_600))
    val closed = mission("closed").copy(completed = true)
    assertEquals(NowState.DoneForToday, nowState(listOf(ended, closed), START, setOf(ended.id)))
  }

  @Test
  fun nowState_moreThan45MinutesBeforeStart_isLater() {
    val next = mission()
    assertEquals(
        NowState.Later(next),
        nowState(listOf(next), START.minusSeconds(2_760), emptySet()),
    )
  }

  @Test
  fun nowState_atThe45MinuteBoundary_becomesSoon() {
    val next = mission()
    val boundary = START.minus(Duration.ofMinutes(45))
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
  fun nowState_whenTheCheckInWindowOpens_staysSoonUntilTheMissionStarts() {
    val current = mission()
    // Reuse the automatic/manual check-in window instead of restating its 15-minute rule.
    val opening = current.checkInWindow().start
    for (now in listOf(opening.minusNanos(1), opening, opening.plusNanos(1), START.minusNanos(1))) {
      assertEquals(NowState.Soon(current), nowState(listOf(current), now, emptySet()))
      assertEquals(NowState.Soon(current), nowState(listOf(current), now, setOf(current.id)))
    }
    assertEquals(
        NowState.Ongoing(current, null),
        nowState(listOf(current), START, setOf(current.id)),
    )
  }

  @Test
  fun nowState_withAClosedCurrentMission_selectsTheFollowingOne() {
    val closed = mission().copy(completed = true)
    val next = mission("next", START.plusSeconds(600))
    assertEquals(NowState.Soon(next), nowState(listOf(closed, next), START, setOf(closed.id)))
  }

  @Test
  fun nowState_withAClosedFutureMission_skipsIt() {
    val closed = mission("closed", START.plusSeconds(600)).copy(completed = true)
    val next = mission("next", START.plusSeconds(5_400))
    assertEquals(NowState.Later(next), nowState(listOf(closed, next), START, emptySet()))
  }

  @Test
  fun nowState_withAStartedMission_showsTheNextOneWithin45Minutes() {
    val current = mission("current", START.minusSeconds(600))
    val next = mission("next", START.plusSeconds(1_800))
    val after = mission("after", START.plusSeconds(2_400))
    val missions = listOf(after, next, current)
    assertEquals(NowState.Late(current, next), nowState(missions, START, emptySet()))
    assertEquals(NowState.Ongoing(current, next), nowState(missions, START, setOf(current.id)))
  }

  @Test
  fun nowState_withTheNextMissionAtThe45MinuteBoundary_includesIt() {
    val current = mission("current", START.minusSeconds(600))
    val next = mission("next", START.plus(Duration.ofMinutes(45)))
    for (checkedIn in listOf(emptySet(), setOf(current.id))) {
      val expected =
          if (current.id in checkedIn) NowState.Ongoing(current, next)
          else NowState.Late(current, next)
      assertEquals(expected, nowState(listOf(current, next), START, checkedIn))
    }
  }

  @Test
  fun nowState_withTheNextMissionBeyond45Minutes_omitsIt() {
    val current = mission("current", START.minusSeconds(600))
    val next = mission("next", START.plus(Duration.ofMinutes(45)).plusNanos(1))
    val missions = listOf(current, next)
    assertEquals(NowState.Late(current, null), nowState(missions, START, emptySet()))
    assertEquals(NowState.Ongoing(current, null), nowState(missions, START, setOf(current.id)))
  }

  @Test
  fun nowState_withBackToBackMissions_switchesAtTheSharedBoundary() {
    val first = mission("first", START.minusSeconds(3_600), START)
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
  fun nowState_withOverlappingRunningMissions_keepsTheEarliestStart() {
    val first = mission("first", START.minusSeconds(1_200), START.plusSeconds(3_600))
    val second = mission("second", START.minusSeconds(600), START.plusSeconds(1_800))
    val next = mission("next", START.plusSeconds(300))
    for (missions in listOf(listOf(first, second, next), listOf(next, second, first))) {
      assertEquals(NowState.Late(first, next), nowState(missions, START, setOf(second.id)))
      assertEquals(NowState.Ongoing(first, next), nowState(missions, START, setOf(first.id)))
    }
  }

  @Test
  fun nowState_withEqualStarts_selectsTheEarliestEnd() {
    val shorter = mission("z-shorter", START, START.plusSeconds(1_800))
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
    val first = mission("first", START.plusSeconds(600))
    val later = mission("later", START.plusSeconds(1_200))
    assertEquals(NowState.Soon(first), nowState(listOf(later, first), START, emptySet()))
  }

  @Test
  fun nowState_withoutALocation_usesTheSameTimeAndPresenceRules() {
    val current = mission().copy(location = null)
    assertEquals(
        NowState.Soon(current),
        nowState(listOf(current), START.minusSeconds(60), emptySet()),
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
    val current = mission("night", midnight.minusSeconds(1_800), midnight.plusSeconds(1_800))
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

  private fun mission(
      id: String = "mission",
      start: Instant = START,
      end: Instant = start.plusSeconds(3_600),
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
  }
}
