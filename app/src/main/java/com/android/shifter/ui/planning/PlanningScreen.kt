// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.ui.theme.shifter_mutedText

object PlanningScreenTestTags {
  const val LOADING = "PlanningLoading"
  const val ERROR = "PlanningError"
  const val RETRY_BUTTON = "PlanningRetryButton"
}

/** The volunteer's Planning in the event overview, loaded by [viewModel]. */
@Composable
fun PlanningScreen(viewModel: PlanningViewModel, modifier: Modifier = Modifier) {
  val uiState by viewModel.uiState.collectAsState()
  PlanningContent(state = uiState, onRetry = viewModel::load, modifier = modifier)
}

/** The Planning once loaded, or what replaces it while loading and after an error. */
@Composable
fun PlanningContent(state: PlanningUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
  Column(
      modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 16.dp)
  ) {
    when {
      state.isLoading ->
          CircularProgressIndicator(
              modifier =
                  Modifier.align(Alignment.CenterHorizontally)
                      .size(24.dp)
                      .testTag(PlanningScreenTestTags.LOADING),
              strokeWidth = 2.dp,
          )
      state.error != null -> LoadError(state.error, onRetry)
      else -> Planning(missions = state.missions)
    }
  }
}

@Composable
private fun LoadError(error: PlanningError, onRetry: () -> Unit) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
        text =
            stringResource(
                when (error) {
                  PlanningError.OFFLINE -> R.string.planning_error_offline
                  PlanningError.UNEXPECTED -> R.string.planning_error_unexpected
                }
            ),
        style = MaterialTheme.typography.bodySmall,
        color = shifter_mutedText,
        modifier = Modifier.weight(1f).testTag(PlanningScreenTestTags.ERROR),
    )
    TextButton(
        onClick = onRetry,
        modifier = Modifier.testTag(PlanningScreenTestTags.RETRY_BUTTON),
    ) {
      Text(stringResource(R.string.planning_retry), fontWeight = FontWeight.SemiBold)
    }
  }
}
