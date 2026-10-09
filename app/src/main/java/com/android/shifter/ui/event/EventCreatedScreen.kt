// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.shifter_mutedText

object EventCreatedScreenTestTags {
  const val BACK_BUTTON = "EventCreatedBackButton"
  const val LOADING_INDICATOR = "EventCreatedLoadingIndicator"
  const val LOAD_ERROR = "EventCreatedLoadError"
  const val RETRY_BUTTON = "EventCreatedRetryButton"
  const val EVENT_TITLE = "EventCreatedTitle"
  const val JOIN_CODE = "EventCreatedJoinCode"
  const val DONE_BUTTON = "EventCreatedDoneButton"
}

/**
 * Shown once an organizer has created an event: displays its unique join code in large type, so the
 * organizer can share it with volunteers. Styled like [CreateEventScreen], which leads here.
 *
 * @param onDone called when the organizer leaves this screen, e.g. to open their events.
 */
@Composable
fun EventCreatedScreen(viewModel: EventCreatedViewModel, onDone: () -> Unit) {
  val uiState by viewModel.uiState.collectAsState()
  EventCreatedContent(state = uiState, onRetry = viewModel::retry, onDone = onDone)
}

/** Stateless content of [EventCreatedScreen]: renders [state] and reports every interaction. */
@Composable
fun EventCreatedContent(state: EventCreatedUiState, onRetry: () -> Unit, onDone: () -> Unit) {
  Scaffold(topBar = { EventCreatedHeader(onDone) }) { padding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      when {
        state.isLoading ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(
                  Modifier.testTag(EventCreatedScreenTestTags.LOADING_INDICATOR)
              )
            }
        state.error != null -> {
          Text(
              text = stringResource(state.error.message),
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.testTag(EventCreatedScreenTestTags.LOAD_ERROR),
          )
          // Only a connection problem can be fixed by trying again.
          if (state.error == EventCreatedError.OFFLINE) {
            OutlinedButton(
                onClick = onRetry,
                shape = MaterialTheme.shapes.medium,
                modifier =
                    Modifier.fillMaxWidth()
                        .height(48.dp)
                        .testTag(EventCreatedScreenTestTags.RETRY_BUTTON),
            ) {
              Text(
                  text = stringResource(R.string.event_created_retry),
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }
        else -> {
          Text(
              text = state.eventTitle,
              style = MaterialTheme.typography.bodySmall,
              color = shifter_mutedText,
              modifier = Modifier.testTag(EventCreatedScreenTestTags.EVENT_TITLE),
          )
          JoinCodeCard(state.joinCode)
          Text(
              text = stringResource(R.string.event_created_code_hint),
              style = MaterialTheme.typography.labelSmall,
              color = shifter_mutedText,
          )
        }
      }
      Button(
          onClick = onDone,
          shape = MaterialTheme.shapes.medium,
          modifier =
              Modifier.fillMaxWidth().height(48.dp).testTag(EventCreatedScreenTestTags.DONE_BUTTON),
      ) {
        Text(
            text = stringResource(R.string.event_created_done),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}

/** Return link to the organizer's events, above the screen title, as on [CreateEventScreen]. */
@Composable
private fun EventCreatedHeader(onBack: () -> Unit) {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .background(MaterialTheme.colorScheme.background)
              .padding(start = 24.dp, top = 4.dp, end = 24.dp, bottom = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
        modifier =
            Modifier.clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onBack)
                .testTag(EventCreatedScreenTestTags.BACK_BUTTON),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          Icons.AutoMirrored.Filled.KeyboardArrowLeft,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(14.dp),
      )
      Text(
          text = stringResource(R.string.create_event_back),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
      )
    }
    Text(
        text = stringResource(R.string.event_created_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
  }
}

/** The join code in large type, in a highlighted card like the date cards of the form. */
@Composable
private fun JoinCodeCard(joinCode: String) {
  val colorScheme = MaterialTheme.colorScheme
  val shape = MaterialTheme.shapes.medium
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .clip(shape)
              .background(colorScheme.primaryContainer)
              .border(1.dp, colorScheme.primary, shape)
              .padding(horizontal = 12.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
        text = stringResource(R.string.event_created_code_label),
        style = MaterialTheme.typography.labelSmall,
        color = colorScheme.onSurfaceVariant,
    )
    Text(
        text = joinCode,
        style = MaterialTheme.typography.displayMedium,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        letterSpacing = 6.sp,
        color = colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(EventCreatedScreenTestTags.JOIN_CODE),
    )
  }
}

@get:StringRes
private val EventCreatedError.message: Int
  get() =
      when (this) {
        EventCreatedError.OFFLINE -> R.string.event_created_error_offline
        EventCreatedError.NOT_FOUND -> R.string.event_created_error_not_found
        EventCreatedError.UNEXPECTED -> R.string.event_created_error_unexpected
      }

@Preview(showBackground = true)
@Composable
private fun EventCreatedContentPreview() {
  ShifterTheme {
    EventCreatedContent(
        state =
            EventCreatedUiState(
                eventTitle = "Paléo Festival",
                joinCode = "ABC234",
                isLoading = false,
            ),
        onRetry = {},
        onDone = {},
    )
  }
}
