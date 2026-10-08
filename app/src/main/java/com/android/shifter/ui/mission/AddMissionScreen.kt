// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.mission

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.shifter_mutedText
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object AddMissionScreenTestTags {
  const val BACK_BUTTON = "AddMissionBackButton"
  const val EVENT_TITLE = "AddMissionEventTitle"
  const val EVENT_PERIOD = "AddMissionEventPeriod"
  const val LOADING_INDICATOR = "AddMissionLoadingIndicator"
  const val LOAD_ERROR = "AddMissionLoadError"
  const val RETRY_BUTTON = "AddMissionRetryButton"
  const val TITLE_FIELD = "AddMissionTitleField"
  const val DESCRIPTION_FIELD = "AddMissionDescriptionField"
  const val GENERAL_CHIP = "AddMissionTeamChip_General"
  const val VOLUNTEERS_COUNT = "AddMissionVolunteersCount"
  const val DECREASE_VOLUNTEERS = "AddMissionDecreaseVolunteers"
  const val INCREASE_VOLUNTEERS = "AddMissionIncreaseVolunteers"
  const val DAY_FIELD = "AddMissionDayField"
  const val START_FIELD = "AddMissionStartField"
  const val END_FIELD = "AddMissionEndField"
  const val PICKER_CONFIRM = "AddMissionPickerConfirm"
  const val SUBMIT_BUTTON = "AddMissionSubmitButton"
  const val CANCEL_BUTTON = "AddMissionCancelButton"
  const val SAVING_INDICATOR = "AddMissionSavingIndicator"
  const val SAVE_ERROR = "AddMissionSaveError"

  fun teamChip(teamId: String) = "AddMissionTeamChip_$teamId"

  fun error(field: MissionFormField) = "AddMissionError_${field.name}"
}

/**
 * Lets an organizer add a mission to an event.
 *
 * @param onMissionAdded called once with the persisted mission, e.g. to go back to the management
 *   screen and confirm it.
 * @param onBack called when the organizer leaves without adding the mission.
 */
@Composable
fun AddMissionScreen(
    viewModel: AddMissionViewModel,
    onMissionAdded: (Mission) -> Unit,
    onBack: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()

  LaunchedEffect(uiState.createdMission) { uiState.createdMission?.let(onMissionAdded) }

  AddMissionContent(
      state = uiState,
      actions =
          AddMissionActions(
              onTitleChange = viewModel::onTitleChange,
              onDescriptionChange = viewModel::onDescriptionChange,
              onTeamChange = viewModel::onTeamChange,
              onIncreaseVolunteers = viewModel::onIncreaseVolunteers,
              onDecreaseVolunteers = viewModel::onDecreaseVolunteers,
              onDayChange = viewModel::onDayChange,
              onStartTimeChange = viewModel::onStartTimeChange,
              onEndTimeChange = viewModel::onEndTimeChange,
              onSubmit = viewModel::addMission,
              onRetryLoad = viewModel::loadEvent,
              onBack = onBack,
          ),
  )
}

