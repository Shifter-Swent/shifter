// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.scan

import com.swent.shifter.model.event.Event
import com.swent.shifter.ui.event.JoinEventError

/** What became of the join code the scan read, shown under the frame until the volunteer acts. */
sealed interface ScanResult {
  /** The event that uses [code] is being looked up. */
  data class Looking(val code: String) : ScanResult

  /** [event] is the one that uses [code]. */
  data class Found(val code: String, val event: Event) : ScanResult

  /** No event was found, for the reason [error] gives, e.g. no event uses the code. */
  data class Failed(val error: JoinEventError) : ScanResult
}
