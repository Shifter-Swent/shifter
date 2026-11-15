// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.nowcard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.ui.components.PrimaryButton
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.shifter_warning
import java.time.Duration

// Previews of ThenStrip, kept apart from it: they are tooling the IDE renders and no test ever
// runs, so coverage is told to leave this file alone.

/** The volunteer's strip with its action, and the manager's with its note. */
@Preview(showBackground = true, backgroundColor = 0xFF192337)
@Composable
private fun ThenStripPreview() {
  ShifterTheme {
    Column(
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer).width(358.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      ThenStrip(
          startsIn = Duration.ofMinutes(30),
          title = "Welcome desk",
          place = "Main gate",
          action = { PrimaryButton(text = "View on map", onClick = {}, testTag = "preview") },
      )
      ThenStrip(
          startsIn = Duration.ofMinutes(10),
          title = "Welcome desk · 1 of 2 arrived",
          place = null,
          note = { Text("Short of 1 volunteer", color = shifter_warning, fontSize = 13.sp) },
      )
    }
  }
}
