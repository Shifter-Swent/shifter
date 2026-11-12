// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.ui.team.TeamIcon
import com.swent.shifter.ui.theme.shifter_divider
import com.swent.shifter.ui.theme.shifter_mutedText

object ChatChannelsTestTags {
  const val LOADING = "chatChannelsLoading"
  const val ERROR = "chatChannelsError"
  const val RETRY_BUTTON = "chatChannelsRetryButton"
  const val EMPTY = "chatChannelsEmpty"
  const val LIST = "chatChannelsList"
  const val GENERAL = "chatChannelGeneral"
  const val ADMIN = "chatChannelAdmin"
  const val TEAMS_HEADER = "chatChannelsTeamsHeader"

  fun channel(teamId: String) = "chatChannel_$teamId"
}

/** Which chat channel of an event is opened: a team's, or one of the whole event. */
enum class ChannelKind {
  TEAM,
  GENERAL,
  ADMIN,
}

/**
 * Opens the channel of [kind], of the team [teamId] for [ChannelKind.TEAM] (null otherwise), under
 * the title [name].
 */
typealias OpenChannel = (kind: ChannelKind, teamId: String?, name: String) -> Unit

/** The channels of an event listed by [viewModel], which [onOpenChannel] opens. */
@Composable
fun ChatChannelsScreen(
    viewModel: ChatChannelsViewModel,
    onOpenChannel: OpenChannel,
    modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsState()
  ChatChannelsContent(uiState, onOpenChannel, viewModel::load, modifier)
}

/**
 * The event's general channel, its admin channel for those who may open it, then the team channels
 * the user can open. With no team channel, it says why under the team header: a volunteer is in no
 * team yet, or the organizer has not created any. The list is always shown, even with a single team
 * channel, as the general channel is always there too.
 */
@Composable
fun ChatChannelsContent(
    state: ChatChannelsUiState,
    onOpenChannel: OpenChannel,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    when {
      state.isLoading ->
          CircularProgressIndicator(modifier = Modifier.testTag(ChatChannelsTestTags.LOADING))
      state.error != null ->
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Message(
                if (state.error == ChatError.OFFLINE) R.string.chat_channels_error_offline
                else R.string.chat_channels_error_unexpected,
                ChatChannelsTestTags.ERROR,
            )
            TextButton(
                onClick = onRetry,
                modifier = Modifier.testTag(ChatChannelsTestTags.RETRY_BUTTON),
            ) {
              Text(stringResource(R.string.chat_retry), fontWeight = FontWeight.SemiBold)
            }
          }
      else -> {
        val general = stringResource(R.string.chat_channel_general)
        val admin = stringResource(R.string.chat_channel_admin)
        LazyColumn(modifier = Modifier.fillMaxSize().testTag(ChatChannelsTestTags.LIST)) {
          item(key = ChatChannelsTestTags.GENERAL) {
            ChannelRow(
                general,
                rememberVectorPainter(Icons.Outlined.Groups),
                ChatChannelsTestTags.GENERAL,
            ) {
              onOpenChannel(ChannelKind.GENERAL, null, general)
            }
            HorizontalDivider(color = shifter_divider)
          }
          if (state.showsAdmin) {
            item(key = ChatChannelsTestTags.ADMIN) {
              ChannelRow(
                  admin,
                  rememberVectorPainter(Icons.Outlined.Lock),
                  ChatChannelsTestTags.ADMIN,
              ) {
                onOpenChannel(ChannelKind.ADMIN, null, admin)
              }
              HorizontalDivider(color = shifter_divider)
            }
          }
          item(key = ChatChannelsTestTags.TEAMS_HEADER) { TeamsHeader() }
          if (state.channels.isEmpty()) {
            item(key = ChatChannelsTestTags.EMPTY) {
              Message(
                  if (state.asOrganizer) R.string.chat_channels_empty_organizer
                  else R.string.chat_channels_empty_volunteer,
                  ChatChannelsTestTags.EMPTY,
              )
            }
          }
          items(state.channels, key = { it.teamId }) { channel ->
            ChannelRow(
                channel.name,
                TeamIcon.fromId(channel.icon)?.let { painterResource(it.drawable) },
                ChatChannelsTestTags.channel(channel.teamId),
            ) {
              onOpenChannel(ChannelKind.TEAM, channel.teamId, channel.name)
            }
            HorizontalDivider(color = shifter_divider)
          }
        }
      }
    }
  }
}

/** The title of the team channels, so they read apart from the event's channels above them. */
@Composable
private fun TeamsHeader() {
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
private fun ChannelRow(name: String, icon: Painter?, tag: String, onClick: () -> Unit) {
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

@Composable
private fun Message(@StringRes text: Int, tag: String) {
  Text(
      text = stringResource(text),
      color = shifter_mutedText,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 24.dp).testTag(tag),
  )
}
