// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodicRunsTest {

  private val runs = PeriodicRuns(Duration.ofMinutes(5))

  @Test
  fun takeIfDue_theFirstRun_isDue() {
    assertTrue(runs.takeIfDue(USER, NOW))
  }

  @Test
  fun takeIfDue_withinThePeriod_isNotDue() {
    runs.takeIfDue(USER, NOW)

    assertFalse(runs.takeIfDue(USER, NOW + Duration.ofMinutes(4)))
  }

  @Test
  fun takeIfDue_aTickLandingJustAfterTheMinute_keepsThe5Minutes() {
    runs.takeIfDue(USER, NOW.plusMillis(20))

    assertTrue(runs.takeIfDue(USER, NOW + Duration.ofMinutes(5).plusMillis(5)))
  }

  @Test
  fun takeIfDue_countsFromTheLastRun_notFromTheTicksSkipped() {
    runs.takeIfDue(USER, NOW)
    runs.takeIfDue(USER, NOW + Duration.ofMinutes(4))

    assertTrue(runs.takeIfDue(USER, NOW + Duration.ofMinutes(5)))
    assertFalse(runs.takeIfDue(USER, NOW + Duration.ofMinutes(9)))
  }

  @Test
  fun takeIfDue_whenTheClockWentBack_isDueAtOnce() {
    runs.takeIfDue(USER, NOW)

    assertTrue(runs.takeIfDue(USER, NOW - Duration.ofHours(1)))
  }

  @Test
  fun takeIfDue_forAnotherUser_isDueAtOnce() {
    runs.takeIfDue(USER, NOW)

    assertTrue(runs.takeIfDue("user-2", NOW + Duration.ofMinutes(1)))
  }

  private companion object {
    const val USER = "user-1"
    val NOW: Instant = Instant.parse("2026-11-27T08:00:00Z")
  }
}
