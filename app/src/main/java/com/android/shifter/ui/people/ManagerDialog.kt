// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.people

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.swent.shifter.R
import com.swent.shifter.model.team.Team

object ManagerDialogTestTags {
  const val DIALOG = "ManagerDialog"
  const val CONFIRM_BUTTON = "ManagerDialogConfirmButton"
  const val CANCEL_BUTTON = "ManagerDialogCancelButton"
}

/**
 * Asks the organizer to confirm a change of [team]'s manager: appointing [participant], a member,
 * or removing them when they are its manager. Stateless: [onConfirm] and [onDismiss] only report
 * the choice.
 *
 * @param currentManager the team's manager when [participant] is a member, who then goes back to
 *   the team; null when it has none.
 */
@Composable
fun ManagerDialog(
    participant: ParticipantItem,
    team: Team,
    currentManager: ParticipantItem?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
  val name = participant.displayName()
  val title: String
  val message: String
  if (participant.isManager) {
    title = stringResource(R.string.participants_remove_manager_title, name)
    message = stringResource(R.string.participants_remove_manager_message, name, team.name)
  } else {
    title = stringResource(R.string.participants_appoint_manager_title, name)
    message =
        if (currentManager == null) {
          stringResource(R.string.participants_appoint_manager_message, name, team.name)
        } else {
          stringResource(
              R.string.participants_replace_manager_message,
              name,
              team.name,
              currentManager.displayName(),
          )
        }
  }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(title) },
      text = { Text(message) },
      confirmButton = {
        TextButton(
            onClick = onConfirm,
            modifier = Modifier.testTag(ManagerDialogTestTags.CONFIRM_BUTTON),
        ) {
          Text(
              stringResource(R.string.participants_manager_confirm),
              fontWeight = FontWeight.SemiBold,
          )
        }
      },
      dismissButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag(ManagerDialogTestTags.CANCEL_BUTTON),
        ) {
          Text(stringResource(R.string.participants_manager_cancel))
        }
      },
      modifier = Modifier.testTag(ManagerDialogTestTags.DIALOG),
  )
}

@Composable
private fun ParticipantItem.displayName(): String =
    name ?: stringResource(R.string.participants_unknown_volunteer)
