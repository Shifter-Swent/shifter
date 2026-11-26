// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

import com.swent.shifter.model.notification.NotificationData.Key
import java.text.BreakIterator

/* The data of an announcement's notification (#618), written by FirestoreChatRepository. */

/** The most characters [announcementPreview] keeps, its ellipsis included. */
internal const val ANNOUNCEMENT_PREVIEW_LENGTH = 80

/** The kind of this channel, without its ids, as a route or a notification names it. */
internal val ChatChannelId.kind: ChannelKind
  get() =
      when (this) {
        is ChatChannelId.Team -> ChannelKind.TEAM
        is ChatChannelId.General -> ChannelKind.GENERAL
        is ChatChannelId.Admin -> ChannelKind.ADMIN
      }

/** The data of an announcement of [senderName] in [channel]: the team for a team's channel only. */
internal fun announcementData(
    channel: ChatChannelId,
    senderName: String?,
    teamName: String?,
    text: String,
): Map<String, String> = buildMap {
  put(Key.CHANNEL, channel.kind.name)
  if (channel is ChatChannelId.Team) {
    put(Key.TEAM_ID, channel.teamId)
    teamName?.let { put(Key.TEAM_NAME, it) }
  }
  senderName?.takeIf { it.isNotBlank() }?.let { put(Key.SENDER_NAME, it) }
  put(Key.PREVIEW, announcementPreview(text))
}

/**
 * The start of [text], on one line, cut to [ANNOUNCEMENT_PREVIEW_LENGTH] characters with "…" when
 * longer: between two characters as the user sees them, never inside an emoji, a flag or a family.
 */
internal fun announcementPreview(text: String): String {
  val oneLine = inOneLine(text)
  if (oneLine.length <= ANNOUNCEMENT_PREVIEW_LENGTH) return oneLine
  val characters = BreakIterator.getCharacterInstance().apply { setText(oneLine) }
  val end = characters.preceding(ANNOUNCEMENT_PREVIEW_LENGTH)
  return oneLine.substring(0, end).trimEnd() + "…"
}

/** [text] trimmed, each run of whitespace, non-breaking spaces included, turned into one space. */
private fun inOneLine(text: String): String = buildString {
  for (char in text.trim()) {
    when {
      !char.isWhitespace() -> append(char)
      !endsWith(' ') -> append(' ')
    }
  }
}
