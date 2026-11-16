// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.scan

import android.Manifest
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.swent.shifter.model.scan.ScanError
import com.swent.shifter.ui.permission.hasPermission
import com.swent.shifter.ui.permission.openAppSettings
import kotlinx.coroutines.flow.first

/**
 * Shows [camera] once the camera can be used, and a [ScanFallback] that leads to the typed code
 * otherwise. It works like `LocationPermissionGate`: it asks for the permission when it opens,
 * reads it again after the dialog and each time the screen resumes, so a permission granted in the
 * settings brings the camera back.
 *
 * The scanner outlives the screen, and its error only goes back to null once its preview is shown
 * again. So each time the gate opens it shows [camera] first, and an [error] only counts once it
 * has been null in this gate: an error left by a previous visit gives the camera another try.
 *
 * @param error the scanner's `QrScanner.error`, collected by the screen.
 * @param onEnterCode goes back to the typed code.
 * @param camera the camera preview, e.g. `QrScanner.Preview`.
 */
@Composable
fun CameraGate(
    error: ScanError?,
    onEnterCode: () -> Unit,
    modifier: Modifier = Modifier,
    camera: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val activity = LocalActivity.current
  var granted by remember { mutableStateOf(context.hasPermission(Manifest.permission.CAMERA)) }
  var requested by rememberSaveable { mutableStateOf(false) }
  val launcher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requested = true
        granted = context.hasPermission(Manifest.permission.CAMERA)
      }
  LifecycleResumeEffect(Unit) {
    granted = context.hasPermission(Manifest.permission.CAMERA)
    onPauseOrDispose {}
  }
  LaunchedEffect(Unit) { if (!granted && !requested) launcher.launch(Manifest.permission.CAMERA) }

  val currentError by rememberUpdatedState(error)
  var cameraStarted by remember { mutableStateOf(false) }
  val access =
      cameraAccess(
          granted = granted,
          requested = requested,
          shouldShowRationale =
              activity != null &&
                  ActivityCompat.shouldShowRequestPermissionRationale(
                      activity,
                      Manifest.permission.CAMERA,
                  ),
      )
  when {
    // The system dialog is open: nothing to show yet, and no fallback that flashes.
    access == CameraAccess.NOT_REQUESTED ->
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    access != CameraAccess.GRANTED ->
        ScanFallback(
            reason = ScanFallbackReason.PERMISSION_REFUSED,
            onEnterCode = onEnterCode,
            onAllowCamera = {
              if (access == CameraAccess.DENIED) launcher.launch(Manifest.permission.CAMERA)
              else context.openAppSettings()
            },
            modifier = modifier,
        )
    cameraStarted && error != null ->
        ScanFallback(
            reason = ScanFallbackReason.CAMERA_ERROR,
            onEnterCode = onEnterCode,
            onAllowCamera = {},
            modifier = modifier,
        )
    else -> {
      Box(modifier.fillMaxSize()) { camera() }
      LaunchedEffect(Unit) {
        snapshotFlow { currentError }.first { it == null }
        cameraStarted = true
      }
    }
  }
}
