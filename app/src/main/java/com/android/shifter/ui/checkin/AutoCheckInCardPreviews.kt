// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.swent.shifter.model.location.LocationStatus
import com.swent.shifter.ui.theme.ShifterTheme

// Previews of AutoCheckInCard, kept apart from the card itself: they are tooling the IDE renders
// and no test ever runs, so coverage is told to leave this file alone.

/** Waiting for the zone, checked in, offline, and the location denied. */
private class AutoCheckInStateProvider : PreviewParameterProvider<AutoCheckInUiState> {
  override val values =
      sequenceOf(
          AutoCheckInUiState(),
          AutoCheckInUiState(checkedIn = AutoCheckedIn("Bar", "18:02")),
          AutoCheckInUiState(error = AutoCheckInError.OFFLINE),
          AutoCheckInUiState(locationStatus = LocationStatus.DENIED),
      )
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27)
@Composable
private fun AutoCheckInCardPreview(
    @PreviewParameter(AutoCheckInStateProvider::class) state: AutoCheckInUiState
) {
  ShifterTheme {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
      AutoCheckInCard(state = state, onRefreshLocation = {})
    }
  }
}
