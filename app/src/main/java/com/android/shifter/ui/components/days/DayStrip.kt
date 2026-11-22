// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.days

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.model.time.formatDayOfWeek
import com.swent.shifter.model.time.formatFullDate
import com.swent.shifter.ui.theme.shifter_dimmedText
import com.swent.shifter.ui.theme.shifter_mutedText
import java.time.LocalDate
import kotlinx.coroutines.flow.first

object DayStripTestTags {
  const val STRIP = "dayStrip"

  fun day(date: LocalDate) = "dayStripDay_$date"

  fun missionsDot(date: LocalDate) = "dayStripMissionsDot_$date"

  /** A day before or after the event, added so a short event still fills the strip. */
  fun outsideDay(date: LocalDate) = "dayStripOutsideDay_$date"
}

/** The width of a day's chip, and the gap between two. */
private val CHIP_WIDTH = 44.dp
private val CHIP_GAP = 6.dp

/** A day around a short event: half as visible as a day of the event without missions. */
private val OUTSIDE_DAY_COLOR = shifter_dimmedText.copy(alpha = 0.5f)

/**
 * [days] widened with the days just before and after them until [fit] days, the days of the event
 * in the middle (one more after than before when the count is odd); [days] as they are when they
 * already fill it. The days must follow each other, as [eventDays] gives them.
 */
internal fun padToFit(days: List<LocalDate>, fit: Int): List<LocalDate> {
  if (days.isEmpty() || days.size >= fit) return days
  val before = (fit - days.size) / 2L
  return (0L until fit).map { days.first().plusDays(it - before) }
}

/**
 * The days of an event as a row of chips, one of them [selected], scrolling sideways when they do
 * not fit. The selected day is filled, today is in the accent color, a dot marks a day with
 * missions, and a day without any is dimmed.
 *
 * A short event still fills the width: the days just before and after it are added around it,
 * dimmed, and can be neither tapped nor read out.
 *
 * @param days the days to offer, in order.
 * @param daysWithMissions the days among [days] that hold at least one mission.
 * @param today the event day it is now, `now.eventDay(zone)`, not `LocalDate.now()`: between
 *   midnight and 04:00 it is still the day before, under which the missions running are grouped.
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
  BoxWithConstraints(modifier = modifier.fillMaxWidth().testTag(DayStripTestTags.STRIP)) {
    val fit = ((maxWidth + CHIP_GAP) / (CHIP_WIDTH + CHIP_GAP)).toInt()
    val shown = remember(days, fit) { padToFit(days, fit) }
    val listState = rememberLazyListState()
    // Brings the selected day into view when it is not fully on screen, with the day before it on
    // the left: the days to come matter more than the past. Near the end of the event, the strip
    // stops at its last day, so more past days stay in view.
    LaunchedEffect(selected, shown) {
      val index = shown.indexOf(selected)
      if (index < 0) return@LaunchedEffect
      // Waits for the first layout: before it, no chip is visible and the strip would slide on
      // open.
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
        // Days that all fit spread over the whole width; more scroll with a fixed gap.
        horizontalArrangement =
            if (shown.size <= fit) Arrangement.SpaceBetween else Arrangement.spacedBy(CHIP_GAP),
        modifier = Modifier.fillMaxWidth(),
    ) {
      items(shown, key = { it.toEpochDay() }) { day ->
        if (day in days) {
          DayChip(
              day = day,
              isSelected = day == selected,
              isToday = day == today,
              hasMissions = day in daysWithMissions,
              onClick = { onSelect(day) },
          )
        } else {
          OutsideDayChip(day, isToday = day == today)
        }
      }
    }
  }
}

/**
 * A day around a short event: fainter than a day of the event without missions, so the event's
 * first and last days stand out, and neither tappable nor read out. Today keeps its name, as when
 * the event starts tomorrow, in a faded primary so it can still be found.
 */
@Composable
private fun OutsideDayChip(day: LocalDate, isToday: Boolean) {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(1.dp),
      modifier =
          Modifier.width(CHIP_WIDTH)
              .semantics { hideFromAccessibility() }
              .padding(vertical = 6.dp)
              .testTag(DayStripTestTags.outsideDay(day)),
  ) {
    Text(
        text = if (isToday) stringResource(R.string.day_strip_today) else day.formatDayOfWeek(),
        style = MaterialTheme.typography.labelSmall,
        color =
            if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else OUTSIDE_DAY_COLOR,
        maxLines = 1,
    )
    Text(
        text = day.dayOfMonth.toString(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = OUTSIDE_DAY_COLOR,
    )
    // The room of the missions dot, so it is as tall as the other chips.
    Box(modifier = Modifier.size(4.dp))
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
  val fullDate = day.formatFullDate()
  val date =
      if (isToday) stringResource(R.string.day_strip_today_description, fullDate) else fullDate
  val description = if (hasMissions) date else stringResource(R.string.day_strip_no_missions, date)
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(1.dp),
      modifier =
          Modifier.width(CHIP_WIDTH)
              .clip(MaterialTheme.shapes.medium)
              .background(if (isSelected) colors.primary else Color.Transparent)
              .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
              .semantics { contentDescription = description }
              .padding(vertical = 6.dp)
              .testTag(DayStripTestTags.day(day)),
  ) {
    Text(
        text = if (isToday) stringResource(R.string.day_strip_today) else day.formatDayOfWeek(),
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
