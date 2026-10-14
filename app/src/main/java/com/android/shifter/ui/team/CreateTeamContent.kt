// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.team

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.ui.theme.ShifterTheme

/** Every edit the team creation form reports, grouped so the form takes a single parameter. */
data class CreateTeamActions(
    val onNameChange: (String) -> Unit = {},
    val onIconChange: (String) -> Unit = {},
    val onDescriptionChange: (String) -> Unit = {},
)

/**
 * The team creation form of the designs: the name of the team and its icon on one row, a short
 * description below, in a single card.
 *
 * Stateless, and only the form: it renders [state] and reports every edit through [actions], so the
 * screen that hosts it keeps its own header and its own buttons and stays the one that talks to
 * [CreateTeamViewModel].
 */
@Composable
fun CreateTeamContent(
    state: CreateTeamUiState,
    actions: CreateTeamActions,
    modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  // 16dp in the designs, as `large` is; the fields inside use `small`, which is their 8dp.
  val shape = MaterialTheme.shapes.large
  // Nothing is edited while the team is being saved, so the form cannot drift from what is sent.
  val editable = !state.isSaving
  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(shape)
              .background(colorScheme.surfaceVariant)
              .border(1.dp, colorScheme.outlineVariant, shape)
              .padding(12.dp)
              .testTag(CreateTeamFormTestTags.FORM),
      verticalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      LabeledField(R.string.create_team_field_name, modifier = Modifier.weight(1f)) {
        FormTextField(
            value = state.name,
            onValueChange = actions.onNameChange,
            label = R.string.create_team_field_name,
            imeAction = ImeAction.Next,
            testTag = CreateTeamFormTestTags.NAME_FIELD,
            error = state.errorFor(TeamFormField.NAME),
            enabled = editable,
        )
      }
      LabeledField(R.string.create_team_field_icon) {
        IconSelector(
            selected = TeamIcon.fromId(state.icon),
            onIconChange = actions.onIconChange,
            error = state.errorFor(TeamFormField.ICON),
            enabled = editable,
        )
      }
    }
    // Under the row rather than under the selector: its 72dp are too narrow for the message.
    FieldError(state.errorFor(TeamFormField.ICON))
    LabeledField(R.string.create_team_field_description) {
      FormTextField(
          value = state.description,
          onValueChange = actions.onDescriptionChange,
          label = R.string.create_team_field_description,
          imeAction = ImeAction.Default,
          testTag = CreateTeamFormTestTags.DESCRIPTION_FIELD,
          error = state.errorFor(TeamFormField.DESCRIPTION),
          enabled = editable,
          singleLine = false,
      )
    }
  }
}

/**
 * What is wrong with a field, under it, or nothing while it is valid. The form only ever holds
 * errors once the organizer has tried to submit, which [CreateTeamViewModel] decides.
 */
@Composable
private fun FieldError(error: TeamFormError?) {
  if (error == null) return
  Text(
      text = errorMessage(error),
      style = FORM_LABEL_STYLE,
      color = MaterialTheme.colorScheme.error,
      modifier = Modifier.testTag(CreateTeamFormTestTags.error(error.field)),
  )
}

/** The message of [error], with the limit it is about when it has one. */
@Composable
private fun errorMessage(error: TeamFormError): String =
    when (error) {
      TeamFormError.NAME_EMPTY -> stringResource(R.string.create_team_error_name_empty)
      TeamFormError.NAME_TOO_LONG ->
          stringResource(
              R.string.create_team_error_name_too_long,
              CreateTeamViewModel.NAME_MAX_LENGTH,
          )
      TeamFormError.ICON_MISSING -> stringResource(R.string.create_team_error_icon_missing)
      TeamFormError.DESCRIPTION_EMPTY ->
          stringResource(R.string.create_team_error_description_empty)
      TeamFormError.DESCRIPTION_TOO_LONG ->
          stringResource(
              R.string.create_team_error_description_too_long,
              CreateTeamViewModel.DESCRIPTION_MAX_LENGTH,
          )
    }

