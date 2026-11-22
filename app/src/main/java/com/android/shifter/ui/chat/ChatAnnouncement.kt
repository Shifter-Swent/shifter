// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import com.swent.shifter.ui.theme.shifter_mutedText
import com.swent.shifter.ui.theme.shifter_onAnnouncementContainer
import java.time.LocalDate
import java.time.ZoneId

/**
 * An announcement, alone on its side of the channel: the tag above an amber bubble with the
 * sender's name, the text and the time.
 */
@Composable
internal fun AnnouncementMessage(
    message: ChatMessageItem,
    zone: ZoneId,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
  val name =
      if (message.isMine) stringResource(R.string.chat_you)
      else message.senderName ?: stringResource(R.string.chat_unknown_sender)
  val alignment = if (message.isMine) Alignment.End else Alignment.Start
  Box(modifier = modifier.fillMaxWidth()) {
    Column(
        horizontalAlignment = alignment,
        modifier =
            Modifier.align(if (message.isMine) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = 280.dp)
                // Read as one message by TalkBack: the tag, sender, text and time together.
                .semantics(mergeDescendants = true) {}
                .testTag(TeamChatTestTags.message(message.id)),
    ) {
      AnnouncementTag(message.id)
      // Set here, as a background alone, unlike a Surface, gives the text no color on the bubble.
      Column(
          horizontalAlignment = alignment,
          modifier =
              Modifier.background(shifter_announcementContainer, RoundedCornerShape(12.dp))
                  .padding(horizontal = 12.dp, vertical = 8.dp),
      ) {
        Text(
            name,
            color = shifter_onAnnouncementContainer,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            message.text,
            color = shifter_onAnnouncementContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
        MessageStatus(message, zone, today, shifter_mutedText)
      }
    }
  }
}

/** The megaphone and "Announcement", above the bubble of an announcement. */
@Composable
internal fun AnnouncementTag(messageId: String) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      modifier =
          Modifier.padding(bottom = 4.dp).testTag(TeamChatTestTags.announcementTag(messageId)),
  ) {
    Icon(
        Icons.Filled.Campaign,
        contentDescription = null,
        tint = shifter_announcement,
        modifier = Modifier.size(16.dp),
    )
    Text(
        stringResource(R.string.chat_announcement_tag),
        color = shifter_announcement,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
    )
  }
}
