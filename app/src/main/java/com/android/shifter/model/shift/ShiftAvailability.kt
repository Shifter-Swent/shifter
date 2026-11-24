// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.schedule.TimeSlot
import com.swent.shifter.model.schedule.merged
import com.swent.shifter.model.time.formatDayOfWeek
import com.swent.shifter.model.time.formatHour
import java.time.LocalTime
import java.time.ZoneId

/**
 * The stretches of time [shifts] cover, sorted by start, the shifts that follow each other back to
 * back joined into one: "Sat 10–12" and "Sat 12–14" give "Sat 10–14", also across midnight. It is
 * the availability of a volunteer who chose these shifts.
 *
 * Joined by [merged], as the schedule proposal joins a volunteer's free time.
 */
fun availabilityOf(shifts: Collection<Shift>): List<AvailabilitySlot> =
    merged(shifts.map { TimeSlot(it.startAt, it.endAt) }).map { AvailabilitySlot(it.start, it.end) }

/**
 * This stretch as the shifts are summed up, in whole hours in [zone]: "Fri 18–20" within a day,
 * "Fri 22–24" up to midnight, and "Fri 22–Sat 02" when it runs into another day.
 */
fun AvailabilitySlot.formatShiftHours(zone: ZoneId): String {
  val start = startAt.atZone(zone)
  val end = endAt.atZone(zone)
  val startDay = start.toLocalDate()
  val endsAtMidnight = end.toLocalTime() == LocalTime.MIDNIGHT
  val from = "${startDay.formatDayOfWeek()} ${formatHour(start.hour)}"
  return when {
    end.toLocalDate() == startDay -> "$from–${formatHour(end.hour)}"
    endsAtMidnight && end.toLocalDate() == startDay.plusDays(1) -> "$from–24"
    else -> "$from–${end.toLocalDate().formatDayOfWeek()} ${formatHour(end.hour)}"
  }
}
