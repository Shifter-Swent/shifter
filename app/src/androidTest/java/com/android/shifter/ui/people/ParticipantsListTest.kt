// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.people

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.team.Team
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The list on its own: what each state shows, and what a tap on a card reports. */
@RunWith(AndroidJUnit4::class)
class ParticipantsListTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val bar = team("bar", "Bar")
  private val logistics = team("logistics", "Logistics")
  private val alice = ParticipantItem("alice", "Alice Martin", isManager = true)
  private val bob = ParticipantItem("bob", "Bob Rossi", isManager = false)
  private val unnamed = ParticipantItem("carol", null, isManager = false)

  private fun setList(
      state: ParticipantsUiState,
      onRetry: () -> Unit = {},
      onParticipantClick: (ParticipantItem, Team) -> Unit = { _, _ -> },
  ) {
    composeTestRule.setContent {
      ShifterTheme { ParticipantsList(state, onRetry, onParticipantClick) }
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  /** The badge, which the card merges into itself, so it is only its own node unmerged. */
  private fun badge(userId: String) =
      composeTestRule.onNodeWithTag(
          ParticipantsListTestTags.managerBadge(userId),
          useUnmergedTree = true,
      )

  @Test
  fun groups_showEachTeamBarThenItsParticipantsWithTheManagerBadge() {
    setList(
        ParticipantsUiState(
            groups =
                listOf(
                    ParticipantGroup(bar, listOf(alice, bob)),
                    ParticipantGroup(logistics, emptyList()),
                    ParticipantGroup(null, listOf(unnamed)),
                )
        )
    )

    node(ParticipantsListTestTags.teamBar("bar")).assertTextContains("Bar · 2")
    node(ParticipantsListTestTags.participantCard("alice")).assertTextContains("Alice Martin")
    badge("alice").assertIsDisplayed()
    node(ParticipantsListTestTags.participantCard("bob")).assertTextContains("Bob Rossi")
    badge("bob").assertDoesNotExist()
    node(ParticipantsListTestTags.teamBar("logistics")).assertTextContains("Logistics · 0")
    node(ParticipantsListTestTags.noParticipants("logistics")).assertIsDisplayed()
    node(ParticipantsListTestTags.teamBar(null)).assertTextContains("No team · 1")
    node(ParticipantsListTestTags.participantCard("carol")).assertTextContains("Unknown volunteer")
  }

  @Test
  fun tappingACard_reportsTheParticipantAndTheirTeam() {
    var tapped: Pair<ParticipantItem, Team>? = null
    setList(
        ParticipantsUiState(groups = listOf(ParticipantGroup(bar, listOf(bob)))),
        onParticipantClick = { participant, team -> tapped = participant to team },
    )

    node(ParticipantsListTestTags.participantCard("bob")).performClick()

    composeTestRule.runOnIdle {
      assertEquals(bob, tapped?.first)
      assertSame(bar, tapped?.second)
    }
  }

  /** There is no team to act on, so nothing is reported. */
  @Test
  fun cardsOfNoTeam_cannotBeTapped() {
    setList(
        ParticipantsUiState(
            groups =
                listOf(
                    ParticipantGroup(bar, listOf(bob)),
                    ParticipantGroup(null, listOf(unnamed)),
                )
        )
    )

    node(ParticipantsListTestTags.participantCard("bob")).assertHasClickAction()
    node(ParticipantsListTestTags.participantCard("carol")).assertHasNoClickAction()
  }

  @Test
  fun loading_showsTheIndicator() {
    setList(ParticipantsUiState(isLoading = true))

    node(ParticipantsListTestTags.LOADING).assertIsDisplayed()
    node(ParticipantsListTestTags.EMPTY).assertDoesNotExist()
  }

  @Test
  fun noTeamsOrParticipants_showsTheEmptyMessage() {
    setList(ParticipantsUiState())

    node(ParticipantsListTestTags.EMPTY).assertIsDisplayed()
  }

  @Test
  fun error_showsItsMessageAndRetries() {
    var retries = 0
    setList(ParticipantsUiState(error = ParticipantsError.OFFLINE), onRetry = { retries++ })

    node(ParticipantsListTestTags.ERROR).assertTextContains("You are offline, try again later")
    node(ParticipantsListTestTags.EMPTY).assertDoesNotExist()
    node(ParticipantsListTestTags.RETRY_BUTTON).performClick()

    composeTestRule.runOnIdle { assertEquals(1, retries) }
  }

  @Test
  fun everyError_hasItsOwnMessage() {
    val messages =
        mapOf(
            ParticipantsError.OFFLINE to "You are offline, try again later",
            ParticipantsError.PERMISSION_DENIED to "You cannot see the participants of this event",
            ParticipantsError.UNEXPECTED to "Something went wrong, try again",
        )
    val state = mutableStateOf(ParticipantsUiState())
    composeTestRule.setContent { ShifterTheme { ParticipantsList(state.value, {}, { _, _ -> }) } }

    messages.forEach { (each, message) ->
      composeTestRule.runOnIdle { state.value = ParticipantsUiState(error = each) }
      composeTestRule.onNodeWithText(message).assertIsDisplayed()
    }
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
