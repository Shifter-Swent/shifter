// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.permission

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** The preferences file remembering that the notification permission was asked, for the phone. */
internal const val NOTIFICATION_PERMISSION_PREFERENCES = "notification_permission"

/** Its key: true once the permission was asked. */
internal const val NOTIFICATION_PERMISSION_ASKED = "asked"

/**
 * Asks once for the permission to post notifications, the first time someone reaches the app past
 * sign-in and the choice of a mode, so the system dialog opens over My Events, as in the Figma.
 * Without it, Android 13 and later drop every notification without a word (#614); earlier versions
 * need no permission.
 *
 * It asks once per installation, whatever the answer, and never again: a refused permission can
 * still be granted in the system settings. It waits for the app to be in the foreground, where
 * Android shows the dialog, then records the question before opening it, so a rotation or a process
 * killed while it is open does not ask again at the next launch.
 *
 * @param inApp whether the screen shown is in the app past sign-in and the choice of a mode, such
 *   as `currentBackStackEntryFlow.map { it.isInEventsApp() }`.
 */
@Composable
fun NotificationPermissionRequest(inApp: Flow<Boolean>) {
  val context = LocalContext.current
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  // The answer needs no handling: the engine reads the permission each time it shows something.
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
  LaunchedEffect(inApp, lifecycle) {
    // Before Android 13 there is no such permission: notifications are allowed.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
    // Checked before waiting too, so a phone already asked does not follow the navigation for
    // nothing.
    if (!context.shouldAskNotificationPermission()) return@LaunchedEffect
    inApp.first { it }
    lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
    // Granted in the system settings while signing in: nothing to ask any more.
    if (!context.shouldAskNotificationPermission()) return@LaunchedEffect
    context.notificationPermissionPreferences().edit {
      putBoolean(NOTIFICATION_PERMISSION_ASKED, true)
    }
    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
  }
}

/** Whether the permission is still to be asked: not granted, and never asked on this phone. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Context.shouldAskNotificationPermission(): Boolean =
    !hasPermission(Manifest.permission.POST_NOTIFICATIONS) &&
        !notificationPermissionPreferences().getBoolean(NOTIFICATION_PERMISSION_ASKED, false)

private fun Context.notificationPermissionPreferences() =
    getSharedPreferences(NOTIFICATION_PERMISSION_PREFERENCES, Context.MODE_PRIVATE)
