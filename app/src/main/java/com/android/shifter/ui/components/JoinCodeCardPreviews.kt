// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.shifter.ui.theme.ShifterTheme

// Previews of JoinCodeCard, kept apart from the card itself: they are tooling the IDE renders and
// no test ever runs, so coverage is told to leave this file alone.

@Preview(showBackground = true)
@Composable
private fun JoinCodeCardPreview() {
  ShifterTheme {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      JoinCodeCard(joinCode = "ABC234", onCopy = {})
      JoinCodeCard(joinCode = "ABC234", onCopy = {}, size = JoinCodeCardSize.Compact)
      JoinCodeCard(
          joinCode = JOIN_CODE_PLACEHOLDER,
          onCopy = {},
          size = JoinCodeCardSize.Compact,
          copyEnabled = false,
      )
      JoinCodeUnavailableCard(onRetry = {})
    }
  }
}
