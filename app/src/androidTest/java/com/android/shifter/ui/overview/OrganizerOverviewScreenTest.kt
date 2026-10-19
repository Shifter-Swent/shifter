// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.overview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.mission.AssigneeOption
import com.swent.shifter.ui.mission.AssigneePickerTestTags
import com.swent.shifter.ui.mission.AssigneeSelection
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrganizerOverviewScreenTest {

  @get:Rule val compose = createComposeRule()

  private val teams = listOf(OverviewTeam("logistics", "Logistics"), OverviewTeam("bar", "Bar"))
  private val missions =
      listOf(
          OverviewMission("m1", "Set up the collection", "logistics", true),
          OverviewMission("m2", "Serve drinks", "bar", false),
          OverviewMission("m3", "Welcome the volunteers", null, false),
          OverviewMission("m4", "Prepare the counter", "bar", true),
      )
  private val loaded =
      OrganizerOverviewUiState(isLoading = false, teams = teams, missions = missions)

  private fun show(
      state: OrganizerOverviewUiState,
      actions: OrganizerOverviewActions = OrganizerOverviewActions(),
  ) {
    compose.setContent { ShifterTheme { OrganizerOverviewContent(state, actions) } }
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

    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.ALL_TEAMS_CHIP).assertIsSelected()
    compose
        .onNodeWithTag(OrganizerOverviewScreenTestTags.MISSIONS_HEADER)
        .assertTextEquals("Missions · All teams (4)")
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
            missions = listOf(OverviewMission("m5", "Guard the entrance", "deleted", false))
        )
    )

    compose.onNodeWithText("Unknown team").assertIsDisplayed()
    compose.onNodeWithText("General").assertDoesNotExist()
  }

  @Test
  fun selectingATeam_listsOnlyItsMissions() {
    show(loaded.copy(selectedTeamId = "bar"))

    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.teamChip("bar")).assertIsSelected()
    compose
        .onNodeWithTag(OrganizerOverviewScreenTestTags.MISSIONS_HEADER)
        .assertTextEquals("Missions · Bar (2)")
    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.mission("m2")).assertIsDisplayed()
    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.mission("m4")).assertIsDisplayed()
    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.mission("m1")).assertDoesNotExist()
    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.mission("m3")).assertDoesNotExist()
  }

  @Test
  fun chips_reportTheSelectedTeam() {
    val selected = mutableListOf<String?>()
    show(loaded, OrganizerOverviewActions(onSelectTeam = { selected += it }))

    compose.onNodeWithTag(OrganizerOverviewScreenTestTags.teamChip("logistics")).performClick()
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
            missions = listOf(OverviewMission("m1", "Set up", "logistics", false, 3, listOf("ana")))
        )
    )

    compose
        .onNodeWithTag(OrganizerOverviewScreenTestTags.assignedCount("m1"))
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
  fun assignDialog_whileSaving_disablesSave() {
    show(loaded.copy(assigning = assigning().copy(isSaving = true)))

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
}
