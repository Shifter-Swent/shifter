// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.days

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupByDayTest {

  private data class Item(val id: String, val start: Instant)

  private fun item(id: String, start: String) = Item(id, Instant.parse(start))

  private fun group(items: List<Item>, zone: ZoneId = ZoneOffset.UTC) =
      groupByDay(items, { it.start }, zone).map { (day, items) -> day to items.map { it.id } }

  private fun day(date: String) = LocalDate.parse(date)

  @Test
  fun groupByDay_withSeveralDays_ordersTheDaysAndTheItemsInside() {
    val items =
        listOf(
            item("sun-morning", "2025-06-15T09:00:00Z"),
            item("sat-evening", "2025-06-14T18:00:00Z"),
            item("sat-morning", "2025-06-14T08:00:00Z"),
        )

    assertEquals(
        listOf(
            day("2025-06-14") to listOf("sat-morning", "sat-evening"),
            day("2025-06-15") to listOf("sun-morning"),
        ),
        group(items),
    )
  }

  @Test
  fun groupByDay_withANightShift_putsItWithTheEveningBefore() {
    val items =
        listOf(item("night", "2025-06-15T02:00:00Z"), item("evening", "2025-06-14T19:00:00Z"))

    assertEquals(listOf(day("2025-06-14") to listOf("evening", "night")), group(items))
  }

  @Test
  fun groupByDay_withItemsStartingTogether_keepsTheirGivenOrder() {
    val items =
        listOf(item("second", "2025-06-14T08:00:00Z"), item("first", "2025-06-14T08:00:00Z"))

    assertEquals(listOf(day("2025-06-14") to listOf("second", "first")), group(items))
  }

  @Test
  fun groupByDay_withNoItems_returnsNoGroup() {
    assertEquals(emptyList<Pair<LocalDate, List<String>>>(), group(emptyList()))
  }
}
