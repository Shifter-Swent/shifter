// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.JoinCode
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.team.Team
import com.swent.shifter.ui.components.Pill
import com.swent.shifter.ui.components.PrimaryButton
import com.swent.shifter.ui.components.membershipStatusColors
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.shifter_inputBackground
import com.swent.shifter.ui.theme.shifter_mutedText
import java.time.Instant

object JoinEventScreenTestTags {
  const val JOIN_CODE_FIELD = "JoinEventCodeField"
  const val FIND_BUTTON = "JoinEventFindButton"
  const val EVENT_TITLE = "JoinEventTitle"
  const val APPLY_BUTTON = "JoinEventApplyButton"
  const val ENDED = "JoinEventEnded"
  const val APPLIED_MESSAGE = "JoinEventAppliedMessage"
  const val ERROR_MESSAGE = "JoinEventErrorMessage"
  const val CODE_HINT = "JoinEventCodeHint"
  const val NO_TEAMS = "JoinEventNoTeams"

  fun teamOption(teamId: String) = "JoinEventTeam_$teamId"

  fun codeBox(index: Int) = "JoinEventCodeBox_$index"
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
      onTeamSelected = viewModel::onTeamSelected,
      onApply = viewModel::applyToEvent,
  )
}

/**
 * Joining in two steps: the volunteer types the event's join code, then sees the details of the
 * event it found, picks one of its teams and applies to it. Back, on screen or on the device,
 * returns to the code.
 */
@Composable
fun JoinEventContent(
    uiState: JoinEventUiState,
    onJoinCodeChange: (String) -> Unit,
    onFindEvent: () -> Unit,
    onBack: () -> Unit,
    onTeamSelected: (String) -> Unit,
    onApply: () -> Unit,
) {
  val event = uiState.event
  Box(
      modifier =
          Modifier.fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              // The top is left to each step: the event's top bar runs behind the status bar.
              .windowInsetsPadding(
                  WindowInsets.safeDrawing.only(
                      WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                  )
              )
  ) {
    if (event == null) {
      Box(Modifier.statusBarsPadding()) { JoinCodeStep(uiState, onJoinCodeChange, onFindEvent) }
    } else {
      BackHandler(onBack = onBack)
      EventDetailsContent(
          event = event,
          teams = uiState.teams,
          selectedTeamId = uiState.selectedTeamId,
          isLoading = uiState.isLoading,
          requestStatus = uiState.requestStatus,
          error = uiState.error,
          hasEnded = uiState.hasEnded,
          onBack = onBack,
          onTeamSelected = onTeamSelected,
          onApply = onApply,
      )
    }
  }
}

/** The code step scrolls, so the keyboard cannot hide the Find button on a small screen. */
@Composable
private fun JoinCodeStep(
    uiState: JoinEventUiState,
    onJoinCodeChange: (String) -> Unit,
    onFindEvent: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  BoxWithConstraints(Modifier.fillMaxSize()) {
    Column(
        modifier =
            Modifier.verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
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
      JoinCodeField(
          uiState.joinCode,
          onJoinCodeChange,
          onDone = { if (uiState.canFind) onFindEvent() },
      )
      if (uiState.hasUnusedCharacter) {
        Text(
            text = stringResource(R.string.join_event_code_unused_characters),
            color = colors.error,
            fontSize = 14.sp,
            modifier = Modifier.testTag(JoinEventScreenTestTags.CODE_HINT),
        )
      }
      if (uiState.isLoading) {
        CircularProgressIndicator(color = colors.primary)
      } else {
        PrimaryButton(
            stringResource(R.string.join_event_find),
            onFindEvent,
            JoinEventScreenTestTags.FIND_BUTTON,
            uiState.canFind,
        )
      }
      uiState.error?.let { ErrorText(it) }
    }
  }
}

/**
 * One box per character of the code. A single hidden text field takes the input, so typing fills
 * the next box, deleting empties the last one and pasting a full code fills them all. The box the
 * next character goes into is outlined, and so is, in the error color, a character no code uses.
 * The boxes share the width available, up to [CODE_BOX_MAX_WIDTH] each, and their text is sized
 * from the box, so the row fits on a narrow screen and with a large font.
 */
