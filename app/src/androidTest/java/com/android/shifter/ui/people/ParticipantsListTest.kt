// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.people

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasStateDescription
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

/** The list on its own: what each state shows, how a card unfolds, and what its actions report. */
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
      onManagerAction: (ParticipantItem, Team) -> Unit = { _, _ -> },
      onMoveAction: ((ParticipantItem) -> Unit)? = null,
  ) {
    composeTestRule.setContent {
      ShifterTheme {
        ParticipantsList(state, onRetry, onManagerAction, onMoveAction = onMoveAction)
      }
    }
  }

  private fun card(userId: String) = node(ParticipantsListTestTags.participantCard(userId))

  private fun managerButton(userId: String) = node(ParticipantsListTestTags.managerButton(userId))

  private fun moveButton(userId: String) = node(ParticipantsListTestTags.moveButton(userId))

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
  fun updating_disablesTheActions() {
    setList(
        ParticipantsUiState(
            groups = listOf(ParticipantGroup(bar, listOf(alice, bob))),
            isUpdating = true,
        ),
        onMoveAction = {},
    )

    card("bob").performClick()

    managerButton("bob").assertIsNotEnabled()
    moveButton("bob").assertIsNotEnabled()
  }

  @Test
  fun updateFailed_keepsTheListAndShowsTheFailureWithoutRetry() {
    setList(
        ParticipantsUiState(
            groups = listOf(ParticipantGroup(bar, listOf(alice, bob))),
            error = ParticipantsError.UPDATE_FAILED,
        )
    )

    node(ParticipantsListTestTags.ERROR)
        .assertTextContains("The manager could not be changed, try again")
    node(ParticipantsListTestTags.RETRY_BUTTON).assertDoesNotExist()
    node(ParticipantsListTestTags.participantCard("bob")).assertHasClickAction()
  }

  @Test
  fun tappingACard_unfoldsItAndTappingAgainFoldsIt() {
    setList(ParticipantsUiState(groups = listOf(ParticipantGroup(bar, listOf(bob)))))
    managerButton("bob").assertDoesNotExist()

    card("bob").performClick()
    managerButton("bob").assertIsDisplayed().assertTextContains("Make manager")

    card("bob").performClick()
    managerButton("bob").assertDoesNotExist()
  }

  @Test
  fun card_saysWhetherItIsOpen() {
    setList(ParticipantsUiState(groups = listOf(ParticipantGroup(bar, listOf(bob)))))

    card("bob").assert(hasStateDescription("Collapsed"))
    card("bob").performClick()
    card("bob").assert(hasStateDescription("Expanded"))
  }

  @Test
  fun anAction_foldsTheCard() {
    setList(ParticipantsUiState(groups = listOf(ParticipantGroup(bar, listOf(bob)))))

    card("bob").performClick()
    managerButton("bob").performClick()

    managerButton("bob").assertDoesNotExist()
  }

  /** A participant gone from the list, then back, comes back folded. */
  @Test
  fun participantGoneFromTheList_comesBackFolded() {
    val state =
        mutableStateOf(ParticipantsUiState(groups = listOf(ParticipantGroup(bar, listOf(bob)))))
    composeTestRule.setContent { ShifterTheme { ParticipantsList(state.value, {}, { _, _ -> }) } }
    card("bob").performClick()

    composeTestRule.runOnIdle {
      state.value = ParticipantsUiState(groups = listOf(ParticipantGroup(bar, emptyList())))
    }
    composeTestRule.runOnIdle {
      state.value = ParticipantsUiState(groups = listOf(ParticipantGroup(bar, listOf(bob))))
    }

    managerButton("bob").assertDoesNotExist()
  }

  @Test
  fun openingAnotherCard_foldsTheFirst() {
    setList(ParticipantsUiState(groups = listOf(ParticipantGroup(bar, listOf(alice, bob)))))

    card("bob").performClick()
    card("alice").performClick()

    managerButton("bob").assertDoesNotExist()
    managerButton("alice").assertIsDisplayed()
  }

  @Test
  fun makeManager_reportsTheParticipantAndTheirTeam() {
    var tapped: Pair<ParticipantItem, Team>? = null
    setList(
        ParticipantsUiState(groups = listOf(ParticipantGroup(bar, listOf(bob)))),
        onManagerAction = { participant, team -> tapped = participant to team },
    )

    card("bob").performClick()
    managerButton("bob").performClick()

    composeTestRule.runOnIdle {
      assertEquals(bob, tapped?.first)
      assertSame(bar, tapped?.second)
    }
  }

  /** Moving a team's manager is out of scope, so their card only offers to stop managing. */
  @Test
  fun managersCard_onlyOffersToStopManaging() {
    setList(
        ParticipantsUiState(groups = listOf(ParticipantGroup(bar, listOf(alice)))),
        onMoveAction = {},
    )

    card("alice").performClick()

    managerButton("alice").assertTextContains("Stop managing")
    moveButton("alice").assertDoesNotExist()
  }

  /** Until #397 wires the move, a participant of no team has nothing to act on. */
  @Test
  fun withoutTheMove_cardsOfNoTeamDoNotUnfold() {
    setList(
        ParticipantsUiState(
            groups =
                listOf(
                    ParticipantGroup(bar, listOf(bob)),
                    ParticipantGroup(null, listOf(unnamed)),
                )
        )
    )

    card("bob").assertHasClickAction()
    card("carol").assertHasNoClickAction()
    card("bob").performClick()
    moveButton("bob").assertDoesNotExist()
  }

  @Test
  fun withTheMove_aMemberChangesTeam() {
    var moved: ParticipantItem? = null
    setList(
        ParticipantsUiState(groups = listOf(ParticipantGroup(bar, listOf(bob)))),
        onMoveAction = { moved = it },
    )

    card("bob").performClick()
    moveButton("bob").assertTextContains("Change team").performClick()

    composeTestRule.runOnIdle { assertEquals(bob, moved) }
  }

  @Test
  fun withTheMove_aParticipantOfNoTeamIsAddedToOne() {
    var moved: ParticipantItem? = null
    setList(
        ParticipantsUiState(groups = listOf(ParticipantGroup(null, listOf(unnamed)))),
        onMoveAction = { moved = it },
    )

    card("carol").performClick()
    moveButton("carol").assertTextContains("Add to a team").performClick()
    managerButton("carol").assertDoesNotExist()

    composeTestRule.runOnIdle { assertEquals(unnamed, moved) }
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
