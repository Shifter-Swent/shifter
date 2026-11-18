// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.swent.shifter.ui.theme.ShifterTheme

// Previews of ScanScreen, kept apart from it: they are tooling the IDE renders and no test ever
// runs, so coverage is told to leave this file alone. They draw the screen without its CameraGate,
// which asks for the permission, and with a grey box in place of the camera image.

@Composable
private fun ScanPreviewHost(viewfinder: ViewfinderState) {
  ShifterTheme {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
      ScanCamera(viewfinder = viewfinder, refusals = 0, onEnterCode = {}) {
        Box(Modifier.fillMaxSize().background(Color(0xFF3A3F47)))
      }
      ScanTopBar(
          cameraShown = true,
          showTorch = true,
          torchOn = false,
          onTorchChange = {},
          onBack = {},
      )
    }
  }
}

@Preview(name = "Scanning", widthDp = 390, heightDp = 844)
@Composable
private fun ScanningPreview() = ScanPreviewHost(ViewfinderState.SEARCHING)

@Preview(name = "Code read", widthDp = 390, heightDp = 844)
@Composable
private fun CodeReadPreview() = ScanPreviewHost(ViewfinderState.FOUND)

@Preview(name = "Not a Shifter code", widthDp = 390, heightDp = 844)
@Composable
private fun InvalidCodePreview() = ScanPreviewHost(ViewfinderState.UNKNOWN)

@Preview(name = "Landscape", widthDp = 844, heightDp = 390)
@Composable
private fun LandscapePreview() = ScanPreviewHost(ViewfinderState.SEARCHING)
