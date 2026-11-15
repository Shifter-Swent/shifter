// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.days

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.LocalDate

// Previews of DayStrip, kept apart from the component itself: they are tooling the IDE renders and
// no test ever runs, so coverage is told to leave this file alone.

private val FRIDAY: LocalDate = LocalDate.parse("2025-06-13")
private val TODAY: LocalDate = FRIDAY.plusDays(1)

/** The week of the Figma frames: Friday 13 to Thursday 19, nothing planned on Monday 16. */
private val WEEK: List<LocalDate> = (0L until 7L).map { FRIDAY.plusDays(it) }
private val WEEK_WITH_MISSIONS: Set<LocalDate> = WEEK.toSet() - FRIDAY.plusDays(3)

@Composable
private fun PreviewHost(selected: LocalDate) {
  ShifterTheme {
    // The width of the Planning on a phone, so the strip scrolls as it does in the app.
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
      DayStrip(
          days = WEEK,
          daysWithMissions = WEEK_WITH_MISSIONS,
          selected = selected,
          today = TODAY,
          onSelect = {},
          modifier = Modifier.width(358.dp),
      )
    }
  }
}

@Preview(name = "Today selected")
@Composable
private fun TodaySelectedPreview() = PreviewHost(selected = TODAY)

@Preview(name = "Tomorrow selected")
@Composable
private fun TomorrowSelectedPreview() = PreviewHost(selected = TODAY.plusDays(1))

@Preview(name = "A day without missions selected")
@Composable
private fun EmptyDaySelectedPreview() = PreviewHost(selected = FRIDAY.plusDays(3))
