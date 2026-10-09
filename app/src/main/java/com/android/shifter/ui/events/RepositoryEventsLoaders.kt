// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventStatus
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequestStatus
import java.time.Clock
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Loads the events [organizerId] owns, with their lifecycle as badge. */
fun organizerEventsLoader(
    repository: EventRepository,
    organizerId: String,
    clock: Clock = Clock.systemDefaultZone(),
) = MyEventsLoader {
  repository
      .getEventsByOrganizer(organizerId)
      .sortedBy { it.startAt }
      .map { event ->
        val badge =
            when {
              event.hasEnded(clock) -> EventBadge.ENDED
              event.status == EventStatus.ONGOING -> EventBadge.ONGOING
              else -> EventBadge.IN_PREPARATION
            }
        event.toCard(badge, EventCardFooter.ORGANIZER, clock)
      }
}

/**
 * Loads the events [userId] takes part in or applied to. An accepted request counts as joined: the
 * volunteer is added to the event participants, not to [Event.memberIds]. Rejected requests are not
 * shown.
 */
fun staffEventsLoader(
    eventRepository: EventRepository,
    membershipRequestRepository: MembershipRequestRepository,
    userId: String,
    clock: Clock = Clock.systemDefaultZone(),
) = MyEventsLoader {
  val members = eventRepository.getEventsByMember(userId).associateBy { it.id }
  val requests = membershipRequestRepository.getMembershipRequestsByUId(userId)
  val confirmed =
      members.keys + requests.filterValues { it.status == MembershipRequestStatus.ACCEPTED }.keys
  val pending = requests.filterValues { it.status == MembershipRequestStatus.PENDING }.keys
  (confirmed + pending)
      .mapNotNull { id -> members[id] ?: eventRepository.getEvent(id) }
      .sortedBy { it.startAt }
      .map { event ->
        val isConfirmed = event.id in confirmed
        val badge =
            when {
              event.hasEnded(clock) -> EventBadge.ENDED
              isConfirmed -> EventBadge.CONFIRMED
              else -> EventBadge.PENDING_APPROVAL
            }
        val footer =
            if (isConfirmed) EventCardFooter.VOLUNTEER else EventCardFooter.AWAITING_APPROVAL
        event.toCard(badge, footer, clock)
      }
}

private fun Event.hasEnded(clock: Clock) =
    status == EventStatus.COMPLETED ||
        status == EventStatus.ARCHIVED ||
        !endAt.isAfter(clock.instant())

private fun Event.toCard(badge: EventBadge, footer: EventCardFooter, clock: Clock): EventCardUi {
  val start = startAt.atZone(clock.zone)
  val end = endAt.atZone(clock.zone)
  return EventCardUi(
      id = id,
      title = title,
      dateLabel = start.format(DATE_FORMAT),
      locationLabel = location.address,
      timeLabel = "${start.format(TIME_FORMAT)} – ${end.format(TIME_FORMAT)}",
      badge = badge,
      footer = footer,
  )
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM uuuu", Locale.ENGLISH)
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
