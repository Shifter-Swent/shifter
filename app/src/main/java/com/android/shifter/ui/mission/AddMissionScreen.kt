// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Map
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swent.shifter.R
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.ui.components.FieldLabel
import com.swent.shifter.ui.components.FormPrimaryButton
import com.swent.shifter.ui.components.FormTextField
import com.swent.shifter.ui.components.ShifterTopBar
import com.swent.shifter.ui.components.toPickerDate
import com.swent.shifter.ui.components.toPickerMillis
import com.swent.shifter.ui.map.LocationPicker
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.shifter_mutedText
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object AddMissionScreenTestTags {
  const val EVENT_TITLE = "AddMissionEventTitle"
  const val EVENT_PERIOD = "AddMissionEventPeriod"
  const val LOADING_INDICATOR = "AddMissionLoadingIndicator"
  const val LOAD_ERROR = "AddMissionLoadError"
  const val RETRY_BUTTON = "AddMissionRetryButton"
  const val NOT_ALLOWED = "AddMissionNotAllowed"
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
  const val CONFLICT_ERROR = "AddMissionConflictError"
  const val ADDRESS_FIELD = "AddMissionAddressField"
  const val ADDRESS_RULE = "AddMissionAddressRule"
  const val LATITUDE_FIELD = "AddMissionLatitudeField"
  const val LONGITUDE_FIELD = "AddMissionLongitudeField"
  const val PICK_ON_MAP_BUTTON = "AddMissionPickOnMapButton"
  const val CLEAR_LOCATION_BUTTON = "AddMissionClearLocationButton"
  const val LOCATION_PICKER_DIALOG = "AddMissionLocationPickerDialog"
  const val LOCATION_PICKER_CLOSE = "AddMissionLocationPickerClose"
  const val LOCATION_PICKER_CONFIRM = "AddMissionLocationPickerConfirm"
  const val ASSIGNED_COUNT = "AddMissionAssignedCount"
  const val BELOW_ASSIGNEES = "AddMissionBelowAssignees"
  const val NOT_FOUND = "AddMissionNotFound"
  const val COMPLETED = "AddMissionCompleted"
  const val SLOT_NOT_EDITABLE = "AddMissionSlotNotEditable"

  fun teamChip(teamId: String) = "AddMissionTeamChip_$teamId"

  fun error(field: MissionFormField) = "AddMissionError_${field.name}"
}

/**
 * Lets the organizer of an event, or the manager of one of its teams, add a mission to it, or edit
 * one when [viewModel] was given a mission.
 *
 * @param onMissionSaved called once with the persisted mission, added or edited, e.g. to go back to
 *   the management screen and confirm it.
 * @param onBack called when the organizer leaves without saving.
 */
