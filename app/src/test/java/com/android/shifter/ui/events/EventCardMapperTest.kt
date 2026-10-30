// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventStatus
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventCardMapperTest {

  private val zone = ZoneId.of("Europe/Paris")
  private val now = Instant.parse("2025-06-01T10:00:00Z")

  @Test
  fun organizerCard_showsTheEventWithItsLabelsInTheGivenZone() {
    val card = testEvent().toOrganizerCard(now, zone)

    assertEquals(
        EventCardUi(
            id = "marathon",
            title = "City Marathon",
            dateLabel = "Sat 14 Jun 2025",
            locationLabel = "Lyon, Place Bellecour",
            timeLabel = "08:00 – 14:00",
            badge = EventBadge.IN_PREPARATION,
            footer = EventCardFooter.ORGANIZER,
        ),
        card,
    )
  }

  @Test
  fun card_showsBothDaysOfAMultiDayEvent() {
    val card =
        testEvent(startAt = "2025-07-21T10:00:00Z", endAt = "2025-07-26T19:30:00Z")
            .toOrganizerCard(now, zone)

    assertEquals("21 Jul – 26 Jul 2025", card.dateLabel)
    assertEquals("12:00 – 21:30", card.timeLabel)
  }

  @Test
  fun organizerBadge_followsTheEventLifecycle() {
    assertEquals(EventBadge.ONGOING, testEvent(status = EventStatus.ONGOING).organizerBadge())
    assertEquals(EventBadge.ENDED, testEvent(status = EventStatus.COMPLETED).organizerBadge())
    assertEquals(EventBadge.ENDED, testEvent(status = EventStatus.ARCHIVED).organizerBadge())
    assertEquals(EventBadge.ENDED, testEvent(endAt = "2025-06-01T10:00:00Z").organizerBadge())
  }

  @Test
  fun staffCard_followsTheParticipationUntilTheEventIsOver() {
    val accepted = testEvent().toStaffCard(accepted = true, manages = false, now, zone)
    assertEquals(EventBadge.CONFIRMED, accepted.badge)
    assertEquals(EventCardFooter.VOLUNTEER, accepted.footer)

    val pending = testEvent().toStaffCard(accepted = false, manages = false, now, zone)
    assertEquals(EventBadge.PENDING_APPROVAL, pending.badge)
    assertEquals(EventCardFooter.AWAITING_APPROVAL, pending.footer)

    val ended =
        testEvent(status = EventStatus.COMPLETED)
            .toStaffCard(accepted = true, manages = false, now, zone)
    assertEquals(EventBadge.ENDED, ended.badge)
    assertEquals(EventCardFooter.VOLUNTEER, ended.footer)
  }

  @Test
  fun staffCard_ofSomeoneManagingATeam_namesThemTheManagerEvenOnceItIsOver() {
    val manager = testEvent().toStaffCard(accepted = true, manages = true, now, zone)
    assertEquals(EventBadge.CONFIRMED, manager.badge)
    assertEquals(EventCardFooter.MANAGER, manager.footer)

    val ended =
        testEvent(status = EventStatus.COMPLETED)
            .toStaffCard(accepted = true, manages = true, now, zone)
    assertEquals(EventBadge.ENDED, ended.badge)
    assertEquals(EventCardFooter.MANAGER, ended.footer)
  }

  @Test
  fun isOver_onlyOnceTheEndHasPassedOrTheEventIsClosed() {
    assertFalse(isOver(testEvent(), now))
    assertFalse(isOver(testEvent(endAt = "2025-06-01T10:00:01Z"), now))
    assertTrue(isOver(testEvent(endAt = "2025-06-01T10:00:00Z"), now))
    assertTrue(isOver(testEvent(status = EventStatus.ARCHIVED), now))
  }

  @Test
  fun inDisplayOrder_listsTheNextEventFirstThenTheMostRecentPastOne() {
    val later =
        testEvent(id = "later", startAt = "2025-08-01T08:00:00Z", endAt = "2025-08-01T10:00:00Z")
    val sooner = testEvent(id = "sooner", startAt = "2025-07-01T08:00:00Z")
    val older =
        testEvent(id = "older", startAt = "2025-01-01T08:00:00Z", endAt = "2025-01-01T10:00:00Z")
    val recent =
        testEvent(id = "recent", startAt = "2025-05-01T08:00:00Z", endAt = "2025-05-01T10:00:00Z")

    val ids =
        listOf(older, later, recent, sooner)
            .map { it to it.toOrganizerCard(now, zone) }
            .inDisplayOrder()
            .map { it.id }

    assertEquals(listOf("sooner", "later", "recent", "older"), ids)
  }

  private fun Event.organizerBadge() = toOrganizerCard(now, zone).badge
}
