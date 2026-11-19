// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.sharing

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.swent.shifter.ui.event.EventCreatedUiState
import com.swent.shifter.ui.theme.ShifterTheme

// Previews of SharingContent, kept apart from the screen itself: they are tooling the IDE renders
// and no test ever runs, so coverage is told to leave this file alone.

@Preview(showBackground = true)
@Composable
private fun SharingContentPreview() {
  ShifterTheme {
    SharingContent(
        state =
            EventCreatedUiState(
                eventTitle = "City Marathon 2025",
                joinCode = "ABC234",
                isLoading = false,
            ),
        onShare = { _, _ -> },
        onExport = {},
        onRetry = {},
    )
  }
}