@Composable
fun AddMissionScreen(
    viewModel: AddMissionViewModel,
    onMissionSaved: (Mission) -> Unit,
    onBack: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()

  val savedMission = uiState.savedMission
  LaunchedEffect(savedMission, uiState.savedMissionHandled) {
    if (savedMission != null && !uiState.savedMissionHandled) {
      viewModel.onSaveHandled()
      onMissionSaved(savedMission)
    }
  }

  AddMissionContent(
      state = uiState,
      actions =
          AddMissionActions(
              onTitleChange = viewModel::onTitleChange,
              onDescriptionChange = viewModel::onDescriptionChange,
              onTeamChange = viewModel::onTeamChange,
              onIncreaseVolunteers = viewModel::onIncreaseVolunteers,
              onDecreaseVolunteers = viewModel::onDecreaseVolunteers,
              onToggleAssignee = viewModel::onToggleAssignee,
              onRetryAssignees = viewModel::retryLoadAssignees,
              onDayChange = viewModel::onDayChange,
              onStartTimeChange = viewModel::onStartTimeChange,
              onEndTimeChange = viewModel::onEndTimeChange,
              onAddressChange = viewModel::onAddressChange,
              onLatitudeChange = viewModel::onLatitudeChange,
              onLongitudeChange = viewModel::onLongitudeChange,
              onPickLocation = viewModel::onPickLocation,
              onLocationDraftChange = viewModel::onLocationDraftChange,
              onConfirmLocation = viewModel::onConfirmLocation,
              onCancelLocationPicker = viewModel::onCancelLocationPicker,
              onClearLocation = viewModel::onClearLocation,
              onSubmit = viewModel::save,
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
    val onToggleAssignee: (String) -> Unit = {},
    val onRetryAssignees: () -> Unit = {},
    val onDayChange: (LocalDate) -> Unit = {},
    val onStartTimeChange: (LocalTime) -> Unit = {},
    val onEndTimeChange: (LocalTime) -> Unit = {},
    val onAddressChange: (String) -> Unit = {},
    val onLatitudeChange: (String) -> Unit = {},
    val onLongitudeChange: (String) -> Unit = {},
    val onPickLocation: () -> Unit = {},
    val onLocationDraftChange: (GeoPosition) -> Unit = {},
    val onConfirmLocation: () -> Unit = {},
    val onCancelLocationPicker: () -> Unit = {},
    val onClearLocation: () -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onRetryLoad: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Stateless form of [AddMissionScreen]: renders [state] and reports every interaction. */
@Composable
fun AddMissionContent(state: AddMissionUiState, actions: AddMissionActions) {
  // A mission that cannot be saved, completed or with a slot the form cannot show, has its fields
  // disabled too, so nothing invites changes that would be lost.
  val editable = !state.isSaving && !state.missionCompleted && !state.slotNotEditable
  Scaffold(
      topBar = {
        ShifterTopBar(
            title =
                stringResource(
                    if (state.isEditing) R.string.edit_mission_title else R.string.add_mission_title
                ),
            onBack = actions.onBack,
        )
      }
  ) { padding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(padding)
                // The Scaffold leaves room for the bars, not the keyboard: the fields scroll above
                // it.
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      EventContext(state, actions.onRetryLoad)
      if (state.notAllowed) {
        Text(
            text =
                stringResource(
                    if (state.isEditing) R.string.edit_mission_not_allowed
                    else R.string.add_mission_not_allowed
                ),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag(AddMissionScreenTestTags.NOT_ALLOWED),
        )
        return@Column
      }
      // Nothing to type in before the mission arrives, as it would overwrite what was typed.
      if (state.isEditing && !state.missionLoaded) return@Column
      if (state.missionNotFound) {
        ErrorText(R.string.edit_mission_not_found, AddMissionScreenTestTags.NOT_FOUND)
        return@Column
      }
      if (state.missionCompleted) {
        ErrorText(R.string.edit_mission_completed, AddMissionScreenTestTags.COMPLETED)
      } else if (state.slotNotEditable) {
        ErrorText(
            R.string.edit_mission_slot_not_editable,
            AddMissionScreenTestTags.SLOT_NOT_EDITABLE,
        )
      }
      MissionTextField(
          value = state.title,
          onValueChange = actions.onTitleChange,
          label = R.string.add_mission_field_title,
          error = state.errorFor(MissionFormField.TITLE),
          enabled = editable,
          testTag = AddMissionScreenTestTags.TITLE_FIELD,
      )
      MissionTextField(
          value = state.description,
          onValueChange = actions.onDescriptionChange,
          label = R.string.add_mission_field_description,
          error = state.errorFor(MissionFormField.DESCRIPTION),
          enabled = editable,
          testTag = AddMissionScreenTestTags.DESCRIPTION_FIELD,
          singleLine = false,
      )
      // The teams load with the event: until then, a manager would see "General" selected.
      if (state.event != null) {
        TeamSelector(
            teams = state.teams,
            canChooseGeneral = state.canChooseGeneral,
            selectedId = state.teamId,
            // An edited mission keeps its team, changed from nowhere yet.
            enabled = editable && !state.isEditing,
            onTeamChange = actions.onTeamChange,
        )
      }
      VolunteersStepper(
          count = state.volunteersNeeded,
          minimum = state.minVolunteers,
          enabled = editable,
          onIncrease = actions.onIncreaseVolunteers,
          onDecrease = actions.onDecreaseVolunteers,
      )
      state.belowAssignees?.let { count ->
        Text(
            text = pluralStringResource(R.plurals.edit_mission_below_assignees, count, count),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag(AddMissionScreenTestTags.BELOW_ASSIGNEES),
        )
      }
      // The assignees of an existing mission are changed from the Overview, not here.
      if (state.isEditing) {
        Text(
            text =
                pluralStringResource(
                    R.plurals.edit_mission_assigned,
                    state.assigneeCount,
                    state.assigneeCount,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = shifter_mutedText,
            modifier = Modifier.testTag(AddMissionScreenTestTags.ASSIGNED_COUNT),
        )
      } else if (state.event != null) {
        // Who may be assigned depends on the team, known once the event has loaded.
        AssigneePicker(
            selection = state.assignees,
            limit = state.volunteersNeeded,
            enabled = editable,
            onToggle = actions.onToggleAssignee,
            onRetry = actions.onRetryAssignees,
        )
      }
      Schedule(state, editable, actions)
      Location(state, editable, actions)

      state.conflict?.let { conflict ->
        Text(
            text = formatConflict(conflict, state.isEditing),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag(AddMissionScreenTestTags.CONFLICT_ERROR),
        )
      }
      if (state.saveFailed) {
        ErrorText(
            if (state.isEditing) R.string.edit_mission_save_failed
            else R.string.add_mission_error_save_failed,
            AddMissionScreenTestTags.SAVE_ERROR,
        )
      }
      FormActions(state, actions)
    }
  }
  if (state.isPickingLocation) {
    LocationPickerDialog(
        draft = state.locationDraft,
        eventPosition = state.eventPosition,
        actions = actions,
    )
  }
}

/** A message in the error colour, such as why the form cannot be saved. */
@Composable
private fun ErrorText(@StringRes text: Int, testTag: String) {
  Text(
      text = stringResource(text),
      color = MaterialTheme.colorScheme.error,
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.testTag(testTag),
  )
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
                text =
                    stringResource(
                        if (state.isEditing) R.string.edit_mission_load_failed
                        else R.string.add_mission_load_failed
                    ),
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
private fun Hint(@StringRes text: Int, modifier: Modifier = Modifier) {
  Text(
      text = stringResource(text),
      style = MaterialTheme.typography.labelSmall,
      color = shifter_mutedText,
      modifier = modifier,
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

/** A [FormTextField] with the [error] of its field shown under it. */
@Composable
private fun MissionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    error: MissionFormError?,
    enabled: Boolean,
    testTag: String,
    singleLine: Boolean = true,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    FormTextField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(label),
        isError = error != null,
        enabled = enabled,
        testTag = testTag,
        singleLine = singleLine,
    )
    FieldError(error)
  }
}

/** "General", when [canChooseGeneral], followed by [teams]; exactly one is selected. */
@Composable
private fun TeamSelector(
    teams: List<TeamOption>,
    canChooseGeneral: Boolean,
    selectedId: String?,
    enabled: Boolean,
    onTeamChange: (String?) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FieldLabel(stringResource(R.string.add_mission_field_team))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      if (canChooseGeneral) {
        TeamChip(
            label = stringResource(R.string.add_mission_team_general),
            selected = selectedId == null,
            enabled = enabled,
            onClick = { onTeamChange(null) },
            testTag = AddMissionScreenTestTags.GENERAL_CHIP,
        )
      }
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
    if (canChooseGeneral) Hint(R.string.add_mission_team_hint)
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

/**
 * How many people the mission needs, changed one at a time and never below [minimum]: one, or the
 * number of people already assigned.
 */
@Composable
private fun VolunteersStepper(
    count: Int,
    minimum: Int,
    enabled: Boolean,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  val shape = MaterialTheme.shapes.medium
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    FieldLabel(stringResource(R.string.add_mission_field_volunteers))
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
          enabled = enabled && count > minimum,
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
    FieldLabel(stringResource(R.string.add_mission_field_day))
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
    when {
      error != null -> FieldError(error)
      state.endsNextDay -> Hint(R.string.add_mission_schedule_ends_next_day)
      else -> Hint(R.string.add_mission_schedule_hint)
    }
  }
}

/**
 * The optional place of the mission: an address, with the coordinates that put it on the map, typed
 * in or picked on a map. Leaving all three empty adds a mission with no location. The fields stay
 * next to the map, which a screen reader cannot use and which is grey offline.
 */
@Composable
private fun Location(state: AddMissionUiState, enabled: Boolean, actions: AddMissionActions) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    MissionTextField(
        value = state.address,
        onValueChange = actions.onAddressChange,
        label = R.string.add_mission_field_address,
        error = state.errorFor(MissionFormField.ADDRESS),
        enabled = enabled,
        testTag = AddMissionScreenTestTags.ADDRESS_FIELD,
    )
    // Under the error, if any: the error says what is missing, the rule says why.
    Hint(
        R.string.add_mission_address_rule,
        modifier = Modifier.testTag(AddMissionScreenTestTags.ADDRESS_RULE),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Box(modifier = Modifier.weight(1f)) {
        MissionTextField(
            value = state.latitude,
            onValueChange = actions.onLatitudeChange,
            label = R.string.add_mission_field_latitude,
            error = state.errorFor(MissionFormField.LATITUDE),
            enabled = enabled,
            testTag = AddMissionScreenTestTags.LATITUDE_FIELD,
        )
      }
      Box(modifier = Modifier.weight(1f)) {
        MissionTextField(
            value = state.longitude,
            onValueChange = actions.onLongitudeChange,
            label = R.string.add_mission_field_longitude,
            error = state.errorFor(MissionFormField.LONGITUDE),
            enabled = enabled,
            testTag = AddMissionScreenTestTags.LONGITUDE_FIELD,
        )
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      // The map opens where the event is, which is only known once it has loaded, not while it
      // loads nor after it failed to.
      OutlinedButton(
          onClick = actions.onPickLocation,
          enabled = enabled && state.event != null,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.testTag(AddMissionScreenTestTags.PICK_ON_MAP_BUTTON),
      ) {
        Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            text = stringResource(R.string.add_mission_pick_on_map),
            modifier = Modifier.padding(start = 8.dp),
        )
      }
      if (state.hasLocation) {
        TextButton(
            onClick = actions.onClearLocation,
            enabled = enabled,
            modifier = Modifier.testTag(AddMissionScreenTestTags.CLEAR_LOCATION_BUTTON),
        ) {
          Text(stringResource(R.string.add_mission_clear_location))
        }
      }
    }
    Hint(R.string.add_mission_location_hint)
  }
}

/**
 * The map to pick the place of the mission on, over the whole screen, since a map inside the
 * scrolling form would fight its gestures. The point picked is only used with "Use this place";
 * closing the dialog, or going back, leaves the coordinates as they were.
 */
@Composable
private fun LocationPickerDialog(
    draft: GeoPosition?,
    eventPosition: GeoPosition?,
    actions: AddMissionActions,
) {
  Dialog(
      onDismissRequest = actions.onCancelLocationPicker,
      properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
        modifier = Modifier.fillMaxSize().testTag(AddMissionScreenTestTags.LOCATION_PICKER_DIALOG)
    ) {
      Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
              onClick = actions.onCancelLocationPicker,
              modifier = Modifier.testTag(AddMissionScreenTestTags.LOCATION_PICKER_CLOSE),
          ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.add_mission_picker_close),
            )
          }
          Text(
              text = stringResource(R.string.add_mission_picker_title),
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.weight(1f),
          )
          TextButton(
              onClick = actions.onConfirmLocation,
              enabled = draft != null,
              modifier = Modifier.testTag(AddMissionScreenTestTags.LOCATION_PICKER_CONFIRM),
          ) {
            Text(stringResource(R.string.add_mission_picker_confirm))
          }
        }
        LocationPicker(
            selected = draft,
            onSelect = actions.onLocationDraftChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
            initialCenter = eventPosition,
        )
      }
    }
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
            Text(stringResource(R.string.dialog_confirm))
          }
        },
        dismissButton = {
          TextButton(onClick = { open = false }) { Text(stringResource(R.string.dialog_cancel)) }
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
    FieldLabel(stringResource(label))
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
            Text(stringResource(R.string.dialog_confirm))
          }
        },
        dismissButton = {
          TextButton(onClick = { open = false }) { Text(stringResource(R.string.dialog_cancel)) }
        },
        text = { TimePicker(state = pickerState) },
    )
  }
}

