// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.permission

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Whether this app holds [permission], e.g. `Manifest.permission.CAMERA`. */
internal fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
