// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequestRepositoryException
import com.swent.shifter.model.membership.MembershipRequestStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JoinEventViewModelTest {

  private val events = mockk<EventRepository>()
  private val requests = mockk<MembershipRequestRepository>()
  private var userId: String? = UID
  private val viewModel = JoinEventViewModel(events, requests, { userId }, { NOW })

  @Before fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

  @After fun resetMain() = Dispatchers.resetMain()

  @Test
  fun findEvent_showsTheEventFoundByTheTypedCode() {
    coEvery { events.getEventByJoinCode("ABC123") } returns EVENT

    viewModel.onJoinCodeChange(" ABC123 ")
    viewModel.findEvent()

    assertEquals(EVENT, viewModel.uiState.value.event)
    assertNull(viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun findEvent_matchesALowercaseCode() {
    coEvery { events.getEventByJoinCode("ABC123") } returns EVENT

    viewModel.onJoinCodeChange("abc123")
    viewModel.findEvent()

    assertEquals(EVENT, viewModel.uiState.value.event)
  }

  @Test
  fun findEvent_showsAMessageForEachFailure() {
    fun failWith(error: Exception): JoinEventError? {
      coEvery { events.getEventByJoinCode(any()) } throws error
      viewModel.onJoinCodeChange("ABC123")
      viewModel.findEvent()
      return viewModel.uiState.value.error
    }

    assertEquals(JoinEventError.OFFLINE, failWith(EventRepositoryException.Unavailable()))
    assertEquals(JoinEventError.UNEXPECTED, failWith(EventRepositoryException.Unknown()))
    assertNull(viewModel.uiState.value.event)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun changeCode_leavesTheEventAndKeepsTheCode() {
    loadEvent()

    viewModel.changeCode()

    assertNull(viewModel.uiState.value.event)
    assertEquals("ABC123", viewModel.uiState.value.joinCode)
  }

  @Test
  fun findEvent_reportsAnUnknownCodeAndIgnoresABlankOne() {
    coEvery { events.getEventByJoinCode(any()) } returns null

    viewModel.findEvent()
    coVerify(exactly = 0) { events.getEventByJoinCode(any()) }

    viewModel.onJoinCodeChange("NOPE")
    viewModel.findEvent()
    assertNull(viewModel.uiState.value.event)
    assertEquals(JoinEventError.UNKNOWN_CODE, viewModel.uiState.value.error)

    viewModel.onJoinCodeChange("NOPE2")
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun apply_sendsAPendingRequestCoveringTheWholeEvent() {
    loadEvent()
    coEvery { requests.apply(any(), any()) } answers { secondArg() }

    viewModel.applyToEvent()

    val expected =
        MembershipRequest(
            userId = UID,
            availability = listOf(AvailabilitySlot(EVENT.startAt, EVENT.endAt)),
            createdAt = NOW,
        )
    coVerify(exactly = 1) { requests.apply(EVENT.id, expected) }
    assertEquals(MembershipRequestStatus.PENDING, viewModel.uiState.value.requestStatus)
  }

  @Test
  fun apply_showsTheStatusOfAnAlreadyDecidedRequest() {
    loadEvent()
    coEvery { requests.apply(any(), any()) } answers
        {
          secondArg<MembershipRequest>().copy(status = MembershipRequestStatus.REJECTED)
        }

    viewModel.applyToEvent()

    assertEquals(MembershipRequestStatus.REJECTED, viewModel.uiState.value.requestStatus)
  }

  @Test
  fun apply_doesNothingTwiceOrWithoutAnEvent() {
    viewModel.applyToEvent()
    coVerify(exactly = 0) { requests.apply(any(), any()) }

    loadEvent()
    coEvery { requests.apply(any(), any()) } answers { secondArg() }
    viewModel.applyToEvent()
    viewModel.applyToEvent()
    coVerify(exactly = 1) { requests.apply(any(), any()) }
  }

  @Test
  fun formatEventDate_showsDayDateAndTime() {
    assertEquals(
        "Mon 22 Jun · 09:30",
        formatEventDate(Instant.parse("2026-06-22T09:30:00Z"), java.time.ZoneOffset.UTC),
    )
  }

  @Test
  fun apply_requiresASignedInUser() {
    loadEvent()
    userId = null

    viewModel.applyToEvent()

    assertEquals(JoinEventError.NOT_SIGNED_IN, viewModel.uiState.value.error)
    coVerify(exactly = 0) { requests.apply(any(), any()) }
  }

  @Test
  fun apply_showsAMessageForEachFailure() {
    loadEvent()
    fun failWith(error: Exception): JoinEventError? {
      coEvery { requests.apply(any(), any()) } throws error
      viewModel.applyToEvent()
      return viewModel.uiState.value.error
    }

    assertEquals(
        JoinEventError.OFFLINE,
        failWith(MembershipRequestRepositoryException.Unavailable()),
    )
    assertEquals(
        JoinEventError.PERMISSION_DENIED,
        failWith(MembershipRequestRepositoryException.PermissionDenied()),
    )
    assertEquals(JoinEventError.UNEXPECTED, failWith(IllegalStateException()))
    assertNull(viewModel.uiState.value.requestStatus)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  private fun loadEvent() {
    coEvery { events.getEventByJoinCode(any()) } returns EVENT
    viewModel.onJoinCodeChange("ABC123")
    viewModel.findEvent()
  }

  private companion object {
    const val UID = "volunteer-1"
    val NOW: Instant = Instant.ofEpochSecond(600)
    val EVENT =
        Event(
            id = "event-1",
            organizerId = "organizer-1",
            title = "Lakeside",
            description = "A festival by the lake.",
            type = EventType.MUSIC,
            startAt = Instant.ofEpochSecond(1_000),
            endAt = Instant.ofEpochSecond(2_000),
            location = EventLocation("Lausanne"),
            createdAt = Instant.ofEpochSecond(500),
        )
  }
}
