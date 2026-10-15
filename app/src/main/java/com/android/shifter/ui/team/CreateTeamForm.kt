// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.model.team.Team
import com.swent.shifter.ui.components.FormPrimaryButton

/** Tags of the whole form: the card of [CreateTeamContent] and the actions under it. */
object CreateTeamFormTestTags {
  const val FORM = "CreateTeamForm"
  const val NAME_FIELD = "CreateTeamNameField"
  const val ICON_SELECTOR = "CreateTeamIconSelector"
  const val DESCRIPTION_FIELD = "CreateTeamDescriptionField"
  const val SUBMIT_BUTTON = "CreateTeamSubmitButton"
  const val CANCEL_BUTTON = "CreateTeamCancelButton"
  const val SAVING_INDICATOR = "CreateTeamSavingIndicator"
  const val SAVE_ERROR = "CreateTeamSaveError"

  fun iconOption(icon: TeamIcon) = "CreateTeamIconOption_${icon.id}"

  fun error(field: TeamFormField) = "CreateTeamError_${field.name}"
}

/**
 * Creates one team, as the card the organizer opens inside the teams of an event.
 *
 * Inline, not a page: no header, no scrolling of its own and no navigation, so the screen that
 * shows the teams can drop it under them and take it away again. It only says what happened through
 * [onTeamCreated] and [onCancel]; what to do about it is that screen's business.
 *
 * Everything the form decides — what is valid, when errors appear, whether it can still be
 * submitted — belongs to [viewModel], which the caller builds for the right event.
 *
 * @param onTeamCreated called once with the persisted team, even if this is recomposed or recreated
 *   afterwards. [viewModel] refuses to create a second one, so the caller gives each new team a
 *   fresh instance.
 * @param onCancel called when the organizer gives up on the team: the form is dismissed and nothing
 *   is saved.
 */
@Composable
fun CreateTeamForm(
    viewModel: CreateTeamViewModel,
    onTeamCreated: (Team) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsState()

  val createdTeam = uiState.createdTeam
  LaunchedEffect(createdTeam, uiState.createdTeamHandled) {
    if (createdTeam != null && !uiState.createdTeamHandled) {
      viewModel.onTeamCreatedHandled()
      onTeamCreated(createdTeam)
    }
  }

  CreateTeamFormContent(
      state = uiState,
      actions =
          CreateTeamActions(
              onNameChange = viewModel::onNameChange,
              onIconChange = viewModel::onIconChange,
              onDescriptionChange = viewModel::onDescriptionChange,
          ),
      onSubmit = viewModel::createTeam,
      onCancel = onCancel,
      modifier = modifier,
  )
}

/**
 * Stateless form of [CreateTeamForm]: the card of the designs, then the two actions that only the
 * parent around it owns.
 */
@Composable
fun CreateTeamFormContent(
    state: CreateTeamUiState,
    actions: CreateTeamActions,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    CreateTeamContent(state = state, actions = actions)

    // Above the actions, as the other forms of the app show why the last attempt failed.
    if (state.saveFailed) {
      Text(
          text = stringResource(R.string.create_team_error_save_failed),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag(CreateTeamFormTestTags.SAVE_ERROR),
      )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      OutlinedButton(
          onClick = onCancel,
          enabled = state.isEditable,
          shape = MaterialTheme.shapes.medium,
          modifier =
              Modifier.weight(1f)
                  .height(ACTION_HEIGHT)
                  .testTag(CreateTeamFormTestTags.CANCEL_BUTTON),
      ) {
        Text(
            text = stringResource(R.string.create_team_cancel),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
      }
      // FormPrimaryButton fills the width it is given, so the box is what gives it half the row.
      Box(modifier = Modifier.weight(1f)) {
        FormPrimaryButton(
            text = stringResource(R.string.create_team_submit),
            onClick = onSubmit,
            testTag = CreateTeamFormTestTags.SUBMIT_BUTTON,
            // False once the team exists, so a second tap cannot create it twice.
            enabled = state.canSubmit,
            isLoading = state.isSaving,
            loadingIndicatorTestTag = CreateTeamFormTestTags.SAVING_INDICATOR,
        )
      }
    }
  }
}

/** Height of both actions, which is the one [FormPrimaryButton] gives itself. */
private val ACTION_HEIGHT = 48.dp
