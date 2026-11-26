// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.model.chat.ChannelKind
import com.swent.shifter.ui.team.TeamIcon
import com.swent.shifter.ui.theme.shifter_avatarBackground
import com.swent.shifter.ui.theme.shifter_mutedText

object ChatChannelsTestTags {
  const val LOADING = "chatChannelsLoading"
  const val ERROR = "chatChannelsError"
  const val RETRY_BUTTON = "chatChannelsRetryButton"
  const val EMPTY = "chatChannelsEmpty"
  const val LIST = "chatChannelsList"
  const val TITLE = "chatChannelsTitle"
  const val EVENT_HEADER = "chatChannelsEventHeader"
  const val GENERAL = "chatChannelGeneral"
  const val ADMIN = "chatChannelAdmin"
  const val TEAMS_HEADER = "chatChannelsTeamsHeader"

  fun channel(teamId: String) = "chatChannel_$teamId"
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
 * The "Chat" title, then the event's channels under "Event": the general one, and the admin one for
 * those who may open it. Then the team channels the user can open, under their own header. With no
 * team channel, a card says why there: a volunteer is in no team yet, or the organizer has not
 * created any. The list is always shown, even with a single team channel, as the general channel is
 * always there too.
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
      else -> Channels(state, onOpenChannel)
    }
  }
}

@Composable
private fun Channels(state: ChatChannelsUiState, onOpenChannel: OpenChannel) {
  val general = stringResource(R.string.chat_channel_general)
  val admin = stringResource(R.string.chat_channel_admin)
  val colors = MaterialTheme.colorScheme
  LazyColumn(
      modifier = Modifier.fillMaxSize().testTag(ChatChannelsTestTags.LIST),
      contentPadding = PaddingValues(bottom = 16.dp),
  ) {
    item(key = ChatChannelsTestTags.TITLE) {
      Text(
          stringResource(R.string.chat_channels_title),
          color = colors.onSurface,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          modifier =
              Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)
                  .semantics { heading() }
                  .testTag(ChatChannelsTestTags.TITLE),
      )
    }
    item(key = ChatChannelsTestTags.EVENT_HEADER) {
      GroupHeader(
          stringResource(R.string.chat_channels_event),
          ChatChannelsTestTags.EVENT_HEADER,
      )
    }
    item(key = ChatChannelsTestTags.GENERAL) {
      ChannelRow(
          general,
          rememberVectorPainter(Icons.Outlined.Groups),
          ChannelTint(colors.primaryContainer, colors.primary),
          ChatChannelsTestTags.GENERAL,
      ) {
        onOpenChannel(ChannelKind.GENERAL, null, general)
      }
    }
    if (state.showsAdmin) {
      item(key = ChatChannelsTestTags.ADMIN) {
        ChannelRow(
            admin,
            rememberVectorPainter(Icons.Outlined.Lock),
            ChannelTint(shifter_avatarBackground, colors.onSurfaceVariant),
            ChatChannelsTestTags.ADMIN,
        ) {
          onOpenChannel(ChannelKind.ADMIN, null, admin)
        }
      }
    }
    item(key = ChatChannelsTestTags.TEAMS_HEADER) {
      // The organizer sees every team of the event, in none of which they are, where a volunteer
      // or a manager sees the teams they are in or manage, one or several. With none, it stays
      // singular, above the card saying they are in no team yet.
      GroupHeader(
          if (state.asOrganizer) stringResource(R.string.chat_channels_teams)
          else {
            val count = state.channels.size.coerceAtLeast(1)
            pluralStringResource(R.plurals.chat_channels_your_teams, count)
          },
          ChatChannelsTestTags.TEAMS_HEADER,
      )
    }
    if (state.channels.isEmpty()) {
      item(key = ChatChannelsTestTags.EMPTY) {
        if (state.asOrganizer) {
          NoTeamCard(
              R.string.chat_channels_no_team_organizer,
              R.string.chat_channels_empty_organizer,
              ChatChannelsTestTags.EMPTY,
          )
        } else {
          NoTeamCard(
              R.string.chat_channels_no_team_volunteer,
              R.string.chat_channels_empty_volunteer,
              ChatChannelsTestTags.EMPTY,
          )
        }
      }
    }
    items(state.channels, key = { it.teamId }) { channel ->
      // Keyed on the team's id, so a renamed team keeps its colour.
      val tint = chatTint(channel.teamId)
      ChannelRow(
          channel.name,
          TeamIcon.fromId(channel.icon)?.let { painterResource(it.drawable) }
              ?: rememberVectorPainter(Icons.Outlined.Groups),
          ChannelTint(tileBackground(tint), tint),
          ChatChannelsTestTags.channel(channel.teamId),
      ) {
        onOpenChannel(ChannelKind.TEAM, channel.teamId, channel.name)
      }
    }
  }
}

@Composable
private fun Message(@StringRes text: Int, tag: String) {
  Text(
      text = stringResource(text),
      color = shifter_mutedText,
      textAlign = TextAlign.Center,
      // The full width, so the text is centered even on a single line.
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag(tag),
  )
}
