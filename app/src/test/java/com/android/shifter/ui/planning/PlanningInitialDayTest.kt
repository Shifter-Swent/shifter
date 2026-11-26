// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.planning

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The [Planning] opens on the day a notification asks for, or on today without one. */
@RunWith(AndroidJUnit4::class)
class PlanningInitialDayTest {

  @get:Rule val compose = createComposeRule()

  private val today = LocalDate.parse("2026-11-20")

  private fun mission(id: String, day: LocalDate) =
      PlanningMission(
          id = id,
          title = "Mission $id",
          startAt = day.atTime(10, 0).toInstant(ZoneOffset.UTC),
          endAt = day.atTime(11, 0).toInstant(ZoneOffset.UTC),
      )

  private val todays = mission("today", today)
  private val sundays = mission("sunday", today.plusDays(2))

  private fun show(initialDay: LocalDate?) {
    compose.setContent {
      ShifterTheme {
        Planning(
            missions = listOf(todays, sundays),
            eventPeriod = null,
            today = today,
            zone = ZoneOffset.UTC,
            initialDay = initialDay,
        )
      }
    }
  }

  private fun row(mission: PlanningMission) =
      compose.onNodeWithTag(PlanningTestTags.mission(mission.id))

  @Test
  fun initialDay_opensThePlanningOnIt() {
    show(initialDay = today.plusDays(2))

    row(sundays).assertExists()
    row(todays).assertDoesNotExist()
  }

  @Test
  fun initialDay_notADayOfTheEvent_showsToday() {
    show(initialDay = today.plusDays(30))

    row(todays).assertExists()
    row(sundays).assertDoesNotExist()
  }

  @Test
  fun noInitialDay_showsToday() {
    show(initialDay = null)

    row(todays).assertExists()
    row(sundays).assertDoesNotExist()
  }
}
