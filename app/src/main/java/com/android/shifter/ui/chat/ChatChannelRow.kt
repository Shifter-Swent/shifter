// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.ui.theme.shifter_mutedText

/** The title of the team channels, so they read apart from the event's channels above them. */
@Composable
internal fun TeamsHeader() {
  Text(
      stringResource(R.string.chat_channels_teams),
      color = shifter_mutedText,
      style = MaterialTheme.typography.labelLarge,
      modifier =
          Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
              .testTag(ChatChannelsTestTags.TEAMS_HEADER),
  )
}

/** A channel named [name], with its [icon] if it has one, which [onClick] opens. */
@Composable
internal fun ChannelRow(name: String, icon: Painter?, tag: String, onClick: () -> Unit) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clickable(onClick = onClick)
              .padding(horizontal = 16.dp, vertical = 16.dp)
              .testTag(tag),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      Icon(
          icon,
          contentDescription = null,
          modifier = Modifier.size(24.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.width(16.dp))
    } else {
      // The width of the icon and its spacer, so every name starts at the same place.
      Spacer(Modifier.width(40.dp))
    }
    Text(
        name,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
  }
}
