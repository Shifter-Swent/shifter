// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.scan

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A [QrScanner] without a camera for the instrumented tests, where `FakeQrScanner` of the JVM tests
 * is not available: [read] hands it a code as if the camera saw it.
 */
class TestQrScanner : QrScanner {

  private val sightings = MutableSharedFlow<String>(extraBufferCapacity = 1)

  override val codes: Flow<String> = sightings

  /** Whether a screen collects [codes]: like the camera's, a code read before is lost. */
  val isListening: Boolean
    get() = sightings.subscriptionCount.value > 0

  override val error: StateFlow<ScanError?> = MutableStateFlow(null)

  override val hasTorch: StateFlow<Boolean> = MutableStateFlow(false)

  override val torchOn: StateFlow<Boolean> = MutableStateFlow(false)

  override fun torch(on: Boolean) = Unit

  /** Reads [text], as the camera does when a QR code holding it comes into view. */
  fun read(text: String) {
    check(sightings.tryEmit(text)) { "Nothing collects the codes yet" }
  }

  /** An empty box in place of the camera image. */
  @Composable
  override fun Preview(modifier: Modifier) {
    Box(modifier)
  }
}
