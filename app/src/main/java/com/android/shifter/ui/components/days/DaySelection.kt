// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.days

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.model.time.formatFullDate
import java.time.LocalDate

/**
 * The day shown among [days]: the one picked in a [DayStrip], or [today] until one is.
 *
 * Create it with [rememberDaySelection].
 */
@Stable
class DaySelection
internal constructor(
    private val days: List<LocalDate>,
    private val today: LocalDate,
    private val picked: MutableState<Long?>,
) {
  /**
   * The day shown: the one picked while it is still one of the [days], else [today], or the first
   * day before the event and the last one after it. Null when there are no days at all.
   */
  val selected: LocalDate?
    get() =
        picked.value?.let(LocalDate::ofEpochDay)?.takeIf { it in days }
            ?: days.firstOrNull()?.let { today.coerceIn(it, days.last()) }

  /** Whether another day than [today] is shown while today is one of the [days]. */
  val awayFromToday: Boolean
    get() = today in days && selected != today

  /** The day before the one shown, or null on the first day. */
  val previousDay: LocalDate?
    get() = besideSelected(-1)

  /** The day after the one shown, or null on the last day. */
  val nextDay: LocalDate?
    get() = besideSelected(1)

  private fun besideSelected(step: Int): LocalDate? = selected?.let {
    days.getOrNull(days.indexOf(it) + step)
  }

  /** Shows [day], and keeps showing it when today changes. */
  fun pick(day: LocalDate) {
    picked.value = day.toEpochDay()
  }

  /** Shows [day], following today again when [day] is today, as [backToToday] does. */
  fun show(day: LocalDate) {
    if (day == today) backToToday() else pick(day)
  }

  /** Shows today again, and follows it from then on, e.g. when the night goes past 04:00. */
  fun backToToday() {
    picked.value = null
  }
}

/**
 * The day shown among [days], kept through a rotation.
 *
 * @param days the days that can be shown, in order, e.g. from [eventDays].
 * @param today the event day it is now, see [com.swent.shifter.model.time.eventDay].
 */
@Composable
fun rememberDaySelection(days: List<LocalDate>, today: LocalDate): DaySelection {
  // The picked day by epoch day, which a Bundle can hold; null follows today.
  val picked = rememberSaveable { mutableStateOf<Long?>(null) }
  return remember(days, today, picked) { DaySelection(days, today, picked) }
}

/** "Back to today", shown in a title's row while another day is shown. */
@Composable
fun BackToToday(onClick: () -> Unit, modifier: Modifier = Modifier) {
  Text(
      text = stringResource(R.string.day_strip_back_to_today),
      fontSize = 13.sp,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.primary,
      modifier = modifier.clickable(role = Role.Button, onClick = onClick),
  )
}

/** Says that nothing is planned on [day], where its missions would be. */
@Composable
fun DayWithoutMissions(day: LocalDate, modifier: Modifier = Modifier) {
  Text(
      text = stringResource(R.string.day_strip_nothing_planned, day.formatFullDate()),
      fontSize = 13.sp,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier =
          modifier
              .fillMaxWidth()
              .clip(MaterialTheme.shapes.medium)
              .background(MaterialTheme.colorScheme.surfaceContainer)
              .padding(horizontal = 14.dp, vertical = 16.dp),
  )
}
