// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequestStatus
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Loads the events the signed-in user organizes, for the organizer view. */
class OrganizerEventsLoader(
    private val eventRepository: EventRepository,
    private val currentUserId: () -> String?,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : MyEventsLoader {

  override suspend fun load(): List<EventCardUi> {
    val userId = signedInUser(currentUserId)
    val now = now()
    val zone = zone()
    return eventRepository
        .getEventsByOrganizer(userId)
        .map { it to it.toOrganizerCard(now, zone) }
        .inDisplayOrder()
  }
}

/**
 * Loads the events the signed-in user takes part in or applied to, for the staff view.
 *
 * Participation is read from the user's membership requests: an accepted request makes them a
 * volunteer of the event, a pending one shows it as awaiting approval. Rejected requests are left
 * out, and so are pending ones whose event is over, since the user never took part in it.
 */
class StaffEventsLoader(
    private val eventRepository: EventRepository,
    private val membershipRequestRepository: MembershipRequestRepository,
    private val currentUserId: () -> String?,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : MyEventsLoader {

  override suspend fun load(): List<EventCardUi> = coroutineScope {
    val userId = signedInUser(currentUserId)
    val now = now()
    val zone = zone()
    membershipRequestRepository
        .getMembershipRequestsByUId(userId)
        .filterValues { it.status != MembershipRequestStatus.REJECTED }
        .map { (eventId, request) ->
          async {
            val accepted = request.status == MembershipRequestStatus.ACCEPTED
            // An event deleted since the request was sent has nothing left to show.
            eventRepository
                .getEvent(eventId)
                ?.takeIf { accepted || !isOver(it, now) }
                ?.let { it to it.toStaffCard(accepted, now, zone) }
          }
        }
        .awaitAll()
        .filterNotNull()
        .inDisplayOrder()
  }
}

private fun signedInUser(currentUserId: () -> String?): String =
    checkNotNull(currentUserId()) { "My Events needs a signed-in user" }
