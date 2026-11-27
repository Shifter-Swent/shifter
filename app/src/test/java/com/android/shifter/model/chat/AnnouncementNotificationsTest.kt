// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

import com.swent.shifter.model.notification.NotificationData.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the data of an announcement's notification, for an event's channel and a team's, and the
 * preview of its text: on one line, and cut with an ellipsis past [ANNOUNCEMENT_PREVIEW_LENGTH]
 * characters, never inside an emoji.
 */
class AnnouncementNotificationsTest {

  @Test
  fun announcementData_ofAnEventsChannel_holdsTheChannelTheSenderAndThePreviewOnly() {
    val data = announcementData(ChatChannelId.General("e1"), "Alice", "Bar", "Gates open at 18:00.")

    assertEquals(
        mapOf(
            Key.CHANNEL to "GENERAL",
            Key.SENDER_NAME to "Alice",
            Key.PREVIEW to "Gates open at 18:00.",
        ),
        data,
    )
  }

  @Test
  fun announcementData_ofATeamsChannel_holdsTheTeam() {
    val data = announcementData(ChatChannelId.Team("e1", "t1"), "Alice", "Bar", "Briefing")

    assertEquals(
        mapOf(
            Key.CHANNEL to "TEAM",
            Key.TEAM_ID to "t1",
            Key.TEAM_NAME to "Bar",
            Key.SENDER_NAME to "Alice",
            Key.PREVIEW to "Briefing",
        ),
        data,
    )
  }

  @Test
  fun announcementData_withNoSenderName_leavesTheNameOut() {
    val admin = ChatChannelId.Admin("e1")

    assertTrue(Key.SENDER_NAME !in announcementData(admin, null, null, "Briefing"))
    assertTrue(Key.SENDER_NAME !in announcementData(admin, " ", null, "Briefing"))
  }

  @Test
  fun announcementPreview_ofAShortText_isTheText() {
    val text = "a".repeat(ANNOUNCEMENT_PREVIEW_LENGTH)

    assertEquals(text, announcementPreview(text))
  }

  @Test
  fun announcementPreview_ofALongerText_cutsItWithAnEllipsis() {
    val preview = announcementPreview("a".repeat(ANNOUNCEMENT_PREVIEW_LENGTH + 1))

    assertEquals("a".repeat(ANNOUNCEMENT_PREVIEW_LENGTH - 1) + "…", preview)
  }

  @Test
  fun announcementPreview_ofSeveralLinesAndSpaces_isOneLine() {
    assertEquals(
        "Gates open at 18:00. Bring your badge.",
        announcementPreview("Gates open at 18:00.\n\n  Bring your badge."),
    )
  }

  @Test
  fun announcementPreview_cutAfterASpace_leavesNoSpaceBeforeTheEllipsis() {
    val text = "a".repeat(ANNOUNCEMENT_PREVIEW_LENGTH - 2) + " " + "b".repeat(10)

    assertEquals("a".repeat(ANNOUNCEMENT_PREVIEW_LENGTH - 2) + "…", announcementPreview(text))
  }

  /** An emoji, a flag (two letters) and a family (three emoji joined), placed across the cut. */
  @Test
  fun announcementPreview_cutAtAnEmoji_leavesItOutWhole() {
    for (emoji in listOf("🎉", "🇨🇭", "👨‍👩‍👧")) {
      val before = "a".repeat(ANNOUNCEMENT_PREVIEW_LENGTH - emoji.length + 1)

      assertEquals(emoji, before + "…", announcementPreview(before + emoji + "b"))
    }
  }
}
