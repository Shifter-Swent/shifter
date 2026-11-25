// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDayTest {

  private fun day(date: String) = LocalDate.parse(date)

  @Test
  fun eventDay_beforeFour_isTheDayBefore() {
    assertEquals(day("2025-06-14"), Instant.parse("2025-06-15T03:59:00Z").eventDay(ZoneOffset.UTC))
  }

  @Test
  fun eventDay_atFour_isTheSameDay() {
    assertEquals(day("2025-06-15"), Instant.parse("2025-06-15T04:00:00Z").eventDay(ZoneOffset.UTC))
  }

  @Test
  fun eventDay_countsTheHoursOfTheGivenZone() {
    // 03:00 in UTC is already 05:00 in Zurich (UTC+2 in June): past the start of the day there.
    val instant = Instant.parse("2025-06-15T03:00:00Z")

    assertEquals(day("2025-06-14"), instant.eventDay(ZoneOffset.UTC))
    assertEquals(day("2025-06-15"), instant.eventDay(ZoneId.of("Europe/Zurich")))
  }

  /** Whether something running from [from] to [to] runs on [date], in [zone]. */
  private fun runsOn(date: String, from: String, to: String, zone: ZoneId = ZoneOffset.UTC) =
      runsOnEventDay(day(date), Instant.parse(from), Instant.parse(to), zone)

  @Test
  fun runsOnEventDay_insideTheDay_isOnIt() {
    assertTrue(runsOn("2025-06-15", "2025-06-15T09:00:00Z", "2025-06-15T11:00:00Z"))
  }

  @Test
  fun runsOnEventDay_beforeFourInTheMorning_isTheDayBefore() {
    // The day runs from 04:00 to 04:00, so a 02:00 shift belongs to the evening before, and the
    // morning's mission to the day that opens at 04:00.
    assertTrue(runsOn("2025-06-14", "2025-06-15T02:00:00Z", "2025-06-15T03:00:00Z"))
    assertFalse(runsOn("2025-06-15", "2025-06-15T02:00:00Z", "2025-06-15T03:00:00Z"))
    assertFalse(runsOn("2025-06-14", "2025-06-15T09:00:00Z", "2025-06-15T11:00:00Z"))
  }

  @Test
  fun runsOnEventDay_acrossTheTurnOfTheDay_isOnBoth() {
    // A night shift from 02:00 to 05:00 is still under way when the day turns at 04:00.
    assertTrue(runsOn("2025-06-14", "2025-06-15T02:00:00Z", "2025-06-15T05:00:00Z"))
    assertTrue(runsOn("2025-06-15", "2025-06-15T02:00:00Z", "2025-06-15T05:00:00Z"))
  }

  @Test
  fun runsOnEventDay_atTheBounds_takesTheDayItRunsIn() {
    // The day is [04:00, 04:00): one ending exactly at 04:00 is the day before's, and one
    // starting then is the new day's.
    assertFalse(runsOn("2025-06-15", "2025-06-15T03:00:00Z", "2025-06-15T04:00:00Z"))
    assertFalse(runsOn("2025-06-15", "2025-06-16T04:00:00Z", "2025-06-16T05:00:00Z"))
    assertTrue(runsOn("2025-06-15", "2025-06-16T03:59:00Z", "2025-06-16T05:00:00Z"))
  }

  @Test
  fun runsOnEventDay_countsTheHoursOfTheGivenZone() {
    // 03:00 in UTC is 05:00 in Zurich (UTC+2 in June), so the day there has already turned.
    val from = "2025-06-15T03:00:00Z"
    val to = "2025-06-15T03:30:00Z"

    assertTrue(runsOn("2025-06-14", from, to))
    assertFalse(runsOn("2025-06-15", from, to))
    assertFalse(runsOn("2025-06-14", from, to, ZoneId.of("Europe/Zurich")))
    assertTrue(runsOn("2025-06-15", from, to, ZoneId.of("Europe/Zurich")))
  }

  @Test
  fun runsOnEventDay_onTheDayTheClockChanges_keepsTheLocalFourOClock() {
    // Zurich goes back to winter time on 26 October 2025: the day is 25 hours long, and both its
    // bounds are still 04:00 local, so a 23:00 shift of that night is the 26th's.
    val zurich = ZoneId.of("Europe/Zurich")

    assertTrue(runsOn("2025-10-26", "2025-10-26T22:00:00Z", "2025-10-26T23:00:00Z", zurich))
    assertFalse(runsOn("2025-10-27", "2025-10-26T22:00:00Z", "2025-10-26T23:00:00Z", zurich))
    // 03:00 UTC on the 27th is 04:00 in Zurich: the new day has just opened.
    assertTrue(runsOn("2025-10-27", "2025-10-27T03:00:00Z", "2025-10-27T04:00:00Z", zurich))
  }
}
