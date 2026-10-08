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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.shifter_mutedText

object CreateEventScreenTestTags {
  const val BACK_BUTTON = "CreateEventBackButton"
  const val TITLE_FIELD = "CreateEventTitleField"
  const val DESCRIPTION_FIELD = "CreateEventDescriptionField"
  const val ADDRESS_FIELD = "CreateEventAddressField"
  const val START_FIELD = "CreateEventStartField"
  const val END_FIELD = "CreateEventEndField"
  const val SUBMIT_BUTTON = "CreateEventSubmitButton"
  const val SAVING_INDICATOR = "CreateEventSavingIndicator"
  const val SAVE_ERROR = "CreateEventSaveError"

  fun typeChip(type: EventType) = "CreateEventTypeChip_${type.name}"

  fun error(field: EventFormField) = "CreateEventError_${field.name}"
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
  Scaffold(topBar = { FormHeader(onBack) }) { padding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      Text(
          text = stringResource(R.string.create_event_intro),
          style = MaterialTheme.typography.bodySmall,
          color = shifter_mutedText,
      )
      FormTextField(
          value = state.title,
          onValueChange = onTitleChange,
          label = R.string.create_event_field_title,
          field = EventFormField.TITLE,
          state = state,
          testTag = CreateEventScreenTestTags.TITLE_FIELD,
      )
      TypeSelector(selected = state.type, error = state.errorFor(EventFormField.TYPE), onTypeChange)
      FormTextField(
          value = state.description,
          onValueChange = onDescriptionChange,
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
      FormTextField(
          value = state.address,
          onValueChange = onAddressChange,
          label = R.string.create_event_field_address,
          field = EventFormField.ADDRESS,
          state = state,
          testTag = CreateEventScreenTestTags.ADDRESS_FIELD,
      )
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      ActivityPeriod(state, onStartAtChange, onEndAtChange)

      if (state.saveFailed) {
        Text(
            text = stringResource(R.string.create_event_error_save_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag(CreateEventScreenTestTags.SAVE_ERROR),
        )
      }

      Button(
          onClick = onSubmit,
          enabled = !state.isSaving,
          shape = MaterialTheme.shapes.medium,
          modifier =
              Modifier.fillMaxWidth()
                  .height(48.dp)
                  .testTag(CreateEventScreenTestTags.SUBMIT_BUTTON),
      ) {
        if (state.isSaving) {
          CircularProgressIndicator(
              modifier = Modifier.size(24.dp).testTag(CreateEventScreenTestTags.SAVING_INDICATOR),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.dp,
          )
        } else {
          Text(
              text = stringResource(R.string.create_event_submit),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
  }
}

/** Return link to the organizer's events, above the screen title. */
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
                .testTag(CreateEventScreenTestTags.BACK_BUTTON),
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
        text = stringResource(R.string.create_event_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
  }
}

@Composable
private fun FieldLabel(text: String) {
  Text(
      text = text,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
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
) {
  val error = state.errorFor(field)
  val colorScheme = MaterialTheme.colorScheme
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    FieldLabel(stringResource(label))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
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
        minLines = if (singleLine) 1 else 2,
        enabled = !state.isSaving,
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
  }
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
      Text(
          text = labelText,
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.onSurfaceVariant,
      )
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
