// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatRowsTest {

  /** Each message row as (id, startsGroup, endsGroup), without the day rows. */
  private fun groups(vararg messages: ChatMessageItem) =
      chatRows(messages.toList(), ZoneOffset.UTC, TODAY).filterIsInstance<ChatRow.Message>().map {
        Triple(it.message.id, it.startsGroup, it.endsGroup)
      }

  /** Each row, a day as its date and a message as its id with where it starts and ends a group. */
  private fun rows(vararg messages: ChatMessageItem, zone: ZoneId = ZoneOffset.UTC) =
      chatRows(messages.toList(), zone, TODAY).map {
        when (it) {
          is ChatRow.Day -> "${it.day}"
          is ChatRow.Message ->
              it.message.id +
                  (if (it.startsGroup) " starts" else "") +
                  (if (it.endsGroup) " ends" else "")
        }
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

  @Test
  fun chatRows_messagesOverSeveralDays_putAChipBeforeEachDay() {
    val pending = from(ME, "4", sentAt = null).copy(isPending = true)

    assertEquals(
        listOf(
            "2027-06-17",
            "1 starts",
            "2 ends",
            "2027-06-19",
            "3 starts ends",
            "2027-06-20",
            "4 starts ends",
        ),
        rows(
            from(ALICE, "1", AT.minus(3, ChronoUnit.DAYS)),
            from(ALICE, "2", AT.minus(3, ChronoUnit.DAYS).plusSeconds(60)),
            from(ALICE, "3", AT.minus(1, ChronoUnit.DAYS)),
            pending,
        ),
    )
  }

  @Test
  fun chatRows_groupCutByMidnight_endsBeforeTheChipOfTheNextDay() {
    assertEquals(
        listOf("2027-06-19", "1 starts ends", "2027-06-20", "2 starts", "3 ends"),
        rows(
            from(ALICE, "1", Instant.parse("2027-06-19T23:50:00Z")),
            from(ALICE, "2", Instant.parse("2027-06-20T00:10:00Z")),
            from(ALICE, "3", Instant.parse("2027-06-20T00:20:00Z")),
        ),
    )
  }

  @Test
  fun chatRows_midnightOfTheZone_cutsTheDays() {
    // 21:50 and 22:10 in UTC are either side of midnight in Zurich, at UTC+2 in summer.
    assertEquals(
        listOf("2027-06-19", "1 starts ends", "2027-06-20", "2 starts ends"),
        rows(
            from(ALICE, "1", Instant.parse("2027-06-19T21:50:00Z")),
            from(ALICE, "2", Instant.parse("2027-06-19T22:10:00Z")),
            zone = ZoneId.of("Europe/Zurich"),
        ),
    )
  }

  @Test
  fun chatRows_pendingDatedBeforeTheLastMessage_staysOnItsDay() {
    // The server is past midnight while the device clock is not yet.
    val pending = from(ME, "3", sentAt = null).copy(isPending = true)

    assertEquals(
        listOf("2027-06-20", "1 starts ends", "2027-06-21", "2 starts ends", "3 starts ends"),
        rows(from(ALICE, "1"), from(ALICE, "2", AT.plus(1, ChronoUnit.DAYS)), pending),
    )
  }

  @Test
  fun chatRows_firstMessageOfTheDayGone_keepsTheKeyOfTheChip() {
    val first = from(ALICE, "1")
    val second = from(ALICE, "2", AT.plusSeconds(60))

    fun chipKey(vararg messages: ChatMessageItem) =
        chatRows(messages.toList(), ZoneOffset.UTC, TODAY).first().key

    assertEquals(chipKey(first, second), chipKey(second))
  }

  private companion object {
    const val ME = "me"
    const val ALICE = "alice"
    val AT: Instant = Instant.parse("2027-06-20T09:30:00Z")
    val TODAY: LocalDate = LocalDate.of(2027, 6, 20)
  }
}
