// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import java.time.ZoneOffset

// Previews of the team channel, kept apart from the screen itself: they are tooling the IDE renders
// and no test ever runs, so coverage is told to leave this file alone.

@Composable
private fun PreviewHost(state: TeamChatUiState) {
  ShifterTheme {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
      TeamChatContent(
          state = state,
          onDraftChange = {},
          onSend = {},
          onRetry = {},
          zone = ZoneOffset.UTC,
      )
    }
  }
}

private fun at(time: String): Instant = Instant.parse("2025-06-14T$time:00Z")

private val MESSAGES =
    listOf(
        ChatMessageItem(
            "1",
            "Morning all! Briefing at the volunteer tent.",
            "Alice",
            at("07:45"),
            false,
        ),
        ChatMessageItem("2", "On my way, 5 min.", "Liam", at("07:52"), isMine = true),
        ChatMessageItem(
            "5",
            "Gates open at 9:00. Everyone at their station by 8:45.",
            "Alice",
            at("08:00"),
            false,
            isAnnouncement = true,
        ),
        ChatMessageItem(
            "3",
            "Can someone bring extra cups to station A?",
            null,
            at("08:10"),
            false,
        ),
        ChatMessageItem(
            "4",
            "I'll take them.",
            "Liam",
            sentAt = null,
            isMine = true,
            isPending = true,
        ),
    )

@Preview(showBackground = true, backgroundColor = 0xFF151D27, heightDp = 640)
@Composable
private fun TeamChatPreview() {
  PreviewHost(TeamChatUiState(isLoading = false, messages = MESSAGES, draft = "See you there"))
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27, heightDp = 640)
@Composable
private fun TeamChatEmptyPreview() {
  PreviewHost(TeamChatUiState(isLoading = false))
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27, heightDp = 640)
@Composable
private fun TeamChatSendFailedPreview() {
  PreviewHost(
      TeamChatUiState(
          isLoading = false,
          messages = MESSAGES.take(3),
          draft = "I'll take them.",
          sendFailed = true,
      )
  )
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27, heightDp = 640)
@Composable
private fun TeamChatOfflinePreview() {
  PreviewHost(TeamChatUiState(isLoading = false, error = ChatError.OFFLINE))
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27, heightDp = 640)
@Composable
private fun TeamChatNotAllowedPreview() {
  PreviewHost(TeamChatUiState(isLoading = false, error = ChatError.NOT_ALLOWED))
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27, heightDp = 640)
@Composable
private fun TeamChatAnnouncingPreview() {
  PreviewHost(
      TeamChatUiState(
          isLoading = false,
          messages = MESSAGES,
          draft = "Lunch break at noon",
          canAnnounce = true,
          asAnnouncement = true,
      )
  )
}