/** A field of the form: its label, then the field itself. */
@Composable
private fun LabeledField(
    @StringRes label: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
        text = stringResource(label),
        style = FORM_LABEL_STYLE,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    content()
  }
}

/**
 * The 11sp regular of the designs, which labels the fields and, in red, says what is wrong with
 * one. `labelSmall` is the 11sp of the theme, but medium and letter-spaced.
 */
private val FORM_LABEL_STYLE: TextStyle
  @Composable
  get() =
      MaterialTheme.typography.labelSmall.copy(
          fontWeight = FontWeight.Normal,
          letterSpacing = 0.sp,
      )

/**
 * A text field of the form, in the compact dark box of the designs. A [BasicTextField] rather than
 * an `OutlinedTextField`, whose 56dp minimum is taller than that box and would break the row the
 * name shares with the icon.
 */
@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    imeAction: ImeAction,
    testTag: String,
    error: TeamFormError?,
    enabled: Boolean,
    singleLine: Boolean = true,
) {
  val colorScheme = MaterialTheme.colorScheme
  val shape = MaterialTheme.shapes.small
  val interactionSource = remember { MutableInteractionSource() }
  val focused by interactionSource.collectIsFocusedAsState()
  val labelText = stringResource(label)
  val errorText = error?.let { errorMessage(it) }
  // The name is the 14sp medium of the designs, the description their smaller 12sp regular.
  val textStyle =
      if (singleLine) {
        MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
        )
      } else {
        MaterialTheme.typography.bodySmall.copy(
            lineHeight = 17.sp,
            color = colorScheme.onSurface,
        )
      }
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else DESCRIPTION_MAX_LINES,
        textStyle = textStyle,
        cursorBrush = SolidColor(colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        interactionSource = interactionSource,
        // Unlike OutlinedTextField, BasicTextField exposes neither its label nor its error.
        modifier =
            Modifier.fillMaxWidth().testTag(testTag).semantics {
              contentDescription = labelText
              if (errorText != null) error(errorText)
            },
        decorationBox = { innerTextField ->
          Box(
              modifier =
                  Modifier.fillMaxWidth()
                      .then(
                          // The name keeps the height of the row; the description grows with its
                          // lines, starting from the single line the designs draw.
                          if (singleLine) Modifier.height(ROW_FIELD_HEIGHT)
                          else Modifier.heightIn(min = DESCRIPTION_MIN_HEIGHT)
                      )
                      .clip(shape)
                      .background(colorScheme.background)
                      .border(1.dp, borderOf(error != null, focused), shape)
                      .padding(horizontal = 10.dp, vertical = if (singleLine) 0.dp else 8.dp),
              contentAlignment = Alignment.CenterStart,
          ) {
            innerTextField()
          }
        },
    )
    FieldError(error)
  }
}

/** The border of a field: red when it is wrong, blue while it is being typed in, else plain. */
@Composable
private fun borderOf(hasError: Boolean, focused: Boolean): Color {
  val colorScheme = MaterialTheme.colorScheme
  return when {
    hasError -> colorScheme.error
    focused -> colorScheme.primary
    else -> colorScheme.outline
  }
}

/**
 * The icon of the team: the chosen glyph next to a chevron, which open the catalogue of [TeamIcon].
 * Nothing is drawn next to the chevron until an icon is chosen.
 */
