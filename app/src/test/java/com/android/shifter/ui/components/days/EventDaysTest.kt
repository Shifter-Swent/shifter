// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.days

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class EventDaysTest {

  private fun at(instant: String): Instant = Instant.parse(instant)

  private fun day(date: String): LocalDate = LocalDate.parse(date)

  private fun days(from: String, to: String): List<LocalDate> =
      generateSequence(day(from)) { it.plusDays(1) }.takeWhile { it <= day(to) }.toList()

  private fun eventDays(period: ClosedRange<Instant>?, itemDays: Set<LocalDate>) =
      eventDays(period, itemDays, ZoneOffset.UTC)

  @Test
  fun eventDays_coversTheWholePeriod_daysWithoutItemsIncluded() {
    val period = at("2025-06-13T08:00:00Z")..at("2025-06-16T22:00:00Z")

    assertEquals(
        days("2025-06-13", "2025-06-16"),
        eventDays(period, setOf(day("2025-06-14"))),
    )
  }

  @Test
  fun eventDays_withoutThePeriod_fillsTheDaysBetweenTheItems() {
    assertEquals(
        days("2025-06-14", "2025-06-16"),
        eventDays(null, setOf(day("2025-06-16"), day("2025-06-14"))),
    )
  }

  @Test
  fun eventDays_withAnItemOutsideThePeriod_reachesIt() {
    val period = at("2025-06-13T08:00:00Z")..at("2025-06-14T22:00:00Z")

    assertEquals(
        days("2025-06-13", "2025-06-15"),
        eventDays(period, setOf(day("2025-06-15"))),
    )
  }

  @Test
  fun eventDays_withAnEventEndingAtFourSharp_endsWithTheNightBefore() {
    val period = at("2025-06-13T20:00:00Z")..at("2025-06-15T04:00:00Z")

    assertEquals(days("2025-06-13", "2025-06-14"), eventDays(period, emptySet()))
  }

  @Test
  fun eventDays_withAnEventEndingAfterFour_includesItsLastMorning() {
    val period = at("2025-06-13T20:00:00Z")..at("2025-06-15T04:01:00Z")

    assertEquals(days("2025-06-13", "2025-06-15"), eventDays(period, emptySet()))
  }

  @Test
  fun eventDays_withNeitherPeriodNorItems_isEmpty() {
    assertEquals(emptyList<LocalDate>(), eventDays(null, emptySet()))
  }
}
