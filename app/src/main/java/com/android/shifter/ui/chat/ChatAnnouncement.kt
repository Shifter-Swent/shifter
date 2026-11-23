// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.ui.theme.shifter_announcement
import com.swent.shifter.ui.theme.shifter_announcementContainer
import com.swent.shifter.ui.theme.shifter_announcementMuted
import com.swent.shifter.ui.theme.shifter_onAnnouncementContainer
import java.time.LocalDate
import java.time.ZoneId

/**
 * An announcement: a full-width amber card with the megaphone and its author, its text, and the
 * time it was sent, the same for the user's own as for someone else's.
 */
@Composable
internal fun AnnouncementCard(
    message: ChatMessageItem,
    zone: ZoneId,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
  Column(
      verticalArrangement = Arrangement.spacedBy(4.dp),
      modifier =
          modifier
              .fillMaxWidth()
              // Read as one message by TalkBack: the megaphone, author, text and time together.
              .semantics(mergeDescendants = true) {}
              .testTag(TeamChatTestTags.message(message.id))
              .background(shifter_announcementContainer, RoundedCornerShape(18.dp))
              .padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 8.dp),
  ) {
    AnnouncementAuthor(
        message,
        modifier = Modifier.testTag(TeamChatTestTags.announcementTag(message.id)),
    )
    Text(
        message.text,
        color = shifter_onAnnouncementContainer,
        style = MaterialTheme.typography.bodyMedium,
    )
    MessageStatus(
        message,
        zone,
        today,
        color = shifter_announcementMuted,
        modifier = Modifier.align(Alignment.End),
    )
  }
}

/**
 * The megaphone and the author of an announcement, in amber; the megaphone reads as "Announcement"
 * to TalkBack.
 */
@Composable
internal fun AnnouncementAuthor(message: ChatMessageItem, modifier: Modifier = Modifier) {
  val author =
      if (message.isMine) stringResource(R.string.chat_you)
      else message.senderName ?: stringResource(R.string.chat_unknown_sender)
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = modifier,
  ) {
    Icon(
        Icons.Filled.Campaign,
        contentDescription = stringResource(R.string.chat_announcement_tag),
        tint = shifter_announcement,
        modifier = Modifier.size(16.dp),
    )
    Text(
        author,
        color = shifter_announcement,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
    )
  }
}
