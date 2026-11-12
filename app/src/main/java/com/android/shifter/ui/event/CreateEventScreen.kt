// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.ui.components.FieldLabel
import com.swent.shifter.ui.components.FormPrimaryButton
import com.swent.shifter.ui.components.FormTextField
import com.swent.shifter.ui.components.ShifterTopBar
import com.swent.shifter.ui.components.toPickerDate
import com.swent.shifter.ui.components.toPickerMillis
import com.swent.shifter.ui.map.LocationPickerDialog
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.shifter_mutedText
import com.swent.shifter.ui.theme.shifter_warning
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object CreateEventScreenTestTags {
  const val TITLE_FIELD = "CreateEventTitleField"
  const val DESCRIPTION_FIELD = "CreateEventDescriptionField"
  const val ADDRESS_FIELD = "CreateEventAddressField"
  const val LATITUDE_FIELD = "CreateEventLatitudeField"
  const val LONGITUDE_FIELD = "CreateEventLongitudeField"
  const val COORDINATES_RULE = "CreateEventCoordinatesRule"
  const val PICK_ON_MAP_BUTTON = "CreateEventPickOnMapButton"
  const val CLEAR_LOCATION_BUTTON = "CreateEventClearLocationButton"
  const val LOCATION_PICKER_DIALOG = "CreateEventLocationPickerDialog"
  const val LOCATION_PICKER_CLOSE = "CreateEventLocationPickerClose"
  const val LOCATION_PICKER_CONFIRM = "CreateEventLocationPickerConfirm"
  const val START_FIELD = "CreateEventStartField"
  const val END_FIELD = "CreateEventEndField"
  const val SUBMIT_BUTTON = "CreateEventSubmitButton"
  const val SAVING_INDICATOR = "CreateEventSavingIndicator"
  const val SAVE_ERROR = "CreateEventSaveError"
  const val NOT_SIGNED_IN_ERROR = "CreateEventNotSignedInError"
  const val SAVE_UNCONFIRMED = "CreateEventSaveUnconfirmed"
  const val SAVE_UNCONFIRMED_ICON = "CreateEventSaveUnconfirmedIcon"

  /** Confirm button shared by the date and the time dialogs: only one is ever open at a time. */
  const val PICKER_CONFIRM = "CreateEventPickerConfirm"
  const val DATE_PICKER = "CreateEventDatePicker"
  const val TIME_PICKER = "CreateEventTimePicker"

  fun typeChip(type: EventType) = "CreateEventTypeChip_${type.name}"

  fun error(field: EventFormField) = "CreateEventError_${field.name}"

  fun pickerButton(field: EventFormField) = "CreateEventPicker_${field.name}"
}

/**
 * Lets an organizer create an event.
 *
 * @param onEventCreated called once with the persisted event, e.g. to open it, even if the screen
 *   is recomposed or recreated afterwards.
 * @param onBack called when the organizer leaves without creating the event.
 */
@Composable
fun CreateEventScreen(
    viewModel: CreateEventViewModel,
    onEventCreated: (Event) -> Unit,
    onBack: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()

  val createdEvent = uiState.createdEvent
  LaunchedEffect(createdEvent, uiState.createdEventHandled) {
    if (createdEvent != null && !uiState.createdEventHandled) {
      viewModel.onEventCreatedHandled()
      onEventCreated(createdEvent)
    }
  }

  CreateEventContent(
      state = uiState,
      actions =
          CreateEventActions(
              onTitleChange = viewModel::onTitleChange,
              onDescriptionChange = viewModel::onDescriptionChange,
              onTypeChange = viewModel::onTypeChange,
              onAddressChange = viewModel::onAddressChange,
              onLatitudeChange = viewModel::onLatitudeChange,
              onLongitudeChange = viewModel::onLongitudeChange,
              onPickLocation = viewModel::onPickLocation,
              onLocationDraftChange = viewModel::onLocationDraftChange,
              onConfirmLocation = viewModel::onConfirmLocation,
              onCancelLocationPicker = viewModel::onCancelLocationPicker,
              onClearLocation = viewModel::onClearLocation,
              onStartAtChange = viewModel::onStartAtChange,
              onEndAtChange = viewModel::onEndAtChange,
              onSubmit = viewModel::createEvent,
              onBack = onBack,
          ),
  )
}

