// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow

/**
 * Reads QR codes with the camera. Screens depend on this interface, never on an implementation, so
 * they are tested with a fake and no camera.
 *
 * Scanning runs only while [Preview] is in the composition. The permission to use the camera is not
 * checked here: the screen asks for it before showing [Preview].
 */
interface QrScanner {

  /**
   * The texts of the QR codes seen, each once: the same code seen again within 2 seconds is not
   * emitted twice. They are raw texts, which the screen validates (e.g. with `JoinCode.isValid`).
   */
  val codes: Flow<String>

  /** Emits an error when the camera cannot start, or stops while scanning. */
  val errors: Flow<ScanError>

  /** Turns the torch on or off; ignored when the camera has no flash. */
  fun torch(on: Boolean)

  /** The camera image, filling [modifier]. Codes are read while it is shown. */
  @Composable fun Preview(modifier: Modifier)
}
