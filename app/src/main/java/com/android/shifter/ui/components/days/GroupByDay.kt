// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.days

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The hour an event's day starts at. A night shift starting at 02:00 belongs to the evening before,
 * which is where a volunteer looks for it.
 */
const val DAY_STARTS_AT_HOUR = 4L

/** The event day [this] falls on in [zone], a day running from 04:00 to 04:00. */
fun Instant.eventDay(zone: ZoneId): LocalDate =
    atZone(zone).toLocalDateTime().minusHours(DAY_STARTS_AT_HOUR).toLocalDate()

/**
 * Whether something running from [startAt] to [endAt] runs during the event [day] in [zone], the
 * day running from 04:00 to 04:00 as [eventDay] counts it. One crossing 04:00 runs on both days, so
 * it is on each of them.
 */
fun runsOnEventDay(day: LocalDate, startAt: Instant, endAt: Instant, zone: ZoneId): Boolean =
    endAt > day.eventDayStart(zone) && startAt < day.plusDays(1).eventDayStart(zone)

/**
 * When the event day [this] starts in [zone]: 04:00 local time, also on a day the clock changes.
 */
private fun LocalDate.eventDayStart(zone: ZoneId): Instant =
    atTime(LocalTime.of(DAY_STARTS_AT_HOUR.toInt(), 0)).atZone(zone).toInstant()

/**
 * [items] grouped by the [eventDay] they start on, earliest day first.
 *
 * Inside a day, the items are ordered by start. The sort is stable, so items starting together keep
 * the order they were given in: a caller with its own tie-break sorts them first.
 *
 * @param startOf when an item starts: an item belongs to the day it starts on.
 * @param zone the time zone the days are counted in.
 */
fun <T> groupByDay(
    items: List<T>,
    startOf: (T) -> Instant,
    zone: ZoneId,
): List<Pair<LocalDate, List<T>>> =
    items.sortedBy(startOf).groupBy { startOf(it).eventDay(zone) }.toList()
