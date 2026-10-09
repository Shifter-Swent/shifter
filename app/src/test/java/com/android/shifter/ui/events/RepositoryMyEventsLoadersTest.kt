// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.event.FakeEventRepository
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequestStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryMyEventsLoadersTest {

  private val now = Instant.parse("2025-06-01T10:00:00Z")
  private val zone = ZoneId.of("Europe/Paris")
  private val events = FakeEventRepository()
  private val requests = mockk<MembershipRequestRepository>()

  private val upcoming = event(id = "upcoming")
  private val past =
      event(id = "past", startAt = "2025-05-01T08:00:00Z", endAt = "2025-05-01T10:00:00Z")

  private fun organizerLoader(userId: String? = "organizer") =
      OrganizerEventsLoader(events, { userId }, { now }, { zone })

  private fun staffLoader(userId: String? = "volunteer") =
      StaffEventsLoader(events, requests, { userId }, { now }, { zone })

  private fun request(status: MembershipRequestStatus) =
      MembershipRequest(
          id = "volunteer",
          userId = "volunteer",
          availability = listOf(AvailabilitySlot(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600))),
          status = status,
          createdAt = Instant.EPOCH,
      )

  @Test
  fun organizer_loadsOnlyTheEventsOfTheSignedInUser() = runTest {
    events.events += listOf(past, upcoming, event(id = "other").copy(organizerId = "someone"))

    val cards = organizerLoader().load()

    assertEquals(listOf("upcoming", "past"), cards.map { it.id })
    assertEquals(listOf(EventBadge.IN_PREPARATION, EventBadge.ENDED), cards.map { it.badge })
    assertTrue(cards.all { it.footer == EventCardFooter.ORGANIZER })
  }

  @Test
  fun staff_showsAcceptedAndPendingEventsAndLeavesRejectedOnesOut() = runTest {
    events.events += listOf(upcoming, past, event(id = "pending"), event(id = "rejected"))
    coEvery { requests.getMembershipRequestsByUId("volunteer") } returns
        mapOf(
            "upcoming" to request(MembershipRequestStatus.ACCEPTED),
            "past" to request(MembershipRequestStatus.ACCEPTED),
            "pending" to request(MembershipRequestStatus.PENDING),
            "rejected" to request(MembershipRequestStatus.REJECTED),
        )

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
  fun staff_leavesOutPendingRequestsOfOverEventsAndDeletedEvents() = runTest {
    events.events += past
    coEvery { requests.getMembershipRequestsByUId("volunteer") } returns
        mapOf(
            "past" to request(MembershipRequestStatus.PENDING),
            "deleted" to request(MembershipRequestStatus.ACCEPTED),
        )

    assertEquals(emptyList<EventCardUi>(), staffLoader().load())
  }

  @Test
  fun staff_withoutRequestsLoadsNoEvent() = runTest {
    coEvery { requests.getMembershipRequestsByUId("volunteer") } returns emptyMap()

    assertEquals(emptyList<EventCardUi>(), staffLoader().load())
  }

  @Test
  fun loaders_failWithoutASignedInUserAndQueryNothing() = runTest {
    val organizerError = runCatching { organizerLoader(null).load() }.exceptionOrNull()
    val staffError = runCatching { staffLoader(null).load() }.exceptionOrNull()

    assertTrue(organizerError is IllegalStateException)
    assertTrue(staffError is IllegalStateException)
    coVerify(exactly = 0) { requests.getMembershipRequestsByUId(any()) }
  }

  @Test
  fun loaders_letRepositoryErrorsThroughForTheViewModelToReport() = runTest {
    events.failure = EventRepositoryException.Unavailable(RuntimeException("offline"))
    coEvery { requests.getMembershipRequestsByUId("volunteer") } returns
        mapOf("upcoming" to request(MembershipRequestStatus.ACCEPTED))

    val organizerError = runCatching { organizerLoader().load() }.exceptionOrNull()
    val staffError = runCatching { staffLoader().load() }.exceptionOrNull()

    // Coroutines may rethrow a copy of the exception, so only its type is checked.
    assertTrue(organizerError is EventRepositoryException.Unavailable)
    assertTrue(staffError is EventRepositoryException.Unavailable)
  }
}