/** Every interaction of the event creation form, grouped so the form takes a single parameter. */
data class CreateEventActions(
    val onTitleChange: (String) -> Unit = {},
    val onDescriptionChange: (String) -> Unit = {},
    val onTypeChange: (EventType) -> Unit = {},
    val onAddressChange: (String) -> Unit = {},
    val onLatitudeChange: (String) -> Unit = {},
    val onLongitudeChange: (String) -> Unit = {},
    val onPickLocation: () -> Unit = {},
    val onLocationDraftChange: (GeoPosition) -> Unit = {},
    val onConfirmLocation: () -> Unit = {},
    val onCancelLocationPicker: () -> Unit = {},
    val onClearLocation: () -> Unit = {},
    val onStartAtChange: (String) -> Unit = {},
    val onEndAtChange: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Stateless form of [CreateEventScreen]: renders [state] and reports every interaction. */
@Composable
fun CreateEventContent(
    state: CreateEventUiState,
    actions: CreateEventActions,
) {
  Scaffold(
      topBar = {
        ShifterTopBar(
            title = stringResource(R.string.create_event_title),
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
      Text(
          text = stringResource(R.string.create_event_intro),
          style = MaterialTheme.typography.bodySmall,
          color = shifter_mutedText,
      )
      EventTextField(
          value = state.title,
          onValueChange = actions.onTitleChange,
          label = R.string.create_event_field_title,
          field = EventFormField.TITLE,
          state = state,
          testTag = CreateEventScreenTestTags.TITLE_FIELD,
      )
      TypeSelector(
          selected = state.type,
          error = state.errorFor(EventFormField.TYPE),
          onTypeChange = actions.onTypeChange,
      )
      EventTextField(
          value = state.description,
          onValueChange = actions.onDescriptionChange,
          label = R.string.create_event_field_description,
          field = EventFormField.DESCRIPTION,
          state = state,
          testTag = CreateEventScreenTestTags.DESCRIPTION_FIELD,
          singleLine = false,
      )
      Text(
          text = stringResource(R.string.create_event_description_hint),
          style = MaterialTheme.typography.labelSmall,
          color = shifter_mutedText,
      )
      EventTextField(
          value = state.address,
          onValueChange = actions.onAddressChange,
          label = R.string.create_event_field_address,
          field = EventFormField.ADDRESS,
          state = state,
          testTag = CreateEventScreenTestTags.ADDRESS_FIELD,
      )
      LocationSection(state, actions)
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      ActivityPeriod(state, actions.onStartAtChange, actions.onEndAtChange)

      if (state.saveFailed) {
        Text(
            text = stringResource(R.string.create_event_error_save_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag(CreateEventScreenTestTags.SAVE_ERROR),
        )
      }

      if (state.notSignedIn) {
        Text(
            text = stringResource(R.string.create_event_error_not_signed_in),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag(CreateEventScreenTestTags.NOT_SIGNED_IN_ERROR),
        )
      }

      if (state.saveUnconfirmed) {
        // A warning rather than an error: the event is on its way, so it must not read as a
        // failure, but a muted line was missed. The theme is dark only, which the warning colour
        // is chosen for.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(
              imageVector = Icons.Filled.Warning,
              // The text says it all; the icon only draws the eye.
              contentDescription = null,
              tint = shifter_warning,
              modifier =
                  Modifier.size(16.dp).testTag(CreateEventScreenTestTags.SAVE_UNCONFIRMED_ICON),
          )
          Text(
              text = stringResource(R.string.create_event_save_unconfirmed),
              color = shifter_warning,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.testTag(CreateEventScreenTestTags.SAVE_UNCONFIRMED),
          )
        }
      }

      FormPrimaryButton(
          text = stringResource(R.string.create_event_submit),
          onClick = actions.onSubmit,
          testTag = CreateEventScreenTestTags.SUBMIT_BUTTON,
          enabled = !state.isSaving,
          isLoading = state.isSaving,
          loadingIndicatorTestTag = CreateEventScreenTestTags.SAVING_INDICATOR,
      )
    }
  }
  if (state.isPickingLocation) {
    LocationPickerDialog(
        draft = state.locationDraft,
        onDraftChange = actions.onLocationDraftChange,
        onConfirm = actions.onConfirmLocation,
        onCancel = actions.onCancelLocationPicker,
        dialogTestTag = CreateEventScreenTestTags.LOCATION_PICKER_DIALOG,
        closeTestTag = CreateEventScreenTestTags.LOCATION_PICKER_CLOSE,
        confirmTestTag = CreateEventScreenTestTags.LOCATION_PICKER_CONFIRM,
    )
  }
}

/**
 * The optional point of the event: latitude and longitude, typed in or picked on a map. The address
 * above stays mandatory, unlike a mission's; these coordinates are optional, but then both are
 * required together, and clearing them keeps the address. The fields sit next to the map, which a
 * screen reader cannot use and which is grey offline, so the coordinates stay the accessible way to
 * enter a point. The map itself is the [LocationPickerDialog] the mission form uses.
 */
@Composable
private fun LocationSection(state: CreateEventUiState, actions: CreateEventActions) {
  val enabled = !state.isSaving
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Box(modifier = Modifier.weight(1f)) {
        EventTextField(
            value = state.latitude,
            onValueChange = actions.onLatitudeChange,
            label = R.string.field_latitude,
            field = EventFormField.LATITUDE,
            state = state,
            testTag = CreateEventScreenTestTags.LATITUDE_FIELD,
        )
      }
      Box(modifier = Modifier.weight(1f)) {
        EventTextField(
            value = state.longitude,
            onValueChange = actions.onLongitudeChange,
            label = R.string.field_longitude,
            field = EventFormField.LONGITUDE,
            state = state,
            testTag = CreateEventScreenTestTags.LONGITUDE_FIELD,
        )
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      OutlinedButton(
          onClick = actions.onPickLocation,
          enabled = enabled,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.testTag(CreateEventScreenTestTags.PICK_ON_MAP_BUTTON),
      ) {
        Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            text = stringResource(R.string.pick_on_map),
            modifier = Modifier.padding(start = 8.dp),
        )
      }
      if (state.hasCoordinates) {
        TextButton(
            onClick = actions.onClearLocation,
            enabled = enabled,
            modifier = Modifier.testTag(CreateEventScreenTestTags.CLEAR_LOCATION_BUTTON),
        ) {
          Text(stringResource(R.string.create_event_clear_location))
        }
      }
    }
    Text(
        text = stringResource(R.string.create_event_coordinates_rule),
        style = MaterialTheme.typography.labelSmall,
        color = shifter_mutedText,
        modifier = Modifier.testTag(CreateEventScreenTestTags.COORDINATES_RULE),
    )
  }
}

