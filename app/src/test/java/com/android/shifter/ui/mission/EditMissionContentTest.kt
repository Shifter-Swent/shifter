// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.mission

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.navigation.NavigationTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The mission form of [AddMissionContent] when it edits an existing mission. */
@RunWith(AndroidJUnit4::class)
class EditMissionContentTest {

  @get:Rule val compose = createComposeRule()

  private val eventDay = LocalDate.of(2027, 6, 20)
  private val editing =
      AddMissionUiState(
          event =
              EventPeriod(
                  title = "Local Food Drive",
                  start = LocalDateTime.of(eventDay, LocalTime.of(10, 0)),
                  end = LocalDateTime.of(eventDay, LocalTime.of(18, 0)),
              ),
          isLoadingEvent = false,
          teams = listOf(TeamOption("logistics", "Logistics"), TeamOption("bar", "Bar")),
          title = "Sort the food donations",
          description = "Sort the products.",
          teamId = "logistics",
          volunteersNeeded = 3,
          day = eventDay,
          startTime = LocalTime.of(14, 0),
          endTime = LocalTime.of(15, 30),
          isEditing = true,
          missionLoaded = true,
          assigneeCount = 2,
      )

  private fun show(state: AddMissionUiState, actions: AddMissionActions = AddMissionActions()) {
    compose.setContent { ShifterTheme { AddMissionContent(state, actions) } }
  }

  @Test
  fun editing_showsTheEditTitleAndSubmit() {
    show(editing)

    compose.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertTextEquals("Edit the mission")
    compose
        .onNodeWithTag(AddMissionScreenTestTags.SUBMIT_BUTTON)
        .performScrollTo()
        .assertIsEnabled()
    compose.onNodeWithText("Save the changes").assertIsDisplayed()
  }

  @Test
  fun editing_disablesTheTeamChips() {
    show(editing)

    compose.onNodeWithTag(AddMissionScreenTestTags.teamChip("bar")).assertIsNotEnabled()
    compose.onNodeWithTag(AddMissionScreenTestTags.GENERAL_CHIP).assertIsNotEnabled()
  }

  @Test
  fun editing_showsTheAssigneeCountInsteadOfThePicker() {
    show(editing)

    compose
        .onNodeWithTag(AddMissionScreenTestTags.ASSIGNED_COUNT)
        .performScrollTo()
        .assertTextEquals("2 people are assigned. Change who from the Overview.")
    compose.onNodeWithTag(AssigneePickerTestTags.COUNT).assertDoesNotExist()
  }

  @Test
  fun belowAssignees_showsHowManyAreAssigned() {
    show(editing.copy(belowAssignees = 3))

    compose
        .onNodeWithTag(AddMissionScreenTestTags.BELOW_ASSIGNEES)
        .performScrollTo()
        .assertTextEquals(
            "3 people are already assigned: the mission needs at least as many volunteers."
        )
  }

  @Test
  fun completed_explainsWhyAndDisablesSubmit() {
    show(editing.copy(missionCompleted = true))

    compose.onNodeWithTag(AddMissionScreenTestTags.COMPLETED).assertIsDisplayed()
    compose
        .onNodeWithTag(AddMissionScreenTestTags.SUBMIT_BUTTON)
        .performScrollTo()
        .assertIsNotEnabled()
  }

  @Test
  fun notFound_replacesTheForm() {
    show(editing.copy(missionNotFound = true))

    compose.onNodeWithTag(AddMissionScreenTestTags.NOT_FOUND).assertIsDisplayed()
    compose.onNodeWithTag(AddMissionScreenTestTags.TITLE_FIELD).assertDoesNotExist()
  }

  @Test
  fun cancel_goesBackWithoutSaving() {
    var back = 0
    var submitted = 0
    show(editing, AddMissionActions(onBack = { back++ }, onSubmit = { submitted++ }))

    compose.onNodeWithTag(AddMissionScreenTestTags.CANCEL_BUTTON).performScrollTo().performClick()

    assertEquals(1, back)
    assertEquals(0, submitted)
  }

  @Test
  fun beforeTheMissionLoads_hidesTheForm() {
    show(editing.copy(missionLoaded = false, title = ""))

    compose.onNodeWithTag(AddMissionScreenTestTags.EVENT_TITLE).assertIsDisplayed()
    compose.onNodeWithTag(AddMissionScreenTestTags.TITLE_FIELD).assertDoesNotExist()
    compose.onNodeWithTag(AddMissionScreenTestTags.SUBMIT_BUTTON).assertDoesNotExist()
  }

  @Test
  fun slotNotEditable_explainsWhyAndDisablesTheForm() {
    show(editing.copy(slotNotEditable = true))

    compose.onNodeWithTag(AddMissionScreenTestTags.SLOT_NOT_EDITABLE).assertIsDisplayed()
    compose.onNodeWithTag(AddMissionScreenTestTags.TITLE_FIELD).assertIsNotEnabled()
    compose.onNodeWithTag(AddMissionScreenTestTags.INCREASE_VOLUNTEERS).assertIsNotEnabled()
    compose
        .onNodeWithTag(AddMissionScreenTestTags.SUBMIT_BUTTON)
        .performScrollTo()
        .assertIsNotEnabled()
  }

  @Test
  fun loadFailed_saysTheMissionCouldNotLoad() {
    show(editing.copy(event = null, missionLoaded = false, loadFailed = true))

    compose
        .onNodeWithTag(AddMissionScreenTestTags.LOAD_ERROR)
        .assertTextEquals("Could not load the mission.")
    compose.onNodeWithTag(AddMissionScreenTestTags.RETRY_BUTTON).assertIsDisplayed()
  }

  @Test
  fun notAllowed_saysWhoMayChangeTheMission() {
    show(editing.copy(notAllowed = true))

    compose
        .onNodeWithTag(AddMissionScreenTestTags.NOT_ALLOWED)
        .assertTextEquals("Only the organizer and the manager of its team can change this mission.")
    compose.onNodeWithTag(AddMissionScreenTestTags.TITLE_FIELD).assertDoesNotExist()
  }
}
