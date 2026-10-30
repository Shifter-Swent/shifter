// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.event.FakeEventRepository
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.FakeMembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestRepositoryException
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepositoryException
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryMyEventsLoadersTest {

  private val now = Instant.parse("2025-06-01T10:00:00Z")
  private val zone = ZoneId.of("Europe/Paris")
  private val events = FakeEventRepository()
  private val requests = FakeMembershipRequestRepository()
  private val teams = FakeTeamRepository()

  private val upcoming = testEvent(id = "upcoming")
  private val past =
      testEvent(id = "past", startAt = "2025-05-01T08:00:00Z", endAt = "2025-05-01T10:00:00Z")

  private fun organizerLoader(userId: String? = "organizer") =
      OrganizerEventsLoader(events, { userId }, { now }, { zone })

  private fun staffLoader(userId: String? = "volunteer") =
      StaffEventsLoader(events, requests, teams, { userId }, { now }, { zone })

  private fun request(status: MembershipRequestStatus) =
      MembershipRequest(
          id = "volunteer",
          userId = "volunteer",
          teamId = "bar",
          availability = listOf(AvailabilitySlot(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600))),
          status = status,
          createdAt = Instant.EPOCH,
      )

  @Test
  fun organizer_loadsOnlyTheEventsOfTheSignedInUser() = runTest {
    events.events += listOf(past, upcoming, testEvent(id = "other").copy(organizerId = "someone"))

    val cards = organizerLoader().load()

    assertEquals(listOf("upcoming", "past"), cards.map { it.id })
    assertEquals(listOf(EventBadge.IN_PREPARATION, EventBadge.ENDED), cards.map { it.badge })
    assertTrue(cards.all { it.footer == EventCardFooter.ORGANIZER })
  }

  @Test
  fun staff_showsAcceptedAndPendingEventsAndLeavesRejectedOnesOut() = runTest {
    events.events += listOf(upcoming, past, testEvent(id = "pending"), testEvent(id = "rejected"))
    requests.add("upcoming", request(MembershipRequestStatus.ACCEPTED))
    requests.add("past", request(MembershipRequestStatus.ACCEPTED))
    requests.add("pending", request(MembershipRequestStatus.PENDING))
    requests.add("rejected", request(MembershipRequestStatus.REJECTED))

    val cards = staffLoader().load().associate { it.id to it.badge }

    assertEquals(
        mapOf(
            "upcoming" to EventBadge.CONFIRMED,
            "pending" to EventBadge.PENDING_APPROVAL,
            "past" to EventBadge.ENDED,
        ),
        cards,
    )
  }

  @Test
  fun staff_namesTheRoleOfEachAcceptedEventAndAsksNothingForTheOthers() = runTest {
    events.events += listOf(upcoming, past, testEvent(id = "pending"))
    requests.add("upcoming", request(MembershipRequestStatus.ACCEPTED))
    requests.add("past", request(MembershipRequestStatus.ACCEPTED))
    requests.add("pending", request(MembershipRequestStatus.PENDING))
    // They manage a team of the event that is over, and none of the one to come.
    teams.teams +=
        Team(
            id = "bar",
            eventId = "past",
            name = "Bar",
            description = "",
            icon = "wine",
            managerId = "volunteer",
            createdAt = now,
        )

    val cards = staffLoader().load().associate { it.id to it.footer }

    assertEquals(
        mapOf(
            "upcoming" to EventCardFooter.VOLUNTEER,
            // Managing a team is still their role on an event that is over.
            "past" to EventCardFooter.MANAGER,
            "pending" to EventCardFooter.AWAITING_APPROVAL,
        ),
        cards,
    )
  }

  @Test
  fun staff_whenTheTeamsCannotBeRead_stillListsTheEventsWithoutTheRole() = runTest {
    events.events += listOf(upcoming, past)
    requests.add("upcoming", request(MembershipRequestStatus.ACCEPTED))
    requests.add("past", request(MembershipRequestStatus.ACCEPTED))
    teams.failure = TeamRepositoryException.Unavailable()

    val cards = staffLoader().load()

    // The events did load, so they are listed; only the role they are labelled with is lost.
    assertEquals(listOf("upcoming", "past"), cards.map { it.id })
    assertEquals(listOf(EventBadge.CONFIRMED, EventBadge.ENDED), cards.map { it.badge })
    assertTrue(cards.all { it.footer == EventCardFooter.VOLUNTEER })
  }

  @Test
  fun staff_whenTheTeamReadIsCancelled_cancelsTheLoad() = runTest {
    events.events += upcoming
    requests.add("upcoming", request(MembershipRequestStatus.ACCEPTED))
    // Cancellation is not a failure to fall back from: it must carry on up.
    teams.failure = CancellationException("cancelled")

    val failure = runCatching { staffLoader().load() }.exceptionOrNull()

    assertTrue("got $failure", failure is CancellationException)
  }

  @Test
  fun staff_withOnlyAPendingRequest_asksForNoTeamAtAll() = runTest {
    events.events += testEvent(id = "pending")
    requests.add("pending", request(MembershipRequestStatus.PENDING))
    // A query would throw this: nobody awaiting approval manages a team yet.
    teams.failure = TeamRepositoryException.Unavailable()

    val cards = staffLoader().load()

    assertEquals(listOf(EventCardFooter.AWAITING_APPROVAL), cards.map { it.footer })
  }

  @Test
  fun staff_leavesOutPendingRequestsOfOverEventsAndDeletedEvents() = runTest {
    events.events += past
    requests.add("past", request(MembershipRequestStatus.PENDING))
    requests.add("deleted", request(MembershipRequestStatus.ACCEPTED))

    assertEquals(emptyList<EventCardUi>(), staffLoader().load())
  }

  @Test
  fun staff_withoutRequestsLoadsNoEvent() = runTest {
    requests.add("upcoming", request(MembershipRequestStatus.ACCEPTED).copy(userId = "someone"))

    assertEquals(emptyList<EventCardUi>(), staffLoader().load())
  }

  @Test
  fun loaders_failWithoutASignedInUserAndQueryNothing() = runTest {
    // A query would throw these instead of the expected IllegalStateException.
    events.failure = EventRepositoryException.Unavailable(RuntimeException("queried"))
    requests.failure = MembershipRequestRepositoryException.Unavailable()

    val organizerError = runCatching { organizerLoader(null).load() }.exceptionOrNull()
    val staffError = runCatching { staffLoader(null).load() }.exceptionOrNull()

    assertTrue(organizerError is IllegalStateException)
    assertTrue(staffError is IllegalStateException)
  }

  @Test
  fun loaders_letRepositoryErrorsThroughForTheViewModelToReport() = runTest {
    events.failure = EventRepositoryException.Unavailable(RuntimeException("offline"))
    requests.add("upcoming", request(MembershipRequestStatus.ACCEPTED))

    val organizerError = runCatching { organizerLoader().load() }.exceptionOrNull()
    val staffError = runCatching { staffLoader().load() }.exceptionOrNull()

    // Coroutines may rethrow a copy of the exception, so only its type is checked.
    assertTrue(organizerError is EventRepositoryException.Unavailable)
    assertTrue(staffError is EventRepositoryException.Unavailable)
  }

  @Test
  fun withdrawer_withdrawsTheSignedInUserFromTheEvent() = runTest {
    requests.add("upcoming", request(MembershipRequestStatus.ACCEPTED))
    requests.participants["upcoming"] = mutableSetOf("volunteer", "other")

    MembershipEventWithdrawer(requests) { "volunteer" }.withdraw("upcoming")

    assertEquals(
        emptyMap<String, MembershipRequest>(),
        requests.getMembershipRequestsByUId("volunteer"),
    )
    assertEquals(setOf("other"), requests.participants["upcoming"])
  }

  @Test
  fun withdrawer_failsWithoutASignedInUserAndWithdrawsNobody() = runTest {
    requests.add("upcoming", request(MembershipRequestStatus.ACCEPTED))

    val error = runCatching { MembershipEventWithdrawer(requests) { null }.withdraw("upcoming") }

    assertTrue(error.exceptionOrNull() is IllegalStateException)
    assertEquals(setOf("upcoming"), requests.getMembershipRequestsByUId("volunteer").keys)
  }
}
