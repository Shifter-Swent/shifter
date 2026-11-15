// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.permission

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Opens this app's settings, where a permission refused for good can still be granted, or does
 * nothing if the device has no such screen.
 */
internal fun Context.openAppSettings() {
  try {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
    )
  } catch (_: ActivityNotFoundException) {
    // Nothing else can grant the permission: the screen stays as it is.
  }
}
