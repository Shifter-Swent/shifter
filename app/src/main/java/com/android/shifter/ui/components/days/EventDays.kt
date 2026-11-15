// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.days

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Every [eventDay] from the earliest to the latest of the event's [period] and of [itemDays], none
 * skipped: a day without items in between is still offered, dimmed.
 *
 * Without the [period], when the event could not be read, the days run from the first to the last
 * item: only the empty days before and after them are missing.
 *
 * @param period when the event starts and ends, or null when unknown.
 * @param itemDays the days holding at least one item, e.g. the keys of [groupByDay].
 * @return the days in order, or an empty list when there is neither a period nor an item.
 */
fun eventDays(
    period: ClosedRange<Instant>?,
    itemDays: Collection<LocalDate>,
    zone: ZoneId,
): List<LocalDate> {
  val ends = period?.let { listOf(it.start.eventDay(zone), it.endInclusive.eventDay(zone)) }
  val bounds = itemDays + ends.orEmpty()
  val first = bounds.minOrNull() ?: return emptyList()
  val last = bounds.max()
  return generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.toList()
}
