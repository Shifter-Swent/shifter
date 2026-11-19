// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.days

import androidx.compose.runtime.mutableStateOf
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DaySelectionTest {

  private val friday = LocalDate.parse("2025-06-13")
  private val saturday = friday.plusDays(1)
  private val sunday = friday.plusDays(2)
  private val days = listOf(friday, saturday, sunday)

  private fun selection(today: LocalDate = saturday, days: List<LocalDate> = this.days) =
      DaySelection(days, today, mutableStateOf(null))

  @Test
  fun previousAndNextDay_areTheDaysAroundTheOneShown() {
    val selection = selection()

    assertEquals(friday, selection.previousDay)
    assertEquals(sunday, selection.nextDay)
  }

  @Test
  fun previousAndNextDay_stopAtTheEnds() {
    val selection = selection()

    selection.pick(friday)
    assertNull(selection.previousDay)

    selection.pick(sunday)
    assertNull(selection.nextDay)
  }

  @Test
  fun previousAndNextDay_withoutDays_areNull() {
    val selection = selection(days = emptyList())

    assertNull(selection.previousDay)
    assertNull(selection.nextDay)
  }

  @Test
  fun showingToday_followsTodayAgain() {
    val picked = mutableStateOf<Long?>(null)
    DaySelection(days, saturday, picked).show(saturday)

    // Shown on the next day of the event, once the night goes past 04:00.
    assertEquals(sunday, DaySelection(days, sunday, picked).selected)
  }

  @Test
  fun showingAnotherDay_keepsIt() {
    val picked = mutableStateOf<Long?>(null)
    DaySelection(days, saturday, picked).show(friday)

    assertEquals(friday, DaySelection(days, sunday, picked).selected)
  }
}
