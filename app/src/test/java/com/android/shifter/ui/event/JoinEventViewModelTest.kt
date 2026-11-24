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
import com.swent.shifter.model.shift.FakeShiftRepository
import com.swent.shifter.model.shift.Shift
import com.swent.shifter.model.shift.ShiftRepositoryException
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
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
  private val teams = FakeTeamRepository(listOf(team(TEAM_ID, "Welcome"), team("team-b", "Bar")))
  private var userId: String? = UID
  private var clock = NOW
  private val shifts = FakeShiftRepository()
  private val viewModel =
      JoinEventViewModel(events, requests, teams, { userId }, { clock }, shiftRepository = shifts)

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    coEvery { requests.getMembershipRequest(any(), any()) } returns null
  }

  @After fun resetMain() = Dispatchers.resetMain()

  @Test
  fun findEvent_showsTheEventFoundByTheTypedCode() {
    coEvery { events.getEventByJoinCode("ABC234") } returns EVENT

    viewModel.onJoinCodeChange(" ABC234 ")
    viewModel.findEvent()

    assertEquals(EVENT, viewModel.uiState.value.event)
    assertNull(viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun onCodeScanned_fillsInTheCodeAndShowsItsEvent() {
    coEvery { events.getEventByJoinCode("ABC234") } returns EVENT

    viewModel.onCodeScanned("ABC234")

    assertEquals("ABC234", viewModel.uiState.value.joinCode)
    assertEquals(EVENT, viewModel.uiState.value.event)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun onCodeScanned_withAnUnknownCode_saysSo() {
    coEvery { events.getEventByJoinCode(any()) } returns null

    viewModel.onCodeScanned("ABC234")

    assertEquals("ABC234", viewModel.uiState.value.joinCode)
    assertNull(viewModel.uiState.value.event)
    assertEquals(JoinEventError.UNKNOWN_CODE, viewModel.uiState.value.error)
  }

  @Test
  fun onCodeScanned_whileLoading_isIgnored() {
    val lookup = CompletableDeferred<Event?>()
    coEvery { events.getEventByJoinCode("ABC234") } coAnswers { lookup.await() }
    viewModel.onJoinCodeChange("ABC234")
    viewModel.findEvent()

    viewModel.onCodeScanned("XYZ789")
    lookup.complete(EVENT)

    assertEquals("ABC234", viewModel.uiState.value.joinCode)
    assertEquals(EVENT, viewModel.uiState.value.event)
    coVerify(exactly = 0) { events.getEventByJoinCode("XYZ789") }
  }

  @Test
  fun findEvent_matchesALowercaseCode() {
    coEvery { events.getEventByJoinCode("ABC234") } returns EVENT

    viewModel.onJoinCodeChange("abc234")
    viewModel.findEvent()

    assertEquals(EVENT, viewModel.uiState.value.event)
  }

  @Test
  fun findEvent_showsAMessageForEachFailure() {
    fun failWith(error: Exception): JoinEventError? {
      coEvery { events.getEventByJoinCode(any()) } throws error
      viewModel.onJoinCodeChange("ABC234")
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
    // A request found already sent keeps the volunteer on the event: only a new one leaves it.
    assertNull(viewModel.uiState.value.requestSentTo)
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
    assertEquals("ABC234", viewModel.uiState.value.joinCode)
  }

  @Test
  fun cancelLookup_whileLookingUp_dropsWhatItFindsAndKeepsTheCode() {
    val lookup = CompletableDeferred<Event?>()
    coEvery { events.getEventByJoinCode("ABC234") } coAnswers { lookup.await() }
    viewModel.onCodeScanned("ABC234")

    viewModel.cancelLookup()
    lookup.complete(EVENT)

    assertEquals("ABC234", viewModel.uiState.value.joinCode)
    assertNull(viewModel.uiState.value.event)
    assertNull(viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun cancelLookup_afterTheLookup_dropsItsError() {
    coEvery { events.getEventByJoinCode(any()) } returns null
    viewModel.onCodeScanned("ABC234")

    viewModel.cancelLookup()

    assertEquals("ABC234", viewModel.uiState.value.joinCode)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun cancelLookup_whileSendingARequest_keepsIt() {
    loadEvent()
    val sent = CompletableDeferred<MembershipRequest>()
    coEvery { requests.apply(any(), any()) } coAnswers { sent.await() }
    viewModel.applyToEvent()

    viewModel.cancelLookup()
    sent.complete(request(MembershipRequestStatus.PENDING))

    assertEquals(EVENT, viewModel.uiState.value.event)
    assertEquals(MembershipRequestStatus.PENDING, viewModel.uiState.value.requestStatus)
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
  fun findEvent_reportsAnUnknownCodeAndIgnoresAnIncompleteOne() {
    coEvery { events.getEventByJoinCode(any()) } returns null

    viewModel.findEvent()
    viewModel.onJoinCodeChange("NPE23")
    viewModel.findEvent()
    coVerify(exactly = 0) { events.getEventByJoinCode(any()) }

    viewModel.onJoinCodeChange("NPE234")
    viewModel.findEvent()
    assertNull(viewModel.uiState.value.event)
    assertEquals(JoinEventError.UNKNOWN_CODE, viewModel.uiState.value.error)

    viewModel.onJoinCodeChange("NPE23")
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun findEvent_withACharacterNoCodeUses_doesNotLookItUp() {
    viewModel.onJoinCodeChange("ABCDE0")
    viewModel.findEvent()

    coVerify(exactly = 0) { events.getEventByJoinCode(any()) }
    assertFalse(viewModel.uiState.value.canFind)
    assertTrue(viewModel.uiState.value.hasUnusedCharacter)
  }

  @Test
  fun onJoinCodeChange_upperCasesAndCutsThePastedCode() {
    viewModel.onJoinCodeChange("abc-234xyz")

    assertEquals("ABC234", viewModel.uiState.value.joinCode)
  }

  @Test
  fun canFind_onlyOnceTheCodeIsComplete() {
    viewModel.onJoinCodeChange("ABC23")
    assertFalse(viewModel.uiState.value.canFind)

    viewModel.onJoinCodeChange("ABC234")
    assertTrue(viewModel.uiState.value.canFind)
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
  fun apply_reportsTheEventTheRequestWasSentTo_untilHandled() {
    loadEvent()
    coEvery { requests.apply(any(), any()) } answers { secondArg() }

    viewModel.applyToEvent()
    assertEquals(EVENT.title, viewModel.uiState.value.requestSentTo)

    viewModel.onRequestSentHandled()
    assertNull(viewModel.uiState.value.requestSentTo)
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
    assertNull(viewModel.uiState.value.requestSentTo)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  /** Finds [EVENT], then picks [team] in it unless it is null. */
  private fun loadEvent(team: String? = TEAM_ID) {
    coEvery { events.getEventByJoinCode(any()) } returns EVENT
    viewModel.onJoinCodeChange("ABC234")
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

  // ── The shifts the volunteer picks (#600) ──

  @Test
  fun findEvent_loadsTheShiftsOfTheEvent() {
    shifts.put(shift(FRIDAY_18))
    shifts.put(shift(SATURDAY_10).copy(eventId = "event-2"))

    loadEvent()

    assertEquals(listOf(shift(FRIDAY_18)), viewModel.uiState.value.shiftPicking.shifts)
    assertEquals(emptySet<String>(), viewModel.uiState.value.shiftPicking.pickedShiftIds)
  }

  @Test
  fun findEvent_showsTheShiftsOfTheRequestAlreadySent() {
    seedShifts()
    coEvery { requests.getMembershipRequest(EVENT.id, UID) } returns
        request(MembershipRequestStatus.PENDING).copy(shiftIds = listOf(shift(SATURDAY_10).id))

    loadEvent(team = null)
    viewModel.onShiftPicked(shift(FRIDAY_18).id)

    assertEquals(setOf(shift(SATURDAY_10).id), viewModel.uiState.value.shiftPicking.pickedShiftIds)
  }

  @Test
  fun findEvent_showsAnErrorWhenTheShiftsCannotBeLoaded() {
    fun failWith(error: Exception): JoinEventError? {
      shifts.failure = error
      loadEvent()
      return viewModel.uiState.value.error
    }

    assertEquals(JoinEventError.OFFLINE, failWith(ShiftRepositoryException.Unavailable()))
    assertEquals(
        JoinEventError.PERMISSION_DENIED,
        failWith(ShiftRepositoryException.PermissionDenied()),
    )
    assertEquals(JoinEventError.UNEXPECTED, failWith(ShiftRepositoryException.Unknown()))
    assertNull(viewModel.uiState.value.event)
  }

  @Test
  fun onShiftPicked_onTwoDays_picksBoth() {
    seedShifts()
    loadEvent()

    viewModel.onShiftPicked(shift(FRIDAY_18).id)
    viewModel.onShiftPicked(shift(SATURDAY_10).id)

    assertEquals(
        listOf(shift(FRIDAY_18), shift(SATURDAY_10)),
        viewModel.uiState.value.shiftPicking.picked,
    )
  }

  @Test
  fun onShiftPicked_again_letsItGo() {
    seedShifts()
    loadEvent()

    viewModel.onShiftPicked(shift(FRIDAY_18).id)
    viewModel.onShiftPicked(shift(FRIDAY_18).id)

    assertEquals(emptySet<String>(), viewModel.uiState.value.shiftPicking.pickedShiftIds)
  }

  @Test
  fun onShiftPicked_ignoresAShiftTheEventDoesNotHave() {
    seedShifts()
    loadEvent()

    viewModel.onShiftPicked("not-a-shift")

    assertEquals(emptySet<String>(), viewModel.uiState.value.shiftPicking.pickedShiftIds)
  }

  @Test
  fun onShiftPicked_onAnEventThatHasEnded_picksNothing() {
    seedShifts()
    findEndedEvent()

    viewModel.onShiftPicked(shift(FRIDAY_18).id)

    assertEquals(emptySet<String>(), viewModel.uiState.value.shiftPicking.pickedShiftIds)
  }

  @Test
  fun onShiftPicked_onceApplied_isIgnored() {
    seedShifts()
    loadEvent()
    viewModel.onShiftPicked(shift(FRIDAY_18).id)
    coEvery { requests.apply(any(), any()) } answers { secondArg() }
    viewModel.applyToEvent()

    viewModel.onShiftPicked(shift(SATURDAY_10).id)

    assertEquals(setOf(shift(FRIDAY_18).id), viewModel.uiState.value.shiftPicking.pickedShiftIds)
  }

  @Test
  fun apply_withoutAShiftWhenTheEventHasSome_sendsNothing() {
    seedShifts()
    loadEvent()
    coEvery { requests.apply(any(), any()) } answers { secondArg() }

    viewModel.applyToEvent()

    assertFalse(viewModel.uiState.value.canApply)
    coVerify(exactly = 0) { requests.apply(any(), any()) }
  }

  @Test
  fun apply_sendsTheShiftsPickedAndTheirSlotsJoined() {
    seedShifts()
    loadEvent()
    coEvery { requests.apply(any(), any()) } answers { secondArg() }

    viewModel.onShiftPicked(shift(SATURDAY_12).id)
    viewModel.onShiftPicked(shift(FRIDAY_18).id)
    viewModel.onShiftPicked(shift(SATURDAY_10).id)
    viewModel.applyToEvent()

    val expected =
        MembershipRequest(
            userId = UID,
            teamId = TEAM_ID,
            availability =
                listOf(
                    AvailabilitySlot(FRIDAY_18, FRIDAY_18.plusHours(2)),
                    AvailabilitySlot(SATURDAY_10, SATURDAY_12.plusHours(2)),
                ),
            shiftIds = listOf(shift(FRIDAY_18).id, shift(SATURDAY_10).id, shift(SATURDAY_12).id),
            createdAt = NOW,
        )
    coVerify(exactly = 1) { requests.apply(EVENT.id, expected) }
  }

  @Test
  fun apply_withoutAShiftRepository_coversTheWholeEvent() {
    val withoutShifts = JoinEventViewModel(events, requests, teams, { userId }, { clock })
    coEvery { events.getEventByJoinCode(any()) } returns EVENT
    coEvery { requests.apply(any(), any()) } answers { secondArg() }

    withoutShifts.onJoinCodeChange("ABC234")
    withoutShifts.findEvent()
    withoutShifts.onTeamSelected(TEAM_ID)
    withoutShifts.applyToEvent()

    coVerify(exactly = 1) {
      requests.apply(
          EVENT.id,
          match {
            it.availability == listOf(AvailabilitySlot(EVENT.startAt, EVENT.endAt)) &&
                it.shiftIds.isEmpty()
          },
      )
    }
  }

  @Test
  fun changeCode_dropsTheShiftsAndThePicks() {
    seedShifts()
    loadEvent()
    viewModel.onShiftPicked(shift(FRIDAY_18).id)

    viewModel.changeCode()

    assertEquals(ShiftPicking(), viewModel.uiState.value.shiftPicking)
  }

  /** Opens three shifts of [EVENT]: Friday 18–20, then Saturday 10–12 and 12–14, back to back. */
  private fun seedShifts() =
      listOf(FRIDAY_18, SATURDAY_10, SATURDAY_12).forEach { shifts.put(shift(it)) }

  private fun shift(startAt: Instant) =
      Shift(eventId = EVENT.id, startAt = startAt, endAt = startAt.plusHours(2), createdAt = NOW)

  private fun Instant.plusHours(hours: Long): Instant = plusSeconds(hours * 3_600)

  // ── An event that has ended (#409) ──

  /** Finds [EVENT], ended exactly now: its end is no longer in the future. */
  private fun findEndedEvent() {
    coEvery { events.getEventByJoinCode("ABC234") } returns EVENT.copy(endAt = NOW)
    viewModel.onJoinCodeChange("ABC234")
    viewModel.findEvent()
  }

  @Test
  fun findEvent_withAnEventThatHasEnded_saysSo() {
    findEndedEvent()

    assertTrue(viewModel.uiState.value.hasEnded)
  }

  @Test
  fun findEvent_withAnEventStillRunning_takesApplications() {
    coEvery { events.getEventByJoinCode("ABC234") } returns EVENT

    viewModel.onJoinCodeChange("ABC234")
    viewModel.findEvent()

    assertFalse(viewModel.uiState.value.hasEnded)
  }

  @Test
  fun onTeamSelected_onAnEventThatHasEnded_picksNoTeam() {
    findEndedEvent()

    viewModel.onTeamSelected(TEAM_ID)

    assertNull(viewModel.uiState.value.selectedTeamId)
  }

  @Test
  fun applyToEvent_afterTheEventEndsWhileOpen_sendsNothingAndSaysSo() {
    loadEvent()
    coEvery { requests.apply(any(), any()) } answers { secondArg() }
    clock = EVENT.endAt

    viewModel.applyToEvent()

    coVerify(exactly = 0) { requests.apply(any(), any()) }
    assertTrue(viewModel.uiState.value.hasEnded)
    assertNull(viewModel.uiState.value.requestStatus)
  }

  @Test
  fun changeCode_afterAnEventThatHasEnded_forgetsIt() {
    findEndedEvent()

    viewModel.changeCode()

    assertFalse(viewModel.uiState.value.hasEnded)
  }

  private companion object {
    const val UID = "volunteer-1"
    const val TEAM_ID = "team-a"
    val NOW: Instant = Instant.ofEpochSecond(600)
    val FRIDAY_18: Instant = Instant.parse("2026-06-19T16:00:00Z")
    val SATURDAY_10: Instant = Instant.parse("2026-06-20T08:00:00Z")
    val SATURDAY_12: Instant = Instant.parse("2026-06-20T10:00:00Z")
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
