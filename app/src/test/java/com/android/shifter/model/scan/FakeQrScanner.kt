// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.scan

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [QrScanner] for tests: a test reads a code with [emit] and breaks the camera with
 * [fail].
 *
 * Like the camera, [codes] is hot: a code emitted while nobody collects is lost, so a test waits
 * for [activeCollectors] before emitting. Unlike the camera, it does not filter repeated codes:
 * each [emit] is delivered.
 *
 * @param hasTorch whether the fake camera has a torch.
 */
class FakeQrScanner(hasTorch: Boolean = true) : QrScanner {

  private val seenCodes = MutableSharedFlow<String>(extraBufferCapacity = 64)
  override val codes: SharedFlow<String> = seenCodes.asSharedFlow()

  private val mutableError = MutableStateFlow<ScanError?>(null)
  override val error: StateFlow<ScanError?> = mutableError.asStateFlow()

  private val mutableHasTorch = MutableStateFlow(hasTorch)
  override val hasTorch: StateFlow<Boolean> = mutableHasTorch.asStateFlow()

  private val mutableTorchOn = MutableStateFlow(false)
  override val torchOn: StateFlow<Boolean> = mutableTorchOn.asStateFlow()

  /** How many collectors are listening for codes right now, to wait for the screen. */
  val activeCollectors: Int
    get() = seenCodes.subscriptionCount.value

  /** Reads [code], as if the camera saw it; lost if nobody is listening. */
  fun emit(code: String) {
    seenCodes.tryEmit(code)
  }

  /** Reports [error], as if the camera could not start or stopped; the torch turns off. */
  fun fail(error: ScanError) {
    mutableTorchOn.value = false
    mutableError.value = error
  }

  override fun torch(on: Boolean) {
    if (mutableHasTorch.value) mutableTorchOn.value = on
  }

  /**
   * The error [Preview] reports as soon as it enters, like the camera on a device without one; null
   * for a camera that starts.
   */
  var errorOnPreview: ScanError? = null

  /**
   * An empty box in place of the camera image. Like the camera, it reports [errorOnPreview] when it
   * enters, and clears [error] when it leaves.
   */
  @Composable
  override fun Preview(modifier: Modifier) {
    DisposableEffect(Unit) {
      errorOnPreview?.let(::fail)
      onDispose { mutableError.value = null }
    }
    Box(modifier)
  }
}
