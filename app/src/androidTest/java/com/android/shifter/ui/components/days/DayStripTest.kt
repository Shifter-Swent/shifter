// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.days

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
      modifier: Modifier = Modifier,
  ) {
    compose.setContent {
      ShifterTheme {
        DayStrip(
            days = days,
            daysWithMissions = daysWithMissions,
            selected = selected,
            today = today,
            onSelect = { picked = it },
            modifier = modifier,
        )
      }
    }
  }

  private fun day(date: LocalDate) = compose.onNodeWithTag(DayStripTestTags.day(date))

  private fun outsideDay(date: LocalDate) = compose.onNodeWithTag(DayStripTestTags.outsideDay(date))

  /** Room for exactly 7 chips of 44 dp with 6 dp between them. */
  private val sevenDaysWide = Modifier.width(344.dp)

  @Test
  fun aShortEvent_isPaddedWithTheDaysAroundIt_untilTheStripIsFull() {
    // Friday to Monday in room for 7: one day before, two after.
    show(modifier = sevenDaysWide)

    outsideDay(friday.minusDays(1))
        .assert(hasAnyChild(hasText("Thu")))
        .assert(hasAnyChild(hasText("12")))
    outsideDay(monday.plusDays(1)).assertExists()
    outsideDay(monday.plusDays(2)).assertExists()
    outsideDay(friday.minusDays(2)).assertDoesNotExist()
    outsideDay(monday.plusDays(3)).assertDoesNotExist()
    day(friday).assertExists()
  }

  @Test
  fun aPaddedDay_cannotBePicked_andIsNotReadOut() {
    show(modifier = sevenDaysWide)

    outsideDay(monday.plusDays(1)).performClick()

    assertEquals(null, picked)
    outsideDay(monday.plusDays(1))
        .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    compose
        .onNodeWithTag(
            DayStripTestTags.missionsDot(monday.plusDays(1)),
            useUnmergedTree = true,
        )
        .assertDoesNotExist()
  }

  @Test
  fun aLongEvent_isNotPadded() {
    val month = (0L until 30L).map { friday.plusDays(it) }

    show(
        days = month,
        daysWithMissions = month.toSet(),
        selected = friday,
        modifier = sevenDaysWide,
    )

    outsideDay(friday.minusDays(1)).assertDoesNotExist()
    day(friday.plusDays(10)).assertIsNotDisplayed()
  }

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
