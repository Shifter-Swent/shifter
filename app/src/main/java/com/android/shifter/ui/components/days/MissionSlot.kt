// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.days

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.swent.shifter.R
import com.swent.shifter.model.time.formatTime
import com.swent.shifter.model.time.formatWeekday
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * When a mission from [startAt] to [endAt] runs, as listed under [day]: its times alone,
 * "08:00–11:00". The date is added for one starting on another date, as one after midnight that an
 * event's day lists under the evening before ("Sun 22 Nov, 01:00–03:00"), and both dates for one
 * crossing midnight ("Sat 21 Nov 22:00 – Sun 22 Nov 02:00"), rather than "22:00–02:00". The dates
 * name their weekday, as the Planning's days do.
 */
@Composable
fun missionSlot(startAt: Instant, endAt: Instant, day: LocalDate, zone: ZoneId): String {
  val startDay = startAt.atZone(zone).toLocalDate()
  val endDay = endAt.atZone(zone).toLocalDate()
  return when {
    startDay != endDay ->
        stringResource(
            R.string.mission_slot_across_days,
            startDay.formatWeekday(),
            startAt.formatTime(zone),
            endDay.formatWeekday(),
            endAt.formatTime(zone),
        )
    startDay != day ->
        stringResource(
            R.string.mission_slot_on_day,
            startDay.formatWeekday(),
            startAt.formatTime(zone),
            endAt.formatTime(zone),
        )
    else -> stringResource(R.string.mission_slot, startAt.formatTime(zone), endAt.formatTime(zone))
  }
}
