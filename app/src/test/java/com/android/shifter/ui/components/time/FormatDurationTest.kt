// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.time

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormatDurationTest {

  private val resources: Resources = ApplicationProvider.getApplicationContext<Context>().resources

  private fun format(duration: Duration) = formatDuration(resources, duration)

  @Test
  fun formatDuration_zero_isZeroMinutes() {
    assertEquals("0 min", format(Duration.ZERO))
  }

  @Test
  fun formatDuration_underAMinute_isZeroMinutes() {
    assertEquals("0 min", format(Duration.ofSeconds(59)))
  }

  @Test
  fun formatDuration_inMinutes_writesMinutes() {
    assertEquals("25 min", format(Duration.ofMinutes(25)))
    assertEquals("59 min", format(Duration.ofSeconds(59 * 60 + 59)))
  }

  @Test
  fun formatDuration_anHour_writesOneHour() {
    assertEquals("1 h", format(Duration.ofMinutes(60)))
  }

  @Test
  fun formatDuration_hoursAndMinutes_padsTheMinutes() {
    assertEquals("1 h 15", format(Duration.ofMinutes(75)))
    assertEquals("2 h 05", format(Duration.ofMinutes(125)))
  }

  @Test
  fun formatDuration_wholeHours_writesHours() {
    assertEquals("3 h", format(Duration.ofHours(3)))
    assertEquals("25 h", format(Duration.ofHours(25)))
  }

  @Test
  fun formatDuration_negative_isZeroMinutes() {
    assertEquals("0 min", format(Duration.ofMinutes(-5)))
  }
}