/** A [FormTextField] showing the error of [field], disabled while the event is saved. */
@Composable
private fun EventTextField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    field: EventFormField,
    state: CreateEventUiState,
    testTag: String,
    singleLine: Boolean = true,
) {
  val error = state.errorFor(field)
  FormTextField(
      value = value,
      onValueChange = onValueChange,
      label = stringResource(label),
      isError = error != null,
      enabled = !state.isSaving,
      testTag = testTag,
      singleLine = singleLine,
      minLines = 2,
      supportingText =
          error?.let {
            {
              Text(
                  text = stringResource(it.message),
                  modifier = Modifier.testTag(CreateEventScreenTestTags.error(field)),
              )
            }
          },
  )
}

/** Second part of the form: when the event takes place. */
@Composable
private fun ActivityPeriod(
    state: CreateEventUiState,
    onStartAtChange: (String) -> Unit,
    onEndAtChange: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    val summary =
        listOfNotNull(
            state.title.trim().ifEmpty { null },
            state.type?.let { stringResource(it.label) },
        )
    if (summary.isNotEmpty()) {
      Text(
          text = summary.joinToString(" · "),
          style = MaterialTheme.typography.bodySmall,
          color = shifter_mutedText,
      )
    }
    Text(
        text = stringResource(R.string.create_event_period_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
        text = stringResource(R.string.create_event_period_instructions),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      DateCard(
          value = state.startAt,
          onValueChange = onStartAtChange,
          label = R.string.create_event_field_start,
          field = EventFormField.START,
          state = state,
          testTag = CreateEventScreenTestTags.START_FIELD,
          modifier = Modifier.weight(1f),
      )
      DateCard(
          value = state.endAt,
          onValueChange = onEndAtChange,
          label = R.string.create_event_field_end,
          field = EventFormField.END,
          state = state,
          testTag = CreateEventScreenTestTags.END_FIELD,
          modifier = Modifier.weight(1f),
      )
    }
  }
}

