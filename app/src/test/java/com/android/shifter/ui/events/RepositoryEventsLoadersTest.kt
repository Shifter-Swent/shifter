// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.event.EventStatus
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FakeEventRepository
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequestStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RepositoryEventsLoadersTest {

  private val clock = Clock.fixed(Instant.parse("2027-07-01T10:00:00Z"), ZoneId.of("Europe/Zurich"))
  private val events = FakeEventRepository()
  private val requests = mutableMapOf<String, MembershipRequest>()

  private val membershipRepository =
      object : MembershipRequestRepository {
        override suspend fun apply(eventId: String, request: MembershipRequest) = error("unused")

        override suspend fun accept(eventId: String, userId: String): Unit = error("unused")

        override suspend fun reject(eventId: String, userId: String): Unit = error("unused")

        override suspend fun getMembershipRequestsByEId(eventId: String): List<MembershipRequest> =
            error("unused")

        override suspend fun getMembershipRequestsByUId(userId: String) = requests.filterValues {
          it.userId == userId
        }
      }

  private suspend fun event(
      title: String,
      start: String,
      end: String,
      organizerId: String = "organizer-1",
      memberIds: List<String> = emptyList(),
      status: EventStatus = EventStatus.PREPARATION,
  ): Event =
      events.createEvent(
          Event(
              organizerId = organizerId,
              title = title,
              description = "",
              type = EventType.OTHER,
              startAt = Instant.parse(start),
              endAt = Instant.parse(end),
              location = EventLocation("Nyon, Route de Saint-Cergue"),
              memberIds = memberIds,
              status = status,
              createdAt = Instant.parse("2027-01-01T00:00:00Z"),
          )
      )

  private fun request(event: Event, status: MembershipRequestStatus) {
    requests[event.id] =
        MembershipRequest(
            userId = "volunteer-1",
            availability = listOf(AvailabilitySlot(event.startAt, event.endAt)),
            status = status,
            createdAt = Instant.parse("2027-01-01T00:00:00Z"),
        )
  }

  private fun staffLoader() = staffEventsLoader(events, membershipRepository, "volunteer-1", clock)

  @Test
  fun organizerCard_showsTheEventInTheUsersTimeZone() = runTest {
    val paleo = event("Paléo Festival", "2027-07-21T10:00:00Z", "2027-07-21T20:30:00Z")

    val cards = organizerEventsLoader(events, "organizer-1", clock).load()

    assertEquals(
        listOf(
            EventCardUi(
                id = paleo.id,
                title = "Paléo Festival",
                dateLabel = "Wed 21 Jul 2027",
                locationLabel = "Nyon, Route de Saint-Cergue",
                timeLabel = "12:00 – 22:30",
                badge = EventBadge.IN_PREPARATION,
                footer = EventCardFooter.ORGANIZER,
            )
        ),
        cards,
    )
  }

  @Test
  fun organizer_seesOnlyTheirEvents_withTheirLifecycle_soonestFirst() = runTest {
    val later = event("Later", "2027-08-01T10:00:00Z", "2027-08-01T12:00:00Z")
    val ongoing =
        event(
            "Ongoing",
            "2027-07-01T08:00:00Z",
            "2027-07-01T18:00:00Z",
            status = EventStatus.ONGOING,
        )
    val pastButNotClosed = event("Over", "2027-06-01T08:00:00Z", "2027-06-01T12:00:00Z")
    val completed =
        event(
            "Done",
            "2027-09-01T08:00:00Z",
            "2027-09-01T12:00:00Z",
            status = EventStatus.COMPLETED,
        )
    event("Someone else's", "2027-07-10T08:00:00Z", "2027-07-10T12:00:00Z", organizerId = "other")

    val cards = organizerEventsLoader(events, "organizer-1", clock).load()

    assertEquals(
        listOf(
            pastButNotClosed.id to EventBadge.ENDED,
            ongoing.id to EventBadge.ONGOING,
            later.id to EventBadge.IN_PREPARATION,
            completed.id to EventBadge.ENDED,
        ),
        cards.map { it.id to it.badge },
    )
  }

  @Test
  fun staff_seesJoinedAcceptedAndPendingEvents_butNotRejectedOrOthers() = runTest {
    val member =
        event(
            "Member",
            "2027-07-10T08:00:00Z",
            "2027-07-10T12:00:00Z",
            memberIds = listOf("volunteer-1"),
        )
    val accepted = event("Accepted", "2027-07-11T08:00:00Z", "2027-07-11T12:00:00Z")
    val pending = event("Pending", "2027-07-12T08:00:00Z", "2027-07-12T12:00:00Z")
    val rejected = event("Rejected", "2027-07-13T08:00:00Z", "2027-07-13T12:00:00Z")
    event("Not applied", "2027-07-14T08:00:00Z", "2027-07-14T12:00:00Z")
    request(accepted, MembershipRequestStatus.ACCEPTED)
    request(pending, MembershipRequestStatus.PENDING)
    request(rejected, MembershipRequestStatus.REJECTED)

    val cards = staffLoader().load()

    assertEquals(
        listOf(
            Triple(member.id, EventBadge.CONFIRMED, EventCardFooter.VOLUNTEER),
            Triple(accepted.id, EventBadge.CONFIRMED, EventCardFooter.VOLUNTEER),
            Triple(pending.id, EventBadge.PENDING_APPROVAL, EventCardFooter.AWAITING_APPROVAL),
        ),
        cards.map { Triple(it.id, it.badge, it.footer) },
    )
  }

  @Test
  fun staff_memberWhoseRequestWasAccepted_appearsOnce() = runTest {
    val joined =
        event(
            "Joined",
            "2027-07-10T08:00:00Z",
            "2027-07-10T12:00:00Z",
            memberIds = listOf("volunteer-1"),
        )
    request(joined, MembershipRequestStatus.ACCEPTED)

    assertEquals(listOf(joined.id), staffLoader().load().map { it.id })
  }

  @Test
  fun staff_endedEvent_isEndedAndKeepsTheVolunteerFooter() = runTest {
    val ended = event("Ended", "2027-06-01T08:00:00Z", "2027-06-01T12:00:00Z")
    request(ended, MembershipRequestStatus.ACCEPTED)

    val card = staffLoader().load().single()

    assertEquals(EventBadge.ENDED, card.badge)
    assertEquals(EventCardFooter.VOLUNTEER, card.footer)
  }

  @Test
  fun staff_requestForADeletedEvent_isSkipped() = runTest {
    val kept = event("Kept", "2027-07-10T08:00:00Z", "2027-07-10T12:00:00Z")
    request(kept, MembershipRequestStatus.PENDING)
    requests["deleted-event"] = requests.getValue(kept.id)

    assertEquals(listOf(kept.id), staffLoader().load().map { it.id })
  }

  @Test(expected = EventRepositoryException.Unavailable::class)
  fun backendFailure_reachesTheViewModel() = runTest {
    events.failure = EventRepositoryException.Unavailable()

    staffLoader().load()
  }
}
