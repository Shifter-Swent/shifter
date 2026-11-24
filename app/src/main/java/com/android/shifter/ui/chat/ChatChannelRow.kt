// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swent.shifter.ui.theme.shifter_avatarBackground
import com.swent.shifter.ui.theme.shifter_mutedText

/** The colours of a channel's tile: its [background] and its [icon]. */
internal data class ChannelTint(val background: Color, val icon: Color)

/** The title of a group of channels, so the event's and the teams' channels read apart. */
@Composable
internal fun GroupHeader(text: String, tag: String) {
  Text(
      text,
      color = shifter_mutedText,
      style = MaterialTheme.typography.labelLarge,
      modifier =
          Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)
              .semantics { heading() }
              .testTag(tag),
  )
}

/** The rounded tile of a channel, with its [icon] in the colours of [tint]. */
@Composable
private fun ChannelTile(icon: Painter, tint: ChannelTint) {
  Box(
      modifier = Modifier.size(44.dp).background(tint.background, RoundedCornerShape(12.dp)),
      contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = null, tint = tint.icon, modifier = Modifier.size(22.dp))
  }
}

/** A channel named [name], with its tile showing [icon] in [tint], which [onClick] opens. */
@Composable
internal fun ChannelRow(
    name: String,
    icon: Painter,
    tint: ChannelTint,
    tag: String,
    onClick: () -> Unit,
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clickable(role = Role.Button, onClick = onClick)
              .padding(horizontal = 16.dp, vertical = 10.dp)
              .testTag(tag),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    ChannelTile(icon, tint)
    Text(
        name,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
  }
}

/**
 * The card in place of the team channels when there is none: its [title], and its [text] saying
 * what will bring one.
 */
@Composable
internal fun NoTeamCard(@StringRes title: Int, @StringRes text: Int, tag: String) {
  Row(
      modifier =
          Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
              .fillMaxWidth()
              .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
              .padding(16.dp)
              // Read as one card by TalkBack: its title and its explanation together.
              .semantics(mergeDescendants = true) {}
              .testTag(tag),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    ChannelTile(
        rememberVectorPainter(Icons.Outlined.Groups),
        ChannelTint(shifter_avatarBackground, shifter_mutedText),
    )
    Column {
      Text(
          stringResource(title),
          color = MaterialTheme.colorScheme.onSurface,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
      )
      Text(
          stringResource(text),
          color = shifter_mutedText,
          style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}
