// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.time

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

  @Test
  fun formatTime_inTheZone_writesHoursAndMinutes() {
    val instant = Instant.parse("2026-11-14T08:05:00Z")

    assertEquals("09:05", instant.formatTime(ZoneId.of("Europe/Zurich")))
  }

  @Test
  fun formatTime_atMidnight_writesZeroHours() {
    assertEquals("00:00", Instant.parse("2026-11-14T00:00:00Z").formatTime(ZoneOffset.UTC))
  }
}
