// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.days

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DayStripTest {

  @get:Rule val compose = createComposeRule()

  private val friday = LocalDate.parse("2025-06-13")
  private val saturday = LocalDate.parse("2025-06-14")
  private val sunday = LocalDate.parse("2025-06-15")
  private val monday = LocalDate.parse("2025-06-16")

  private var picked: LocalDate? = null

  private fun show(
      days: List<LocalDate> = listOf(friday, saturday, sunday, monday),
      daysWithMissions: Set<LocalDate> = setOf(friday, saturday, sunday),
      selected: LocalDate = saturday,
      today: LocalDate = saturday,
  ) {
    compose.setContent {
      ShifterTheme {
        DayStrip(
            days = days,
            daysWithMissions = daysWithMissions,
            selected = selected,
            today = today,
            onSelect = { picked = it },
        )
      }
    }
  }

  private fun day(date: LocalDate) = compose.onNodeWithTag(DayStripTestTags.day(date))

  @Test
  fun dayStrip_showsTheWeekdayAndTheDayOfTheMonth() {
    show()

    day(friday).assert(hasText("Fri")).assert(hasText("13"))
  }

  @Test
  fun dayStrip_namesToday() {
    show(selected = friday)

    day(saturday).assert(hasText("Today")).assert(hasText("14"))
    day(friday).assert(hasText("Fri"))
  }

  @Test
  fun dayStrip_marksOnlyTheSelectedDay() {
    val days = listOf(friday, saturday, sunday, monday)

    show(days = days, selected = sunday)

    days.forEach { if (it == sunday) day(it).assertIsSelected() else day(it).assertIsNotSelected() }
  }

  @Test
  fun dayStrip_tappingADay_picksIt() {
    show()

    day(monday).performClick()

    assertEquals(monday, picked)
  }

  @Test
  fun dayStrip_dotsOnlyTheDaysWithMissions() {
    show()

    compose
        .onNodeWithTag(DayStripTestTags.missionsDot(sunday), useUnmergedTree = true)
        .assertExists()
    compose
        .onNodeWithTag(DayStripTestTags.missionsDot(monday), useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun dayStrip_readsOutADayWithoutMissions() {
    show()

    day(sunday).assertContentDescriptionEquals("Sunday 15 June")
    day(monday).assertContentDescriptionEquals("Monday 16 June, no missions")
  }

  @Test
  fun dayStrip_readsOutToday() {
    show(daysWithMissions = setOf(friday))

    day(saturday).assertContentDescriptionEquals("Today, Saturday 14 June, no missions")
  }

  @Test
  fun dayStrip_withTheSelectedDayVisible_doesNotScroll() {
    val month = (0L until 30L).map { friday.plusDays(it) }

    show(days = month, daysWithMissions = month.toSet(), selected = month[2])

    // Scrolled to the selected day, the strip would push the first one off-screen.
    day(month.first()).assertIsDisplayed()
  }

  @Test
  fun dayStrip_withTheSelectedDayOffScreen_scrollsToIt() {
    val month = (0L until 30L).map { friday.plusDays(it) }

    show(days = month, daysWithMissions = month.toSet(), selected = month[15])

    day(month[15]).assertIsDisplayed()
    // The day before stays in view on the left, and the one before that is scrolled away.
    day(month[14]).assertIsDisplayed()
    day(month[13]).assertIsNotDisplayed()
  }

  @Test
  fun dayStrip_withTheLastDaySelected_keepsMorePastDaysInView() {
    val month = (0L until 30L).map { friday.plusDays(it) }

    show(days = month, daysWithMissions = month.toSet(), selected = month.last())

    day(month.last()).assertIsDisplayed()
    // The strip stops at its last day instead of leaving it alone next to the day before.
    day(month[month.size - 3]).assertIsDisplayed()
  }
}
