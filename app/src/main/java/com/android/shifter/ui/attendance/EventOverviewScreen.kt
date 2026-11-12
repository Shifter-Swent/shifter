// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.attendance

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.swent.shifter.R
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay

/**
 * The Overview of an event as the signed-in user may see it: whoever manages one of its teams, or
 * organizes it, watches the live attendance of the missions they are responsible for; anyone else
 * gets [volunteerOverview].
 *
 * Whether they may watch it comes from [AttendanceViewModel], which reads it from the teams it
 * already loads, so opening this tab asks the backend for nothing more than it is allowed to show.
 * The role is decided here once: a caller filling in [volunteerOverview] never repeats it.
 *
 * @param volunteerOverview what someone who manages no team of the event sees instead. It is the
 *   stand-in until #204 puts the volunteer's planning there, and the only place to change for it.
 * @param onAddMission opens the mission creation form, offered to a manager only.
 */
@Composable
fun EventOverviewScreen(
    viewModel: AttendanceViewModel,
    volunteerOverview: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onAddMission: () -> Unit = {},
) {
  val state by viewModel.uiState.collectAsState()
  // A check-in still in its undo window is sent when the screen is left or the app goes to the
  // background, rather than lost with them. Not on a rotation: the ViewModel outlives it, and so
  // does the chance to undo.
  val activity = LocalActivity.current
  val sendUnlessRotating = {
    if (activity?.isChangingConfigurations != true) viewModel.sendUndoableCheckIn()
  }
  LifecycleEventEffect(Lifecycle.Event.ON_STOP) { sendUnlessRotating() }
  DisposableEffect(viewModel) { onDispose { sendUnlessRotating() } }
  when (state.canViewAttendance) {
    true ->
        ManagerOverviewContent(
            state = state,
            now = rememberNow(),
            onRetry = viewModel::refresh,
            modifier = modifier,
            onCheckIn = viewModel::checkIn,
            onUndoCheckIn = viewModel::undoCheckIn,
            onUndoWindowOver = viewModel::sendUndoableCheckIn,
            onCheckInErrorShown = viewModel::onCheckInErrorShown,
            onMarkCompleted = viewModel::markCompleted,
            onCompletionFailureShown = viewModel::onCompletionFailureShown,
            onAddMission = onAddMission,
        )
    false -> volunteerOverview()
    // Not known yet, which a first load that failed also leaves it at: neither screen is shown,
    // since the attendance is not offered to someone whose part in the event is unknown.
    null -> UnknownRole(state.loadFailed, viewModel::refresh, modifier)
  }
}

/** Either the read is still running, or it failed and is worth another try. */
@Composable
private fun UnknownRole(loadFailed: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
  Column(
      modifier = modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (loadFailed) {
      Text(
          text = stringResource(R.string.attendance_load_failed),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
      )
      TextButton(onClick = onRetry) { Text(stringResource(R.string.attendance_retry)) }
    } else {
      CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
  }
}

/**
 * Now, moved on while the screen is composed, so a check-in window opens on its own without an
 * arrival to announce it. The effect is cancelled with the screen, and a minute is fine for a
 * window measured in them.
 */
@Composable
private fun rememberNow(): Instant {
  var now by remember { mutableStateOf(Instant.now()) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(1.minutes)
      now = Instant.now()
    }
  }
  return now
}
