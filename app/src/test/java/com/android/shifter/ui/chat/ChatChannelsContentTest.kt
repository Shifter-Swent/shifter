// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.chat.ChannelKind
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

  /** The channels opened, as their kind, team id (for a team's channel) and name. */
  private val opened = mutableListOf<Triple<ChannelKind, String?, String>>()
  private var retries = 0

  private val open: OpenChannel = { kind, teamId, name -> opened += Triple(kind, teamId, name) }

  private fun setContent(state: ChatChannelsUiState) {
    composeTestRule.setContent {
      ShifterTheme {
        ChatChannelsContent(
            state = state,
            onOpenChannel = open,
            onRetry = { retries++ },
        )
      }
    }
  }

  private fun tag(tag: String) = composeTestRule.onNodeWithTag(tag)

  /** The test tags of the rows of the list, in their order. */
  private fun rows(): List<String?> =
      tag(ChatChannelsTestTags.LIST).fetchSemanticsNode().children.map {
        it.config.getOrNull(SemanticsProperties.TestTag)
      }

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

    assertEquals(listOf(Triple(ChannelKind.TEAM, "kitchen", "Kitchen")), opened)
  }

  @Test
  fun list_asVolunteer_groupsTheEventChannelsAboveTheirTeam() {
    setContent(ChatChannelsUiState(channels = CHANNELS, isLoading = false))

    composeTestRule.onNodeWithText("Discussions").assertIsDisplayed()
    composeTestRule.onNodeWithText("Event").assertIsDisplayed()
    composeTestRule.onNodeWithText("Your team").assertIsDisplayed()
    assertEquals(
        listOf(
            ChatChannelsTestTags.TITLE,
            ChatChannelsTestTags.EVENT_HEADER,
            ChatChannelsTestTags.GENERAL,
            ChatChannelsTestTags.TEAMS_HEADER,
            ChatChannelsTestTags.channel("bar"),
            ChatChannelsTestTags.channel("kitchen"),
        ),
        rows(),
    )

    tag(ChatChannelsTestTags.GENERAL).assertIsDisplayed().performClick()

    assertEquals(listOf(Triple(ChannelKind.GENERAL, null, "General")), opened)
  }

  @Test
  fun list_asOrganizer_listsTheAdminChannelUnderEvent_andTheirTeams() {
    setContent(
        ChatChannelsUiState(
            asOrganizer = true,
            channels = CHANNELS,
            showsAdmin = true,
            isLoading = false,
        )
    )

    composeTestRule.onNodeWithText("Your teams").assertIsDisplayed()
    assertEquals(
        listOf(
            ChatChannelsTestTags.TITLE,
            ChatChannelsTestTags.EVENT_HEADER,
            ChatChannelsTestTags.GENERAL,
            ChatChannelsTestTags.ADMIN,
            ChatChannelsTestTags.TEAMS_HEADER,
            ChatChannelsTestTags.channel("bar"),
            ChatChannelsTestTags.channel("kitchen"),
        ),
        rows(),
    )

    tag(ChatChannelsTestTags.ADMIN).assertIsDisplayed().performClick()

    assertEquals(listOf(Triple(ChannelKind.ADMIN, null, "Admin")), opened)
  }

  @Test
  fun list_asManager_headsTheTeamsAsTheirs() {
    setContent(ChatChannelsUiState(channels = CHANNELS, showsAdmin = true, isLoading = false))

    tag(ChatChannelsTestTags.ADMIN).assertIsDisplayed()
    composeTestRule.onNodeWithText("Your teams").assertIsDisplayed()
    composeTestRule.onNodeWithText("Your team").assertDoesNotExist()
  }

  @Test
  fun admin_whenNotShown_isNotListed() {
    setContent(ChatChannelsUiState(channels = CHANNELS, isLoading = false))

    tag(ChatChannelsTestTags.GENERAL).assertIsDisplayed()
    tag(ChatChannelsTestTags.ADMIN).assertDoesNotExist()
  }

  @Test
  fun noTeamChannel_asVolunteer_showsACardSayingWhy() {
    setContent(ChatChannelsUiState(isLoading = false))

    tag(ChatChannelsTestTags.GENERAL).assertIsDisplayed()
    assertEquals(
        listOf(
            ChatChannelsTestTags.TITLE,
            ChatChannelsTestTags.EVENT_HEADER,
            ChatChannelsTestTags.GENERAL,
            ChatChannelsTestTags.TEAMS_HEADER,
            ChatChannelsTestTags.EMPTY,
        ),
        rows(),
    )
    tag(ChatChannelsTestTags.EMPTY)
        .assertIsDisplayed()
        .assertTextContains("No team yet")
        .assertTextContains("Your team's channel shows up here once a manager puts you in a team.")
  }

  @Test
  fun noTeamChannel_asOrganizer_showsACardSayingTheEventHasNoTeam() {
    setContent(ChatChannelsUiState(asOrganizer = true, showsAdmin = true, isLoading = false))

    tag(ChatChannelsTestTags.GENERAL).assertIsDisplayed()
    tag(ChatChannelsTestTags.EMPTY)
        .assertIsDisplayed()
        .assertTextContains("No teams yet")
        .assertTextContains(
            "This event has no team yet. Each team you create gets its own channel."
        )
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
  fun screen_withASingleTeamChannel_listsItWithoutOpeningIt() {
    val viewModel =
        ChatChannelsViewModel(EVENT, asOrganizer = false, FakeTeamRepository(listOf(BAR))) { ME }
    composeTestRule.setContent {
      ShifterTheme { ChatChannelsScreen(viewModel, onOpenChannel = open) }
    }

    tag(ChatChannelsTestTags.GENERAL).assertIsDisplayed()
    composeTestRule.waitForIdle()
    assertEquals(emptyList<Triple<ChannelKind, String?, String>>(), opened)

    tag(ChatChannelsTestTags.channel("bar")).performClick()

    assertEquals(listOf(Triple(ChannelKind.TEAM, "bar", "Bar")), opened)
  }

  private companion object {
    const val EVENT = "event-1"
    const val ME = "me"
    val CHANNELS = listOf(ChatChannel("bar", "Bar", "wine"), ChatChannel("kitchen", "Kitchen", ""))
    val BAR =
        Team(
            id = "bar",
            eventId = EVENT,
            name = "Bar",
            description = "",
            icon = "wine",
            memberIds = listOf(ME),
            createdAt = Instant.EPOCH,
        )
  }
}
