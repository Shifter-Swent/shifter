// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.team

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.shifter.R
import com.swent.shifter.model.team.Team
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deleting a team from its card (#399), over the real [TeamSetupViewModel] and a fake repository:
 * the bin, the confirmation, the deletion running, and a deletion that fails.
 */
@RunWith(AndroidJUnit4::class)
class TeamSetupDeletionTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val eventId = "event-1"

  private val context
    get() = InstrumentationRegistry.getInstrumentation().targetContext

  private fun team(id: String, name: String) =
      Team(
          id = id,
          eventId = eventId,
          name = name,
          description = "What $name does",
          icon = TeamIcon.TRUCK.id,
          createdAt = Instant.parse("2027-06-20T08:00:00Z"),
      )

  private val teams = FakeTeams(listOf(team("team-1", "Logistics"), team("team-2", "Bar")))

  /** Opens the step over [teams], in Manage teams unless told otherwise, and waits for its load. */
  private fun open(mode: TeamSetupMode = TeamSetupMode.MANAGEMENT) {
    composeTestRule.setContent {
      ShifterTheme {
        TeamSetupScreen(
            viewModel = remember { TeamSetupViewModel(eventId, teams) },
            onBack = {},
            onProceed = {},
            mode = mode,
        )
      }
    }
    composeTestRule.waitForIdle()
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  private fun text(text: String) = composeTestRule.onNodeWithText(text)

  /** Taps the bin of the team [teamId], then confirms its deletion. */
  private fun deleteAndConfirm(teamId: String) {
    node(TeamSetupScreenTestTags.deleteButton(teamId)).performScrollTo().performClick()
    node(DeleteTeamDialogTestTags.CONFIRM_BUTTON).performClick()
  }

  private fun waitForText(text: String) =
      composeTestRule.waitUntil(TIMEOUT_MILLIS) {
        composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
      }

  @Test
  fun bin_saysWhichTeamItDeletes() {
    open()

    node(TeamSetupScreenTestTags.deleteButton("team-1"))
        .performScrollTo()
        .assertContentDescriptionEquals(context.getString(R.string.team_setup_delete, "Logistics"))
  }

  @Test
  fun bin_answersATapPastItsIcon() {
    open()

    // 18dp right of the bin's center and 14dp up: past its 24dp icon, within its 48 x 32dp target.
    node(TeamSetupScreenTestTags.deleteButton("team-1")).performScrollTo().performTouchInput {
      click(center + Offset(18.dp.toPx(), -14.dp.toPx()))
    }

    node(DeleteTeamDialogTestTags.DIALOG).assertIsDisplayed()
  }

  @Test
  fun nameField_tappedAtItsTopRightCorner_doesNotOpenTheDialog() {
    open()

    // The corner right under the bin, which a taller touch target would cover.
    composeTestRule
        .onAllNodesWithTag(CreateTeamFormTestTags.NAME_FIELD)
        .filterToOne(hasAnyAncestor(hasTestTag(TeamSetupScreenTestTags.savedTeam("team-1"))))
        .performScrollTo()
        .performTouchInput { click(Offset(width - 2.dp.toPx(), 2.dp.toPx())) }

    node(DeleteTeamDialogTestTags.DIALOG).assertDoesNotExist()
  }

  @Test
  fun bin_asksForConfirmationAndCancelKeepsTheTeam() {
    open()

    node(TeamSetupScreenTestTags.deleteButton("team-1")).performScrollTo().performClick()

    text(context.getString(R.string.team_setup_delete_title, "Logistics")).assertIsDisplayed()
    text(context.getString(R.string.team_setup_delete_message)).assertIsDisplayed()
    node(DeleteTeamDialogTestTags.CANCEL_BUTTON).performClick()

    node(DeleteTeamDialogTestTags.DIALOG).assertDoesNotExist()
    node(TeamSetupScreenTestTags.savedTeam("team-1")).assertExists()
    assertEquals(listOf("Logistics", "Bar"), teams.teams.map { it.name })
  }

  @Test
  fun dialogOfATeamGoneFromTheList_doesNotComeBackWithIt() {
    var state by mutableStateOf(TeamSetupUiState(isLoading = false, teams = teams.teams.toList()))
    composeTestRule.setContent {
      ShifterTheme {
        TeamSetupContent(
            state = state,
            onBack = {},
            onProceed = {},
            onRetry = {},
            onAddAnotherTeam = {},
            form = null,
        )
      }
    }
    node(TeamSetupScreenTestTags.deleteButton("team-1")).performScrollTo().performClick()
    node(DeleteTeamDialogTestTags.DIALOG).assertIsDisplayed()

    val listed = state.teams
    state = state.copy(teams = listed.filterNot { it.id == "team-1" })
    composeTestRule.waitForIdle()
    state = state.copy(teams = listed)
    composeTestRule.waitForIdle()

    node(DeleteTeamDialogTestTags.DIALOG).assertDoesNotExist()
  }

  @Test
  fun confirmingTheDeletion_removesOnlyThatTeam() {
    open()

    deleteAndConfirm("team-1")
    composeTestRule.waitForIdle()

    node(DeleteTeamDialogTestTags.DIALOG).assertDoesNotExist()
    node(TeamSetupScreenTestTags.savedTeam("team-1")).assertDoesNotExist()
    node(TeamSetupScreenTestTags.savedTeam("team-2")).assertExists()
    assertEquals(listOf("Bar"), teams.teams.map { it.name })
  }

  @Test
  fun deletionRunning_showsTheSpinnerAndLocksTheCard() {
    val answer = CompletableDeferred<Unit>()
    teams.deletionAnswer = answer
    open()

    deleteAndConfirm("team-1")

    node(TeamSetupScreenTestTags.deletingIndicator("team-1")).assertIsDisplayed()
    node(TeamSetupScreenTestTags.deleteButton("team-1")).assertDoesNotExist()
    composeTestRule
        .onAllNodesWithTag(CreateTeamFormTestTags.NAME_FIELD)
        .filterToOne(hasAnyAncestor(hasTestTag(TeamSetupScreenTestTags.savedTeam("team-1"))))
        .assertIsNotEnabled()

    answer.complete(Unit)
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule
          .onAllNodesWithTag(TeamSetupScreenTestTags.savedTeam("team-1"))
          .fetchSemanticsNodes()
          .isEmpty()
    }
  }

  @Test
  fun creation_whileItsOnlyTeamIsDeleted_cannotCreateTheEvent() {
    teams.teams.removeAll { it.id == "team-2" }
    teams.deletionAnswer = CompletableDeferred()
    open(TeamSetupMode.CREATION)
    node(TeamSetupScreenTestTags.PROCEED_BUTTON).assertIsEnabled()

    deleteAndConfirm("team-1")

    node(TeamSetupScreenTestTags.deletingIndicator("team-1")).assertIsDisplayed()
    node(TeamSetupScreenTestTags.PROCEED_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun failedDeletion_keepsTheTeamAndRetryDeletesIt() {
    teams.deletionFailure = IllegalStateException("denied")
    open()

    deleteAndConfirm("team-1")
    waitForText(context.getString(R.string.team_setup_delete_failed))

    node(TeamSetupScreenTestTags.deleteButton("team-1")).assertExists()
    assertEquals(listOf("Logistics", "Bar"), teams.teams.map { it.name })

    teams.deletionFailure = null
    text(context.getString(R.string.team_setup_retry)).performClick()
    composeTestRule.waitForIdle()

    node(TeamSetupScreenTestTags.savedTeam("team-1")).assertDoesNotExist()
    assertEquals(listOf("Bar"), teams.teams.map { it.name })
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
