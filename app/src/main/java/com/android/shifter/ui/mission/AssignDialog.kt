// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.mission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swent.shifter.R

/** Test tags of [AssignDialog], the same on every screen that shows it. */
object AssignDialogTestTags {
  const val DIALOG = "AssignDialog"
  const val SAVE = "AssignDialogSave"
  const val CANCEL = "AssignDialogCancel"
  const val ERROR = "AssignDialogError"
  const val OVER_LIMIT = "AssignDialogOverLimit"
}

/**
 * Who is being assigned to the mission [missionId], while the organizer edits it.
 *
 * @property limit the most people that can be picked, the mission's volunteers needed, as last
 *   known: a refused save sets it to what the stored mission needs.
 * @property isSaving whether the picked people are being stored.
 * @property saveFailed whether storing them failed; the selection is kept to try again.
 */
data class AssignState(
    val missionId: String,
    val title: String,
    val limit: Int,
    val selection: AssigneeSelection,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
) {
  /**
   * Whether more people are picked than the mission needs now, after its volunteers needed were
   * lowered: nothing can be saved until some are unpicked. Derived rather than stored, so it holds
   * until the selection fits, however many are unpicked.
   */
  val overLimit: Boolean
    get() = selection.selectedIds.size > limit
}

/**
 * Edits who is assigned to the mission of [assigning]; nothing is stored until Save.
 *
 * Stateless: it shows [assigning] and reports each tap on a candidate through [onToggle], with the
 * candidate's id, a retry of the candidates through [onRetry], Save through [onSave], and Cancel or
 * a tap outside through [onDismiss].
 */
@Composable
internal fun AssignDialog(
    assigning: AssignState,
    onToggle: (String) -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
  AlertDialog(
      // A save cannot be taken back: the dialog stays open until it ends.
      onDismissRequest = { if (!assigning.isSaving) onDismiss() },
      modifier = Modifier.testTag(AssignDialogTestTags.DIALOG),
      title = { Text(assigning.title) },
      text = {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          AssigneePicker(
              selection = assigning.selection,
              limit = assigning.limit,
              enabled = !assigning.isSaving,
              onToggle = onToggle,
              onRetry = onRetry,
          )
          // Shown as long as too many are picked, not only after the refused save, so it does not
          // vanish while Save stays off.
          if (assigning.overLimit) {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.assign_dialog_over_limit,
                        assigning.limit,
                        assigning.limit,
                    ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(AssignDialogTestTags.OVER_LIMIT),
            )
          }
          if (assigning.saveFailed) {
            Text(
                text = stringResource(R.string.assign_dialog_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(AssignDialogTestTags.ERROR),
            )
          }
        }
      },
      confirmButton = {
        TextButton(
            onClick = onSave,
            // Saving before the candidates are loaded could not show who is picked, and too many
            // picked would be refused again.
            enabled =
                !assigning.isSaving &&
                    !assigning.selection.isLoading &&
                    !assigning.selection.loadFailed &&
                    !assigning.overLimit,
            modifier = Modifier.testTag(AssignDialogTestTags.SAVE),
        ) {
          Text(stringResource(R.string.assign_dialog_save))
        }
      },
      dismissButton = {
        TextButton(
            onClick = onDismiss,
            enabled = !assigning.isSaving,
            modifier = Modifier.testTag(AssignDialogTestTags.CANCEL),
        ) {
          Text(stringResource(R.string.assign_dialog_cancel))
        }
      },
  )
}
