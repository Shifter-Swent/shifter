// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swent.shifter.R
import com.swent.shifter.model.location.GeoPosition

/**
 * A [LocationPicker] over the whole screen, for a form whose own scrolling would fight the map's
 * gestures: a close button, "Use this place", and the map. The mission and the event forms share
 * it, so picking a place works the same in both.
 *
 * It keeps no state: [draft] is the point picked so far, kept by the caller's ViewModel so it
 * survives a configuration change, and tapping the map reports a new one through [onDraftChange].
 * "Use this place" is disabled while there is no point, so nothing can be confirmed without one;
 * closing the dialog, with the button or by going back, calls [onCancel] and is the caller's cue to
 * leave its coordinates as they were. The test tags are the caller's too, so each form's tests and
 * Testing board keep their own names.
 *
 * @param initialCenter where the map opens with no [draft], or else the app's default place.
 */
@Composable
fun LocationPickerDialog(
    draft: GeoPosition?,
    onDraftChange: (GeoPosition) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    dialogTestTag: String,
    closeTestTag: String,
    confirmTestTag: String,
    initialCenter: GeoPosition? = null,
) {
  Dialog(
      onDismissRequest = onCancel,
      properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(modifier = Modifier.fillMaxSize().testTag(dialogTestTag)) {
      Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(onClick = onCancel, modifier = Modifier.testTag(closeTestTag)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.map_picker_close),
            )
          }
          Text(
              text = stringResource(R.string.map_picker_title),
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.weight(1f),
          )
          TextButton(
              onClick = onConfirm,
              enabled = draft != null,
              modifier = Modifier.testTag(confirmTestTag),
          ) {
            Text(stringResource(R.string.map_picker_confirm))
          }
        }
        LocationPicker(
            selected = draft,
            onSelect = onDraftChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
            initialCenter = initialCenter,
        )
      }
    }
  }
}
