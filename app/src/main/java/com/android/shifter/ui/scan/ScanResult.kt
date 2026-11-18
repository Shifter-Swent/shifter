// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.scan

import com.swent.shifter.model.event.Event
import com.swent.shifter.ui.event.JoinEventError
import com.swent.shifter.ui.event.JoinEventUiState

/** What became of the join code the scan read, shown under the frame until the volunteer acts. */
sealed interface ScanResult {
  /** The event that uses [code] is being looked up. */
  data class Looking(val code: String) : ScanResult

  /** [event] is the one that uses [code]. */
  data class Found(val code: String, val event: Event) : ScanResult

  /** No event was found, for the reason [error] gives, e.g. no event uses the code. */
  data class Failed(val error: JoinEventError) : ScanResult
}

/**
 * The result of the code the join screen looks up, or null while it looks nothing up: before a code
 * is read, or once the volunteer scans again.
 */
fun JoinEventUiState.toScanResult(): ScanResult? =
    when {
      isLoading -> ScanResult.Looking(joinCode)
      event != null -> ScanResult.Found(joinCode, event)
      error != null -> ScanResult.Failed(error)
      else -> null
    }
