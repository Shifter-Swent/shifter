// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.swent.shifter.ui.theme.ShifterTheme

// Previews of the channel list, kept apart from the screen itself: they are tooling the IDE renders
// and no test ever runs, so coverage is told to leave this file alone.

@Composable
private fun PreviewHost(state: ChatChannelsUiState) {
  ShifterTheme {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
      ChatChannelsContent(
          state = state,
          onOpenChannel = { _, _, _ -> },
          onRetry = {},
      )
    }
  }
}

private val CHANNELS =
    listOf(
        ChatChannel("bar", "Bar", "wine"),
        ChatChannel("kitchen", "Kitchen", "chef-hat"),
        ChatChannel("logistics", "Logistics", "truck"),
        ChatChannel("welcome", "Welcome desk and lost property at the north entrance", ""),
    )

@Preview(showBackground = true, backgroundColor = 0xFF151D27, heightDp = 640)
@Composable
private fun ChatChannelsPreview() {
  PreviewHost(ChatChannelsUiState(channels = CHANNELS, showsAdmin = true, isLoading = false))
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27, heightDp = 640)
@Composable
private fun ChatChannelsLoadingPreview() {
  PreviewHost(ChatChannelsUiState())
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27, heightDp = 640)
@Composable
private fun ChatChannelsEmptyVolunteerPreview() {
  PreviewHost(ChatChannelsUiState(isLoading = false))
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27, heightDp = 640)
@Composable
private fun ChatChannelsEmptyOrganizerPreview() {
  PreviewHost(ChatChannelsUiState(asOrganizer = true, showsAdmin = true, isLoading = false))
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27, heightDp = 640)
@Composable
private fun ChatChannelsOfflinePreview() {
  PreviewHost(ChatChannelsUiState(isLoading = false, error = ChatError.OFFLINE))
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27, heightDp = 640)
@Composable
private fun ChatChannelsUnexpectedErrorPreview() {
  PreviewHost(ChatChannelsUiState(isLoading = false, error = ChatError.UNEXPECTED))
}
