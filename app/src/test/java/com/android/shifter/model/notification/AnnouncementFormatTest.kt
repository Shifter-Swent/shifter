// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import com.swent.shifter.R
import com.swent.shifter.model.chat.ChannelKind
import com.swent.shifter.model.notification.NotificationData.Key
import com.swent.shifter.model.user.UserMode
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers, through [AnnouncementFormat.format], the title and the text of each kind of channel, the
 * grouping by channel (one notification, one line per announcement, the last ones only), the items
 * that cannot be read, and the channel a tap opens.
 */
class AnnouncementFormatTest {

  private val texts = FakeNotificationTexts(ZoneOffset.UTC)

  private val format = AnnouncementFormat()

  private val sentAt = Instant.parse("2026-11-28T17:00:00Z")

  private var nextId = 0

  private fun item(
      channel: String = "GENERAL",
      preview: String? = "Gates open at 18:00.",
      senderName: String? = "Alice",
      teamId: String? = null,
      teamName: String? = null,
      eventId: String = "e1",
      createdAt: Instant = sentAt,
  ) =
      inboxNotification(
          id = "n${nextId++}",
          kind = NotificationKind.ANNOUNCEMENT,
          eventId = eventId,
          eventTitle = "Summer Festival",
          senderId = "organizer",
          data =
              listOfNotNull(
                      Key.CHANNEL to channel,
                      preview?.let { Key.PREVIEW to it },
                      senderName?.let { Key.SENDER_NAME to it },
                      teamId?.let { Key.TEAM_ID to it },
                      teamName?.let { Key.TEAM_NAME to it },
                  )
                  .toMap(),
          createdAt = createdAt,
      )

  private fun title(channelName: String) =
      texts.string(R.string.notification_announcement_title, "Summer Festival", channelName)

  private fun line(senderName: String, preview: String) =
      texts.string(R.string.notification_announcement_text, senderName, preview)

  @Test
  fun format_ofTheGeneralChannel_namesTheEventTheChannelAndTheSender() {
    val shown = format.format(listOf(item()), texts).single()

    assertEquals(
        AppNotification(
            kind = NotificationKind.ANNOUNCEMENT,
            key = "e1/GENERAL",
            title = title(texts.string(R.string.chat_channel_general)),
            text = line("Alice", "Gates open at 18:00."),
            at = sentAt,
            target =
                NotificationTarget.Channel(
                    "e1",
                    "Summer Festival",
                    UserMode.VOLUNTEER,
                    ChannelKind.GENERAL,
                    teamId = null,
                ),
        ),
        shown,
    )
  }

  @Test
  fun format_announcementsOfDifferentChannels_makeOneNotificationEach() {
    val items =
        listOf(
            item("ADMIN", teamId = "t1"),
            item("TEAM", teamId = "t1", teamName = "Bar"),
            item("TEAM", teamId = "t2", teamName = "Gate"),
            item(eventId = "e2"),
        )

    val shown = format.format(items, texts)

    assertEquals(listOf("e1/ADMIN", "e1/TEAM/t1", "e1/TEAM/t2", "e2/GENERAL"), shown.map { it.key })
    assertEquals(title(texts.string(R.string.chat_channel_admin)), shown[0].title)
    assertEquals(title("Bar"), shown[1].title)
    assertEquals(
        NotificationTarget.Channel(
            "e1",
            "Summer Festival",
            UserMode.VOLUNTEER,
            ChannelKind.TEAM,
            "t1",
        ),
        shown[1].target,
    )
  }

  @Test
  fun format_severalAnnouncementsOfAChannel_listsThemOldestFirstInOneNotification() {
    val later = sentAt.plusSeconds(60)
    val items =
        listOf(
            item(preview = "Gates open at 18:00."),
            item(preview = "Bring your badge.", senderName = "Bob", createdAt = later),
        )

    val shown = format.format(items, texts).single()

    assertEquals(
        line("Alice", "Gates open at 18:00.") + "\n" + line("Bob", "Bring your badge."),
        shown.text,
    )
    assertEquals(later, shown.at)
  }

  @Test
  fun format_moreAnnouncementsThanTheLinesShown_listsTheLatestOnes() {
    val count = AnnouncementFormat.MAX_LINES + 2
    val items =
        List(count) { item(preview = "Update $it", createdAt = sentAt.plusSeconds(it * 60L)) }

    val shown = format.format(items, texts).single()

    assertEquals((2 until count).joinToString("\n") { line("Alice", "Update $it") }, shown.text)
  }

  @Test
  fun format_withNoSenderName_showsThePreviewAlone() {
    val shown = format.format(listOf(item(senderName = null)), texts).single()

    assertEquals("Gates open at 18:00.", shown.text)
  }

  @Test
  fun format_unreadableItems_showNothing() {
    val items =
        listOf(
            item(channel = "LOBBY"),
            item(channel = "TEAM", teamName = "Bar"),
            item(channel = "TEAM", teamId = "t1"),
            item(preview = null),
            item(preview = " "),
        )

    assertTrue(format.format(items, texts).isEmpty())
  }
}
