// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.SavedStateHandle
import com.swent.shifter.R
import kotlinx.coroutines.flow.filterNotNull

// How the join screen tells My Events, under it, that a request was sent: through the saved state
// of My Events' back stack entry, which survives the join screen leaving and the process dying.

private const val REQUEST_SENT_TO = "requestSentTo"

/** Tells the screen owning this handle that a request was just sent to [eventTitle]. */
fun SavedStateHandle.putRequestSent(eventTitle: String) {
  this[REQUEST_SENT_TO] = eventTitle
}

/**
 * Confirms in [snackbarHostState] each request [putRequestSent] reports in [handle]. The report is
 * cleared before it shows, so coming back to the screen or rotating it does not show it again.
 */
@Composable
fun RequestSentSnackbar(handle: SavedStateHandle, snackbarHostState: SnackbarHostState) {
  val resources = LocalResources.current
  LaunchedEffect(handle, snackbarHostState) {
    handle.getStateFlow<String?>(REQUEST_SENT_TO, null).filterNotNull().collect { eventTitle ->
      handle[REQUEST_SENT_TO] = null
      snackbarHostState.showSnackbar(
          resources.getString(R.string.join_event_request_sent, eventTitle)
      )
    }
  }
}
