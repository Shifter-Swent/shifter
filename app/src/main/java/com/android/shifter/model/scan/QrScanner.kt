// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Reads QR codes with the camera. Screens depend on this interface, never on an implementation, so
 * they are tested with a fake and no camera.
 *
 * Scanning runs only while [Preview] is in the composition. So the scan screen owns the scanner and
 * collects it, never a ViewModel: it is injected like `locationProvider` in `ShifterNavHost`, and a
 * ViewModel holding it would keep the camera across a rotation. The permission to use the camera is
 * not checked here: the screen asks for it before showing [Preview].
 */
interface QrScanner {

  /**
   * The texts of the QR codes seen. A code is emitted again only after it has been out of view for
   * 2 seconds, so a code held in front of the camera is emitted once.
   *
   * Hot: codes seen while nobody collects are dropped, so the screen collects it before showing
   * [Preview]. Every collector receives each code; one collector, the scan screen, is expected.
   * They are raw texts, which the screen validates (e.g. with `JoinCode.isValid`).
   */
  val codes: Flow<String>

  /**
   * Why the camera cannot read codes, or null while it can. Readable at any time, so an error that
   * happens before the screen collects is not lost. Set when the camera cannot start or stops, and
   * back to null when [Preview] leaves the composition: an error always belongs to the preview
   * shown, so a screen that opens never sees the error of a previous visit. It may come as soon as
   * [Preview] enters, e.g. [ScanError.NO_CAMERA].
   */
  val error: StateFlow<ScanError?>

  /** Whether the camera has a torch. False until the camera has started, and on devices without. */
  val hasTorch: StateFlow<Boolean>

  /**
   * Whether the torch is on. It stays off when [torch] is called without a torch, and turns off
   * when the camera stops, e.g. when the app goes to the background.
   */
  val torchOn: StateFlow<Boolean>

  /** Turns the torch on or off; ignored when [hasTorch] is false. */
  fun torch(on: Boolean)

  /** The camera image, filling [modifier]. Codes are read while it is shown. */
  @Composable fun Preview(modifier: Modifier = Modifier)
}
