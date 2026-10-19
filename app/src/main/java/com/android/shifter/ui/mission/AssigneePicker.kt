// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.mission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.ui.components.FieldLabel
import com.swent.shifter.ui.theme.shifter_mutedText

object AssigneePickerTestTags {
  const val COUNT = "AssigneePickerCount"
  const val LOADING = "AssigneePickerLoading"
  const val LOAD_ERROR = "AssigneePickerLoadError"
  const val RETRY = "AssigneePickerRetry"
  const val EMPTY = "AssigneePickerEmpty"

  fun option(id: String) = "AssigneePickerOption_$id"
}

/**
 * Lets the organizer or a manager pick who is assigned to a mission, at most [limit] people.
 *
 * Stateless: it shows [selection] and reports each tap on a candidate through [onToggle], with the
 * candidate's id.
 */
@Composable
fun AssigneePicker(
    selection: AssigneeSelection,
    limit: Int,
    enabled: Boolean,
    onToggle: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        FieldLabel(stringResource(R.string.assignee_picker_label))
      }
      Text(
          text = stringResource(R.string.assignee_picker_count, selection.selectedIds.size, limit),
          style = MaterialTheme.typography.labelSmall,
          color = shifter_mutedText,
          modifier = Modifier.testTag(AssigneePickerTestTags.COUNT),
      )
    }
    when {
      selection.isLoading ->
          CircularProgressIndicator(
              modifier = Modifier.size(20.dp).testTag(AssigneePickerTestTags.LOADING),
              strokeWidth = 2.dp,
          )
      selection.loadFailed ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.assignee_picker_load_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).testTag(AssigneePickerTestTags.LOAD_ERROR),
            )
            TextButton(
                onClick = onRetry,
                modifier = Modifier.testTag(AssigneePickerTestTags.RETRY),
            ) {
              Text(stringResource(R.string.assignee_picker_retry))
            }
          }
      selection.candidates.isEmpty() ->
          Text(
              text = stringResource(R.string.assignee_picker_empty),
              style = MaterialTheme.typography.bodySmall,
              color = shifter_mutedText,
              modifier = Modifier.testTag(AssigneePickerTestTags.EMPTY),
          )
      else ->
          selection.candidates.forEach { candidate ->
            val selected = candidate.id in selection.selectedIds
            CandidateRow(
                name = candidate.name ?: stringResource(R.string.assignee_picker_unknown),
                selected = selected,
                // Once full, only the people already picked can be tapped, to unpick them.
                enabled = enabled && (selected || selection.selectedIds.size < limit),
                onToggle = { onToggle(candidate.id) },
                testTag = AssigneePickerTestTags.option(candidate.id),
            )
          }
    }
  }
}

@Composable
private fun CandidateRow(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    testTag: String,
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .toggleable(
                  value = selected,
                  enabled = enabled,
                  role = Role.Checkbox,
                  onValueChange = { onToggle() },
              )
              .testTag(testTag),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    // The row handles the tap, so the box itself only shows the state.
    Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
    Text(
        text = name,
        style = MaterialTheme.typography.bodyMedium,
        color =
            if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    )
  }
}