/** A start or end date typed as "DD/MM/YYYY HH:MM", shown as a highlighted card. */
@Composable
private fun DateCard(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    field: EventFormField,
    state: CreateEventUiState,
    testTag: String,
    modifier: Modifier = Modifier,
) {
  val fieldError = state.errorFor(field)
  val labelText = stringResource(label)
  val errorText = fieldError?.let { stringResource(it.message) }
  val colorScheme = MaterialTheme.colorScheme
  val shape = MaterialTheme.shapes.medium
  val textStyle =
      MaterialTheme.typography.bodyMedium.copy(
          color = colorScheme.onSurface,
          fontWeight = FontWeight.SemiBold,
      )
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(colorScheme.primaryContainer)
                .border(
                    1.dp,
                    if (fieldError != null) colorScheme.error else colorScheme.primary,
                    shape,
                )
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      // The picker sits on the label row, so the value field keeps the card's full width and the
      // whole "dd/MM/yyyy HH:mm" stays visible in the narrow two-column layout.
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = labelText,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        DateTimePickerButton(
            value = value,
            onValueChange = onValueChange,
            label = labelText,
            enabled = !state.isSaving,
            testTag = CreateEventScreenTestTags.pickerButton(field),
        )
      }
      BasicTextField(
          value = value,
          onValueChange = onValueChange,
          enabled = !state.isSaving,
          singleLine = true,
          textStyle = textStyle,
          cursorBrush = SolidColor(colorScheme.primary),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
          // Unlike OutlinedTextField, BasicTextField does not expose its label or error to
          // TalkBack.
          modifier =
              Modifier.fillMaxWidth().testTag(testTag).semantics {
                contentDescription = labelText
                if (errorText != null) error(errorText)
              },
          decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
              if (value.isEmpty()) {
                Text(
                    text = stringResource(R.string.create_event_date_time_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = shifter_mutedText,
                    maxLines = 1,
                )
              }
              innerTextField()
            }
          },
      )
    }
    if (errorText != null) {
      Text(
          text = errorText,
          color = colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.testTag(CreateEventScreenTestTags.error(field)),
      )
    }
  }
}

