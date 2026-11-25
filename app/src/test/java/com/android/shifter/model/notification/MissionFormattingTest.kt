// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the two mission formats share: the day of a time, and the day their tap opens. */
class MissionFormattingTest {

  private val zurich = ZoneId.of("Europe/Zurich")

  @Test
  fun formatDayOfWeek_writesTheShortDayInTheZone() {
    // 23:30 UTC on Saturday is already Sunday in Zurich.
    assertEquals("Sun", Instant.parse("2026-11-28T23:30:00Z").formatDayOfWeek(zurich))
  }

  @Test
  fun planningOf_aMissionAtTwoInTheNight_opensTheDayBefore() {
    // 02:00 on Sunday in Zurich belongs to Saturday's event day.
    val night = Instant.parse("2026-11-29T01:00:00Z")
    val line = MissionLine("m1", "Night watch", night, night + Duration.ofHours(2))

    assertEquals(
        NotificationTarget.Planning("e1", "Fest", "2026-11-28"),
        planningOf("e1", "Fest", line, zurich),
    )
  }
}
