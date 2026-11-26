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
 * Covers, through [MissionChangedFormat.format], the text of a new time, a new place, a place
 * removed, and a new time with either, the grouping by event, a mission changed twice, and the
 * items that cannot be read.
 */
class MissionChangedFormatTest {

  private val texts = FakeNotificationTexts(ZoneId.of("Europe/Zurich"))

  private val format = MissionChangedFormat()

  /** Saturday 28 November 2026, 15:00 in Zurich. */
  private val saturday = Instant.parse("2026-11-28T14:00:00Z")

  private val bar = MissionLine("m1", "Serve at the bar", saturday, saturday + Duration.ofHours(2))
  private val gate =
      MissionLine("m2", "Gate", saturday - Duration.ofDays(1), saturday - Duration.ofDays(1))

  private fun item(
      what: String,
      line: MissionLine = bar,
      address: String? = null,
      eventId: String = "e1",
      createdAt: Instant = Instant.EPOCH,
  ) =
      InboxNotification(
          id = "n-$what-${line.id}",
          kind = NotificationKind.MISSION_CHANGED,
          eventId = eventId,
          eventTitle = "Summer Festival",
          senderId = "organizer",
          data =
              mapOf(Key.MISSIONS to line.encode(), Key.WHAT to what) +
                  listOfNotNull(address?.let { Key.ADDRESS to it }),
          createdAt = createdAt,
      )

  private val newTime =
      texts.string(
          R.string.notification_mission_changed_time,
          "Serve at the bar",
          "Sat",
          "15:00",
          "17:00",
      )

  @Test
  fun format_aNewTime_tellsTheDayStartAndEnd() {
    val shown = format.format(listOf(item(Value.TIME, createdAt = saturday)), texts).single()

    assertEquals(
        AppNotification(
            kind = NotificationKind.MISSION_CHANGED,
            key = "e1",
            title = "Summer Festival",
            text = newTime,
            at = saturday,
            target = NotificationTarget.Planning("e1", "Summer Festival", "2026-11-28"),
        ),
        shown,
    )
  }

  @Test
  fun format_aNewPlace_tellsTheAddress() {
    val shown = format.format(listOf(item(Value.PLACE, address = "Rue de Genève 12")), texts)

    assertEquals(
        listOf(
            texts.string(
                R.string.notification_mission_changed_place,
                "Serve at the bar",
                "Rue de Genève 12",
            )
        ),
        shown.map { it.text },
    )
  }

  @Test
  fun format_aNewTimeAndPlace_tellsBoth() {
    val shown =
        format.format(listOf(item(Value.TIME_AND_PLACE, address = "Rue de Genève 12")), texts)

    assertEquals(
        listOf(
            texts.string(
                R.string.notification_mission_changed_time_and_place,
                "Serve at the bar",
                "Sat",
                "15:00",
                "17:00",
                "Rue de Genève 12",
            )
        ),
        shown.map { it.text },
    )
  }

  @Test
  fun format_thePlaceRemoved_saysThereIsNoPlace() {
    val shown = format.format(listOf(item(Value.PLACE)), texts)

    assertEquals(
        listOf(texts.string(R.string.notification_mission_changed_no_place, "Serve at the bar")),
        shown.map { it.text },
    )
  }

  @Test
  fun format_aNewTimeAndThePlaceRemoved_tellsBoth() {
    val shown = format.format(listOf(item(Value.TIME_AND_PLACE)), texts)

    assertEquals(
        listOf(
            texts.string(
                R.string.notification_mission_changed_time_and_no_place,
                "Serve at the bar",
                "Sat",
                "15:00",
                "17:00",
            )
        ),
        shown.map { it.text },
    )
  }

  @Test
  fun format_aNewTimeThenANewPlace_ofOneMission_readsAsBoth() {
    val moved = bar.copy(startAt = saturday + Duration.ofHours(1))
    val items =
        listOf(
            item(Value.TIME, line = moved),
            item(Value.PLACE, line = moved, address = "Rue de Genève 12"),
        )

    val shown = format.format(items, texts).single()

    assertEquals(
        texts.string(
            R.string.notification_mission_changed_time_and_place,
            "Serve at the bar",
            "Sat",
            "16:00",
            "17:00",
            "Rue de Genève 12",
        ),
        shown.text,
    )
  }

  @Test
  fun format_aNewPlaceThenThePlaceRemoved_ofOneMission_saysThereIsNoPlace() {
    val items = listOf(item(Value.PLACE, address = "Rue de Genève 12"), item(Value.PLACE))

    val shown = format.format(items, texts).single()

    assertEquals(
        texts.string(R.string.notification_mission_changed_no_place, "Serve at the bar"),
        shown.text,
    )
  }

  @Test
  fun format_thePlaceRemovedThenANewTime_ofOneMission_readsAsBoth() {
    val moved = bar.copy(startAt = saturday + Duration.ofHours(1))
    val items = listOf(item(Value.PLACE, line = moved), item(Value.TIME, line = moved))

    val shown = format.format(items, texts).single()

    assertEquals(
        texts.string(
            R.string.notification_mission_changed_time_and_no_place,
            "Serve at the bar",
            "Sat",
            "16:00",
            "17:00",
        ),
        shown.text,
    )
  }

  @Test
  fun format_aMissionChangedTwice_isToldAsItIsAfterTheLatest() {
    val later = bar.copy(startAt = saturday + Duration.ofHours(1))
    val items = listOf(item(Value.TIME), item(Value.TIME, line = later))

    val shown = format.format(items, texts).single()

    assertEquals(
        texts.string(
            R.string.notification_mission_changed_time,
            "Serve at the bar",
            "Sat",
            "16:00",
            "17:00",
        ),
        shown.text,
    )
  }

  @Test
  fun format_severalMissionsOfOneEvent_giveOneNotificationOnTheEarliestDay() {
    val items =
        listOf(
            item(Value.TIME, createdAt = saturday),
            item(Value.PLACE, line = gate, address = "Gate B", createdAt = saturday.plusSeconds(1)),
        )

    val shown = format.format(items, texts).single()

    assertEquals(texts.plural(R.plurals.notification_mission_changed_many, 2, 2), shown.text)
    assertEquals("2026-11-27", (shown.target as NotificationTarget.Planning).day)
    assertEquals(saturday.plusSeconds(1), shown.at)
  }

  @Test
  fun format_missionsOfTwoEvents_giveOneNotificationEach() {
    val items = listOf(item(Value.TIME), item(Value.TIME, line = gate, eventId = "e2"))

    assertEquals(listOf("e1", "e2"), format.format(items, texts).map { it.key })
  }

  @Test
  fun format_unreadableItems_showNothing() {
    val unknownWhat = item("COLOUR")
    val noMission = item(Value.TIME).copy(data = mapOf(Key.WHAT to Value.TIME))

    assertTrue(format.format(listOf(unknownWhat, noMission), texts).isEmpty())
  }

  @Test
  fun format_anUnreadableItemBesideAReadableOne_isLeftOut() {
    val items = listOf(item("COLOUR", line = gate), item(Value.TIME))

    assertEquals(listOf(newTime), format.format(items, texts).map { it.text })
  }
}