@Composable
private fun IconSelector(
    selected: TeamIcon?,
    onIconChange: (String) -> Unit,
    error: TeamFormError?,
    enabled: Boolean,
) {
  val colorScheme = MaterialTheme.colorScheme
  val shape = MaterialTheme.shapes.small
  var expanded by rememberSaveable { mutableStateOf(false) }
  val description =
      selected?.let { stringResource(R.string.create_team_icon_selected, stringResource(it.label)) }
          ?: stringResource(R.string.create_team_icon_none)
  val errorText = error?.let { errorMessage(it) }
  Box {
    Row(
        modifier =
            Modifier.width(SELECTOR_WIDTH)
                .height(ROW_FIELD_HEIGHT)
                .clip(shape)
                .background(colorScheme.primaryContainer)
                // Blue as the designs draw it, red only once it is what blocks the submission.
                .border(
                    1.dp,
                    if (error != null) colorScheme.error else colorScheme.primary,
                    shape,
                )
                .clickable(enabled = enabled, role = Role.DropdownList) { expanded = true }
                .testTag(CreateTeamFormTestTags.ICON_SELECTOR)
                .semantics {
                  contentDescription = description
                  if (errorText != null) error(errorText)
                },
        // The glyph, the chevron and their gap are centred in the width of the designs.
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      if (selected != null) {
        Icon(
            painter = painterResource(selected.drawable),
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(GLYPH_SIZE),
        )
      }
      Icon(
          painter = painterResource(R.drawable.ic_chevron_down),
          contentDescription = null,
          tint = colorScheme.onSurfaceVariant,
          modifier = Modifier.size(12.dp),
      )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        shape = MaterialTheme.shapes.medium,
        containerColor = colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
      TeamIcon.entries.forEach { icon ->
        val chosen = icon == selected
        DropdownMenuItem(
            text = {
              Text(text = stringResource(icon.label), style = MaterialTheme.typography.bodyMedium)
            },
            onClick = {
              onIconChange(icon.id)
              expanded = false
            },
            leadingIcon = {
              Icon(
                  painter = painterResource(icon.drawable),
                  contentDescription = null,
                  modifier = Modifier.size(GLYPH_SIZE),
              )
            },
            colors =
                MenuDefaults.itemColors(
                    textColor = if (chosen) colorScheme.primary else colorScheme.onSurface,
                    leadingIconColor =
                        if (chosen) colorScheme.primary else colorScheme.onSurfaceVariant,
                ),
            // The colour is the only visual sign of the chosen entry, so it is said out loud too,
            // which is also what lets a test tell which entry is highlighted.
            modifier =
                Modifier.testTag(CreateTeamFormTestTags.iconOption(icon)).semantics {
                  // Qualified, as the icon the selector shows is `selected` here too.
                  this.selected = chosen
                },
        )
      }
    }
  }
}

/** Height of the name field and of the icon selector, which the designs keep on one row. */
private val ROW_FIELD_HEIGHT = 36.dp

/** Width of the icon selector, around the glyph and the chevron of the designs. */
private val SELECTOR_WIDTH = 72.dp

/** Size of a team glyph, in the selector and in the menu it opens. */
private val GLYPH_SIZE = 20.dp

/** One line of description between the 8dp the designs pad it with, vertically. */
private val DESCRIPTION_MIN_HEIGHT = 33.dp

/** The form asks for a short description: past this it scrolls instead of growing the card. */
private const val DESCRIPTION_MAX_LINES = 4

@Preview(showBackground = true, backgroundColor = 0xFF151D27)
@Composable
private fun CreateTeamContentEmptyPreview() {
  ShifterTheme {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
      CreateTeamContent(state = CreateTeamUiState(), actions = CreateTeamActions())
    }
  }
}

/**
 * The form with a state of its own, so the interactive preview of Android Studio can type in both
 * fields and pick an icon. The preview plays the part [CreateTeamViewModel] holds in a screen,
 * without being one: it keeps the very [CreateTeamUiState] the ViewModel would expose.
 */
@Preview(showBackground = true, backgroundColor = 0xFF151D27)
@Composable
private fun CreateTeamContentInteractivePreview() {
  ShifterTheme {
    var state by remember {
      mutableStateOf(
          CreateTeamUiState(
              name = "Logistics",
              icon = TeamIcon.TRUCK.id,
              description = "Set up the site and bring in the equipment.",
          )
      )
    }
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
      CreateTeamContent(
          state = state,
          actions =
              CreateTeamActions(
                  onNameChange = { state = state.copy(name = it) },
                  onIconChange = { state = state.copy(icon = it) },
                  onDescriptionChange = { state = state.copy(description = it) },
              ),
      )
    }
  }
}
