// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatChannelsContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val opened = mutableListOf<String>()
  private var retries = 0

  private fun setContent(state: ChatChannelsUiState) {
    composeTestRule.setContent {
      ShifterTheme {
        ChatChannelsContent(
            state = state,
            onOpenChannel = { opened += it.teamId },
            onRetry = { retries++ },
        )
      }
    }
  }

  private fun tag(tag: String) = composeTestRule.onNodeWithTag(tag)

  @Test
  fun loading_showsTheSpinner() {
    setContent(ChatChannelsUiState())

    tag(ChatChannelsTestTags.LOADING).assertIsDisplayed()
  }

  @Test
  fun channel_click_opensIt() {
    setContent(ChatChannelsUiState(channels = CHANNELS, isLoading = false))

    composeTestRule.onNodeWithText("Bar").assertIsDisplayed()
    tag(ChatChannelsTestTags.channel("kitchen")).assertIsDisplayed().performClick()

    assertEquals(listOf("kitchen"), opened)
  }

  @Test
  fun noChannel_asVolunteer_explainsWhy() {
    setContent(ChatChannelsUiState(isLoading = false))

    tag(ChatChannelsTestTags.EMPTY).assertIsDisplayed()
    composeTestRule
        .onNodeWithText("You will see your team's channel once you are in a team.")
        .assertIsDisplayed()
  }

  @Test
  fun noChannel_asOrganizer_saysTheEventHasNoTeam() {
    setContent(ChatChannelsUiState(asOrganizer = true, isLoading = false))

    composeTestRule
        .onNodeWithText("This event has no team yet. Each team you create gets its own channel.")
        .assertIsDisplayed()
  }

  @Test
  fun error_retry_callsOnRetry() {
    setContent(ChatChannelsUiState(isLoading = false, error = ChatError.OFFLINE))

    tag(ChatChannelsTestTags.ERROR).assertIsDisplayed()
    tag(ChatChannelsTestTags.RETRY_BUTTON).performClick()

    assertEquals(1, retries)
  }

  @Test
  fun error_unexpected_saysTheChannelsCouldNotBeLoaded() {
    setContent(ChatChannelsUiState(isLoading = false, error = ChatError.UNEXPECTED))

    composeTestRule.onNodeWithText("The channels could not be loaded.").assertIsDisplayed()
  }

  @Test
  fun singleChannel_opensItOnce() {
    val recompositions = mutableIntStateOf(0)
    showScreen(listOf(team("bar", "Bar")), recompositions)

    composeTestRule.waitForIdle()
    // Redrawing the list must not open the channel a second time.
    recompositions.intValue++
    composeTestRule.waitForIdle()

    assertEquals(listOf("bar"), opened)
  }

  @Test
  fun severalChannels_opensNone() {
    showScreen(listOf(team("bar", "Bar"), team("kitchen", "Kitchen")))

    tag(ChatChannelsTestTags.channel("kitchen")).assertIsDisplayed()

    assertEquals(emptyList<String>(), opened)
  }

  /**
   * The screen with its ViewModel, as a volunteer of [teams]. Reading [recompositions] redraws it
   * each time the value changes.
   */
  private fun showScreen(
      teams: List<Team>,
      recompositions: MutableIntState = mutableIntStateOf(0),
  ) {
    val viewModel =
        ChatChannelsViewModel(EVENT, asOrganizer = false, FakeTeamRepository(teams)) { ME }
    composeTestRule.setContent {
      ShifterTheme {
        Column {
          Text("Redrawn ${recompositions.intValue} times")
          ChatChannelsScreen(viewModel, onOpenChannel = { opened += it.teamId })
        }
      }
    }
  }

  private fun team(id: String, name: String) =
      Team(
          id = id,
          eventId = EVENT,
          name = name,
          description = "",
          icon = "wine",
          memberIds = listOf(ME),
          createdAt = Instant.EPOCH,
      )

  private companion object {
    const val EVENT = "event-1"
    const val ME = "me"
    val CHANNELS = listOf(ChatChannel("bar", "Bar", "wine"), ChatChannel("kitchen", "Kitchen", ""))
  }
}
