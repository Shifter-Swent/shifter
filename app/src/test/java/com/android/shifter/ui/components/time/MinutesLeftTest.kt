// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.time

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class MinutesLeftTest {

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
  }
}
