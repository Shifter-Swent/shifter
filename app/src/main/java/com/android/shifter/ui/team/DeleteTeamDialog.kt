// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.team

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.swent.shifter.R
import com.swent.shifter.model.team.Team

object DeleteTeamDialogTestTags {
  const val DIALOG = "DeleteTeamDialog"
  const val CONFIRM_BUTTON = "DeleteTeamDialogConfirmButton"
  const val CANCEL_BUTTON = "DeleteTeamDialogCancelButton"
}

/**
 * Asks the organizer to confirm the deletion of [team], saying what goes with it and what stays.
 * Stateless: [onConfirm] and [onDismiss] only report the choice.
 */
@Composable
fun DeleteTeamDialog(team: Team, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.team_setup_delete_title, team.name)) },
      text = { Text(stringResource(R.string.team_setup_delete_message)) },
      confirmButton = {
        TextButton(
            onClick = onConfirm,
            colors =
                ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.testTag(DeleteTeamDialogTestTags.CONFIRM_BUTTON),
        ) {
          Text(stringResource(R.string.team_setup_delete_confirm), fontWeight = FontWeight.SemiBold)
        }
      },
      dismissButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag(DeleteTeamDialogTestTags.CANCEL_BUTTON),
        ) {
          Text(stringResource(R.string.team_setup_delete_cancel))
        }
      },
      modifier = Modifier.testTag(DeleteTeamDialogTestTags.DIALOG),
  )
}
