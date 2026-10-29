// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.attendance

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManagerOverviewContentTest {

  @get:Rule val compose = createComposeRule()

  private val start = Instant.parse("2027-06-20T09:00:00Z")
  // Five minutes in: the check-in of "Set up" is open, the one of "Serve drinks" is not.
  private val now = start.plus(Duration.ofMinutes(5))

  private val alice = AttendanceVolunteer("u1", "Alice Martin", start.minus(Duration.ofMinutes(8)))
  private val thomas = AttendanceVolunteer("u2", "Thomas Petit", start.plus(Duration.ofMinutes(3)))
  private val emma = AttendanceVolunteer("u3", "Emma Dubois")

  private val setUp =
      mission("m1", "Set up equipment", "logistics", "Logistics", 0)
          .copy(assignees = listOf(alice, thomas, emma), attendanceKnown = true)
  private val drinks = mission("m2", "Serve drinks", "bar", "Bar", 4)
  private val loaded = AttendanceUiState(isLoading = false, missions = listOf(setUp, drinks))

  private fun mission(
      id: String,
      title: String,
      teamId: String?,
      teamName: String?,
      hour: Long,
  ) =
      AttendanceMission(
          id = id,
          title = title,
          teamId = teamId,
          teamName = teamName,
          volunteersNeeded = 3,
          startAt = start.plus(Duration.ofHours(hour)),
          endAt = start.plus(Duration.ofHours(hour + 2)),
          assignees = emptyList(),
      )

  private fun show(state: AttendanceUiState, onRetry: () -> Unit = {}) {
    compose.setContent {
      ShifterTheme {
        ManagerOverviewContent(
            state = state,
            eventTitle = "City Marathon",
            now = now,
            onRetry = onRetry,
            zone = ZoneId.of("UTC"),
        )
      }
    }
  }

  @Test
  fun collapsedCard_showsTheMissionItsTeamScheduleAndLiveCount() {
    show(loaded)

    compose.onNodeWithTag(ManagerOverviewTestTags.TITLE).assertTextEquals("City Marathon")
    compose.onNodeWithTag(ManagerOverviewTestTags.SUBTITLE).assertTextEquals("Manager Overview")
    compose.onNodeWithText("My Missions").assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.MISSION_COUNT).assertTextEquals("2 missions")
    compose.onNodeWithText("Set up equipment").assertIsDisplayed()
    compose.onNodeWithText("Logistics · 09:00–11:00").assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.counter("m1")).assertTextEquals("2/3")
    // One label per card, and it heads its counter rather than sitting beside it.
    val labels = compose.onAllNodesWithText("LIVE CHECK-IN").assertCountEquals(2)
    val label = labels[0].getBoundsInRoot()
    val counter = compose.onNodeWithTag(ManagerOverviewTestTags.counter("m1")).getBoundsInRoot()
    assertTrue("the label must end above the counter", label.bottom <= counter.top)
    assertEquals(label.left, counter.left)
    // Nothing is told about who is there until the card is expanded.
    compose.onNodeWithTag(ManagerOverviewTestTags.present("m1")).assertDoesNotExist()
  }

  @Test
  fun expandingAMission_showsWhoIsPresentWithTheirTimeAndWhoIsMissing() {
    show(loaded)

    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("m1")).performClick()

    compose.onNodeWithTag(ManagerOverviewTestTags.present("m1")).assertIsDisplayed()
    compose.onNodeWithText("PRESENT (2)").assertIsDisplayed()
    compose.onNodeWithText("Alice Martin").assertIsDisplayed()
    compose.onNodeWithText("08:52").assertIsDisplayed()
    compose.onNodeWithText("Thomas Petit").assertIsDisplayed()
    compose.onNodeWithText("09:03").assertIsDisplayed()
    compose.onNodeWithText("MISSING (1)").assertIsDisplayed()
    compose.onNodeWithText("Emma Dubois").assertIsDisplayed()

    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("m1")).performClick()

    compose.onNodeWithTag(ManagerOverviewTestTags.present("m1")).assertDoesNotExist()
  }

  @Test
  fun missions_expandAndCollapseOnTheirOwn() {
    val b = setUp.copy(id = "b")
    val c = setUp.copy(id = "c")
    show(loaded.copy(missions = listOf(setUp, b, c)))
    val ids = listOf("m1", b.id, c.id)

    // Opening one leaves the ones already open as they are.
    ids.forEach {
      compose
          .onNodeWithTag(ManagerOverviewTestTags.missionHeader(it))
          .performScrollTo()
          .performClick()
    }

    ids.forEach { compose.onNodeWithTag(ManagerOverviewTestTags.present(it)).assertExists() }

    compose
        .onNodeWithTag(ManagerOverviewTestTags.missionHeader(b.id))
        .performScrollTo()
        .performClick()

    compose.onNodeWithTag(ManagerOverviewTestTags.present(b.id)).assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.present("m1")).assertExists()
    compose.onNodeWithTag(ManagerOverviewTestTags.present(c.id)).assertExists()
  }

  @Test
  fun selectingATeam_listsOnlyItsMissions() {
    show(loaded)
    compose.onNodeWithTag(ManagerOverviewTestTags.ALL_TEAMS_CHIP).assertIsSelected()

    compose.onNodeWithTag(ManagerOverviewTestTags.teamChip("bar")).performClick()

    compose.onNodeWithTag(ManagerOverviewTestTags.mission("m2")).assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.mission("m1")).assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.MISSION_COUNT).assertTextEquals("1 mission")
  }

  @Test
  fun filteringATeamOutAndBack_leavesTheCardsOpenedAsTheyWere() {
    show(loaded)
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("m1")).performClick()

    compose.onNodeWithTag(ManagerOverviewTestTags.teamChip("bar")).performClick()
    compose.onNodeWithTag(ManagerOverviewTestTags.ALL_TEAMS_CHIP).performClick()

    compose.onNodeWithTag(ManagerOverviewTestTags.present("m1")).assertExists()
  }

  @Test
  fun missionShortOfVolunteers_isReportedUnderstaffedOnceTheCheckInIsOpen() {
    show(loaded)

    compose.onNodeWithTag(ManagerOverviewTestTags.status("m1")).assertTextEquals("Understaffed")
  }

  @Test
  fun missionWhoseCheckInHasNotOpened_warnsAboutNothingAndNamesNobodyMissing() {
    // Everyone is expected but the window has not opened: not a soul is late yet.
    show(
        loaded.copy(
            missions = listOf(drinks.copy(assignees = listOf(emma), attendanceKnown = true))
        )
    )

    compose
        .onNodeWithTag(ManagerOverviewTestTags.status("m2"))
        .assertTextEquals("Check-in not open yet")
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("m2")).performClick()
    compose.onNodeWithText("MISSING (1)").assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.missing("m2")).assertDoesNotExist()
  }

  @Test
  fun missionWithUnknownAttendance_countsNobodyPresentAndNamesNobodyMissing() {
    val waiting = setUp.copy(attendanceKnown = false)
    val failed = setUp.copy(id = "m3", attendanceKnown = false, attendanceFailed = true)
    show(loaded.copy(missions = listOf(waiting, failed, setUp.copy(id = "m4"))))

    // Not "0/3": nobody is known to be present, which is not the same as nobody being there.
    compose.onNodeWithTag(ManagerOverviewTestTags.counter("m1")).assertTextEquals("—/3")
    compose
        .onNodeWithTag(ManagerOverviewTestTags.status("m1"))
        .assertTextEquals("Waiting for attendance data")
    compose.onNodeWithTag(ManagerOverviewTestTags.counter("m3")).assertTextEquals("—/3")
    compose
        .onNodeWithTag(ManagerOverviewTestTags.status("m3"))
        .assertTextEquals("Attendance unavailable")
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("m3")).performClick()
    compose.onNodeWithTag(ManagerOverviewTestTags.missing("m3")).assertDoesNotExist()
    // The mission that failed leaves the one still followed with its attendance.
    compose.onNodeWithTag(ManagerOverviewTestTags.counter("m4")).assertTextEquals("2/3")
    compose.onNodeWithTag(ManagerOverviewTestTags.status("m4")).assertTextEquals("Understaffed")
  }

  @Test
  fun firstLoad_showsTheSpinner() {
    show(AttendanceUiState())

    compose.onNodeWithTag(ManagerOverviewTestTags.LOADING).assertIsDisplayed()
  }

  @Test
  fun withoutAnyMission_showsTheEmptyState() {
    show(AttendanceUiState(isLoading = false))

    compose.onNodeWithTag(ManagerOverviewTestTags.EMPTY).assertTextEquals("No missions available")
  }

  @Test
  fun failedLoad_showsTheErrorAndRetries() {
    var retries = 0
    show(AttendanceUiState(isLoading = false, loadFailed = true), onRetry = { retries++ })

    compose.onNodeWithTag(ManagerOverviewTestTags.LOAD_ERROR).assertIsDisplayed()
    // The failure says why there is nothing, so it is not called empty on top of it.
    compose.onNodeWithTag(ManagerOverviewTestTags.EMPTY).assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.RETRY).performClick()

    assertEquals(1, retries)
  }
}
