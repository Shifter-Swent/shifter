// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatRowsTest {

  /** Each row as (id, startsGroup, endsGroup). */
  private fun groups(vararg messages: ChatMessageItem) =
      chatRows(messages.toList(), ZoneOffset.UTC, TODAY).map {
        val row = it as ChatRow.Message
        Triple(row.message.id, row.startsGroup, row.endsGroup)
      }

  private fun from(sender: String, id: String, sentAt: Instant? = AT) =
      ChatMessageItem(
          id,
          "Text $id",
          senderId = sender,
          senderName = "Name",
          sentAt,
          isMine = sender == ME,
      )

  @Test
  fun chatRows_oneSender_formsOneGroup() {
    assertEquals(
        listOf(Triple("1", true, false), Triple("2", false, false), Triple("3", false, true)),
        groups(from(ALICE, "1"), from(ALICE, "2"), from(ALICE, "3")),
    )
  }

  @Test
  fun chatRows_sendersTakingTurns_formAGroupEach() {
    assertEquals(
        listOf(
            Triple("1", true, false),
            Triple("2", false, true),
            Triple("3", true, true),
            Triple("4", true, true),
        ),
        groups(from(ALICE, "1"), from(ALICE, "2"), from(ME, "3"), from(ALICE, "4")),
    )
  }

  @Test
  fun chatRows_sameNameFromAnotherSender_startsAGroup() {
    assertEquals(
        listOf(Triple("1", true, true), Triple("2", true, true)),
        groups(from(ALICE, "1"), from("alice2", "2")),
    )
  }

  @Test
  fun chatRows_announcementInTheMiddle_breaksTheGroup() {
    val announcement = from(ALICE, "2").copy(isAnnouncement = true)

    assertEquals(
        listOf(Triple("1", true, true), Triple("2", true, true), Triple("3", true, true)),
        groups(from(ALICE, "1"), announcement, from(ALICE, "3")),
    )
  }

  @Test
  fun chatRows_pendingMessage_joinsTheGroupOfToday() {
    val pending = from(ME, "2", sentAt = null).copy(isPending = true)

    assertEquals(
        listOf(Triple("1", true, false), Triple("2", false, true)),
        groups(from(ME, "1"), pending),
    )
  }

  @Test
  fun chatRows_anotherDay_startsAGroup() {
    assertEquals(
        listOf(Triple("1", true, true), Triple("2", true, true)),
        groups(from(ALICE, "1", AT.minusSeconds(86_400)), from(ALICE, "2")),
    )
  }

  private companion object {
    const val ME = "me"
    const val ALICE = "alice"
    val AT: Instant = Instant.parse("2027-06-20T09:30:00Z")
    val TODAY: LocalDate = LocalDate.of(2027, 6, 20)
  }
}