@Composable
private fun JoinCodeField(
    joinCode: String,
    onJoinCodeChange: (String) -> Unit,
    onDone: () -> Unit,
) {
  val label = stringResource(R.string.join_event_code_label)
  BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    val gaps = CODE_BOX_SPACING * (JoinCode.LENGTH - 1)
    val boxWidth = ((maxWidth - gaps) / JoinCode.LENGTH).coerceAtMost(CODE_BOX_MAX_WIDTH)
    BasicTextField(
        value = joinCode,
        onValueChange = onJoinCodeChange,
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier =
            Modifier.testTag(JoinEventScreenTestTags.JOIN_CODE_FIELD).semantics {
              contentDescription = label
            },
        decorationBox = {
          Row(horizontalArrangement = Arrangement.spacedBy(CODE_BOX_SPACING)) {
            repeat(JoinCode.LENGTH) { index -> CodeBox(joinCode, index, boxWidth) }
          }
        },
    )
  }
}

/** The box of the [index]th character of [joinCode], [width] wide. */
@Composable
private fun CodeBox(joinCode: String, index: Int, width: Dp) {
  val colors = MaterialTheme.colorScheme
  val char = joinCode.getOrNull(index)
  val border =
      when {
        char != null && !JoinCode.isUsed(char) -> colors.error
        index == joinCode.length -> colors.primary
        else -> null
      }
  Box(
      contentAlignment = Alignment.Center,
      modifier =
          Modifier.width(width)
              .aspectRatio(CODE_BOX_ASPECT_RATIO)
              .background(shifter_inputBackground, CODE_BOX_SHAPE)
              .border(if (border != null) 2.dp else 1.dp, border ?: colors.outline, CODE_BOX_SHAPE),
  ) {
    Text(
        text = char?.toString().orEmpty(),
        color = colors.onBackground,
        // In dp rather than sp: the box does not grow with the font scale, so neither does its
        // text.
        fontSize = with(LocalDensity.current) { (width / 2).toSp() },
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier.testTag(JoinEventScreenTestTags.codeBox(index)),
    )
  }
}

private val CODE_BOX_MAX_WIDTH = 44.dp
private val CODE_BOX_SPACING = 8.dp
private const val CODE_BOX_ASPECT_RATIO = 0.8f
private val CODE_BOX_SHAPE = RoundedCornerShape(12.dp)

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
  val text =
      when (status) {
        MembershipRequestStatus.PENDING -> R.string.join_event_status_pending
        MembershipRequestStatus.ACCEPTED -> R.string.join_event_status_accepted
        MembershipRequestStatus.REJECTED -> R.string.join_event_status_rejected
      }
  Pill(
      text = stringResource(text),
      colors = membershipStatusColors(status),
      modifier = Modifier.testTag(JoinEventScreenTestTags.APPLIED_MESSAGE),
      large = true,
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

@Preview(name = "Step 1: join code")
@Composable
private fun JoinEventCodePreview() {
  ShifterTheme { JoinEventContent(JoinEventUiState(joinCode = "ABC234"), {}, {}, {}, {}, {}) }
}

@Preview(name = "Step 2: event details and Apply")
@Composable
private fun JoinEventApplyPreview() {
  ShifterTheme {
    JoinEventContent(
        JoinEventUiState(event = PREVIEW_EVENT, teams = PREVIEW_TEAMS, selectedTeamId = "welcome"),
        {},
        {},
        {},
        {},
        {},
    )
  }
}

@Preview(name = "Step 2: request sent")
@Composable
private fun JoinEventAppliedPreview() {
  ShifterTheme {
    JoinEventContent(
        JoinEventUiState(
            event = PREVIEW_EVENT,
            teams = PREVIEW_TEAMS,
            selectedTeamId = "welcome",
            requestStatus = MembershipRequestStatus.PENDING,
        ),
        {},
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

private val PREVIEW_TEAMS =
    listOf(
        Team(
            id = "welcome",
            eventId = "event",
            name = "Welcome desk",
            description = "Greet donors and explain where each donation goes.",
            icon = "handshake",
            createdAt = Instant.EPOCH,
        ),
        Team(
            id = "sorting",
            eventId = "event",
            name = "Sorting",
            description = "Sort the collected items by category.",
            icon = "box",
            createdAt = Instant.EPOCH,
        ),
    )
