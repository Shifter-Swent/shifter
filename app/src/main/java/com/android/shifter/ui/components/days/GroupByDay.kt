// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.days

import com.swent.shifter.model.time.eventDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
