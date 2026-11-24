// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.model.time.formatDay
import com.swent.shifter.model.time.formatTime
import com.swent.shifter.ui.theme.md_theme_dark_surface
import com.swent.shifter.ui.theme.shifter_mutedText
import com.swent.shifter.ui.theme.shifter_onPrimaryMuted
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private val AVATAR = 28.dp

/**
 * A message in its run: the user's own on the right in blue, others' on the left with their name
 * and avatar in their own tint, and an announcement apart, in amber.
 */
@Composable
internal fun MessageRow(row: ChatRow.Message, zone: ZoneId, today: LocalDate) {
  val message = row.message
  // Read where no name is shown, so TalkBack still says who wrote it: "You", or the sender on the
  // later bubbles of a group. The first bubble of a group already reads its visible name.
  val author =
      when {
        message.isMine -> stringResource(R.string.chat_you)
        row.startsGroup -> null
        else -> message.senderName ?: stringResource(R.string.chat_unknown_sender)
      }
  // Read as one message by TalkBack: the sender, text and time together.
  val merged =
      Modifier.semantics(mergeDescendants = true) { author?.let { contentDescription = it } }
          .testTag(TeamChatTestTags.message(message.id))
  val top = if (row.startsGroup) 10.dp else 3.dp
  when {
    message.isAnnouncement -> AnnouncementMessage(message, zone, today, Modifier.padding(top = top))
    message.isMine ->
        Box(modifier = Modifier.padding(top = top).fillMaxWidth()) {
          ChatMessageBubble(
              message,
              zone,
              today,
              showsTime = row.endsGroup,
              background = MaterialTheme.colorScheme.primary,
              textColor = MaterialTheme.colorScheme.onPrimary,
              statusColor = shifter_onPrimaryMuted,
              shape = bubbleShape(tail = row.endsGroup, mine = true),
              modifier = Modifier.align(Alignment.CenterEnd).widthIn(max = 280.dp).then(merged),
          )
        }
    else -> {
      val tint = chatTint(message.senderId)
      Row(
          modifier = Modifier.padding(top = top).fillMaxWidth().then(merged),
          verticalAlignment = Alignment.Bottom,
      ) {
        if (row.endsGroup) Avatar(message.senderName, tint) else Spacer(Modifier.width(AVATAR))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.widthIn(max = 264.dp)) {
          if (row.startsGroup) {
            Text(
                message.senderName ?: stringResource(R.string.chat_unknown_sender),
                color = tint,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 3.dp),
            )
          }
          ChatMessageBubble(
              message,
              zone,
              today,
              showsTime = row.endsGroup,
              background = MaterialTheme.colorScheme.surfaceVariant,
              textColor = MaterialTheme.colorScheme.onSurface,
              statusColor = shifter_mutedText,
              shape = bubbleShape(tail = row.endsGroup, mine = false),
          )
        }
      }
    }
  }
}

/** A bubble, with its small corner toward the sender when it has the [tail] of its run. */
private fun bubbleShape(tail: Boolean, mine: Boolean): Shape {
  val corner = if (tail) 4.dp else 16.dp
  return if (mine) RoundedCornerShape(16.dp, 16.dp, corner, 16.dp)
  else RoundedCornerShape(16.dp, 16.dp, 16.dp, corner)
}

/** The initial of [name] on a disc of [tint], beside the last message of their run. */
@Composable
private fun Avatar(name: String?, tint: Color) {
  Box(
      // Hidden from TalkBack: the name beside it already says who wrote the message.
      modifier = Modifier.size(AVATAR).background(tint, CircleShape).clearAndSetSemantics {},
      contentAlignment = Alignment.Center,
  ) {
    Text(
        name?.trim()?.firstOrNull()?.uppercase() ?: "?",
        color = md_theme_dark_surface,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
    )
  }
}

/** The text of [message], with its time when it [showsTime] or while it is being sent. */
@Composable
private fun ChatMessageBubble(
    message: ChatMessageItem,
    zone: ZoneId,
    today: LocalDate,
    showsTime: Boolean,
    background: Color,
    textColor: Color,
    statusColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
  // Set here, as a background alone, unlike a Surface, gives the text no color on the bubble.
  Column(
      horizontalAlignment = Alignment.End,
      modifier =
          modifier.background(background, shape).padding(horizontal = 12.dp, vertical = 8.dp),
  ) {
    Text(
        message.text,
        color = textColor,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.align(Alignment.Start),
    )
    if (message.isPending || showsTime) MessageStatus(message, zone, today, statusColor)
  }
}

/** The time of [message], or a clock and "Sending…" while it is being sent. */
@Composable
internal fun MessageStatus(message: ChatMessageItem, zone: ZoneId, today: LocalDate, color: Color) {
  when {
    message.isPending ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 2.dp).testTag(TeamChatTestTags.pending(message.id)),
        ) {
          Icon(
              Icons.Outlined.Schedule,
              contentDescription = null,
              tint = color,
              modifier = Modifier.size(12.dp),
          )
          Text(
              stringResource(R.string.chat_sending),
              color = color,
              style = statusStyle(),
          )
        }
    message.sentAt != null ->
        Text(
            sentAtLabel(message.sentAt.atZone(zone), today),
            color = color,
            style = statusStyle(),
            modifier = Modifier.padding(top = 2.dp),
        )
  }
}

/** The small text of a message's time, tighter than labelSmall so it stays discreet. */
@Composable
private fun statusStyle(): TextStyle =
    MaterialTheme.typography.labelSmall.copy(
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.sp,
    )

/**
 * The time of a message sent [today], or its day and time otherwise: the channel stays open after
 * the event, which can last several days, so an old instruction must not read as a current one.
 */
internal fun sentAtLabel(sentAt: ZonedDateTime, today: LocalDate): String {
  val time = sentAt.toLocalTime().formatTime()
  val day = sentAt.toLocalDate()
  return if (day == today) time else "${day.formatDay(withYear = day.year != today.year)}, $time"
}
