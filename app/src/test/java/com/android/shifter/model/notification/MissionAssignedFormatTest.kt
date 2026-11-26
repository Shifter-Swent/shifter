// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import com.swent.shifter.R
import com.swent.shifter.model.notification.NotificationData.Key
import com.swent.shifter.model.notification.NotificationData.Value
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers, through [MissionAssignedFormat.format], the grouping by event (one notification for
 * several missions or documents), the texts of additions and removals, the latest change of a
 * mission winning, the items that cannot be read, and the Planning a tap opens.
 */
class MissionAssignedFormatTest {

  private val texts = FakeNotificationTexts(ZoneId.of("Europe/Zurich"))

  private val format = MissionAssignedFormat()

  /** Saturday 28 November 2026, 14:00 in Zurich. */
  private val saturday = Instant.parse("2026-11-28T13:00:00Z")

  private val bar = line("m1", "Bar", saturday)
  private val gate = line("m2", "Gate", saturday + Duration.ofDays(1))
  private val setup = line("m3", "Set up", saturday + Duration.ofHours(3))

  private fun line(id: String, title: String, startAt: Instant) =
      MissionLine(id, title, startAt, startAt + Duration.ofHours(2))

  private var nextId = 0

  private fun item(
      change: String,
      vararg lines: MissionLine,
      eventId: String = "e1",
      eventTitle: String = "Summer Festival",
      createdAt: Instant = Instant.EPOCH,
  ) =
      InboxNotification(
          id = "n${nextId++}",
          kind = NotificationKind.MISSION_ASSIGNED,
          eventId = eventId,
          eventTitle = eventTitle,
          senderId = "organizer",
          data = mapOf(Key.CHANGE to change, Key.MISSIONS to MissionLine.encodeAll(lines.toList())),
          createdAt = createdAt,
      )

  private fun addedOne(line: MissionLine) =
      texts.string(R.string.notification_mission_assigned_added_one, line.title, "Sat", "14:00")

  private fun addedMany(count: Int) =
      texts.plural(
          R.plurals.notification_mission_assigned_added_many,
          count,
          count,
          "Summer Festival",
      )

  private fun removedOne(line: MissionLine) =
      texts.string(R.string.notification_mission_assigned_removed_one, line.title)

  @Test
  fun format_oneMissionAdded_namesItWithItsDayAndTime() {
    val shown = format.format(listOf(item(Value.ADDED, bar, createdAt = saturday)), texts).single()

    assertEquals(
        AppNotification(
            kind = NotificationKind.MISSION_ASSIGNED,
            key = "e1",
            title = "Summer Festival",
            text = addedOne(bar),
            at = saturday,
            target = NotificationTarget.Planning("e1", "Summer Festival", "2026-11-28"),
        ),
        shown,
    )
  }

  @Test
  fun format_threeMissionsInOneDocument_giveOneNotification() {
    val shown = format.format(listOf(item(Value.ADDED, bar, gate, setup)), texts)

    assertEquals(listOf(addedMany(3)), shown.map { it.text })
  }

  @Test
  fun format_threeDocumentsOfOneEvent_giveOneNotificationAtTheLatest() {
    val items =
        listOf(
            item(Value.ADDED, bar, createdAt = saturday),
            item(Value.ADDED, gate, createdAt = saturday + Duration.ofMinutes(2)),
            item(Value.ADDED, setup, createdAt = saturday + Duration.ofMinutes(1)),
        )

    val shown = format.format(items, texts).single()

    assertEquals(addedMany(3), shown.text)
    assertEquals(saturday + Duration.ofMinutes(2), shown.at)
  }

  @Test
  fun format_missionsOfTwoEvents_giveOneNotificationEach() {
    val items =
        listOf(
            item(Value.ADDED, bar),
            item(Value.ADDED, gate, eventId = "e2", eventTitle = "Marathon"),
        )

    val shown = format.format(items, texts)

    assertEquals(listOf("e1", "e2"), shown.map { it.key })
    assertEquals(listOf("Summer Festival", "Marathon"), shown.map { it.title })
  }

  @Test
  fun format_oneMissionRemoved_namesIt() {
    val shown = format.format(listOf(item(Value.REMOVED, gate)), texts).single()

    assertEquals(removedOne(gate), shown.text)
    assertEquals("2026-11-29", (shown.target as NotificationTarget.Planning).day)
  }

  @Test
  fun format_severalMissionsRemoved_countsThem() {
    val shown = format.format(listOf(item(Value.REMOVED, bar, gate)), texts).single()

    assertEquals(
        texts.plural(R.plurals.notification_mission_assigned_removed_many, 2, 2),
        shown.text,
    )
  }

  @Test
  fun format_anAdditionAndARemoval_tellsBothAndOpensTheDayOfTheAddition() {
    val items = listOf(item(Value.REMOVED, bar), item(Value.ADDED, gate))

    val shown = format.format(items, texts).single()

    assertEquals(
        "${texts.string(R.string.notification_mission_assigned_added_one, "Gate", "Sun", "14:00")} ${removedOne(bar)}",
        shown.text,
    )
    assertEquals("2026-11-29", (shown.target as NotificationTarget.Planning).day)
  }

  @Test
  fun format_aMissionAddedThenRemoved_isToldAsRemoved() {
    val items = listOf(item(Value.ADDED, bar), item(Value.REMOVED, bar))

    assertEquals(listOf(removedOne(bar)), format.format(items, texts).map { it.text })
  }

  @Test
  fun format_severalAdded_opensTheDayOfTheEarliest() {
    val shown = format.format(listOf(item(Value.ADDED, gate, bar)), texts).single()

    assertEquals("2026-11-28", (shown.target as NotificationTarget.Planning).day)
  }

  @Test
  fun format_takesTheEventTitleOfTheLatestItem() {
    val items =
        listOf(item(Value.ADDED, bar), item(Value.ADDED, gate, eventTitle = "Summer Fest 2026"))

    assertEquals("Summer Fest 2026", format.format(items, texts).single().title)
  }

  @Test
  fun format_unreadableItems_showNothing() {
    val unknownChange = item(Value.JOINED, bar)
    val noMission =
        item(Value.ADDED).copy(data = mapOf(Key.CHANGE to Value.ADDED, Key.MISSIONS to "broken"))
    val noChange = item(Value.ADDED, bar).copy(data = mapOf(Key.MISSIONS to bar.encode()))

    assertTrue(format.format(listOf(unknownChange, noMission, noChange), texts).isEmpty())
  }

  @Test
  fun format_anUnreadableItemBesideAReadableOne_isLeftOut() {
    val items = listOf(item(Value.JOINED, gate), item(Value.ADDED, bar))

    assertEquals(listOf(addedOne(bar)), format.format(items, texts).map { it.text })
  }

  @Test
  fun format_nothing_showsNothing() {
    assertTrue(format.format(emptyList(), texts).isEmpty())
  }
}
