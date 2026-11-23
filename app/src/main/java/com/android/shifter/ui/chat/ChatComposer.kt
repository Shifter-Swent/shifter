// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.ui.theme.md_theme_dark_surface
import com.swent.shifter.ui.theme.shifter_announcement
import com.swent.shifter.ui.theme.shifter_mutedText

/**
 * The field and its round send button, grey until there is text. Those who may announce get a
 * megaphone beside it: when it is on, the megaphone and the send button turn amber.
 */
@Composable
internal fun ChatComposer(
    state: TeamChatUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onAnnouncementChange: (Boolean) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val announcing = state.canAnnounce && state.asAnnouncement
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .background(colors.surfaceContainer)
              .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (state.canAnnounce) {
      FilledIconToggleButton(
          checked = state.asAnnouncement,
          onCheckedChange = onAnnouncementChange,
          shape = CircleShape,
          colors =
              IconButtonDefaults.filledIconToggleButtonColors(
                  containerColor = colors.surfaceVariant,
                  contentColor = shifter_mutedText,
                  checkedContainerColor = shifter_announcement,
                  checkedContentColor = md_theme_dark_surface,
              ),
          modifier = Modifier.size(44.dp).testTag(TeamChatTestTags.ANNOUNCEMENT_TOGGLE),
      ) {
        Icon(
            Icons.Filled.Campaign,
            contentDescription = stringResource(R.string.chat_send_as_announcement),
        )
      }
    }
    val placeholder =
        if (announcing) R.string.chat_announcement_placeholder else R.string.chat_input_placeholder
    TextField(
        value = state.draft,
        onValueChange = onDraftChange,
        placeholder = { Text(stringResource(placeholder), color = shifter_mutedText) },
        maxLines = 4,
        shape = RoundedCornerShape(24.dp),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceVariant,
                unfocusedContainerColor = colors.surfaceVariant,
                disabledContainerColor = colors.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        modifier = Modifier.weight(1f).testTag(TeamChatTestTags.INPUT),
    )
    FilledIconButton(
        onClick = onSend,
        enabled = state.canSend,
        shape = CircleShape,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = if (announcing) shifter_announcement else colors.primary,
                contentColor = if (announcing) md_theme_dark_surface else colors.onPrimary,
                disabledContainerColor = colors.surfaceVariant,
                disabledContentColor = shifter_mutedText,
            ),
        modifier = Modifier.size(48.dp).testTag(TeamChatTestTags.SEND_BUTTON),
    ) {
      Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send))
    }
  }
}

/** A red strip above the field that has its text back: why the last message was not sent. */
@Composable
internal fun SendError(messageRes: Int, tag: String) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .background(MaterialTheme.colorScheme.errorContainer)
              .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(
        Icons.Outlined.ErrorOutline,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(18.dp),
    )
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.weight(1f).testTag(tag),
    )
  }
}
