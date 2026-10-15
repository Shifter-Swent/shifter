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
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
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
  private val teams = FakeTeamRepository(listOf(team(TEAM_ID, "Welcome"), team("team-b", "Bar")))
  private var userId: String? = UID
  private val viewModel = JoinEventViewModel(events, requests, teams, { userId }, { NOW })

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    coEvery { requests.getMembershipRequest(any(), any()) } returns null
  }

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
  fun findEvent_showsTheRequestTheVolunteerAlreadySent() {
    coEvery { requests.getMembershipRequest(EVENT.id, UID) } returns
        request(MembershipRequestStatus.REJECTED)

    loadEvent()

    assertEquals(EVENT, viewModel.uiState.value.event)
    assertEquals(MembershipRequestStatus.REJECTED, viewModel.uiState.value.requestStatus)
    viewModel.applyToEvent()
    coVerify(exactly = 0) { requests.apply(any(), any()) }
  }

  @Test
  fun findEvent_showsTheRequestAgainAfterChangingTheCode() {
    loadEvent()
    coEvery { requests.apply(any(), any()) } answers { secondArg() }
    viewModel.applyToEvent()
    coEvery { requests.getMembershipRequest(EVENT.id, UID) } returns
        request(MembershipRequestStatus.PENDING)

    viewModel.changeCode()
    viewModel.findEvent()

    assertEquals(MembershipRequestStatus.PENDING, viewModel.uiState.value.requestStatus)
  }

  @Test
  fun findEvent_listsTheTeamsOfTheEventByName() {
    teams.teams += team("team-c", "Another event's team", eventId = "event-2")

    loadEvent(team = null)

    assertEquals(listOf("Bar", "Welcome"), viewModel.uiState.value.teams.map { it.name })
    assertNull(viewModel.uiState.value.selectedTeamId)
  }

  @Test
  fun findEvent_showsTheTeamOfTheRequestAlreadySent() {
    coEvery { requests.getMembershipRequest(EVENT.id, UID) } returns
        request(MembershipRequestStatus.PENDING).copy(teamId = "team-b")

    loadEvent(team = null)
    viewModel.onTeamSelected(TEAM_ID)

    assertEquals("team-b", viewModel.uiState.value.selectedTeamId)
  }

  @Test
  fun findEvent_showsAnErrorWhenTheTeamsCannotBeLoaded() {
    teams.failure = IllegalStateException("backend down")

    loadEvent(team = null)

    assertEquals(JoinEventError.UNEXPECTED, viewModel.uiState.value.error)
    assertNull(viewModel.uiState.value.event)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun findEvent_doesNotLookForARequestWithoutAUserOrAnEvent() {
    userId = null
    loadEvent()
    assertEquals(EVENT, viewModel.uiState.value.event)
    assertNull(viewModel.uiState.value.requestStatus)

    userId = UID
    coEvery { events.getEventByJoinCode(any()) } returns null
    viewModel.findEvent()

    coVerify(exactly = 0) { requests.getMembershipRequest(any(), any()) }
  }

  @Test
  fun findEvent_showsAnErrorWhenTheRequestCannotBeLoaded() {
    coEvery { requests.getMembershipRequest(any(), any()) } throws
        MembershipRequestRepositoryException.Unavailable()

    loadEvent()

    assertEquals(JoinEventError.OFFLINE, viewModel.uiState.value.error)
    assertNull(viewModel.uiState.value.event)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun changeCode_leavesTheEventAndItsTeamAndKeepsTheCode() {
    loadEvent()

    viewModel.changeCode()

    assertNull(viewModel.uiState.value.event)
    assertEquals(emptyList<Team>(), viewModel.uiState.value.teams)
    assertNull(viewModel.uiState.value.selectedTeamId)
    assertEquals("ABC123", viewModel.uiState.value.joinCode)
  }

  @Test
  fun findEvent_clearsTheTeamPickedInThePreviousEvent() {
    loadEvent()

    viewModel.findEvent()

    assertEquals(EVENT, viewModel.uiState.value.event)
    assertNull(viewModel.uiState.value.selectedTeamId)
  }

  @Test
  fun onTeamSelected_keepsOnlyTheLastPick() {
    loadEvent(team = null)

    viewModel.onTeamSelected("team-a")
    viewModel.onTeamSelected("team-b")

    assertEquals("team-b", viewModel.uiState.value.selectedTeamId)
  }

  @Test
  fun onTeamSelected_ignoresATeamTheEventDoesNotHave() {
    loadEvent()

    viewModel.onTeamSelected("team-of-another-event")

    assertEquals(TEAM_ID, viewModel.uiState.value.selectedTeamId)
  }

  @Test
  fun onTeamSelected_isIgnoredWithoutAnEventOrOnceApplied() {
    viewModel.onTeamSelected(TEAM_ID)
    assertNull(viewModel.uiState.value.selectedTeamId)

    loadEvent()
    coEvery { requests.apply(any(), any()) } answers { secondArg() }
    viewModel.applyToEvent()
    viewModel.onTeamSelected("team-b")

    assertEquals(TEAM_ID, viewModel.uiState.value.selectedTeamId)
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
            teamId = TEAM_ID,
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
  fun apply_doesNothingUntilATeamIsPicked() {
    loadEvent(team = null)
    coEvery { requests.apply(any(), any()) } answers { secondArg() }

    viewModel.applyToEvent()

    coVerify(exactly = 0) { requests.apply(any(), any()) }
    assertNull(viewModel.uiState.value.requestStatus)
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

  /** Finds [EVENT], then picks [team] in it unless it is null. */
  private fun loadEvent(team: String? = TEAM_ID) {
    coEvery { events.getEventByJoinCode(any()) } returns EVENT
    viewModel.onJoinCodeChange("ABC123")
    viewModel.findEvent()
    team?.let(viewModel::onTeamSelected)
  }

  private fun request(status: MembershipRequestStatus) =
      MembershipRequest(
          userId = UID,
          teamId = TEAM_ID,
          availability = listOf(AvailabilitySlot(EVENT.startAt, EVENT.endAt)),
          createdAt = NOW,
          status = status,
      )

  private fun team(id: String, name: String, eventId: String = EVENT.id) =
      Team(
          id = id,
          eventId = eventId,
          name = name,
          description = "",
          icon = "star",
          createdAt = NOW,
      )

  private companion object {
    const val UID = "volunteer-1"
    const val TEAM_ID = "team-a"
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
