// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.model.event.JoinCode
import com.swent.shifter.model.scan.QrScanner
import com.swent.shifter.ui.components.BackButton
import com.swent.shifter.ui.theme.shifter_warning
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Scans the QR code of an event with [scanner]'s camera, behind a [CameraGate] that leads to the
 * typed code when the camera cannot be used.
 *
 * Only a valid join code is reported: anything else says it is not a Shifter event, and the scan
 * goes on. The text is only trimmed and upper-cased, as a typed code is, and not passed through
 * `JoinCode.normalize`, which would turn a URL into a code made of its first letters.
 *
 * @param onCodeScanned called with the join code read, e.g. to look its event up.
 * @param onEnterCode goes back to the typed code.
 */
@Composable
fun ScanScreen(
    scanner: QrScanner,
    onCodeScanned: (String) -> Unit,
    onEnterCode: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val currentOnCodeScanned by rememberUpdatedState(onCodeScanned)
  var invalidCode by remember { mutableStateOf(false) }
  LaunchedEffect(scanner) {
    // The scanner cannot tell when a code leaves the frame, so a refused one is only said for a
    // while. The next code cancels the wait: another refused one says it again, a valid one at
    // once.
    scanner.codes.collectLatest { text ->
      val code = text.trim().uppercase()
      invalidCode = !JoinCode.isValid(code)
      if (invalidCode) {
        delay(INVALID_CODE_SHOWN_MILLIS)
        invalidCode = false
      } else {
        currentOnCodeScanned(code)
      }
    }
  }
  val error by scanner.error.collectAsState()
  val hasTorch by scanner.hasTorch.collectAsState()
  val torchOn by scanner.torchOn.collectAsState()
  Box(
      modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .testTag(ScanScreenTestTags.SCREEN)
  ) {
    CameraGate(
        error = error,
        onEnterCode = onEnterCode,
        // The title and the torch only go with the camera: the fallback keeps nothing but Back.
        overlay = { cameraShown ->
          ScanTopBar(
              cameraShown = cameraShown,
              showTorch = cameraShown && hasTorch,
              torchOn = torchOn,
              onTorchChange = scanner::torch,
              onBack = onBack,
          )
        },
    ) {
      ScanCamera(invalidCode = invalidCode, onEnterCode = onEnterCode) {
        scanner.Preview(Modifier.fillMaxSize())
      }
    }
  }
}

/**
 * The [camera] image, with the frame to aim at above the middle, what to do or what was wrong with
 * the last code under it, and a way out at the bottom.
 */
@Composable
internal fun ScanCamera(
    invalidCode: Boolean,
    onEnterCode: () -> Unit,
    camera: @Composable () -> Unit,
) {
  Box(Modifier.fillMaxSize()) {
    camera()
    Column(
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = TOP_BAR_HEIGHT)
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Twice as much room under the frame as above it, as in the Figma.
      Spacer(Modifier.weight(1f))
      // A plain frame for now: the viewfinder of #509 replaces it.
      Box(Modifier.size(265.dp).border(3.dp, Color.White, RoundedCornerShape(24.dp)))
      Spacer(Modifier.height(24.dp))
      Text(
          text =
              stringResource(if (invalidCode) R.string.scan_invalid_code else R.string.scan_hint),
          color = if (invalidCode) shifter_warning else Color.White,
          fontSize = 15.sp,
          textAlign = TextAlign.Center,
          modifier =
              Modifier.fillMaxWidth()
                  // Read again when it changes: a refused code is said without anything touched.
                  .semantics { liveRegion = LiveRegionMode.Polite }
                  .testTag(
                      if (invalidCode) ScanScreenTestTags.INVALID_CODE else ScanScreenTestTags.HINT
                  ),
      )
      Spacer(Modifier.weight(2f))
      Button(
          onClick = onEnterCode,
          shape = RoundedCornerShape(18.dp),
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.secondaryContainer,
                  contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
              ),
          modifier = Modifier.fillMaxWidth().height(58.dp).testTag(ScanScreenTestTags.ENTER_CODE),
      ) {
        Text(
            text = stringResource(R.string.scan_enter_code_instead),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}

/** Back, then the title and the torch while the camera is shown. */
@Composable
internal fun ScanTopBar(
    cameraShown: Boolean,
    showTorch: Boolean,
    torchOn: Boolean,
    onTorchChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .windowInsetsPadding(WindowInsets.safeDrawing)
              .height(TOP_BAR_HEIGHT)
              .padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    BackButton(onClick = onBack)
    if (cameraShown) {
      Text(
          text = stringResource(R.string.scan_title),
          // Over the camera image, which does not follow the theme, as the other texts.
          color = Color.White,
          fontSize = 18.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
      )
    }
    if (showTorch) {
      // Dressed like the back button, across the bar from it.
      IconToggleButton(
          checked = torchOn,
          onCheckedChange = onTorchChange,
          colors =
              IconButtonDefaults.iconToggleButtonColors(
                  containerColor = colors.secondaryContainer,
                  contentColor = colors.onSecondaryContainer,
                  checkedContainerColor = colors.secondaryContainer,
                  checkedContentColor = colors.primary,
              ),
          modifier = Modifier.testTag(ScanScreenTestTags.TORCH),
      ) {
        Icon(
            imageVector = if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
            contentDescription = stringResource(R.string.scan_torch),
            modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

private val TOP_BAR_HEIGHT = 56.dp

/** How long a refused code is said under the frame. */
internal const val INVALID_CODE_SHOWN_MILLIS = 3_000L