/** Every interaction of the mission creation form, grouped so the form takes a single parameter. */
data class AddMissionActions(
    val onTitleChange: (String) -> Unit = {},
    val onDescriptionChange: (String) -> Unit = {},
    val onTeamChange: (String?) -> Unit = {},
    val onIncreaseVolunteers: () -> Unit = {},
    val onDecreaseVolunteers: () -> Unit = {},
    val onDayChange: (LocalDate) -> Unit = {},
    val onStartTimeChange: (LocalTime) -> Unit = {},
    val onEndTimeChange: (LocalTime) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onRetryLoad: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Stateless form of [AddMissionScreen]: renders [state] and reports every interaction. */
@Composable
fun AddMissionContent(state: AddMissionUiState, actions: AddMissionActions) {
  val editable = !state.isSaving
  Scaffold(topBar = { FormHeader(actions.onBack) }) { padding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      EventContext(state, actions.onRetryLoad)
      FormTextField(
          value = state.title,
          onValueChange = actions.onTitleChange,
          label = R.string.add_mission_field_title,
          error = state.errorFor(MissionFormField.TITLE),
          enabled = editable,
          testTag = AddMissionScreenTestTags.TITLE_FIELD,
      )
      FormTextField(
          value = state.description,
          onValueChange = actions.onDescriptionChange,
          label = R.string.add_mission_field_description,
          error = state.errorFor(MissionFormField.DESCRIPTION),
          enabled = editable,
          testTag = AddMissionScreenTestTags.DESCRIPTION_FIELD,
          singleLine = false,
      )
      TeamSelector(state.teams, state.teamId, editable, actions.onTeamChange)
      VolunteersStepper(
          count = state.volunteersNeeded,
          enabled = editable,
          onIncrease = actions.onIncreaseVolunteers,
          onDecrease = actions.onDecreaseVolunteers,
      )
      Schedule(state, editable, actions)

      if (state.saveFailed) {
        Text(
            text = stringResource(R.string.add_mission_error_save_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag(AddMissionScreenTestTags.SAVE_ERROR),
        )
      }
      FormActions(state, actions)
    }
  }
}

/** Return link to the management screen, above the screen title. */
@Composable
private fun FormHeader(onBack: () -> Unit) {
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
                .testTag(AddMissionScreenTestTags.BACK_BUTTON),
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
          text = stringResource(R.string.add_mission_back),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
      )
    }
    Text(
        text = stringResource(R.string.add_mission_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
  }
}

/** Which event the mission is added to, and when it takes place. */
@Composable
private fun EventContext(state: AddMissionUiState, onRetryLoad: () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    val event = state.event
    when {
      event != null -> {
        Text(
            text = event.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.testTag(AddMissionScreenTestTags.EVENT_TITLE),
        )
        Text(
            text = formatPeriod(event),
            style = MaterialTheme.typography.labelSmall,
            color = shifter_mutedText,
            modifier = Modifier.testTag(AddMissionScreenTestTags.EVENT_PERIOD),
        )
      }
      state.isLoadingEvent ->
          CircularProgressIndicator(
              modifier = Modifier.size(20.dp).testTag(AddMissionScreenTestTags.LOADING_INDICATOR),
              strokeWidth = 2.dp,
          )
      state.loadFailed ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.add_mission_load_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).testTag(AddMissionScreenTestTags.LOAD_ERROR),
            )
            TextButton(
                onClick = onRetryLoad,
                modifier = Modifier.testTag(AddMissionScreenTestTags.RETRY_BUTTON),
            ) {
              Text(stringResource(R.string.add_mission_retry))
            }
          }
    }
    Text(
        text = stringResource(R.string.add_mission_intro),
        style = MaterialTheme.typography.bodySmall,
        color = shifter_mutedText,
    )
  }
}

@Composable
private fun FieldLabel(@StringRes text: Int) {
  Text(
      text = stringResource(text),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun Hint(@StringRes text: Int) {
  Text(
      text = stringResource(text),
      style = MaterialTheme.typography.labelSmall,
      color = shifter_mutedText,
  )
}

@Composable
private fun FieldError(error: MissionFormError?) {
  if (error != null) {
    Text(
        text = stringResource(error.message),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag(AddMissionScreenTestTags.error(error.field)),
    )
  }
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    error: MissionFormError?,
    enabled: Boolean,
    testTag: String,
    singleLine: Boolean = true,
) {
  val colorScheme = MaterialTheme.colorScheme
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    FieldLabel(label)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        isError = error != null,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = MaterialTheme.shapes.medium,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colorScheme.surfaceVariant,
                unfocusedContainerColor = colorScheme.surfaceVariant,
                disabledContainerColor = colorScheme.surfaceVariant,
                errorContainerColor = colorScheme.surfaceVariant,
                unfocusedBorderColor = colorScheme.outline,
                disabledBorderColor = colorScheme.outline,
            ),
        keyboardOptions =
            KeyboardOptions(imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
    FieldError(error)
  }
}

/** "General" followed by the teams of the event; exactly one is selected. */
@Composable
private fun TeamSelector(
    teams: List<TeamOption>,
    selectedId: String?,
    enabled: Boolean,
    onTeamChange: (String?) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FieldLabel(R.string.add_mission_field_team)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      TeamChip(
          label = stringResource(R.string.add_mission_team_general),
          selected = selectedId == null,
          enabled = enabled,
          onClick = { onTeamChange(null) },
          testTag = AddMissionScreenTestTags.GENERAL_CHIP,
      )
      teams.forEach { team ->
        TeamChip(
            label = team.name,
            selected = team.id == selectedId,
            enabled = enabled,
            onClick = { onTeamChange(team.id) },
            testTag = AddMissionScreenTestTags.teamChip(team.id),
        )
      }
    }
    Hint(R.string.add_mission_team_hint)
  }
}

