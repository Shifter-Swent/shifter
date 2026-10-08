// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class EventPeriodFormatTest {

  @Test
  fun showsTheDayOnceForAOneDayEvent() {
    assertEquals(
        "Mon 22 Jun · 09:30 – 17:00",
        format("2026-06-22T09:30:00Z", "2026-06-22T17:00:00Z"),
    )
  }

  @Test
  fun showsBothDaysForAMultiDayEvent() {
    assertEquals(
        "21 Jul 12:00 – 26 Jul 21:30",
        format("2026-07-21T12:00:00Z", "2026-07-26T21:30:00Z"),
    )
  }

  @Test
  fun showsTheYearWhenItIsNotTheCurrentOne() {
    assertEquals(
        "Tue 22 Jun 2027 · 09:30 – 17:00",
        format("2027-06-22T09:30:00Z", "2027-06-22T17:00:00Z"),
    )
    assertEquals(
        "31 Dec 2026 20:00 – 1 Jan 2027 02:00",
        format("2026-12-31T20:00:00Z", "2027-01-01T02:00:00Z"),
    )
  }

  private fun format(startAt: String, endAt: String) =
      formatEventPeriod(
          Instant.parse(startAt),
          Instant.parse(endAt),
          ZoneOffset.UTC,
          today = LocalDate.of(2026, 10, 9),
      )
}
