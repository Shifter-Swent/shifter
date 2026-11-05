// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TeamChatContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val drafts = mutableListOf<String>()
  private var sends = 0
  private var retries = 0

  private fun setContent(state: TeamChatUiState) {
    composeTestRule.setContent {
      ShifterTheme {
        TeamChatContent(
            state = state,
            onDraftChange = { drafts += it },
            onSend = { sends++ },
            onRetry = { retries++ },
            zone = ZoneOffset.UTC,
        )
      }
    }
  }

  private fun tag(tag: String) = composeTestRule.onNodeWithTag(tag)

  @Test
  fun loading_showsTheSpinner() {
    setContent(TeamChatUiState(isLoading = true))

    tag(TeamChatTestTags.LOADING).assertIsDisplayed()
  }

  @Test
  fun emptyChannel_showsNoMessagesYet() {
    setContent(TeamChatUiState(isLoading = false))

    tag(TeamChatTestTags.EMPTY).assertIsDisplayed()
  }

  @Test
  fun error_retry_callsOnRetry() {
    setContent(TeamChatUiState(isLoading = false, error = ChatError.OFFLINE))

    tag(TeamChatTestTags.ERROR).assertIsDisplayed()
    tag(TeamChatTestTags.RETRY_BUTTON).performClick()

    assertEquals(1, retries)
  }

  @Test
  fun notAllowed_showsTheErrorWithoutRetry() {
    setContent(TeamChatUiState(isLoading = false, error = ChatError.NOT_ALLOWED))

    tag(TeamChatTestTags.ERROR).assert(hasText("no longer have access", substring = true))
    tag(TeamChatTestTags.RETRY_BUTTON).assertDoesNotExist()
  }

  @Test
  fun messages_showTheSenderTheTextAndTheTime() {
    setContent(TeamChatUiState(isLoading = false, messages = listOf(FROM_ALICE, UNNAMED, MINE)))

    tag(TeamChatTestTags.message("1")).assert(hasText("Alice", substring = true))
    tag(TeamChatTestTags.message("1")).assert(hasText("Hello", substring = true))
    tag(TeamChatTestTags.message("1")).assert(hasText("09:30", substring = true))
    tag(TeamChatTestTags.message("2")).assert(hasText("Unnamed member", substring = true))
    tag(TeamChatTestTags.message("3")).assert(hasText("You", substring = true))
  }

  @Test
  fun pendingMessage_showsSending() {
    setContent(
        TeamChatUiState(
            isLoading = false,
            messages = listOf(MINE.copy(id = "4", sentAt = null, isPending = true)),
        )
    )

    // Inside the message, which is read as one node.
    composeTestRule
        .onNodeWithTag(TeamChatTestTags.pending("4"), useUnmergedTree = true)
        .assertIsDisplayed()
  }

  @Test
  fun send_withBlankDraft_isDisabled() {
    setContent(TeamChatUiState(isLoading = false, draft = "  "))

    tag(TeamChatTestTags.SEND_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun send_whenAccessIsLost_isDisabled() {
    setContent(
        TeamChatUiState(isLoading = false, error = ChatError.NOT_ALLOWED, draft = "On my way")
    )

    tag(TeamChatTestTags.SEND_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun send_withADraft_callsOnSend() {
    setContent(TeamChatUiState(isLoading = false, draft = "On my way"))

    tag(TeamChatTestTags.SEND_BUTTON).assertIsEnabled().performClick()

    assertEquals(1, sends)
  }

  @Test
  fun typing_callsOnDraftChange() {
    setContent(TeamChatUiState(isLoading = false))

    tag(TeamChatTestTags.INPUT).performTextInput("Hi")

    assertEquals(listOf("Hi"), drafts)
  }

  @Test
  fun sendFailed_showsTheError() {
    setContent(TeamChatUiState(isLoading = false, draft = "Hi", sendFailed = true))

    tag(TeamChatTestTags.SEND_FAILED).assertIsDisplayed()
  }

  private companion object {
    val AT: Instant = Instant.parse("2027-06-20T09:30:00Z")
    val FROM_ALICE = ChatMessageItem("1", "Hello", "Alice", AT, isMine = false)
    val UNNAMED = ChatMessageItem("2", "Hey", null, AT, isMine = false)
    val MINE = ChatMessageItem("3", "Hi", "Liam", AT, isMine = true)
  }
}