/** "Add the mission", then "Cancel" which leaves without saving. */
@Composable
private fun FormActions(state: AddMissionUiState, actions: AddMissionActions) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    FormPrimaryButton(
        text =
            stringResource(
                if (state.isEditing) R.string.edit_mission_submit else R.string.add_mission_submit
            ),
        onClick = actions.onSubmit,
        testTag = AddMissionScreenTestTags.SUBMIT_BUTTON,
        enabled = state.canSubmit,
        isLoading = state.isSaving,
        loadingIndicatorTestTag = AddMissionScreenTestTags.SAVING_INDICATOR,
    )
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

/** "Overlaps "Bar shift" of the same team (Fri 20 Jun 2025 14:00 – 16:00)." */
@Composable
private fun formatConflict(conflict: MissionConflict, isEditing: Boolean): String =
    stringResource(
        if (isEditing) R.string.edit_mission_error_conflict
        else R.string.add_mission_error_conflict,
        conflict.title,
        conflict.start.format(DATE_TIME_FORMAT),
        conflict.end.format(
            if (conflict.start.toLocalDate() == conflict.end.toLocalDate()) TIME_FORMAT
            else DATE_TIME_FORMAT
        ),
    )

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM uuuu")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM uuuu HH:mm")

@get:StringRes
private val MissionFormError.message: Int
  get() =
      when (this) {
        MissionFormError.TITLE_EMPTY -> R.string.add_mission_error_title_empty
        MissionFormError.TITLE_TOO_LONG -> R.string.add_mission_error_title_too_long
        MissionFormError.DESCRIPTION_EMPTY -> R.string.add_mission_error_description_empty
        MissionFormError.DESCRIPTION_TOO_LONG -> R.string.add_mission_error_description_too_long
        MissionFormError.DAY_MISSING -> R.string.add_mission_error_day_missing
        MissionFormError.START_MISSING -> R.string.add_mission_error_start_missing
        MissionFormError.END_MISSING -> R.string.add_mission_error_end_missing
        MissionFormError.END_NOT_AFTER_START -> R.string.add_mission_error_end_not_after_start
        MissionFormError.OUTSIDE_EVENT -> R.string.add_mission_error_outside_event
        MissionFormError.ADDRESS_MISSING -> R.string.add_mission_error_address_missing
        MissionFormError.LATITUDE_INVALID -> R.string.add_mission_error_latitude_invalid
        MissionFormError.LONGITUDE_INVALID -> R.string.add_mission_error_longitude_invalid
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
