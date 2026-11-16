// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.days

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.ui.theme.shifter_dimmedText
import com.swent.shifter.ui.theme.shifter_mutedText
import java.time.LocalDate
import kotlinx.coroutines.flow.first

object DayStripTestTags {
  const val STRIP = "dayStrip"

  fun day(date: LocalDate) = "dayStripDay_$date"

  fun missionsDot(date: LocalDate) = "dayStripMissionsDot_$date"
}

/**
 * The days of an event as a row of chips, one of them [selected], scrolling sideways when they do
 * not fit. The selected day is filled, today is in the accent color, a dot marks a day with
 * missions, and a day without any is dimmed.
 *
 * @param days the days to offer, in order.
 * @param daysWithMissions the days among [days] that hold at least one mission.
 * @param onSelect called with the day tapped.
 */
@Composable
fun DayStrip(
    days: List<LocalDate>,
    daysWithMissions: Set<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  // Brings the selected day into view when it is not fully on screen, with the day before it on
  // the left: the days to come matter more than the past. Near the end of the event, the strip
  // stops at its last day, so more past days stay in view.
  LaunchedEffect(selected, days) {
    val index = days.indexOf(selected)
    if (index < 0) return@LaunchedEffect
    // Waits for the first layout: before it, no chip is visible and the strip would slide on open.
    val layout = snapshotFlow { listState.layoutInfo }.first { it.visibleItemsInfo.isNotEmpty() }
    val fullyVisible =
        layout.visibleItemsInfo.any {
          it.index == index &&
              it.offset >= layout.viewportStartOffset &&
              it.offset + it.size <= layout.viewportEndOffset
        }
    if (!fullyVisible) listState.animateScrollToItem((index - 1).coerceAtLeast(0))
  }
  LazyRow(
      state = listState,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = modifier.fillMaxWidth().testTag(DayStripTestTags.STRIP),
  ) {
    items(days, key = { it.toEpochDay() }) { day ->
      DayChip(
          day = day,
          isSelected = day == selected,
          isToday = day == today,
          hasMissions = day in daysWithMissions,
          onClick = { onSelect(day) },
      )
    }
  }
}

@Composable
private fun DayChip(
    day: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    hasMissions: Boolean,
    onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val labelColor: Color
  val dayColor: Color
  when {
    isSelected -> {
      labelColor = colors.onPrimary
      dayColor = colors.onPrimary
    }
    isToday -> {
      labelColor = colors.primary
      dayColor = colors.primary
    }
    hasMissions -> {
      labelColor = shifter_mutedText
      dayColor = colors.onBackground
    }
    else -> {
      labelColor = shifter_dimmedText
      dayColor = shifter_dimmedText
    }
  }
  // Read out instead of the chip's texts, so it says all they and the colors show.
  val fullDate = day.fullDate()
  val date =
      if (isToday) stringResource(R.string.day_strip_today_description, fullDate) else fullDate
  val description = if (hasMissions) date else stringResource(R.string.day_strip_no_missions, date)
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(1.dp),
      modifier =
          Modifier.width(44.dp)
              .clip(MaterialTheme.shapes.medium)
              .background(if (isSelected) colors.primary else Color.Transparent)
              .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
              .semantics { contentDescription = description }
              .padding(vertical = 6.dp)
              .testTag(DayStripTestTags.day(day)),
  ) {
    Text(
        text = if (isToday) stringResource(R.string.day_strip_today) else day.shortWeekday(),
        style = MaterialTheme.typography.labelSmall,
        color = labelColor,
        maxLines = 1,
    )
    Text(
        text = day.dayOfMonth.toString(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = dayColor,
    )
    // Keeps its height without a dot, so every chip is the same size.
    Box(modifier = Modifier.size(4.dp)) {
      if (hasMissions) {
        Box(
            modifier =
                Modifier.size(4.dp)
                    .clip(CircleShape)
                    .background(labelColor)
                    .testTag(DayStripTestTags.missionsDot(day))
        )
      }
    }
  }
}
