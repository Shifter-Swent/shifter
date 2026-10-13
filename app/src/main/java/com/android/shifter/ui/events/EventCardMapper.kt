// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Whether [event] is over: the organizer closed it, or its end has passed. An over event goes to
 * the Past tab of both views.
 */
internal fun isOver(event: Event, now: Instant): Boolean =
    event.status == EventStatus.COMPLETED ||
        event.status == EventStatus.ARCHIVED ||
        !event.endAt.isAfter(now)

/** The card of an event the user organizes: its badge follows the event lifecycle. */
internal fun Event.toOrganizerCard(now: Instant, zone: ZoneId): EventCardUi {
  val badge =
      when {
        isOver(this, now) -> EventBadge.ENDED
        status == EventStatus.ONGOING -> EventBadge.ONGOING
        else -> EventBadge.IN_PREPARATION
      }
  return toCard(badge, EventCardFooter.ORGANIZER, zone)
}

/**
 * The card of an event the user takes part in, or applied to when [accepted] is false. Its badge
 * follows the user's participation, until the event is over.
 */
internal fun Event.toStaffCard(accepted: Boolean, now: Instant, zone: ZoneId): EventCardUi =
    when {
      !accepted -> toCard(EventBadge.PENDING_APPROVAL, EventCardFooter.AWAITING_APPROVAL, zone)
      isOver(this, now) -> toCard(EventBadge.ENDED, EventCardFooter.VOLUNTEER, zone)
      else -> toCard(EventBadge.CONFIRMED, EventCardFooter.VOLUNTEER, zone)
    }

/**
 * Orders the cards as the screen lists them: the next event first among the upcoming ones, the most
 * recent first among the past ones.
 */
internal fun List<Pair<Event, EventCardUi>>.inDisplayOrder(): List<EventCardUi> {
  val (past, upcoming) = partition { (_, card) -> card.badge == EventBadge.ENDED }
  return upcoming.sortedBy { (event, _) -> event.startAt }.map { it.second } +
      past.sortedByDescending { (event, _) -> event.startAt }.map { it.second }
}

/**
 * "Sat 14 Jun 2025" and "08:00 – 14:00" within a day; "21 Jul – 26 Jul 2026" and "12:00 – 21:30"
 * across days. Times are shown in [zone], the device's time zone in the app.
 */
private fun Event.toCard(badge: EventBadge, footer: EventCardFooter, zone: ZoneId): EventCardUi {
  val start = startAt.atZone(zone)
  val end = endAt.atZone(zone)
  val dateLabel =
      if (start.toLocalDate() == end.toLocalDate()) start.format(DAY)
      else "${start.format(DAY_IN_RANGE)} – ${end.format(DAY_IN_RANGE)} ${end.year}"
  return EventCardUi(
      id = id,
      title = title,
      dateLabel = dateLabel,
      locationLabel = location.address,
      timeLabel = "${start.format(TIME)} – ${end.format(TIME)}",
      badge = badge,
      footer = footer,
  )
}

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM uuuu", Locale.ENGLISH)
private val DAY_IN_RANGE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
