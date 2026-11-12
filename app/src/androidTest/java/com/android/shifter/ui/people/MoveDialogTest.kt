// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.people

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.team.Team
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The dialog that picks the team a participant moves to, on its own. */
@RunWith(AndroidJUnit4::class)
class MoveDialogTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val bar = team("bar", "Bar")
  private val logistics = team("logistics", "Logistics")
  private val kitchen = team("kitchen", "Kitchen")
  private val bob = ParticipantItem("bob", "Bob Rossi", isManager = false)

  private var confirmed: String? = null
  private var dismissals = 0

  private val teams = mutableStateOf(listOf(bar, logistics, kitchen))

  private fun show(participant: ParticipantItem = bob, from: Team? = bar) {
    composeTestRule.setContent {
      ShifterTheme {
        MoveDialog(
            participant = participant,
            from = from,
            teams = teams.value,
            onConfirm = { confirmed = it },
            onDismiss = { dismissals++ },
        )
      }
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  @Test
  fun opening_offersTheOtherTeamsAndSaysWhatIsLeft() {
    show()

    composeTestRule.onNodeWithText("Move Bob Rossi").assertIsDisplayed()
    composeTestRule
        .onNodeWithText("Bob Rossi leaves Bar and its missions that are not over yet.")
        .assertIsDisplayed()
    node(MoveDialogTestTags.teamOption("logistics")).assertIsDisplayed()
    node(MoveDialogTestTags.teamOption("kitchen")).assertIsDisplayed()
    node(MoveDialogTestTags.teamOption("bar")).assertDoesNotExist()
  }

  @Test
  fun confirm_waitsForATeamThenReportsIt() {
    show()
    node(MoveDialogTestTags.CONFIRM_BUTTON).assertIsNotEnabled()

    node(MoveDialogTestTags.teamOption("kitchen")).performClick()
    node(MoveDialogTestTags.teamOption("kitchen")).assertIsSelected()
    node(MoveDialogTestTags.CONFIRM_BUTTON).assertIsEnabled().performClick()

    composeTestRule.runOnIdle { assertEquals("kitchen", confirmed) }
  }

  @Test
  fun cancel_reportsTheDismissalWithoutAMove() {
    show()

    node(MoveDialogTestTags.teamOption("logistics")).performClick()
    node(MoveDialogTestTags.CANCEL_BUTTON).performClick()

    composeTestRule.runOnIdle {
      assertEquals(1, dismissals)
      assertEquals(null, confirmed)
    }
  }

  /** With no team to leave, every team is offered and nothing is said about leaving. */
  @Test
  fun participantOfNoTeam_isOfferedEveryTeam() {
    show(participant = ParticipantItem("carol", null, isManager = false), from = null)

    composeTestRule.onNodeWithText("Add Unknown volunteer to a team").assertIsDisplayed()
    listOf("bar", "logistics", "kitchen").forEach {
      node(MoveDialogTestTags.teamOption(it)).assertIsDisplayed()
    }
    composeTestRule.onNodeWithText("leaves", substring = true).assertDoesNotExist()
  }

  /** A reload that takes the picked team away leaves nothing to confirm. */
  @Test
  fun pickedTeamGone_cannotBeConfirmed() {
    show()
    node(MoveDialogTestTags.teamOption("kitchen")).performClick()

    composeTestRule.runOnIdle { teams.value = listOf(bar, logistics) }

    node(MoveDialogTestTags.CONFIRM_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun noOtherTeam_saysSoAndCannotBeConfirmed() {
    teams.value = listOf(bar)
    show()

    node(MoveDialogTestTags.NO_OTHER_TEAM).assertIsDisplayed()
    node(MoveDialogTestTags.CONFIRM_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun longList_scrollsToTheLastTeamAndKeepsTheButtons() {
    teams.value = listOf(bar) + (1..40).map { team("team-$it", "Team $it") }
    show()

    node(MoveDialogTestTags.teamOption("team-40")).performScrollTo().performClick()
    node(MoveDialogTestTags.CONFIRM_BUTTON).assertIsDisplayed().performClick()

    composeTestRule.runOnIdle { assertEquals("team-40", confirmed) }
  }

  private fun team(id: String, name: String) =
      Team(
          id = id,
          eventId = "event-1",
          name = name,
          description = "",
          icon = "",
          createdAt = Instant.EPOCH,
      )
}
