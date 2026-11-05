// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.ui.theme.shifter_mutedText
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TeamChatTestTags {
  const val LOADING = "teamChatLoading"
  const val ERROR = "teamChatError"
  const val RETRY_BUTTON = "teamChatRetryButton"
  const val EMPTY = "teamChatEmpty"
  const val MESSAGES = "teamChatMessages"
  const val INPUT = "teamChatInput"
  const val SEND_BUTTON = "teamChatSendButton"
  const val SEND_FAILED = "teamChatSendFailed"

  fun message(id: String) = "teamChatMessage_$id"

  fun pending(id: String) = "teamChatMessagePending_$id"
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
      modifier = modifier,
  )
}

/**
 * The messages of a channel, oldest at the top, above the field to write a new one. The field stays
 * above the keyboard, and the latest message stays in view when the keyboard opens or a message
 * arrives.
 *
 * @param zone the time zone the times are shown in, replaceable in tests.
 */
@Composable
fun TeamChatContent(
    state: TeamChatUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
  Column(modifier = modifier.fillMaxSize().imePadding()) {
    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
      when {
        state.isLoading ->
            CircularProgressIndicator(modifier = Modifier.testTag(TeamChatTestTags.LOADING))
        state.error != null -> LoadError(state.error, onRetry)
        state.messages.isEmpty() ->
            Text(
                text = stringResource(R.string.chat_empty),
                color = shifter_mutedText,
                modifier = Modifier.testTag(TeamChatTestTags.EMPTY),
            )
        else -> Messages(state.messages, zone)
      }
    }
    if (state.sendFailed) {
      Text(
          text = stringResource(R.string.chat_send_failed),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(horizontal = 16.dp).testTag(TeamChatTestTags.SEND_FAILED),
      )
    }
    Input(state, onDraftChange, onSend)
  }
}

@Composable
private fun Messages(messages: List<ChatMessageItem>, zone: ZoneId) {
  val listState = rememberLazyListState()
  val keyboard = WindowInsets.ime.getBottom(LocalDensity.current)
  LaunchedEffect(messages.size, keyboard) { listState.scrollToItem(messages.lastIndex) }
  LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize().testTag(TeamChatTestTags.MESSAGES),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items(messages, key = { it.id }) { Bubble(it, zone) }
  }
}

@Composable
private fun Bubble(message: ChatMessageItem, zone: ZoneId) {
  val name =
      if (message.isMine) stringResource(R.string.chat_you)
      else message.senderName ?: stringResource(R.string.chat_unknown_sender)
  Box(modifier = Modifier.fillMaxWidth()) {
    Column(
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
        modifier =
            Modifier.align(if (message.isMine) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = 280.dp)
                .background(
                    if (message.isMine) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
                // Read as one message by TalkBack: the sender, the text and the time together.
                .semantics(mergeDescendants = true) {}
                .testTag(TeamChatTestTags.message(message.id)),
    ) {
      Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
      Text(message.text, style = MaterialTheme.typography.bodyMedium)
      val status =
          when {
            message.isPending -> stringResource(R.string.chat_sending)
            message.sentAt != null -> TIME.format(message.sentAt.atZone(zone))
            else -> null
          }
      if (status != null) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            color = shifter_mutedText,
            modifier =
                if (message.isPending) Modifier.testTag(TeamChatTestTags.pending(message.id))
                else Modifier,
        )
      }
    }
  }
}

@Composable
private fun Input(state: TeamChatUiState, onDraftChange: (String) -> Unit, onSend: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    OutlinedTextField(
        value = state.draft,
        onValueChange = onDraftChange,
        placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
        maxLines = 4,
        modifier = Modifier.weight(1f).testTag(TeamChatTestTags.INPUT),
    )
    IconButton(
        onClick = onSend,
        enabled = state.canSend,
        modifier = Modifier.testTag(TeamChatTestTags.SEND_BUTTON),
    ) {
      Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send))
    }
  }
}

@Composable
private fun LoadError(error: ChatError, onRetry: () -> Unit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
        text =
            stringResource(
                when (error) {
                  ChatError.OFFLINE -> R.string.chat_error_offline
                  ChatError.NOT_ALLOWED -> R.string.chat_error_not_allowed
                  ChatError.UNEXPECTED -> R.string.chat_error_unexpected
                }
            ),
        color = shifter_mutedText,
        modifier = Modifier.padding(horizontal = 24.dp).testTag(TeamChatTestTags.ERROR),
    )
    // Retrying does not give access back: only a change of team does.
    if (error != ChatError.NOT_ALLOWED) {
      TextButton(onClick = onRetry, modifier = Modifier.testTag(TeamChatTestTags.RETRY_BUTTON)) {
        Text(stringResource(R.string.chat_retry), fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
