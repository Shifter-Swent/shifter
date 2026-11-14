// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.scan

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * In-memory [QrScanner] for tests: a test reads a code with [emit] and breaks the camera with
 * [fail]. Both are kept until collected, so a code given before the screen listens is not lost.
 *
 * Unlike the camera, it does not filter repeated codes: each [emit] is delivered.
 */
class FakeQrScanner : QrScanner {

  private val pendingCodes = Channel<String>(Channel.UNLIMITED)
  private val pendingErrors = Channel<ScanError>(Channel.UNLIMITED)

  override val codes: Flow<String> = pendingCodes.receiveAsFlow()
  override val errors: Flow<ScanError> = pendingErrors.receiveAsFlow()

  /** The state the torch was last set to. */
  var torchOn = false
    private set

  /** Reads [code], as if the camera saw it. */
  fun emit(code: String) {
    pendingCodes.trySend(code)
  }

  /** Reports [error], as if the camera could not start or stopped. */
  fun fail(error: ScanError) {
    pendingErrors.trySend(error)
  }

  override fun torch(on: Boolean) {
    torchOn = on
  }

  /** Draws nothing, so the screen's layout renders without a camera. */
  @Composable override fun Preview(modifier: Modifier) = Box(modifier)
}