@Composable
private fun TeamChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
  val colorScheme = MaterialTheme.colorScheme
  val shape = MaterialTheme.shapes.small
  Box(
      modifier =
          Modifier.clip(shape)
              .background(
                  if (selected) colorScheme.primaryContainer else colorScheme.surfaceVariant
              )
              .border(1.dp, if (selected) colorScheme.primary else colorScheme.outline, shape)
              .selectable(
                  selected = selected,
                  enabled = enabled,
                  role = Role.RadioButton,
                  onClick = onClick,
              )
              .padding(10.dp)
              .testTag(testTag)
  ) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
    )
  }
}

/** How many people the mission needs, changed one at a time and never below one. */
@Composable
private fun VolunteersStepper(
    count: Int,
    enabled: Boolean,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  val shape = MaterialTheme.shapes.medium
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    FieldLabel(R.string.add_mission_field_volunteers)
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(44.dp)
                .clip(shape)
                .background(colorScheme.surfaceVariant)
                .border(1.dp, colorScheme.outline, shape)
                .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      StepperButton(
          onClick = onDecrease,
          enabled = enabled && count > AddMissionUiState.MIN_VOLUNTEERS,
          description = R.string.add_mission_decrease_volunteers,
          testTag = AddMissionScreenTestTags.DECREASE_VOLUNTEERS,
          isIncrease = false,
      )
      Text(
          text = count.toString(),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          color = colorScheme.onSurface,
          textAlign = TextAlign.Center,
          modifier = Modifier.weight(1f).testTag(AddMissionScreenTestTags.VOLUNTEERS_COUNT),
      )
      StepperButton(
          onClick = onIncrease,
          enabled = enabled,
          description = R.string.add_mission_increase_volunteers,
          testTag = AddMissionScreenTestTags.INCREASE_VOLUNTEERS,
          isIncrease = true,
      )
    }
    Hint(R.string.add_mission_volunteers_hint)
  }
}

@Composable
private fun StepperButton(
    onClick: () -> Unit,
    enabled: Boolean,
    @StringRes description: Int,
    testTag: String,
    isIncrease: Boolean,
) {
  IconButton(
      onClick = onClick,
      enabled = enabled,
      shape = RoundedCornerShape(8.dp),
      colors =
          IconButtonDefaults.iconButtonColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.primary,
          ),
      modifier = Modifier.size(32.dp).testTag(testTag),
  ) {
    Icon(
        imageVector = if (isIncrease) Icons.Filled.Add else Icons.Filled.Remove,
        contentDescription = stringResource(description),
        modifier = Modifier.size(18.dp),
    )
  }
}