/**
 * A calendar button that picks a date with a Material3 [DatePicker], then a time with a
 * [TimePicker], and writes the result back through [onValueChange] in [EVENT_DATE_TIME_FORMATTER],
 * the format the form's text fields use. It is the picker counterpart of the manual [DateCard] text
 * field, so both ways of entering the value stay available.
 *
 * The existing [value] is preselected when it parses, read as strictly as the ViewModel reads it: a
 * typed `31/02/2027 10:00` preselects nothing rather than the 28th, so confirming twice can never
 * write a day the organizer did not choose. The date dialog's OK is disabled while no day is
 * selected, which also happens when the typed year is outside the picker's range, so the time
 * dialog only ever opens with a day to write. Cancelling or dismissing either dialog leaves [value]
 * untouched and forgets the day picked, so a later interaction starts over from the date. The date
 * is read and written in UTC midnight millis, the representation Material3 uses, so the chosen day
 * never shifts with the device's zone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerButton(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    testTag: String,
) {
  var showDate by rememberSaveable { mutableStateOf(false) }
  // The day confirmed in the date dialog, which is what opens the time dialog: that one is open
  // exactly while there is a day to write, and both survive a configuration change as an epoch day.
  var pickedEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
  val existing = remember(value) { parseEventDateTime(value) }

  IconButton(
      onClick = { showDate = true },
      enabled = enabled,
      modifier = Modifier.testTag(testTag),
  ) {
    Icon(
        imageVector = Icons.Filled.DateRange,
        contentDescription = stringResource(R.string.create_event_pick_date_time, label),
    )
  }

  if (showDate) {
    val initialDay = existing?.toLocalDate() ?: LocalDate.now()
    // A day outside the picker's year range is not preselected: the dialog opens with no selection.
    val pickerState =
        rememberDatePickerState(initialSelectedDateMillis = initialDay.toPickerMillis())
    DatePickerDialog(
        onDismissRequest = { showDate = false },
        confirmButton = {
          TextButton(
              onClick = {
                pickerState.selectedDateMillis?.let {
                  pickedEpochDay = it.toPickerDate().toEpochDay()
                }
                showDate = false
              },
              // Nothing to confirm without a day: the time dialog would have nothing to write.
              enabled = pickerState.selectedDateMillis != null,
              modifier = Modifier.testTag(CreateEventScreenTestTags.PICKER_CONFIRM),
          ) {
            Text(stringResource(R.string.dialog_confirm))
          }
        },
        dismissButton = {
          TextButton(onClick = { showDate = false }) {
            Text(stringResource(R.string.dialog_cancel))
          }
        },
    ) {
      DatePicker(
          state = pickerState,
          modifier = Modifier.testTag(CreateEventScreenTestTags.DATE_PICKER),
      )
    }
  }

  val pickedDay = pickedEpochDay
  if (pickedDay != null) {
    val start = existing?.toLocalTime() ?: LocalTime.NOON
    val pickerState =
        rememberTimePickerState(
            initialHour = start.hour,
            initialMinute = start.minute,
            is24Hour = true,
        )
    AlertDialog(
        onDismissRequest = { pickedEpochDay = null },
        confirmButton = {
          TextButton(
              onClick = {
                val dateTime =
                    LocalDateTime.of(
                        LocalDate.ofEpochDay(pickedDay),
                        LocalTime.of(pickerState.hour, pickerState.minute),
                    )
                onValueChange(dateTime.format(EVENT_DATE_TIME_FORMATTER))
                pickedEpochDay = null
              },
              modifier = Modifier.testTag(CreateEventScreenTestTags.PICKER_CONFIRM),
          ) {
            Text(stringResource(R.string.dialog_confirm))
          }
        },
        dismissButton = {
          TextButton(onClick = { pickedEpochDay = null }) {
            Text(stringResource(R.string.dialog_cancel))
          }
        },
        text = {
          TimePicker(
              state = pickerState,
              modifier = Modifier.testTag(CreateEventScreenTestTags.TIME_PICKER),
          )
        },
    )
  }
}

/** Two-column grid of selectable event type cards. */
@Composable
private fun TypeSelector(
    selected: EventType?,
    error: EventFormError?,
    onTypeChange: (EventType) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FieldLabel(
        if (selected == null) stringResource(R.string.create_event_field_type)
        else
            stringResource(
                R.string.create_event_field_type_selected,
                stringResource(selected.label),
            )
    )
    EventType.entries.chunked(2).forEach { rowTypes ->
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        rowTypes.forEach { type ->
          TypeOption(
              type = type,
              selected = type == selected,
              onClick = { onTypeChange(type) },
              modifier = Modifier.weight(1f),
          )
        }
        if (rowTypes.size == 1) Spacer(Modifier.weight(1f))
      }
    }
    if (error != null) {
      Text(
          text = stringResource(error.message),
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.testTag(CreateEventScreenTestTags.error(EventFormField.TYPE)),
      )
    }
  }
}

