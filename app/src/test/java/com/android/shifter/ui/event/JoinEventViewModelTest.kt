// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequestRepositoryException
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
import org.junit.Assert.assertTrue
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
    assertNull(viewModel.uiState.value.errorMsg)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun findEvent_reportsAnUnknownCodeAndIgnoresABlankOne() {
    coEvery { events.getEventByJoinCode(any()) } returns null

    viewModel.findEvent()
    coVerify(exactly = 0) { events.getEventByJoinCode(any()) }

    viewModel.onJoinCodeChange("NOPE")
    viewModel.findEvent()
    assertNull(viewModel.uiState.value.event)
    assertEquals("No event uses this code", viewModel.uiState.value.errorMsg)

    viewModel.onJoinCodeChange("NOPE2")
    assertNull(viewModel.uiState.value.errorMsg)
  }

  @Test
  fun apply_sendsAPendingRequestCoveringTheWholeEvent() {
    loadEvent()
    coEvery { requests.apply(any(), any()) } answers { secondArg() }

    viewModel.apply()

    val expected =
        MembershipRequest(
            userId = UID,
            availability = listOf(AvailabilitySlot(EVENT.startAt, EVENT.endAt)),
            createdAt = NOW,
        )
    coVerify(exactly = 1) { requests.apply(EVENT.id, expected) }
    assertTrue(viewModel.uiState.value.applied)
  }

  @Test
  fun apply_doesNothingTwiceOrWithoutAnEvent() {
    viewModel.apply()
    coVerify(exactly = 0) { requests.apply(any(), any()) }

    loadEvent()
    coEvery { requests.apply(any(), any()) } answers { secondArg() }
    viewModel.apply()
    viewModel.apply()
    coVerify(exactly = 1) { requests.apply(any(), any()) }
  }

  @Test
  fun apply_requiresASignedInUser() {
    loadEvent()
    userId = null

    viewModel.apply()

    assertEquals("You must be signed in to apply", viewModel.uiState.value.errorMsg)
    coVerify(exactly = 0) { requests.apply(any(), any()) }
  }

  @Test
  fun apply_showsAMessageForEachFailure() {
    loadEvent()
    fun failWith(error: Exception): String? {
      coEvery { requests.apply(any(), any()) } throws error
      viewModel.apply()
      return viewModel.uiState.value.errorMsg
    }

    assertEquals(
        "You are offline, try again later",
        failWith(MembershipRequestRepositoryException.Unavailable()),
    )
    assertEquals(
        "You cannot apply to this event",
        failWith(MembershipRequestRepositoryException.PermissionDenied()),
    )
    assertEquals("Something went wrong, try again", failWith(IllegalStateException()))
    assertFalse(viewModel.uiState.value.applied)
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
