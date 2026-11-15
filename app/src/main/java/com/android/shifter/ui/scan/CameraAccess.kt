// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.scan

/**
 * What the scan screen can do with the camera permission, like `LocationAccess` for the location.
 */
enum class CameraAccess {
  /** The permission was never asked in this screen: it is being asked. */
  NOT_REQUESTED,

  /** The user refused, but the permission can be asked again. */
  DENIED,

  /** The user refused for good: the dialog no longer shows, only the app settings can grant it. */
  PERMANENTLY_DENIED,

  /** The camera can be used. */
  GRANTED,
}

/** Why the scan screen shows the typed code instead of the camera. */
enum class ScanFallbackReason {
  /** The camera permission is refused: it can still be allowed. */
  PERMISSION_REFUSED,

  /** The scanner reported a `ScanError`: nothing in the app can fix it. */
  CAMERA_ERROR,
}

/**
 * Tells which [CameraAccess] applies, the same way as `locationAccess`. Android only says whether
 * to show a rationale, which is false both before the first request and once the user refused for
 * good, so [requested] tells the two apart.
 *
 * @param granted whether the camera permission is granted.
 * @param requested whether the permission dialog was already shown in this screen.
 * @param shouldShowRationale `shouldShowRequestPermissionRationale` for the camera.
 */
fun cameraAccess(granted: Boolean, requested: Boolean, shouldShowRationale: Boolean): CameraAccess =
    when {
      granted -> CameraAccess.GRANTED
      !requested -> CameraAccess.NOT_REQUESTED
      shouldShowRationale -> CameraAccess.DENIED
      else -> CameraAccess.PERMANENTLY_DENIED
    }
