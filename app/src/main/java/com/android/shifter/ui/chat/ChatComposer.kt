// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.ui.theme.shifter_announcement

@Composable
internal fun Input(
    state: TeamChatUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onAnnouncementChange: (Boolean) -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    if (state.canAnnounce) {
      IconToggleButton(
          checked = state.asAnnouncement,
          onCheckedChange = onAnnouncementChange,
          colors =
              IconButtonDefaults.iconToggleButtonColors(checkedContentColor = shifter_announcement),
          modifier = Modifier.testTag(TeamChatTestTags.ANNOUNCEMENT_TOGGLE),
      ) {
        Icon(
            Icons.Filled.Campaign,
            contentDescription = stringResource(R.string.chat_send_as_announcement),
        )
      }
    }
    val placeholder =
        if (state.asAnnouncement) R.string.chat_announcement_placeholder
        else R.string.chat_input_placeholder
    OutlinedTextField(
        value = state.draft,
        onValueChange = onDraftChange,
        placeholder = { Text(stringResource(placeholder)) },
        maxLines = 4,
        modifier = Modifier.weight(1f).testTag(TeamChatTestTags.INPUT),
    )
    IconButton(
        onClick = onSend,
        enabled = state.canSend,
        modifier = Modifier.testTag(TeamChatTestTags.SEND_BUTTON),
    ) {
      Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send))
    }
  }
}

/** Why the last message was not sent, above the field that has its text back. */
@Composable
internal fun SendError(messageRes: Int, tag: String) {
  Text(
      text = stringResource(messageRes),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error,
      modifier = Modifier.padding(horizontal = 16.dp).testTag(tag),
  )
}