@Composable
private fun TypeOption(
    type: EventType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val shape = MaterialTheme.shapes.medium
  val accent = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant
  Row(
      modifier =
          modifier
              .height(76.dp)
              .clip(shape)
              .background(
                  if (selected) colorScheme.primaryContainer else colorScheme.surfaceVariant
              )
              .border(
                  width = if (selected) 2.dp else 1.dp,
                  color = if (selected) colorScheme.primary else colorScheme.outline,
                  shape = shape,
              )
              .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
              .padding(12.dp)
              .testTag(CreateEventScreenTestTags.typeChip(type)),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        imageVector = type.icon,
        contentDescription = null,
        tint = accent,
        modifier = Modifier.size(22.dp),
    )
    Text(
        text = stringResource(type.label),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = colorScheme.onSurface,
        modifier = Modifier.weight(1f),
    )
    Icon(
        imageVector = if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.Circle,
        contentDescription = null,
        tint = accent,
        modifier = Modifier.size(16.dp),
    )
  }
}

@get:StringRes
private val EventFormError.message: Int
  get() =
      when (this) {
        EventFormError.TITLE_EMPTY -> R.string.create_event_error_title_empty
        EventFormError.TITLE_TOO_LONG -> R.string.create_event_error_title_too_long
        EventFormError.DESCRIPTION_EMPTY -> R.string.create_event_error_description_empty
        EventFormError.DESCRIPTION_TOO_LONG -> R.string.create_event_error_description_too_long
        EventFormError.TYPE_MISSING -> R.string.create_event_error_type_missing
        EventFormError.ADDRESS_EMPTY -> R.string.create_event_error_address_empty
        EventFormError.LATITUDE_INVALID -> R.string.create_event_error_latitude_invalid
        EventFormError.LONGITUDE_INVALID -> R.string.create_event_error_longitude_invalid
        EventFormError.START_INVALID,
        EventFormError.END_INVALID -> R.string.create_event_error_date_invalid
        EventFormError.START_IN_PAST -> R.string.create_event_error_start_in_past
        EventFormError.END_NOT_AFTER_START -> R.string.create_event_error_end_not_after_start
      }

@get:StringRes
internal val EventType.label: Int
  get() =
      when (this) {
        EventType.MUSIC -> R.string.event_type_music
        EventType.MARKET -> R.string.event_type_market
        EventType.NATURE -> R.string.event_type_nature
        EventType.FOOD -> R.string.event_type_food
        EventType.SPORT -> R.string.event_type_sport
        EventType.PARTY -> R.string.event_type_party
        EventType.OTHER -> R.string.event_type_other
      }

private val EventType.icon: ImageVector
  get() =
      when (this) {
        EventType.MUSIC -> Icons.Outlined.MusicNote
        EventType.MARKET -> Icons.Outlined.Storefront
        EventType.NATURE -> Icons.Outlined.Park
        EventType.FOOD -> Icons.Outlined.Restaurant
        EventType.SPORT -> Icons.Outlined.EmojiEvents
        EventType.PARTY -> Icons.Outlined.Celebration
        EventType.OTHER -> Icons.Outlined.Category
      }

@Preview(showBackground = true)
@Composable
private fun CreateEventContentPreview() {
  ShifterTheme {
    CreateEventContent(
        state =
            CreateEventUiState(
                title = "Paléo Festival",
                type = EventType.MUSIC,
                errors = setOf(EventFormError.START_INVALID),
            ),
        actions = CreateEventActions(),
    )
  }
}
