// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.overview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.components.TeamFilterMenuTestTags
import com.swent.shifter.ui.components.days.DayHeaderTestTags
import com.swent.shifter.ui.components.days.DayStripTestTags
import com.swent.shifter.ui.mission.AssigneeOption
import com.swent.shifter.ui.mission.AssigneePickerTestTags
import com.swent.shifter.ui.mission.AssigneeSelection
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrganizerOverviewScreenTest {

  @get:Rule val compose = createComposeRule()

  private val teams = listOf(OverviewTeam("logistics", "Logistics"), OverviewTeam("bar", "Bar"))
  // Sunday 20 June 2027 at noon, counted in UTC.
  private val now = Instant.parse("2027-06-20T12:00:00Z")
  private val missions =
      listOf(
          OverviewMission("m1", "Set up the collection", "logistics", true, now),
          OverviewMission("m2", "Serve drinks", "bar", false, now),
          OverviewMission("m3", "Welcome the volunteers", null, false, now),
          OverviewMission("m4", "Prepare the counter", "bar", true, now),
      )
  private val loaded =
      OrganizerOverviewUiState(isLoading = false, teams = teams, missions = missions)

  private fun show(
      state: OrganizerOverviewUiState,
      actions: OrganizerOverviewActions = OrganizerOverviewActions(),
  ) {
    compose.setContent {
      ShifterTheme { OrganizerOverviewContent(state, actions, now = now, zone = ZoneOffset.UTC) }
    }
  }

  @Test
  fun completedMission_isLabelled() {
    show(loaded)

    compose
        .onNodeWithTag(OrganizerOverviewScreenTestTags.completed("m1"), useUnmergedTree = true)
        .assertIsDisplayed()
    compose
        .onNodeWithTag(OrganizerOverviewScreenTestTags.completed("m2"), useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun loaded_showsTheProgressOfTheWholeEvent() {
    show(loaded.copy(selectedTeamId = "bar"))

    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.PROGRESS_PERCENT).assertTextEquals("50%")
    compose
        .onNodeWithTag(OrganizerOverviewScreenTestTags.PROGRESS_COUNT)
        .assertTextEquals("2 / 4 missions completed")
  }

  @Test
  fun selectingAll_listsEveryMissionWithItsTeam() {
    show(loaded)

    compose.onNodeWithTag(DayHeaderTestTags.TITLE).assertTextEquals("Missions (4)")
    missions.forEach {
      compose
          .onNodeWithTag(OrganizerOverviewScreenTestTags.mission(it.id))
          .performScrollTo()
          .assertIsDisplayed()
    }
    // A mission of no team is shown as "General".
    compose.onNodeWithText("General").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun missionOfAnUnknownTeam_isNotShownAsGeneral() {
    show(
        loaded.copy(
            missions = listOf(OverviewMission("m5", "Guard the entrance", "deleted", false, now))
        )
    )

    compose.onNodeWithText("Unknown team").assertIsDisplayed()
    compose.onNodeWithText("General").assertDoesNotExist()
  }

  @Test
  fun selectingATeam_listsOnlyItsMissions() {
    show(loaded.copy(selectedTeamId = "bar"))

    compose.onNodeWithTag(DayHeaderTestTags.TITLE).assertTextEquals("Missions (2)")
    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.mission("m2")).assertIsDisplayed()
    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.mission("m4")).assertIsDisplayed()
    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.mission("m1")).assertDoesNotExist()
    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.mission("m3")).assertDoesNotExist()
  }

  @Test
  fun teamMenu_marksTheTeamPicked() {
    show(loaded.copy(selectedTeamId = "bar"))

    compose.onNodeWithTag(TeamFilterMenuTestTags.MENU).assertTextEquals("Bar").performClick()

    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.teamChip("bar")).assertIsSelected()
    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.ALL_TEAMS_CHIP).assertIsNotSelected()
  }

  @Test
  fun teamMenu_reportsTheTeamPicked() {
    val selected = mutableListOf<String?>()
    show(loaded, OrganizerOverviewActions(onSelectTeam = { selected += it }))

    compose.onNodeWithTag(TeamFilterMenuTestTags.MENU).assertTextEquals("All teams").performClick()
    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.teamChip("logistics")).performClick()
    compose.onNodeWithTag(TeamFilterMenuTestTags.MENU).performClick()
    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.ALL_TEAMS_CHIP).performClick()

    compose.runOnIdle { assertEquals(listOf("logistics", null), selected) }
  }

  @Test
  fun addMissionAndNotification_reportTheirClick() {
    var addClicks = 0
    var notificationClicks = 0
    show(
        loaded,
        OrganizerOverviewActions(
            onAddMission = { addClicks++ },
            onSendNotification = { notificationClicks++ },
        ),
    )

    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.ADD_MISSION_BUTTON).performClick()
    compose
        .onNodeWithTag(OrganizerOverviewScreenTestTags.NOTIFICATION_BUTTON)
        .performScrollTo()
        .performClick()

    compose.runOnIdle {
      assertEquals(1, addClicks)
      assertEquals(1, notificationClicks)
    }
  }

  @Test
  fun manageTeams_reportsItsClickWithoutTouchingTheFilters() {
    var manageClicks = 0
    var selected: String? = "untouched"
    show(
        loaded,
        OrganizerOverviewActions(
            onManageTeams = { manageClicks++ },
            onSelectTeam = { selected = it },
        ),
    )

    compose
        .onNodeWithTag(OrganizerOverviewScreenTestTags.MANAGE_TEAMS_BUTTON)
        .performScrollTo()
        .performClick()

    compose.runOnIdle {
      assertEquals(1, manageClicks)
      // The teams are a filter here too, and opening their management selects none of them.
      assertEquals("untouched", selected)
    }
  }

  @Test
  fun eventWithoutMissions_invitesToAddOne() {
    show(OrganizerOverviewUiState(isLoading = false, teams = teams))

    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.PROGRESS_PERCENT).assertTextEquals("0%")
    compose
        .onNodeWithTag(OrganizerOverviewScreenTestTags.EMPTY_MISSIONS)
        .assertTextEquals("No missions yet. Add the first one above.")
  }

  @Test
  fun teamWithoutMissions_saysSo() {
    show(loaded.copy(missions = missions.filter { it.teamId != "bar" }, selectedTeamId = "bar"))

    compose
        .onNodeWithTag(OrganizerOverviewScreenTestTags.EMPTY_MISSIONS)
        .assertTextEquals("No missions in this team yet.")
  }

  @Test
  fun loading_showsALoadingIndicatorUntilTheFirstLoad() {
    show(OrganizerOverviewUiState())

    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.LOADING_INDICATOR).assertIsDisplayed()
    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.EMPTY_MISSIONS).assertDoesNotExist()
    // A mission can be added before the list arrives.
    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.ADD_MISSION_BUTTON).assertIsDisplayed()
  }

  @Test
  fun failedLoad_offersToRetry() {
    var retried = false
    show(
        OrganizerOverviewUiState(isLoading = false, loadFailed = true),
        OrganizerOverviewActions(onRetry = { retried = true }),
    )

    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.LOAD_ERROR).assertIsDisplayed()
    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.EMPTY_MISSIONS).assertDoesNotExist()
    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.RETRY_BUTTON).performClick()

    compose.runOnIdle { assertTrue(retried) }
  }

  @Test
  fun missionRow_showsTheAssignedCount() {
    show(
        loaded.copy(
            missions =
                listOf(OverviewMission("m1", "Set up", "logistics", false, now, 3, listOf("ana")))
        )
    )

    compose
        .onNodeWithTag(OrganizerOverviewScreenTestTags.assignedCount("m1"), useUnmergedTree = true)
        .assertTextEquals("Assigned: 1/3")
  }

  @Test
  fun tappingAssign_reportsTheMission() {
    val opened = mutableListOf<String>()
    show(loaded, OrganizerOverviewActions(onAssign = { opened += it }))

    compose
        .onNodeWithTag(OrganizerOverviewScreenTestTags.assignButton("m2"))
        .performScrollTo()
        .performClick()

    compose.runOnIdle { assertEquals(listOf("m2"), opened) }
  }

  @Test
  fun assignDialog_showsThePickerAndReportsItsActions() {
    val toggled = mutableListOf<String>()
    var saves = 0
    var dismissals = 0
    show(
        loaded.copy(assigning = assigning()),
        OrganizerOverviewActions(
            onToggleAssignee = { toggled += it },
            onSaveAssignees = { saves++ },
            onDismissAssign = { dismissals++ },
        ),
    )

    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.ASSIGN_DIALOG).assertIsDisplayed()
    compose.onNodeWithTag(AssigneePickerTestTags.option("ana")).assertIsOn()
    compose.onNodeWithTag(AssigneePickerTestTags.option("ben")).performClick()
    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.ASSIGN_SAVE).performClick()
    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.ASSIGN_CANCEL).performClick()

    compose.runOnIdle {
      assertEquals(listOf("ben"), toggled)
      assertEquals(1, saves)
      assertEquals(1, dismissals)
    }
  }

  @Test
  fun assignDialog_onSaveFailure_showsTheError() {
    show(loaded.copy(assigning = assigning().copy(saveFailed = true)))

    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.ASSIGN_ERROR).assertIsDisplayed()
  }

  @Test
  fun assignDialog_whileSaving_disablesSaveAndCancel() {
    show(loaded.copy(assigning = assigning().copy(isSaving = true)))

    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.ASSIGN_SAVE).assertIsNotEnabled()
    // A save cannot be taken back, so the dialog cannot be closed until it ends.
    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.ASSIGN_CANCEL).assertIsNotEnabled()
  }

  @Test
  fun assignDialog_onCandidatesFailure_disablesSave() {
    val failed =
        assigning().let {
          it.copy(selection = it.selection.copy(candidates = emptyList(), loadFailed = true))
        }
    show(loaded.copy(assigning = failed))

    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.ASSIGN_SAVE).assertIsNotEnabled()
  }

  /** Mission "m1" being assigned, its candidates loaded and Ana picked. */
  private fun assigning() =
      AssignState(
          missionId = "m1",
          title = "Set up the collection",
          limit = 2,
          selection =
              AssigneeSelection(
                  candidates = listOf(AssigneeOption("ana", "Ana"), AssigneeOption("ben", "Ben")),
                  selectedIds = listOf("ana"),
              ),
      )

  // An event from Saturday 19 to Monday 21 June, read on Sunday 20.
  private val today = LocalDate.of(2027, 6, 20)
  private val saturday = today.minusDays(1)
  private val monday = today.plusDays(1)
  private val threeDays =
      loaded.copy(
          missions =
              listOf(
                  OverviewMission("s1", "Unload the trucks", "logistics", false, now.minus(DAY)),
                  OverviewMission("m2", "Serve drinks", "bar", false, now),
                  OverviewMission("m3", "Welcome the volunteers", null, false, now),
                  OverviewMission("t1", "Close the bar", "bar", false, now.plus(DAY)),
              ),
          eventPeriod = now.minus(DAY)..now.plus(DAY),
      )

  private fun pick(day: LocalDate) =
      compose.onNodeWithTag(DayStripTestTags.day(day)).performScrollTo().performClick()

  @Test
  fun eventOfSeveralDays_opensOnTodayWithItsMissionsOnly() {
    show(threeDays)

    compose.onNodeWithTag(DayStripTestTags.day(today)).assertIsSelected()
    compose.onNodeWithText("Serve drinks").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("Welcome the volunteers").assertExists()
    compose.onNodeWithText("Unload the trucks").assertDoesNotExist()
    compose.onNodeWithText("Close the bar").assertDoesNotExist()
    // The missions of the day shown are counted, not those of the event.
    compose.onNodeWithTag(DayHeaderTestTags.TITLE).assertTextEquals("Missions (2)")
    compose.onNodeWithTag(DayHeaderTestTags.BACK_TO_TODAY).assertDoesNotExist()
  }

  @Test
  fun pickingADay_withATeamSelected_showsOnlyThatTeamsMissionsOfThatDay() {
    show(threeDays.copy(selectedTeamId = "bar"))

    pick(monday)

    compose.onNodeWithText("Close the bar").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("Serve drinks").assertDoesNotExist()
    compose.onNodeWithTag(DayHeaderTestTags.TITLE).assertTextEquals("Missions (1)")
  }

  @Test
  fun dayTheTeamHasNothingOn_saysSo_andKeepsEveryDay() {
    show(threeDays.copy(selectedTeamId = "bar"))

    // The strip is the event's, whatever the team: Saturday has only logistics.
    pick(saturday)

    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.EMPTY_DAY).performScrollTo()
    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.EMPTY_DAY).assertIsDisplayed()
    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.EMPTY_MISSIONS).assertDoesNotExist()
    compose.onNodeWithText("Unload the trucks").assertDoesNotExist()
  }

  @Test
  fun anotherDay_hasTheTeamMenuInPlaceOfBackToToday_andTodaysChipBringsItBack() {
    show(threeDays)
    pick(monday)

    compose.onNodeWithTag(DayHeaderTestTags.BACK_TO_TODAY).assertDoesNotExist()
    compose.onNodeWithTag(TeamFilterMenuTestTags.MENU).assertIsDisplayed()
    pick(today)

    compose.onNodeWithText("Serve drinks").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun eventOfOneDay_showsItsMissionsWithoutTheStrip() {
    show(loaded.copy(eventPeriod = now..now.plus(Duration.ofHours(6))))

    compose.onNodeWithTag(DayStripTestTags.STRIP).assertDoesNotExist()
    compose.onNodeWithText("Serve drinks").performScrollTo().assertIsDisplayed()
  }

  private companion object {
    val DAY: Duration = Duration.ofDays(1)
  }
}
