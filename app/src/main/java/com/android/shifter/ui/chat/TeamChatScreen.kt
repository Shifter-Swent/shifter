// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.ui.theme.shifter_mutedText
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
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
 * arrives, unless the user has scrolled up to read older ones.
 *
 * @param zone the time zone the times are shown in, replaceable in tests.
 * @param today the day whose messages show their time only, replaceable in tests.
 */
@Composable
fun TeamChatContent(
    state: TeamChatUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone),
) {
  // Messages the ViewModel still has stay shown through a reload or an error, so a message sent
  // meanwhile is seen as pending rather than typed, and posted, again. Lost access hides them.
  val keepsMessages = state.messages.isNotEmpty() && state.error != ChatError.NOT_ALLOWED
  Column(modifier = modifier.fillMaxSize().imePadding()) {
    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
      when {
        keepsMessages -> Messages(state.messages, zone, today)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Messages(messages: List<ChatMessageItem>, zone: ZoneId, today: LocalDate) {
  val listState = rememberLazyListState()
  val latest = messages.last()
  // Keyed on the latest message rather than the count, which stays at the cap of the channel. It is
  // followed only from the bottom, or when it is the user's own, so reading older ones is not
  // interrupted; the latest one already followed tells whether the list was at the bottom.
  var followedId by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(latest.id) {
    val atBottom =
        followedId == null || listState.layoutInfo.visibleItemsInfo.any { it.key == followedId }
    if (atBottom || latest.isMine) listState.scrollToItem(messages.lastIndex)
    followedId = latest.id
  }
  // Whether it is open, not its height, so the animation of the keyboard does not restart this.
  val keyboardOpen = WindowInsets.isImeVisible
  LaunchedEffect(keyboardOpen) { if (keyboardOpen) listState.scrollToItem(messages.lastIndex) }
  LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize().testTag(TeamChatTestTags.MESSAGES),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items(messages, key = { it.id }) { Bubble(it, zone, today) }
  }
}

@Composable
private fun Bubble(message: ChatMessageItem, zone: ZoneId, today: LocalDate) {
  val name =
      if (message.isMine) stringResource(R.string.chat_you)
      else message.senderName ?: stringResource(R.string.chat_unknown_sender)
  val colors = MaterialTheme.colorScheme
  // Set here, as a background alone, unlike a Surface, gives the text no color on the bubble.
  val textColor = if (message.isMine) colors.onPrimaryContainer else colors.onSurface
  Box(modifier = Modifier.fillMaxWidth()) {
    Column(
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
        modifier =
            Modifier.align(if (message.isMine) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = 280.dp)
                .background(
                    if (message.isMine) colors.primaryContainer else colors.surfaceVariant,
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
                // Read as one message by TalkBack: the sender, the text and the time together.
                .semantics(mergeDescendants = true) {}
                .testTag(TeamChatTestTags.message(message.id)),
    ) {
      Text(
          name,
          color = textColor,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
      )
      Text(message.text, color = textColor, style = MaterialTheme.typography.bodyMedium)
      val status =
          when {
            message.isPending -> stringResource(R.string.chat_sending)
            message.sentAt != null -> sentAtLabel(message.sentAt.atZone(zone), today)
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

/**
 * The time of a message sent [today], or its day and time otherwise: the channel stays open after
 * the event, which can last several days, so an old instruction must not read as a current one.
 */
internal fun sentAtLabel(sentAt: ZonedDateTime, today: LocalDate): String =
    when {
      sentAt.toLocalDate() == today -> TIME
      sentAt.year == today.year -> DAY_AND_TIME
      else -> DATE_AND_TIME
    }.format(sentAt)

private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val DAY_AND_TIME = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH)
private val DATE_AND_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)
