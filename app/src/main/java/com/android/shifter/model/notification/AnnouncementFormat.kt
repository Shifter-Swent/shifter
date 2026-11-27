// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import com.swent.shifter.R
import com.swent.shifter.model.chat.ChannelKind
import com.swent.shifter.model.notification.NotificationData.Key
import com.swent.shifter.model.user.UserMode

/**
 * Shows the [NotificationKind.ANNOUNCEMENT]s: one notification per channel, "Summer Festival ·
 * General", listing its last [MAX_LINES] announcements oldest first, "Alice: Gates open at 18:00.",
 * as a messaging app does. A tap opens the channel. An item with an unknown channel, a team's
 * channel without its team, or no preview is not shown.
 */
class AnnouncementFormat : NotificationFormat {

  override val kind = NotificationKind.ANNOUNCEMENT

  override fun format(
      items: List<InboxNotification>,
      texts: NotificationTexts,
  ): List<AppNotification> =
      items
          .mapNotNull { item -> channelOf(item)?.let { it to item } }
          .groupBy({ it.first }, { it.second })
          .map { (channel, ofChannel) -> notify(channel, ofChannel, texts) }

  /** A channel of an event: [teamId] for a team's only. Its notification's key. */
  private data class Channel(val eventId: String, val kind: ChannelKind, val teamId: String?) {
    val key: String
      get() = listOfNotNull(eventId, kind.name, teamId).joinToString("/")
  }

  /** The channel of [item], or null when it cannot be shown. */
  private fun channelOf(item: InboxNotification): Channel? {
    val data = item.data
    val kind = ChannelKind.entries.firstOrNull { it.name == data[Key.CHANNEL] }
    return when {
      kind == null || data[Key.PREVIEW].isNullOrBlank() -> null
      kind != ChannelKind.TEAM -> Channel(item.eventId, kind, teamId = null)
      data[Key.TEAM_ID].isNullOrBlank() || data[Key.TEAM_NAME].isNullOrBlank() -> null
      else -> Channel(item.eventId, kind, data[Key.TEAM_ID])
    }
  }

  /** The notification of [channel], from its [items], oldest first. */
  private fun notify(
      channel: Channel,
      items: List<InboxNotification>,
      texts: NotificationTexts,
  ): AppNotification {
    val latest = items.last()
    val channelName =
        when (channel.kind) {
          ChannelKind.GENERAL -> texts.string(R.string.chat_channel_general)
          ChannelKind.ADMIN -> texts.string(R.string.chat_channel_admin)
          ChannelKind.TEAM -> latest.data.getValue(Key.TEAM_NAME)
        }
    return AppNotification(
        kind = kind,
        key = channel.key,
        title =
            texts.string(R.string.notification_announcement_title, latest.eventTitle, channelName),
        text = items.takeLast(MAX_LINES).joinToString("\n") { line(it, texts) },
        at = items.maxOf { it.createdAt },
        // Those told are participants, members or managers: volunteers.
        target =
            NotificationTarget.Channel(
                channel.eventId,
                latest.eventTitle,
                UserMode.VOLUNTEER,
                channel.kind,
                channel.teamId,
            ),
    )
  }

  /** "Alice: Gates open at 18:00.", or the preview alone when the sender has no name. */
  private fun line(item: InboxNotification, texts: NotificationTexts): String {
    val preview = item.data.getValue(Key.PREVIEW)
    val sender = item.data[Key.SENDER_NAME]?.takeIf { it.isNotBlank() } ?: return preview
    return texts.string(R.string.notification_announcement_text, sender, preview)
  }

  companion object {
    /** The most announcements a notification lists, as the engine gives back all those shown. */
    const val MAX_LINES = 5
  }
}
