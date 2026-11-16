// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.time

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TimeFormatTest {

  @Test
  fun formatDay_writesTheDayAndMonth() {
    assertEquals("21 Jul", LocalDate.of(2026, 7, 21).formatDay())
    assertEquals("21 Jul 2026", LocalDate.of(2026, 7, 21).formatDay(withYear = true))
    assertEquals("1 Jan", LocalDate.of(2027, 1, 1).formatDay())
  }

  @Test
  fun formatWeekday_writesTheWeekdayFirst() {
    assertEquals("Mon 22 Jun", LocalDate.of(2026, 6, 22).formatWeekday())
    assertEquals("Mon 22 Jun 2026", LocalDate.of(2026, 6, 22).formatWeekday(withYear = true))
  }

  @Test
  fun formatDayOfWeek_writesTheShortDay() {
    assertEquals("Sun", LocalDate.of(2025, 6, 15).formatDayOfWeek())
  }

  @Test
  fun formatFullDate_writesTheWholeDate() {
    assertEquals("Sunday 15 June", LocalDate.of(2025, 6, 15).formatFullDate())
  }

  @Test
  fun formatWeekday_inAnotherLocale_followsIt() {
    val day = LocalDate.of(2026, 6, 22)
    // Compared with the JDK's own French names, which differ between JDKs and Android.
    val french = DateTimeFormatter.ofPattern("EEE d MMM uuuu", Locale.FRENCH).format(day)

    assertEquals(french, day.formatWeekday(withYear = true, locale = Locale.FRENCH))
    assertNotEquals(french, day.formatWeekday(withYear = true))
  }

  @Test
  fun formatTime_inTheZone_writesHoursAndMinutes() {
    val instant = Instant.parse("2026-11-14T08:05:00Z")

    assertEquals("09:05", instant.formatTime(ZoneId.of("Europe/Zurich")))
  }

  @Test
  fun formatTime_atMidnight_writesZeroHours() {
    assertEquals("00:00", Instant.parse("2026-11-14T00:00:00Z").formatTime(ZoneOffset.UTC))
  }

  @Test
  fun formatTime_ofALocalTime_padsHoursAndMinutes() {
    assertEquals("07:05", LocalTime.of(7, 5).formatTime())
  }

  @Test
  fun formatTime_pastMidnightInTheZone_rollsOverTheDate() {
    assertEquals("00:30", Instant.parse("2026-11-14T23:30:00Z").formatTime(ZURICH))
  }

  @Test
  fun formatTime_atSpringForward_skipsTheMissingHour() {
    assertEquals("03:30", Instant.parse("2026-03-29T01:30:00Z").formatTime(ZURICH))
  }

  @Test
  fun formatTime_inTheRepeatedAutumnHour_readsTheSame() {
    assertEquals("02:30", Instant.parse("2026-10-25T00:30:00Z").formatTime(ZURICH))
    assertEquals("02:30", Instant.parse("2026-10-25T01:30:00Z").formatTime(ZURICH))
  }

  @Test
  fun minutesLeft_roundsUpToTheMinute() {
    assertEquals(Duration.ofMinutes(1), minutesLeft(NOW, NOW.plusSeconds(30)))
    assertEquals(Duration.ofMinutes(60), minutesLeft(NOW, NOW.plusSeconds(59 * 60 + 59)))
    assertEquals(Duration.ofMinutes(26), minutesLeft(NOW, NOW.plusSeconds(25 * 60).plusNanos(1)))
  }

  @Test
  fun minutesLeft_onAWholeMinute_keepsIt() {
    assertEquals(Duration.ofMinutes(25), minutesLeft(NOW, NOW.plusSeconds(25 * 60)))
  }

  @Test
  fun minutesLeft_atOrAfterTheEnd_isZero() {
    assertEquals(Duration.ZERO, minutesLeft(NOW, NOW))
    assertEquals(Duration.ZERO, minutesLeft(NOW, NOW.minusSeconds(90)))
  }

  private companion object {
    val NOW: Instant = Instant.parse("2026-11-14T14:00:00Z")
    val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")
  }
}
