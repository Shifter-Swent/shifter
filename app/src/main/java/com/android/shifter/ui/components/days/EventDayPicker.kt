// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.days

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.swent.shifter.model.time.eventDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The day of an event a list of missions is shown on, picked from an [EventDayStrip], with the
 * rules of the Planning: 04:00 days, today by default (the first day before the event, the last one
 * after it), "Back to today", and no strip for an event of one day.
 *
 * Create it with [rememberEventDayPicker].
 *
 * @property days every day of the event, see [eventDays].
 * @property today the event day it is now, see [eventDay].
 */
@Stable
class EventDayPicker
internal constructor(
    val days: List<LocalDate>,
    val today: LocalDate,
    private val selection: DaySelection,
    private val zone: ZoneId,
    hasMissions: Boolean,
) {
  /** The day shown: today when there are no days at all, which leaves nothing to show anyway. */
  val shown: LocalDate
    get() = selection.selected ?: today

  /** Whether the days are offered: an event of one day, or without missions, has no strip. */
  val showsStrip: Boolean = hasMissions && days.size > 1

  /** Shows today again, offered while another day is shown; null otherwise. */
  val onBackToToday: (() -> Unit)?
    get() = selection::backToToday.takeIf { showsStrip && selection.awayFromToday }

  /** Whether something starting at [start] is listed on the day [shown]. */
  fun isShown(start: Instant): Boolean = start.eventDay(zone) == shown

  /** The days of [starts], e.g. to dot those of the team picked. */
  fun daysOf(starts: List<Instant>): Set<LocalDate> = starts.map { it.eventDay(zone) }.toSet()

  /** Shows [day], following today again when [day] is today, as "Back to today" does. */
  fun show(day: LocalDate) = selection.show(day)
}

/**
 * The day shown among those of an event and of its missions, kept through a rotation.
 *
 * @param period when the event starts and ends, or null when unknown: the days then run from the
 *   first to the last mission.
 * @param starts when each mission of the event starts, whatever filter the screen applies, so the
 *   days stay the same when the filter changes.
 * @param now decides which day is today; the caller refreshes it, e.g. with `rememberNow`.
 */
@Composable
fun rememberEventDayPicker(
    period: ClosedRange<Instant>?,
    starts: List<Instant>,
    now: Instant,
    zone: ZoneId,
): EventDayPicker {
  val today = now.eventDay(zone)
  val days =
      remember(period, starts, zone) {
        eventDays(period, starts.map { it.eventDay(zone) }.toSet(), zone)
      }
  val selection = rememberDaySelection(days, today)
  val hasMissions = starts.isNotEmpty()
  return remember(days, today, selection, zone, hasMissions) {
    EventDayPicker(days, today, selection, zone, hasMissions)
  }
}

/**
 * The [DayStrip] of [picker]'s days. Shown by the caller only when [EventDayPicker.showsStrip].
 *
 * @param daysWithMissions the days that get a dot, e.g. those of the team picked, so a dimmed day
 *   is one where it has nothing to do.
 */
@Composable
fun EventDayStrip(
    picker: EventDayPicker,
    daysWithMissions: Set<LocalDate>,
    modifier: Modifier = Modifier,
) {
  DayStrip(
      days = picker.days,
      daysWithMissions = daysWithMissions,
      selected = picker.shown,
      today = picker.today,
      onSelect = picker::show,
      modifier = modifier,
  )
}
