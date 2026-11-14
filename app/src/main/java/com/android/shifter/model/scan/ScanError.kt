// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.scan

/** Why a [QrScanner] cannot read codes. The screen shows the typed code instead. */
enum class ScanError {
  /** The device has no camera the scanner can use. */
  NO_CAMERA,

  /** The camera could not start, or stopped while scanning (in use, disabled or disconnected). */
  CAMERA_FAILED,
}
