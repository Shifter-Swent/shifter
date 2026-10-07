// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventType
import com.swent.shifter.ui.theme.ShifterTheme

object CreateEventScreenTestTags {
  const val BACK_BUTTON = "CreateEventBackButton"
  const val TITLE_FIELD = "CreateEventTitleField"
  const val DESCRIPTION_FIELD = "CreateEventDescriptionField"
  const val ADDRESS_FIELD = "CreateEventAddressField"
  const val START_FIELD = "CreateEventStartField"
  const val END_FIELD = "CreateEventEndField"
  const val TYPE_ERROR = "CreateEventTypeError"
  const val SUBMIT_BUTTON = "CreateEventSubmitButton"
  const val SAVING_INDICATOR = "CreateEventSavingIndicator"
  const val SAVE_ERROR = "CreateEventSaveError"

  fun typeChip(type: EventType) = "CreateEventTypeChip_${type.name}"

  fun error(field: EventFormField) = "CreateEventError_${field.name}"
}

/**
 * Lets an organizer create an event.
 *
 * @param onEventCreated called once with the persisted event, e.g. to open it.
 * @param onBack called when the organizer leaves without creating the event.
 */
@Composable
fun CreateEventScreen(
    viewModel: CreateEventViewModel,
    onEventCreated: (Event) -> Unit,
    onBack: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()

  LaunchedEffect(uiState.createdEvent) { uiState.createdEvent?.let(onEventCreated) }

  CreateEventContent(
      state = uiState,
      onTitleChange = viewModel::onTitleChange,
      onDescriptionChange = viewModel::onDescriptionChange,
      onTypeChange = viewModel::onTypeChange,
      onAddressChange = viewModel::onAddressChange,
      onStartAtChange = viewModel::onStartAtChange,
      onEndAtChange = viewModel::onEndAtChange,
      onSubmit = viewModel::createEvent,
      onBack = onBack,
  )
}

/** Stateless form of [CreateEventScreen]: renders [state] and reports every interaction. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventContent(
    state: CreateEventUiState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onTypeChange: (EventType) -> Unit,
    onAddressChange: (String) -> Unit,
    onStartAtChange: (String) -> Unit,
    onEndAtChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.create_event_title)) },
            navigationIcon = {
              IconButton(
                  onClick = onBack,
                  modifier = Modifier.testTag(CreateEventScreenTestTags.BACK_BUTTON),
              ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.create_event_back),
                )
              }
            },
        )
      }
  ) { padding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      FormTextField(
          value = state.title,
          onValueChange = onTitleChange,
          label = R.string.create_event_field_title,
          field = EventFormField.TITLE,
          state = state,
          testTag = CreateEventScreenTestTags.TITLE_FIELD,
      )
      FormTextField(
          value = state.description,
          onValueChange = onDescriptionChange,
          label = R.string.create_event_field_description,
          field = EventFormField.DESCRIPTION,
          state = state,
          testTag = CreateEventScreenTestTags.DESCRIPTION_FIELD,
          singleLine = false,
      )
      TypeSelector(selected = state.type, error = state.errorFor(EventFormField.TYPE), onTypeChange)
      FormTextField(
          value = state.address,
          onValueChange = onAddressChange,
          label = R.string.create_event_field_address,
          field = EventFormField.ADDRESS,
          state = state,
          testTag = CreateEventScreenTestTags.ADDRESS_FIELD,
      )
      FormTextField(
          value = state.startAt,
          onValueChange = onStartAtChange,
          label = R.string.create_event_field_start,
          field = EventFormField.START,
          state = state,
          testTag = CreateEventScreenTestTags.START_FIELD,
          isDateTime = true,
      )
      FormTextField(
          value = state.endAt,
          onValueChange = onEndAtChange,
          label = R.string.create_event_field_end,
          field = EventFormField.END,
          state = state,
          testTag = CreateEventScreenTestTags.END_FIELD,
          isDateTime = true,
      )

      if (state.saveFailed) {
        Text(
            text = stringResource(R.string.create_event_error_save_failed),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(CreateEventScreenTestTags.SAVE_ERROR),
        )
      }

      Button(
          onClick = onSubmit,
          enabled = !state.isSaving,
          modifier =
              Modifier.fillMaxWidth()
                  .height(52.dp)
                  .testTag(CreateEventScreenTestTags.SUBMIT_BUTTON),
      ) {
        if (state.isSaving) {
          CircularProgressIndicator(
              modifier = Modifier.size(24.dp).testTag(CreateEventScreenTestTags.SAVING_INDICATOR),
              strokeWidth = 2.dp,
          )
        } else {
          Text(stringResource(R.string.create_event_submit))
        }
      }
    }
  }
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    field: EventFormField,
    state: CreateEventUiState,
    testTag: String,
    singleLine: Boolean = true,
    isDateTime: Boolean = false,
) {
  val error = state.errorFor(field)
  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      label = { Text(stringResource(label)) },
      placeholder =
          if (isDateTime) {
            { Text(stringResource(R.string.create_event_date_time_hint)) }
          } else null,
      isError = error != null,
      supportingText =
          error?.let {
            {
              Text(
                  text = stringResource(it.message),
                  modifier = Modifier.testTag(CreateEventScreenTestTags.error(field)),
              )
            }
          },
      singleLine = singleLine,
      minLines = if (singleLine) 1 else 3,
      enabled = !state.isSaving,
      keyboardOptions =
          KeyboardOptions(imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
      modifier = Modifier.fillMaxWidth().testTag(testTag),
  )
}

@Composable
private fun TypeSelector(
    selected: EventType?,
    error: EventFormError?,
    onTypeChange: (EventType) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
        text = stringResource(R.string.create_event_field_type),
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      EventType.entries.forEach { type ->
        FilterChip(
            selected = type == selected,
            onClick = { onTypeChange(type) },
            label = { Text(stringResource(type.label)) },
            modifier = Modifier.testTag(CreateEventScreenTestTags.typeChip(type)),
        )
      }
    }
    if (error != null) {
      Text(
          text = stringResource(error.message),
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.testTag(CreateEventScreenTestTags.TYPE_ERROR),
      )
    }
  }
}

@get:StringRes
private val EventFormError.message: Int
  get() =
      when (this) {
        EventFormError.TITLE_EMPTY -> R.string.create_event_error_title_empty
        EventFormError.DESCRIPTION_EMPTY -> R.string.create_event_error_description_empty
        EventFormError.TYPE_MISSING -> R.string.create_event_error_type_missing
        EventFormError.ADDRESS_EMPTY -> R.string.create_event_error_address_empty
        EventFormError.START_INVALID,
        EventFormError.END_INVALID -> R.string.create_event_error_date_invalid
        EventFormError.START_IN_PAST -> R.string.create_event_error_start_in_past
        EventFormError.END_NOT_AFTER_START -> R.string.create_event_error_end_not_after_start
      }

@get:StringRes
private val EventType.label: Int
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

@Preview(showBackground = true)
@Composable
private fun CreateEventContentPreview() {
  ShifterTheme {
    CreateEventContent(
        state =
            CreateEventUiState(
                title = "Paléo Festival",
                errors = setOf(EventFormError.TYPE_MISSING, EventFormError.START_INVALID),
            ),
        onTitleChange = {},
        onDescriptionChange = {},
        onTypeChange = {},
        onAddressChange = {},
        onStartAtChange = {},
        onEndAtChange = {},
        onSubmit = {},
        onBack = {},
    )
  }
}