/** The day of the mission, then its start and end times side by side. */
@Composable
private fun Schedule(state: AddMissionUiState, enabled: Boolean, actions: AddMissionActions) {
  val event = state.event
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    FieldLabel(R.string.add_mission_field_day)
    DayField(
        day = state.day,
        event = event,
        enabled = enabled && event != null,
        onDayChange = actions.onDayChange,
    )
    FieldError(state.errorFor(MissionFormField.DAY))
  }
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      TimeField(
          label = R.string.add_mission_field_start,
          time = state.startTime,
          initial = event?.start?.toLocalTime(),
          enabled = enabled && event != null,
          onTimeChange = actions.onStartTimeChange,
          testTag = AddMissionScreenTestTags.START_FIELD,
          modifier = Modifier.weight(1f),
      )
      TimeField(
          label = R.string.add_mission_field_end,
          time = state.endTime,
          initial = state.startTime ?: event?.start?.toLocalTime(),
          enabled = enabled && event != null,
          onTimeChange = actions.onEndTimeChange,
          testTag = AddMissionScreenTestTags.END_FIELD,
          modifier = Modifier.weight(1f),
      )
    }
    val error = state.errorFor(MissionFormField.SCHEDULE)
    if (error != null) FieldError(error) else Hint(R.string.add_mission_schedule_hint)
  }
}

/** A read-only field that opens a picker when tapped. */
@Composable
private fun PickerField(
    text: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val shape = MaterialTheme.shapes.medium
  Box(
      modifier =
          modifier
              .fillMaxWidth()
              .heightIn(min = 44.dp)
              .clip(shape)
              .background(colorScheme.surfaceVariant)
              .border(1.dp, colorScheme.outline, shape)
              .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
              .padding(12.dp)
              .testTag(testTag),
      contentAlignment = Alignment.CenterStart,
  ) {
    Text(
        text = text ?: stringResource(R.string.add_mission_pick_placeholder),
        style = MaterialTheme.typography.bodyMedium,
        color = if (text != null) colorScheme.onSurface else shifter_mutedText,
    )
  }
}

