// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.ui.theme.shifter_announcement

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
