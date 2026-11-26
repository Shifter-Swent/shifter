// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.ui.theme.shifter_chatBackground
import com.swent.shifter.ui.theme.shifter_mutedText
import java.time.LocalDate
import java.time.ZoneId

object TeamChatTestTags {
  const val LOADING = "teamChatLoading"
  const val ERROR = "teamChatError"
  const val RETRY_BUTTON = "teamChatRetryButton"
  const val EMPTY = "teamChatEmpty"
  const val MESSAGES = "teamChatMessages"
  const val INPUT = "teamChatInput"
  const val SEND_BUTTON = "teamChatSendButton"
  const val SEND_FAILED = "teamChatSendFailed"
  const val ANNOUNCEMENT_TOGGLE = "teamChatAnnouncementToggle"
  const val ANNOUNCEMENT_REFUSED = "teamChatAnnouncementRefused"

  fun message(id: String) = "teamChatMessage_$id"

  fun pending(id: String) = "teamChatMessagePending_$id"

  fun announcementTag(id: String) = "teamChatAnnouncementTag_$id"

  fun day(day: LocalDate) = "teamChatDay_$day"
}

/** The channel of a team, loaded and sent to by [viewModel]. */
@Composable
fun TeamChatScreen(viewModel: TeamChatViewModel, modifier: Modifier = Modifier) {
  val uiState by viewModel.uiState.collectAsState()
  TeamChatContent(
      state = uiState,
      onDraftChange = viewModel::onDraftChange,
      onSend = viewModel::send,
      onRetry = viewModel::load,
      onAnnouncementChange = viewModel::onAnnouncementChange,
      modifier = modifier,
  )
}

/**
 * The messages of a channel, oldest at the top, above the field to write a new one. The field stays
 * above the keyboard, and the latest message stays in view when the keyboard opens or a message
 * arrives, unless the user has scrolled up to read older ones.
 *
 * @param onAnnouncementChange called when the user chooses whether to send the draft as an
 *   announcement, an option shown only to those who [may][TeamChatUiState.canAnnounce].
 * @param zone the time zone the times are shown in, replaceable in tests.
 * @param today the day whose chip reads "Today", replaceable in tests.
 */
@Composable
fun TeamChatContent(
    state: TeamChatUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onAnnouncementChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone),
) {
  // Messages the ViewModel still has stay shown through a reload or an error, so a message sent
  // meanwhile is seen as pending rather than typed, and posted, again. Lost access hides them.
  val keepsMessages = state.messages.isNotEmpty() && state.error != ChatError.NOT_ALLOWED
  Column(modifier = modifier.fillMaxSize().background(shifter_chatBackground).imePadding()) {
    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
      when {
        keepsMessages -> ChatMessageList(state.messages, zone, today)
        state.isLoading ->
            CircularProgressIndicator(modifier = Modifier.testTag(TeamChatTestTags.LOADING))
        state.error != null -> LoadError(state.error, onRetry)
        else ->
            Text(
                text = stringResource(R.string.chat_empty),
                color = shifter_mutedText,
                modifier = Modifier.testTag(TeamChatTestTags.EMPTY),
            )
      }
    }
    if (keepsMessages && state.error != null) ErrorBanner(state.error, onRetry)
    if (state.sendFailed) SendError(R.string.chat_send_failed, TeamChatTestTags.SEND_FAILED)
    if (state.announcementRefused) {
      SendError(R.string.chat_announcement_refused, TeamChatTestTags.ANNOUNCEMENT_REFUSED)
    }
    ChatComposer(state, onDraftChange, onSend, onAnnouncementChange)
  }
}

/** The error in place of the messages, when there are none to show. */
@Composable
private fun LoadError(error: ChatError, onRetry: () -> Unit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
        text = stringResource(error.messageRes),
        color = shifter_mutedText,
        modifier = Modifier.padding(horizontal = 24.dp).testTag(TeamChatTestTags.ERROR),
    )
    // Retrying does not give access back: only a change of team does.
    if (error != ChatError.NOT_ALLOWED) RetryButton(onRetry)
  }
}

/** The error above the field, when the messages already shown are kept. */
@Composable
private fun ErrorBanner(error: ChatError, onRetry: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        text = stringResource(error.messageRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.weight(1f).testTag(TeamChatTestTags.ERROR),
    )
    RetryButton(onRetry)
  }
}

@Composable
private fun RetryButton(onRetry: () -> Unit) {
  TextButton(onClick = onRetry, modifier = Modifier.testTag(TeamChatTestTags.RETRY_BUTTON)) {
    Text(stringResource(R.string.chat_retry), fontWeight = FontWeight.SemiBold)
  }
}

private val ChatError.messageRes: Int
  get() =
      when (this) {
        ChatError.OFFLINE -> R.string.chat_error_offline
        ChatError.NOT_ALLOWED -> R.string.chat_error_not_allowed
        ChatError.UNEXPECTED -> R.string.chat_error_unexpected
      }
