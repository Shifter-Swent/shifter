// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.scan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.model.event.JoinCode
import com.swent.shifter.model.scan.QrScanner
import com.swent.shifter.ui.components.BackButton
import com.swent.shifter.ui.event.JoinEventError
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

/**
 * Scans the QR code of an event with [scanner]'s camera, behind a [CameraGate] that leads to the
 * typed code when the camera cannot be used.
 *
 * Only a valid join code is reported: anything else says it is not a Shifter event, and the scan
 * goes on. The text is only trimmed and upper-cased, as a typed code is, and not passed through
 * `JoinCode.normalize`, which would turn a URL into a code made of its first letters.
 *
 * What became of a code reported is the [result], shown in a [ScanResultSheet] under the frame. The
 * scan pauses while it shows, until the volunteer continues or scans again.
 *
 * @param onCodeScanned called with the join code read, e.g. to look its event up.
 * @param onEnterCode goes back to the typed code.
 * @param result what became of the code reported, or null while there is nothing to show.
 * @param onContinue goes on with the event found.
 * @param onScanAgain drops the [result], so that the scan reads codes again.
 */
@Composable
fun ScanScreen(
    scanner: QrScanner,
    onCodeScanned: (String) -> Unit,
    onEnterCode: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    result: ScanResult? = null,
    onContinue: () -> Unit = {},
    onScanAgain: () -> Unit = {},
) {
  val currentOnCodeScanned by rememberUpdatedState(onCodeScanned)
  val currentResult by rememberUpdatedState(result)
  val haptics by rememberUpdatedState(LocalHapticFeedback.current)
  var viewfinder by remember { mutableStateOf(ViewfinderState.SEARCHING) }
  var refusals by remember { mutableIntStateOf(0) }
  LaunchedEffect(scanner) {
    // The scanner cannot tell when a code leaves the frame, so a refused one is only said for a
    // while. The next code cancels the wait: another refused one says it again, a valid one at
    // once. A code seen while a result shows is dropped before, so it cancels nothing.
    scanner.codes
        .filter { currentResult == null }
        .collectLatest { text ->
          val code = text.trim().uppercase()
          if (JoinCode.isValid(code)) {
            viewfinder = ViewfinderState.FOUND
            // Felt as well as seen: a light tick for a code read, a firmer buzz for a refused one.
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            currentOnCodeScanned(code)
          } else {
            viewfinder = ViewfinderState.UNKNOWN
            refusals++
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
            delay(INVALID_CODE_SHOWN_MILLIS)
            viewfinder = ViewfinderState.SEARCHING
          }
        }
  }
  // A lookup that fails is felt like a refused code, and a code no event uses looks like one too.
  val failed = result as? ScanResult.Failed
  val unknownCode = failed?.error == JoinEventError.UNKNOWN_CODE
  LaunchedEffect(failed) {
    if (failed != null) {
      if (unknownCode) refusals++
      haptics.performHapticFeedback(HapticFeedbackType.Reject)
    }
  }
  val shown =
      when {
        result == null -> viewfinder
        unknownCode -> ViewfinderState.UNKNOWN
        // Looked up, found or not for another reason, such as being offline: the code was read.
        else -> ViewfinderState.FOUND
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
      ScanCamera(
          viewfinder = shown,
          refusals = refusals,
          onEnterCode = onEnterCode,
          result = result,
          onContinue = onContinue,
          onScanAgain = {
            viewfinder = ViewfinderState.SEARCHING
            onScanAgain()
          },
      ) {
        scanner.Preview(Modifier.fillMaxSize())
      }
    }
  }
}

/**
 * The [camera] image, with the [ScanViewfinder] above the middle, a way out at the bottom, and the
 * [result] of the code read in a [ScanResultSheet] above it.
 *
 * @param refusals how many codes were refused so far, to shake the viewfinder for each.
 */
@Composable
internal fun ScanCamera(
    viewfinder: ViewfinderState,
    refusals: Int,
    onEnterCode: () -> Unit,
    result: ScanResult? = null,
    onContinue: () -> Unit = {},
    onScanAgain: () -> Unit = {},
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
      // Twice as much room under the frame as above it, as in the Figma. The button is measured
      // first, so in landscape the frame shrinks to the height left above it.
      Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        ScanViewfinder(state = viewfinder, refusals = refusals)
        Spacer(Modifier.weight(2f))
      }
      Button(
          onClick = onEnterCode,
          shape = RoundedCornerShape(18.dp),
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.secondaryContainer,
                  contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
              ),
          modifier =
              Modifier.fillMaxWidth()
                  .height(ENTER_CODE_HEIGHT)
                  .testTag(ScanScreenTestTags.ENTER_CODE),
      ) {
        Text(
            text = stringResource(R.string.scan_enter_code_instead),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
      }
    }
    // Laid over the column rather than in it, so the frame stays where it is when the sheet shows.
    Box(
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
                .padding(bottom = ENTER_CODE_HEIGHT + 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
      AnimatedContent(
          targetState = result,
          // Only showing and hiding the sheet slides it: a result that changes, e.g. once its event
          // is found, is drawn in place.
          contentKey = { it != null },
          contentAlignment = Alignment.BottomCenter,
          transitionSpec = {
            (slideInVertically { it } + fadeIn()) togetherWith
                (slideOutVertically { it } + fadeOut()) using
                SizeTransform(clip = false)
          },
          label = "result sheet",
      ) { shown ->
        if (shown != null) ScanResultSheet(shown, onContinue, onScanAgain)
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
private val ENTER_CODE_HEIGHT = 58.dp

/** How long a refused code is said under the frame. */
internal const val INVALID_CODE_SHOWN_MILLIS = 3_000L
