// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventType
import com.swent.shifter.ui.event.JoinEventError
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant

// Previews of ScanScreen, kept apart from it: they are tooling the IDE renders and no test ever
// runs, so coverage is told to leave this file alone. They draw the screen without its CameraGate,
// which asks for the permission, and with a grey box in place of the camera image.

@Composable
private fun ScanPreviewHost(viewfinder: ViewfinderState, result: ScanResult? = null) {
  ShifterTheme {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
      ScanCamera(viewfinder = viewfinder, refusals = 0, onEnterCode = {}, result = result) {
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

@Preview(name = "Event found", widthDp = 390, heightDp = 844)
@Composable
private fun EventFoundPreview() =
    ScanPreviewHost(ViewfinderState.FOUND, ScanResult.Found("ABC234", PREVIEW_EVENT))

@Preview(name = "No event uses the code", widthDp = 390, heightDp = 844)
@Composable
private fun UnknownCodePreview() =
    ScanPreviewHost(ViewfinderState.UNKNOWN, ScanResult.Failed(JoinEventError.UNKNOWN_CODE))

private val PREVIEW_EVENT =
    Event(
        organizerId = "organizer",
        title = "Lake Festival 2026",
        description = "",
        type = EventType.MUSIC,
        startAt = Instant.parse("2026-06-12T08:00:00Z"),
        endAt = Instant.parse("2026-06-14T20:00:00Z"),
        location = EventLocation("Lausanne"),
        createdAt = Instant.EPOCH,
    )
