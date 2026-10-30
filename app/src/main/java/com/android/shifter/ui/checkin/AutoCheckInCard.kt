// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.shifter.R
import com.swent.shifter.model.location.LocationStatus
import com.swent.shifter.ui.location.LocationPermissionGate

object AutoCheckInTestTags {
  const val CARD = "autoCheckInCard"
  const val CONFIRMATION = "autoCheckInConfirmation"
  const val ERROR = "autoCheckInError"
}

/**
 * Runs the automatic check-in while shown: [viewModel] only reads positions while its state is
 * collected, and this collection stops when the app goes to the background.
 */
@Composable
fun AutoCheckInRoute(viewModel: AutoCheckInViewModel, modifier: Modifier = Modifier) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  AutoCheckInCard(
      state = state,
      onRefreshLocation = viewModel::refreshLocation,
      modifier = modifier,
  )
}

/**
 * Tells the volunteer they are checked in automatically when they enter the zone, then shows the
 * last check-in recorded or the last error. While the location cannot be read, it asks for it.
 *
 * @param onRefreshLocation reads the permission and the location services again.
 */
@Composable
fun AutoCheckInCard(
    state: AutoCheckInUiState,
    onRefreshLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier.fillMaxWidth().testTag(AutoCheckInTestTags.CARD),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    GpsHint()
    if (state.locationStatus != LocationStatus.GRANTED) {
      LocationPermissionGate(status = state.locationStatus, onRefreshStatus = onRefreshLocation)
    }
    state.checkedIn?.let {
      Text(
          text = stringResource(R.string.auto_check_in_confirmation, it.missionTitle, it.time),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.testTag(AutoCheckInTestTags.CONFIRMATION),
      )
    }
    state.error?.let {
      Text(
          text = stringResource(it.message),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag(AutoCheckInTestTags.ERROR),
      )
    }
  }
}

/** The pin and the explanation of the automatic check-in, as in the design's GPS hint. */
@Composable
private fun GpsHint() {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(32.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
    ) {
      Icon(
          imageVector = Icons.Filled.LocationOn,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(16.dp),
      )
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
          text = stringResource(R.string.auto_check_in_title),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onBackground,
      )
      Text(
          text = stringResource(R.string.auto_check_in_explanation),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private val AutoCheckInError.message
  get() =
      when (this) {
        AutoCheckInError.NOT_ALLOWED -> R.string.auto_check_in_not_allowed
        AutoCheckInError.OFFLINE -> R.string.auto_check_in_offline
        AutoCheckInError.UNEXPECTED -> R.string.auto_check_in_unexpected
      }
