// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.attendance

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
  private val utc = ZoneId.of("UTC")

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

  /** [setUp] moved to run from [from] to [to], keeping who is there and who is not. */
  private fun setUpRunning(id: String, from: Duration, to: Duration) =
      setUp.copy(id = id, startAt = now.plus(from), endAt = now.plus(to))

  private fun show(state: AttendanceUiState, onRetry: () -> Unit = {}) {
    compose.setContent {
      ShifterTheme {
        ManagerOverviewContent(
            state = state,
            eventTitle = "City Marathon",
            now = now,
            onRetry = onRetry,
            zone = utc,
        )
      }
    }
  }

  /** Brings [tag] into the lazy list, so a card below the fold can be read and tapped. */
  private fun scrollTo(tag: String): SemanticsNodeInteraction {
    compose.onNodeWithTag(ManagerOverviewTestTags.SCREEN).performScrollToNode(hasTestTag(tag))
    return compose.onNodeWithTag(tag)
  }

  @Test
  fun collapsedCard_showsTheMissionItsTeamScheduleAndLiveCount() {
    show(loaded)

    compose.onNodeWithTag(ManagerOverviewTestTags.TITLE).assertTextEquals("City Marathon")
    compose.onNodeWithTag(ManagerOverviewTestTags.SUBTITLE).assertTextEquals("Manager Overview")
    compose.onNodeWithText("My Missions").assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.MISSION_COUNT).assertTextEquals("2 missions")
    compose.onNodeWithText("Set up equipment").assertIsDisplayed()
    // Times alone, since the mission runs on the day being read.
    compose.onNodeWithText("Logistics · 09:00–11:00").assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.counter("m1")).assertTextEquals("2/3")
    // The label heads its counter rather than sitting beside it.
    val label = compose.onAllNodesWithText("LIVE CHECK-IN")[0].getBoundsInRoot()
    val counter = compose.onNodeWithTag(ManagerOverviewTestTags.counter("m1")).getBoundsInRoot()
    assertTrue("the label must end above the counter", label.bottom <= counter.top)
    assertEquals(label.left, counter.left)
    // Nothing is told about who is there until the card is expanded.
    compose.onNodeWithTag(ManagerOverviewTestTags.present("m1")).assertDoesNotExist()
  }

  @Test
  fun missionOnAnotherDayOrAcrossMidnight_showsEnoughOfItsDates() {
    val nextDay = setUpRunning("next", Duration.ofDays(1), Duration.ofDays(1).plusHours(2))
    val overnight =
        setUp.copy(
            id = "night",
            startAt = Instant.parse("2027-06-20T22:00:00Z"),
            endAt = Instant.parse("2027-06-21T02:00:00Z"),
        )
    show(loaded.copy(missions = listOf(nextDay, overnight)))

    // A mission of another day is told apart from one of today, and one crossing midnight reads
    // as the two days it spans rather than "22:00–02:00".
    compose.onNodeWithText("Logistics · 21 Jun, 09:05–11:05").assertIsDisplayed()
    compose.onNodeWithText("Logistics · 20 Jun 22:00 – 21 Jun 02:00").assertIsDisplayed()
  }

  @Test
  fun missionOfNoTeamOrOfAGoneTeam_namesThemGeneralAndUnknown() {
    val general = setUp.copy(id = "general", teamId = null, teamName = null)
    val gone = setUp.copy(id = "gone", teamId = "deleted", teamName = null)
    show(loaded.copy(missions = listOf(general, gone)))

    compose.onNodeWithText("General · 09:00–11:00").assertIsDisplayed()
    // A team that no longer exists is not shown as General, which means no team at all.
    compose.onNodeWithText("Unknown team · 09:00–11:00").assertIsDisplayed()
    // It keeps a chip of its own, so its missions stay filterable; a mission of no team has none.
    compose.onNodeWithTag(ManagerOverviewTestTags.teamChip("deleted")).assertIsDisplayed()
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
    ids.forEach { scrollTo(ManagerOverviewTestTags.missionHeader(it)).performClick() }

    ids.forEach { scrollTo(ManagerOverviewTestTags.present(it)).assertExists() }

    scrollTo(ManagerOverviewTestTags.missionHeader(b.id)).performClick()

    compose.onNodeWithTag(ManagerOverviewTestTags.present(b.id)).assertDoesNotExist()
    scrollTo(ManagerOverviewTestTags.present("m1")).assertExists()
    scrollTo(ManagerOverviewTestTags.present(c.id)).assertExists()
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
  fun teamFilterAndOpenedCards_surviveAConfigurationChange() {
    val restorer = StateRestorationTester(compose)
    restorer.setContent {
      ShifterTheme {
        ManagerOverviewContent(
            state = loaded,
            eventTitle = "City Marathon",
            now = now,
            onRetry = {},
            zone = utc,
        )
      }
    }
    compose.onNodeWithTag(ManagerOverviewTestTags.teamChip("logistics")).performClick()
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("m1")).performClick()

    restorer.emulateSavedInstanceStateRestore()

    // Rotating the phone neither resets the filter nor closes the card opened under it.
    compose.onNodeWithTag(ManagerOverviewTestTags.teamChip("logistics")).assertIsSelected()
    compose.onNodeWithTag(ManagerOverviewTestTags.present("m1")).assertExists()
  }

  @Test
  fun missionShortOfVolunteers_isReportedUnderstaffedOnceItHasStarted() {
    show(loaded)

    compose.onNodeWithTag(ManagerOverviewTestTags.status("m1")).assertTextEquals("Understaffed")
  }

  @Test
  fun missionNotStartedYet_awaitsTheRestWithoutCallingThemMissing() {
    // The check-in opened ten minutes ago, the mission starts in five: nobody is late.
    val soon = setUpRunning("soon", Duration.ofMinutes(5), Duration.ofHours(2))
    show(loaded.copy(missions = listOf(soon)))

    compose.onNodeWithTag(ManagerOverviewTestTags.status("soon")).assertTextEquals("Check-in open")
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("soon")).performClick()

    // Those already there still show, the others are awaited rather than reported late.
    compose.onNodeWithText("PRESENT (2)").assertIsDisplayed()
    compose.onNodeWithText("Alice Martin").assertIsDisplayed()
    compose.onNodeWithText("EXPECTED (1)").assertIsDisplayed()
    compose.onNodeWithText("Emma Dubois").assertIsDisplayed()
    compose.onNodeWithText("MISSING (1)").assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.missing("soon")).assertDoesNotExist()
  }

  @Test
  fun missionThatIsOver_stopsWarningButKeepsWhoWasThere() {
    // Two of the three needed came, and the check-in closed an hour ago with the mission.
    val over = setUpRunning("over", Duration.ofHours(-3), Duration.ofHours(-1))
    show(loaded.copy(missions = listOf(over)))

    // A warning nobody can act on any more is not shown.
    compose.onNodeWithTag(ManagerOverviewTestTags.status("over")).assertTextEquals("Finished")
    compose.onNodeWithTag(ManagerOverviewTestTags.counter("over")).assertTextEquals("2/3")
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("over")).performClick()

    // What happened is still readable, so the manager can go over the attendance afterwards.
    compose.onNodeWithText("PRESENT (2)").assertIsDisplayed()
    compose.onNodeWithText("Alice Martin").assertIsDisplayed()
    compose.onNodeWithText("MISSING (1)").assertIsDisplayed()
    compose.onNodeWithText("Emma Dubois").assertIsDisplayed()
  }

  @Test
  fun missionWhoseCheckInHasNotOpened_warnsAboutNothingAndCannotBeExpanded() {
    // Everyone is expected but the window has not opened: not a soul is late yet.
    show(
        loaded.copy(
            missions = listOf(drinks.copy(assignees = listOf(emma), attendanceKnown = true))
        )
    )

    compose
        .onNodeWithTag(ManagerOverviewTestTags.status("m2"))
        .assertTextEquals("Check-in not open yet")
    // Nothing to reveal, so the header does not offer to open on nothing.
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("m2")).assertHasNoClickAction()
    compose.onNodeWithTag(ManagerOverviewTestTags.missing("m2")).assertDoesNotExist()
  }

  @Test
  fun missionWithUnknownAttendance_countsNobodyPresentAndNamesNobodyMissing() {
    val waiting = setUp.copy(attendanceKnown = false)
    val failed = setUp.copy(id = "m3", attendanceKnown = false, attendanceFailed = true)
    show(loaded.copy(missions = listOf(waiting, failed, setUp.copy(id = "m4"))))

    // Not "0/3": nobody is known to be present, which is not the same as nobody being there.
    compose.onNodeWithTag(ManagerOverviewTestTags.counter("m1")).assertTextEquals("—/3")
    // A dash says nothing out loud, so a screen reader is given the words instead.
    compose
        .onNodeWithTag(ManagerOverviewTestTags.counter("m1"))
        .assertContentDescriptionEquals("Attendance unknown, 3 needed")
    compose
        .onNodeWithTag(ManagerOverviewTestTags.status("m1"))
        .assertTextEquals("Waiting for attendance data")
    // Nothing is known, so neither card offers to show who is there.
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("m1")).assertHasNoClickAction()
    scrollTo(ManagerOverviewTestTags.counter("m3")).assertTextEquals("—/3")
    compose
        .onNodeWithTag(ManagerOverviewTestTags.status("m3"))
        .assertTextEquals("Attendance unavailable")
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("m3")).assertHasNoClickAction()
    compose.onNodeWithTag(ManagerOverviewTestTags.missing("m3")).assertDoesNotExist()
    // The mission that failed leaves the one still followed with its attendance.
    scrollTo(ManagerOverviewTestTags.counter("m4")).assertTextEquals("2/3")
    compose.onNodeWithTag(ManagerOverviewTestTags.status("m4")).assertTextEquals("Understaffed")
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader("m4")).assertHasClickAction()
  }

  @Test
  fun organizer_readsTheScreenUnderTheirOwnTitle() {
    show(loaded.copy(isOrganizer = true))

    compose.onNodeWithTag(ManagerOverviewTestTags.SUBTITLE).assertTextEquals("Organizer Overview")
  }

  @Test
  fun firstLoad_showsTheSpinnerAndCountsNoMissionYet() {
    show(AttendanceUiState())

    compose.onNodeWithTag(ManagerOverviewTestTags.LOADING).assertIsDisplayed()
    // "0 missions" before there is an answer would read as having none.
    compose.onNodeWithTag(ManagerOverviewTestTags.MISSION_COUNT).assertDoesNotExist()
  }

  @Test
  fun reload_withMissionsAlreadyShown_keepsThemAndTheirCount() {
    show(loaded.copy(isLoading = true))

    compose.onNodeWithTag(ManagerOverviewTestTags.LOADING).assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.mission("m1")).assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.MISSION_COUNT).assertTextEquals("2 missions")
  }

  @Test
  fun withoutAnyMission_showsTheEmptyState() {
    show(AttendanceUiState(isLoading = false))

    compose.onNodeWithTag(ManagerOverviewTestTags.EMPTY).assertTextEquals("No missions available")
    compose.onNodeWithTag(ManagerOverviewTestTags.MISSION_COUNT).assertTextEquals("0 missions")
  }

  @Test
  fun failedLoad_showsTheErrorAndRetries() {
    var retries = 0
    show(AttendanceUiState(isLoading = false, loadFailed = true), onRetry = { retries++ })

    compose.onNodeWithTag(ManagerOverviewTestTags.LOAD_ERROR).assertIsDisplayed()
    // The failure says why there is nothing, so it is neither called empty nor counted on top.
    compose.onNodeWithTag(ManagerOverviewTestTags.EMPTY).assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.MISSION_COUNT).assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.RETRY).performClick()

    assertEquals(1, retries)
  }
}
