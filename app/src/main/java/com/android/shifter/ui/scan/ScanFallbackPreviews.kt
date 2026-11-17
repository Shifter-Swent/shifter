// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.swent.shifter.ui.theme.ShifterTheme

// Previews of ScanFallback, kept apart from it: they are tooling the IDE renders and no test ever
// runs, so coverage is told to leave this file alone.

private class ScanFallbackReasonProvider : PreviewParameterProvider<ScanFallbackReason> {
  override val values = ScanFallbackReason.entries.asSequence()
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ScanFallbackPreview(
    @PreviewParameter(ScanFallbackReasonProvider::class) reason: ScanFallbackReason
) {
  ShifterTheme { ScanFallback(reason = reason, onEnterCode = {}, onAllowCamera = {}) }
}
