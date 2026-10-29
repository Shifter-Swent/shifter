// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.location

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.swent.shifter.model.location.LocationStatus

/**
 * Asks for the precise location permission and shows a [LocationPermissionCard] until [status] is
 * [LocationStatus.GRANTED]. The screen's ViewModel gives the provider's [status] and forwards
 * [onRefreshStatus] to it, so this composable never touches the model.
 *
 * @param onRefreshStatus called after the permission dialog and each time the screen resumes, so a
 *   change made in the settings is seen when the user comes back.
 */
@Composable
fun LocationPermissionGate(
    status: LocationStatus,
    onRefreshStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val activity = LocalActivity.current
  val currentOnRefreshStatus by rememberUpdatedState(onRefreshStatus)
  var requested by rememberSaveable { mutableStateOf(false) }
  val launcher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        requested = true
        currentOnRefreshStatus()
      }
  LifecycleResumeEffect(Unit) {
    currentOnRefreshStatus()
    onPauseOrDispose {}
  }

  val access =
      locationAccess(
          status = status,
          requested = requested,
          shouldShowRationale =
              activity != null &&
                  ActivityCompat.shouldShowRequestPermissionRationale(
                      activity,
                      Manifest.permission.ACCESS_FINE_LOCATION,
                  ),
          approximateGranted =
              ContextCompat.checkSelfPermission(
                  context,
                  Manifest.permission.ACCESS_COARSE_LOCATION,
              ) == PackageManager.PERMISSION_GRANTED,
      )
  LocationPermissionCard(
      access = access,
      // Android 12+ ignores a request for the precise location without the approximate one.
      onRequest = {
        launcher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
      },
      onOpenAppSettings = { context.openAppSettings() },
      onOpenLocationSettings = {
        // Some custom ROMs have no location settings screen: the app settings still help.
        try {
          context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } catch (_: ActivityNotFoundException) {
          context.openAppSettings()
        }
      },
      modifier = modifier,
  )
}

/** Opens this app's settings, or does nothing if the device has no such screen. */
private fun Context.openAppSettings() {
  try {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
    )
  } catch (_: ActivityNotFoundException) {
    // Nothing else can grant the permission: the card stays as it is.
  }
}
