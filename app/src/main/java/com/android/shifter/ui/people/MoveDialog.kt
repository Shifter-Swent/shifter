// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.model.team.Team

object MoveDialogTestTags {
  const val DIALOG = "MoveDialog"
  const val CONFIRM_BUTTON = "MoveDialogConfirmButton"
  const val CANCEL_BUTTON = "MoveDialogCancelButton"
  const val NO_OTHER_TEAM = "MoveDialogNoOtherTeam"

  fun teamOption(teamId: String) = "MoveDialogTeam_$teamId"
}

/**
 * Asks the organizer which team [participant] moves to, among [teams] other than [from], their
 * current one, or null when they have none. Stateless but for the team picked, which [onConfirm]
 * reports; [onDismiss] reports a cancel.
 *
 * The pick belongs to [participant], and only counts while its team is still offered: a reload that
 * takes it away leaves nothing to confirm.
 */
@Composable
fun MoveDialog(
    participant: ParticipantItem,
    from: Team?,
    teams: List<Team>,
    onConfirm: (teamId: String) -> Unit,
    onDismiss: () -> Unit,
) {
  val name = participant.name ?: stringResource(R.string.participants_unknown_volunteer)
  val options = teams.filter { it.id != from?.id }
  var picked by rememberSaveable(participant.userId) { mutableStateOf<String?>(null) }
  val selected = picked?.takeIf { id -> options.any { it.id == id } }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = {
        Text(
            if (from == null) stringResource(R.string.participants_add_title, name)
            else stringResource(R.string.participants_move_title, name)
        )
      },
      text = {
        // Scrolls, so the last teams of a long list stay reachable while the buttons stay put.
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
          from?.let { Text(stringResource(R.string.participants_move_message, name, it.name)) }
          if (options.isEmpty()) {
            Text(
                text = stringResource(R.string.participants_move_no_other_team),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(MoveDialogTestTags.NO_OTHER_TEAM),
            )
          }
          Column(Modifier.selectableGroup()) {
            options.forEach { team ->
              Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier =
                      Modifier.fillMaxWidth()
                          .clip(RoundedCornerShape(12.dp))
                          .selectable(
                              selected = selected == team.id,
                              role = Role.RadioButton,
                              onClick = { picked = team.id },
                          )
                          .padding(vertical = 4.dp)
                          .testTag(MoveDialogTestTags.teamOption(team.id)),
              ) {
                RadioButton(selected = selected == team.id, onClick = null)
                Text(
                    text = team.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(
            onClick = { selected?.let(onConfirm) },
            enabled = selected != null,
            modifier = Modifier.testTag(MoveDialogTestTags.CONFIRM_BUTTON),
        ) {
          Text(stringResource(R.string.participants_move_confirm), fontWeight = FontWeight.SemiBold)
        }
      },
      dismissButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag(MoveDialogTestTags.CANCEL_BUTTON),
        ) {
          Text(stringResource(R.string.dialog_cancel))
        }
      },
      modifier = Modifier.testTag(MoveDialogTestTags.DIALOG),
  )
}
