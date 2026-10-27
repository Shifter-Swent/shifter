// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.location

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
import com.swent.shifter.ui.theme.ShifterTheme

// Previews of LocationPermissionCard, kept apart from the card itself: they are tooling the IDE
// renders and no test ever runs, so coverage is told to leave this file alone.

/** Every access the card shows something for; it shows nothing once granted. */
private class LocationAccessProvider : PreviewParameterProvider<LocationAccess> {
  override val values = LocationAccess.entries.filter { it != LocationAccess.GRANTED }.asSequence()
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27)
@Composable
private fun LocationPermissionCardPreview(
    @PreviewParameter(LocationAccessProvider::class) access: LocationAccess
) {
  ShifterTheme {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
      LocationPermissionCard(
          access = access,
          onRequest = {},
          onOpenAppSettings = {},
          onOpenLocationSettings = {},
      )
    }
  }
}
