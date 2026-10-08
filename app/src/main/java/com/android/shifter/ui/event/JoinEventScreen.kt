// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.shifter_inputBackground
import com.swent.shifter.ui.theme.shifter_mutedText
import com.swent.shifter.ui.theme.shifter_success
import com.swent.shifter.ui.theme.shifter_successContainer
import com.swent.shifter.ui.theme.shifter_warning
import com.swent.shifter.ui.theme.shifter_warningContainer
import java.time.Instant

object JoinEventScreenTestTags {
  const val JOIN_CODE_FIELD = "JoinEventCodeField"
  const val FIND_BUTTON = "JoinEventFindButton"
  const val EVENT_TITLE = "JoinEventTitle"
  const val APPLY_BUTTON = "JoinEventApplyButton"
  const val APPLIED_MESSAGE = "JoinEventAppliedMessage"
  const val ERROR_MESSAGE = "JoinEventErrorMessage"
}

/** The join screen wired to [viewModel]. */
@Composable
fun JoinEventScreen(viewModel: JoinEventViewModel) {
  val uiState by viewModel.uiState.collectAsState()
  JoinEventContent(
      uiState = uiState,
      onJoinCodeChange = viewModel::onJoinCodeChange,
      onFindEvent = viewModel::findEvent,
      onBack = viewModel::changeCode,
      onApply = viewModel::applyToEvent,
  )
}

/**
 * Joining in two steps: the volunteer types the event's join code, then sees the details of the
 * event it found and applies to it. Back, on screen or on the device, returns to the code.
 */
@Composable
fun JoinEventContent(
    uiState: JoinEventUiState,
    onJoinCodeChange: (String) -> Unit,
    onFindEvent: () -> Unit,
    onBack: () -> Unit,
    onApply: () -> Unit,
) {
  val event = uiState.event
  Box(
      modifier =
          Modifier.fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .safeDrawingPadding()
  ) {
    if (event == null) {
      JoinCodeStep(uiState, onJoinCodeChange, onFindEvent)
    } else {
      BackHandler(onBack = onBack)
      EventDetailsContent(
          event = event,
          isLoading = uiState.isLoading,
          requestStatus = uiState.requestStatus,
          error = uiState.error,
          onBack = onBack,
          onApply = onApply,
      )
    }
  }
}

@Composable
private fun JoinCodeStep(
    uiState: JoinEventUiState,
    onJoinCodeChange: (String) -> Unit,
    onFindEvent: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
  ) {
    Text(
        text = stringResource(R.string.join_event_title),
        color = colors.onBackground,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.join_event_intro),
        color = shifter_mutedText,
        fontSize = 14.sp,
    )
    JoinCodeField(uiState.joinCode, onJoinCodeChange)
    if (uiState.isLoading) {
      CircularProgressIndicator(color = colors.primary)
    } else {
      PrimaryButton(
          stringResource(R.string.join_event_find),
          onFindEvent,
          JoinEventScreenTestTags.FIND_BUTTON,
          uiState.joinCode.isNotBlank(),
      )
    }
    uiState.error?.let { ErrorText(it) }
  }
}

@Composable
private fun JoinCodeField(joinCode: String, onJoinCodeChange: (String) -> Unit) {
  val colors = MaterialTheme.colorScheme
  OutlinedTextField(
      value = joinCode,
      onValueChange = onJoinCodeChange,
      label = { Text(stringResource(R.string.join_event_code_label)) },
      singleLine = true,
      shape = RoundedCornerShape(14.dp),
      colors =
          OutlinedTextFieldDefaults.colors(
              focusedContainerColor = shifter_inputBackground,
              unfocusedContainerColor = shifter_inputBackground,
              focusedBorderColor = colors.primary,
              unfocusedBorderColor = colors.outline,
          ),
      modifier = Modifier.fillMaxWidth().testTag(JoinEventScreenTestTags.JOIN_CODE_FIELD),
  )
}

@Composable
internal fun ErrorText(error: JoinEventError) {
  Text(
      text = stringResource(error.messageRes()),
      color = MaterialTheme.colorScheme.error,
      fontSize = 14.sp,
      modifier = Modifier.testTag(JoinEventScreenTestTags.ERROR_MESSAGE),
  )
}

/** Where the request stands, in the colors of the matching status of My Events. */
@Composable
internal fun StatusBadge(status: MembershipRequestStatus) {
  val (text, color, container) =
      when (status) {
        MembershipRequestStatus.PENDING ->
            Triple(R.string.join_event_status_pending, shifter_warning, shifter_warningContainer)
        MembershipRequestStatus.ACCEPTED ->
            Triple(R.string.join_event_status_accepted, shifter_success, shifter_successContainer)
        MembershipRequestStatus.REJECTED ->
            Triple(
                R.string.join_event_status_rejected,
                MaterialTheme.colorScheme.error,
                MaterialTheme.colorScheme.errorContainer,
            )
      }
  Text(
      text = stringResource(text),
      color = color,
      fontSize = 13.sp,
      fontWeight = FontWeight.SemiBold,
      modifier =
          Modifier.clip(RoundedCornerShape(20.dp))
              .background(container)
              .padding(horizontal = 12.dp, vertical = 6.dp)
              .testTag(JoinEventScreenTestTags.APPLIED_MESSAGE),
  )
}

@StringRes
private fun JoinEventError.messageRes(): Int =
    when (this) {
      JoinEventError.UNKNOWN_CODE -> R.string.join_event_error_unknown_code
      JoinEventError.NOT_SIGNED_IN -> R.string.join_event_error_not_signed_in
      JoinEventError.OFFLINE -> R.string.join_event_error_offline
      JoinEventError.PERMISSION_DENIED -> R.string.join_event_error_permission_denied
      JoinEventError.UNEXPECTED -> R.string.join_event_error_unexpected
    }

/** Full-width button styled like the sign-in button. */
@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    testTag: String,
    enabled: Boolean = true,
) {
  val colors = MaterialTheme.colorScheme
  Button(
      onClick = onClick,
      enabled = enabled,
      shape = RoundedCornerShape(18.dp),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = colors.primary,
              contentColor = colors.onPrimary,
          ),
      modifier = Modifier.fillMaxWidth().height(58.dp).testTag(testTag),
  ) {
    Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
  }
}

@Preview(name = "Step 1: join code")
@Composable
private fun JoinEventCodePreview() {
  ShifterTheme { JoinEventContent(JoinEventUiState(joinCode = "ABC123"), {}, {}, {}, {}) }
}

@Preview(name = "Step 2: event details and Apply")
@Composable
private fun JoinEventApplyPreview() {
  ShifterTheme { JoinEventContent(JoinEventUiState(event = PREVIEW_EVENT), {}, {}, {}, {}) }
}

@Preview(name = "Step 2: request sent")
@Composable
private fun JoinEventAppliedPreview() {
  ShifterTheme {
    JoinEventContent(
        JoinEventUiState(event = PREVIEW_EVENT, requestStatus = MembershipRequestStatus.PENDING),
        {},
        {},
        {},
        {},
    )
  }
}

private val PREVIEW_EVENT =
    Event(
        organizerId = "organizer",
        title = "Solidarity collection",
        description =
            "Support a community collection. Help welcome donors, receive donations and sort " +
                "collected items with the volunteer team.",
        type = EventType.OTHER,
        startAt = Instant.parse("2026-06-22T07:30:00Z"),
        endAt = Instant.parse("2026-06-22T15:00:00Z"),
        location = EventLocation("Associations House"),
        createdAt = Instant.EPOCH,
    )