/** Picks the day of the mission among the days of the event. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayField(
    day: LocalDate?,
    event: EventPeriod?,
    enabled: Boolean,
    onDayChange: (LocalDate) -> Unit,
) {
  var open by rememberSaveable { mutableStateOf(false) }
  PickerField(
      text = day?.format(DAY_FORMAT),
      enabled = enabled,
      onClick = { open = true },
      testTag = AddMissionScreenTestTags.DAY_FIELD,
  )
  if (open && event != null) {
    val firstDay = event.start.toLocalDate()
    val lastDay = event.end.toLocalDate()
    val pickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = (day ?: firstDay).toPickerMillis(),
            yearRange = firstDay.year..lastDay.year,
            selectableDates =
                remember(firstDay, lastDay) {
                  object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        utcTimeMillis.toPickerDate() in firstDay..lastDay
                  }
                },
        )
    DatePickerDialog(
        onDismissRequest = { open = false },
        confirmButton = {
          TextButton(
              onClick = {
                pickerState.selectedDateMillis?.let { onDayChange(it.toPickerDate()) }
                open = false
              },
              modifier = Modifier.testTag(AddMissionScreenTestTags.PICKER_CONFIRM),
          ) {
            Text(stringResource(R.string.add_mission_dialog_confirm))
          }
        },
        dismissButton = {
          TextButton(onClick = { open = false }) {
            Text(stringResource(R.string.add_mission_cancel))
          }
        },
    ) {
      DatePicker(state = pickerState)
    }
  }
}

/** Picks a time of day, starting from [time] or else from [initial]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(
    @StringRes label: Int,
    time: LocalTime?,
    initial: LocalTime?,
    enabled: Boolean,
    onTimeChange: (LocalTime) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
  var open by rememberSaveable { mutableStateOf(false) }
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    FieldLabel(label)
    PickerField(
        text = time?.format(TIME_FORMAT),
        enabled = enabled,
        onClick = { open = true },
        testTag = testTag,
    )
  }
  if (open) {
    val start = time ?: initial ?: LocalTime.NOON
    val pickerState =
        rememberTimePickerState(
            initialHour = start.hour,
            initialMinute = start.minute,
            is24Hour = true,
        )
    AlertDialog(
        onDismissRequest = { open = false },
        confirmButton = {
          TextButton(
              onClick = {
                onTimeChange(LocalTime.of(pickerState.hour, pickerState.minute))
                open = false
              },
              modifier = Modifier.testTag(AddMissionScreenTestTags.PICKER_CONFIRM),
          ) {
            Text(stringResource(R.string.add_mission_dialog_confirm))
          }
        },
        dismissButton = {
          TextButton(onClick = { open = false }) {
            Text(stringResource(R.string.add_mission_cancel))
          }
        },
        text = { TimePicker(state = pickerState) },
    )
  }
}

/** "Add the mission", then "Cancel" which leaves without saving. */
@Composable
private fun FormActions(state: AddMissionUiState, actions: AddMissionActions) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Button(
        onClick = actions.onSubmit,
        enabled = state.canSubmit,
        shape = MaterialTheme.shapes.medium,
        modifier =
            Modifier.fillMaxWidth().height(48.dp).testTag(AddMissionScreenTestTags.SUBMIT_BUTTON),
    ) {
      if (state.isSaving) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp).testTag(AddMissionScreenTestTags.SAVING_INDICATOR),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
        )
      } else {
        Text(
            text = stringResource(R.string.add_mission_submit),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
      }
    }
    OutlinedButton(
        onClick = actions.onBack,
        enabled = !state.isSaving,
        shape = MaterialTheme.shapes.medium,
        modifier =
            Modifier.fillMaxWidth().height(44.dp).testTag(AddMissionScreenTestTags.CANCEL_BUTTON),
    ) {
      Text(
          text = stringResource(R.string.add_mission_cancel),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

/** "Fri 20 Jun 2025 · 10:00–18:00", or both ends in full when the event spans several days. */
@Composable
private fun formatPeriod(event: EventPeriod): String =
    if (event.start.toLocalDate() == event.end.toLocalDate()) {
      stringResource(
          R.string.add_mission_event_period,
          event.start.format(DAY_FORMAT),
          event.start.format(TIME_FORMAT),
          event.end.format(TIME_FORMAT),
      )
    } else {
      stringResource(
          R.string.add_mission_event_period_multi_day,
          event.start.format(DATE_TIME_FORMAT),
          event.end.format(DATE_TIME_FORMAT),
      )
    }

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM uuuu")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM uuuu HH:mm")

/** The date picker works in UTC milliseconds at midnight, whatever the device's zone. */
private const val MILLIS_PER_DAY = 86_400_000L

private fun LocalDate.toPickerMillis(): Long = toEpochDay() * MILLIS_PER_DAY

private fun Long.toPickerDate(): LocalDate =
    LocalDate.ofEpochDay(Math.floorDiv(this, MILLIS_PER_DAY))

@get:StringRes
private val MissionFormError.message: Int
  get() =
      when (this) {
        MissionFormError.TITLE_EMPTY -> R.string.add_mission_error_title_empty
        MissionFormError.DESCRIPTION_EMPTY -> R.string.add_mission_error_description_empty
        MissionFormError.DAY_MISSING -> R.string.add_mission_error_day_missing
        MissionFormError.START_MISSING -> R.string.add_mission_error_start_missing
        MissionFormError.END_MISSING -> R.string.add_mission_error_end_missing
        MissionFormError.END_NOT_AFTER_START -> R.string.add_mission_error_end_not_after_start
        MissionFormError.OUTSIDE_EVENT -> R.string.add_mission_error_outside_event
      }

@Preview(showBackground = true)
@Composable
private fun AddMissionContentPreview() {
  ShifterTheme {
    AddMissionContent(
        state =
            AddMissionUiState(
                event =
                    EventPeriod(
                        title = "Local Food Drive",
                        start = LocalDateTime.of(2025, 6, 20, 10, 0),
                        end = LocalDateTime.of(2025, 6, 20, 18, 0),
                    ),
                isLoadingEvent = false,
                teams =
                    listOf(
                        TeamOption("logistics", "Logistics"),
                        TeamOption("bar", "Bar"),
                        TeamOption("kitchen", "Kitchen"),
                    ),
                title = "Sort the food donations",
                teamId = "logistics",
                volunteersNeeded = 3,
                day = LocalDate.of(2025, 6, 20),
                startTime = LocalTime.of(14, 0),
            ),
        actions = AddMissionActions(),
    )
  }
}
