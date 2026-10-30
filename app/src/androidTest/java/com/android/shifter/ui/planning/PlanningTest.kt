// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.planning

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanningTest {

  @get:Rule val compose = createComposeRule()

  private fun at(time: String): Instant = Instant.parse("2025-06-14T$time:00Z")

  private fun mission(
      id: String,
      start: String = "08:00",
      end: String = "09:00",
      title: String = "Mission $id",
      address: String? = null,
      checkedInAt: Instant? = null,
  ) = PlanningMission(id, title, at(start), at(end), address, checkedInAt)

  // In UTC, so the hours shown do not depend on the time zone of the device running the tests.
  private fun show(missions: List<PlanningMission>) {
    compose.setContent { ShifterTheme { Planning(missions = missions, zone = ZoneOffset.UTC) } }
  }

  private fun topOf(missionId: String): Float =
      compose
          .onNodeWithTag(PlanningTestTags.mission(missionId))
          .fetchSemanticsNode()
          .boundsInRoot
          .top

  @Test
  fun planning_withUnsortedMissions_showsThemChronologically() {
    show(listOf(mission("late", start = "10:00", end = "11:00"), mission("early", start = "08:00")))

    // The ties are covered by PlanningOrderTest: this only checks that the rows follow the order.
    assertTrue(topOf("early") < topOf("late"))
  }

  @Test
  fun planning_withoutMissions_showsTheEmptyState() {
    show(emptyList())

    compose.onNodeWithTag(PlanningTestTags.EMPTY).assertIsDisplayed()
  }

  @Test
  fun planning_withMissions_hidesTheEmptyState() {
    show(listOf(mission("m1")))

    compose.onNodeWithTag(PlanningTestTags.EMPTY).assertDoesNotExist()
  }

  @Test
  fun missionRow_showsItsTitleAndHours() {
    show(listOf(mission("m1", start = "08:30", end = "11:00", title = "Hand out water")))

    compose.onNodeWithText("Hand out water").assertIsDisplayed()
    compose.onNodeWithText("08:30–11:00").assertIsDisplayed()
  }

  @Test
  fun missionRow_withLocation_showsTheAddress() {
    show(listOf(mission("m1", address = "Hydration station A")))

    compose.onNodeWithTag(PlanningTestTags.address("m1")).assertTextEquals("Hydration station A")
  }

  @Test
  fun missionRow_withoutLocation_hidesTheLocation() {
    show(listOf(mission("m1")))

    compose.onNodeWithTag(PlanningTestTags.mission("m1")).assertIsDisplayed()
    compose.onNodeWithTag(PlanningTestTags.address("m1")).assertDoesNotExist()
  }

  @Test
  fun missionRow_checkedIn_showsTheCheckInTime() {
    show(listOf(mission("m1", address = "Station A", checkedInAt = at("08:31"))))

    compose.onNodeWithTag(PlanningTestTags.checkIn("m1")).assertTextEquals("Checked in at 08:31")
  }

  @Test
  fun missionRow_notCheckedIn_hidesTheCheckInStatus() {
    show(listOf(mission("m1", address = "Station A")))

    compose.onNodeWithTag(PlanningTestTags.checkIn("m1")).assertDoesNotExist()
  }
}
