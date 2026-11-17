// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.checkin

import androidx.lifecycle.viewmodel.CreationExtras
import com.swent.shifter.model.checkin.CheckIn
import com.swent.shifter.model.checkin.CheckInEligibility
import com.swent.shifter.model.checkin.CheckInEligibility.Reason
import com.swent.shifter.model.checkin.CheckInMethod
import com.swent.shifter.model.checkin.CheckInRepository
import com.swent.shifter.model.checkin.CheckInRepositoryException
import com.swent.shifter.model.checkin.FakeCheckInRepository
import com.swent.shifter.model.checkin.checkInWindow
import com.swent.shifter.model.location.FakeLocationProvider
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.location.LocationStatus
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionLocation
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.mission.MissionRepositoryException
import com.swent.shifter.utils.MainDispatcherRule
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoCheckInViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val missions = FakeMissionRepository()
  private val checkIns = FakeCheckInRepository(currentUserId = USER)
  private val location = FakeLocationProvider()

  private var userId: String? = USER
  private var clock: Instant = NOW

  /** Only [NEAR] is eligible, whatever the mission and the time. */
  private var isEligible: (Mission, Instant, GeoPosition) -> Boolean = { _, _, at -> at == NEAR }

  /** Why a position [isEligible] rejects is not eligible. */
  private var ineligibleReason = Reason.TOO_FAR

  /** The instants [isEligible] was asked about, in order. */
  private val eligibleAt = mutableListOf<Instant>()

  private lateinit var viewModel: AutoCheckInViewModel
  private lateinit var screen: Job

  private val state
    get() = viewModel.uiState.value

  /** Opens the screen: the ViewModel only works while its state is collected. */
  private fun TestScope.open(
      repository: CheckInRepository = checkIns,
      missionRepository: MissionRepository = missions,
  ) {
    viewModel =
        AutoCheckInViewModel(
            missionRepository,
            repository,
            location,
            { userId },
            { mission, time, at ->
              eligibleAt += time
              if (isEligible(mission, time, at)) CheckInEligibility.Eligible
              else CheckInEligibility.Ineligible(ineligibleReason)
            },
            { clock },
        )
    screen =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          viewModel.uiState.collect {}
        }
    runCurrent()
  }

  /** Whether the volunteer is checked in to [mission]. */
  private fun isCheckedIn(mission: Mission): Boolean =
      checkIns.checkIns.any { it.eventId == mission.eventId && it.missionId == mission.id }

  private suspend fun TestScope.moveTo(position: GeoPosition) {
    location.emit(position)
    runCurrent()
  }

  private suspend fun addMission(
      title: String = "Bar",
      eventId: String = EVENT,
      at: MissionLocation? = PLACE,
      endsIn: Duration = Duration.ofHours(1),
      startAt: Instant = NOW,
      assignees: List<String> = listOf(USER),
  ): Mission =
      missions.createMission(
          Mission(
              eventId = eventId,
              title = title,
              description = "",
              volunteersNeeded = 2,
              startAt = startAt,
              endAt = startAt + endsIn,
              location = at,
              assigneeIds = assignees,
              createdAt = NOW,
          )
      )

  @Test
  fun eligiblePosition_checksIn() = runTest {
    val mission = addMission()
    open()

    moveTo(NEAR)

    val checkIn = checkIns.checkIns.single()
    assertEquals(
        listOf(EVENT, mission.id, USER),
        listOf(checkIn.eventId, checkIn.missionId, checkIn.userId),
    )
    assertEquals(CheckInMethod.SELF, checkIn.method)
    assertNull(state.whyNotCheckedIn(mission))
  }

  @Test
  fun severalEligiblePositions_checkInOnce() = runTest {
    addMission()
    val counting = CountsCalls(checkIns)
    open(counting)

    repeat(3) { moveTo(NEAR) }

    assertEquals(1, counting.calls)
  }

  @Test
  fun eachPosition_isCheckedAtItsOwnTime() = runTest {
    addMission()
    open()
    clock = NOW + Duration.ofMinutes(5)

    moveTo(FAR)

    assertEquals(listOf(clock), eligibleAt)
  }

  @Test
  fun ineligiblePosition_doesNotCheckIn() = runTest {
    addMission()
    open()

    moveTo(FAR)

    assertTrue(checkIns.checkIns.isEmpty())
  }

  @Test
  fun alreadyCheckedIn_skipsTheMission() = runTest {
    val mission = addMission()
    checkIns.add(CheckIn(EVENT, mission.id, USER, NOW, CheckInMethod.MANAGER, "manager"))
    val counting = CountsCalls(checkIns)
    open(counting)

    moveTo(NEAR)

    assertEquals(0, counting.calls)
  }

  @Test
  fun eligibleForTwoMissions_checksInToEach() = runTest {
    addMission("Bar")
    addMission("Stage", eventId = "other-event")
    open()

    moveTo(NEAR)

    assertEquals(setOf(EVENT, "other-event"), checkIns.checkIns.map { it.eventId }.toSet())
  }

  @Test
  fun missionWithoutLocationOrOver_isNotTried() = runTest {
    addMission("Anywhere", at = null)
    // Ended a minute ago, after an hour: a mission always ends after it starts.
    addMission("Over", startAt = NOW - Duration.ofHours(1), endsIn = Duration.ofMinutes(59))
    isEligible = { _, _, _ -> true }
    open()

    moveTo(NEAR)

    assertTrue(checkIns.checkIns.isEmpty())
  }

  @Test
  fun missionAtTheEndOfItsWindow_isStillTried() = runTest {
    // Ending right now, after an hour.
    addMission(startAt = NOW - Duration.ofHours(1), endsIn = Duration.ofHours(1))
    open()

    moveTo(NEAR)

    assertEquals(1, checkIns.checkIns.size)
  }

  @Test
  fun positions_stopWhenTheLastWindowEnds() = runTest {
    val windowEnd = addMission(endsIn = Duration.ofMinutes(30)).checkInWindow().endInclusive
    open()
    assertEquals(1, location.activeCollectors)

    // The screen stays open and the assignments do not change: only the time passes.
    val untilTheEnd = Duration.between(clock, windowEnd).toMillis()
    clock = windowEnd.plusSeconds(1)
    advanceTimeBy(untilTheEnd + 2)

    assertEquals(0, location.activeCollectors)
  }

  /**
   * The position read for a mission whose window is over says nothing about where the volunteer is
   * when the next one is assigned, maybe hours later: only a new position may check them in.
   */
  @Test
  fun missionAssignedAfterThePositionsStopped_waitsForANewPosition() = runTest {
    val windowEnd = addMission("Bar", endsIn = Duration.ofMinutes(30)).checkInWindow().endInclusive
    // Bar is never eligible, so the position below checks in to nothing and stays the last one.
    isEligible = { mission, _, at -> mission.title == "Stage" && at == NEAR }
    open()
    moveTo(NEAR)
    clock = windowEnd.plusSeconds(1)
    advanceTimeBy(Duration.between(NOW, clock).toMillis() + 1)
    assertEquals(0, location.activeCollectors)

    val stage = addMission("Stage", eventId = "other-event")
    runCurrent()

    assertTrue(checkIns.checkIns.isEmpty())
    moveTo(NEAR)
    assertTrue(isCheckedIn(stage))
  }

  @Test
  fun missionClosedByHand_stopsThePositionsAndIsNotTried() = runTest {
    val mission = addMission()
    isEligible = { _, _, _ -> true }
    open()
    assertEquals(1, location.activeCollectors)

    missions.markCompleted(EVENT, mission.id)
    runCurrent()
    moveTo(NEAR)

    assertEquals(0, location.activeCollectors)
    assertTrue(checkIns.checkIns.isEmpty())
  }

  @Test
  fun notAllowed_isWhyAndDoesNotRetry() = runTest {
    val mission = addMission()
    open()
    checkIns.failure = CheckInRepositoryException.NotAllowed()

    moveTo(NEAR)
    checkIns.failure = null
    moveTo(NEAR)

    assertEquals(NotCheckedIn.REFUSED, state.whyNotCheckedIn(mission))
    assertTrue(checkIns.checkIns.isEmpty())
  }

  @Test
  fun offline_isWhyAndRetriesOnTheNextPosition() = runTest {
    val mission = addMission()
    open()
    checkIns.failure = CheckInRepositoryException.Unavailable()

    moveTo(NEAR)
    assertEquals(NotCheckedIn.OFFLINE, state.whyNotCheckedIn(mission))
    checkIns.failure = null
    moveTo(NEAR)

    assertEquals(1, checkIns.checkIns.size)
    assertNull(state.whyNotCheckedIn(mission))
  }

  @Test
  fun checkInThatNeverAnswers_showsOfflineAfterTheTimeout() = runTest {
    val mission = addMission()
    open(NeverAnswers(checkIns))

    moveTo(NEAR)
    assertNull(state.whyNotCheckedIn(mission))
    advanceTimeBy(AutoCheckInViewModel.CHECK_IN_TIMEOUT_MILLIS + 1)

    assertEquals(NotCheckedIn.OFFLINE, state.whyNotCheckedIn(mission))
  }

  @Test
  fun positionOutsideTheZone_isWhyTheMissionIsNotCheckedIn() = runTest {
    val mission = addMission()
    open()

    moveTo(FAR)

    assertEquals(NotCheckedIn.OUTSIDE_ZONE, state.whyNotCheckedIn(mission))
  }

  @Test
  fun positionInTheZone_clearsWhyAndChecksIn() = runTest {
    val mission = addMission()
    open()
    moveTo(FAR)

    moveTo(NEAR)

    assertNull(state.whyNotCheckedIn(mission))
    assertEquals(1, checkIns.checkIns.size)
  }

  @Test
  fun ineligibleForAnotherReason_isNotOutsideTheZone() = runTest {
    val mission = addMission()
    open()
    moveTo(FAR)

    ineligibleReason = Reason.TOO_EARLY
    moveTo(FAR)

    assertNull(state.whyNotCheckedIn(mission))
  }

  @Test
  fun refusal_isWhyOnlyThatMissionIsNotCheckedIn() = runTest {
    val refused = addMission(title = "Bar")
    val other = addMission(title = "Stage")
    open(RefusesMission(checkIns, refused.id))

    moveTo(NEAR)

    assertEquals(NotCheckedIn.REFUSED, state.whyNotCheckedIn(refused))
    assertNull(state.whyNotCheckedIn(other))
    assertEquals(listOf(other.id), checkIns.checkIns.map { it.missionId })
  }

  @Test
  fun offline_isWhyUntilTheRetrySucceeds() = runTest {
    val mission = addMission()
    open()
    checkIns.failure = CheckInRepositoryException.Unavailable()
    moveTo(NEAR)
    assertEquals(NotCheckedIn.OFFLINE, state.whyNotCheckedIn(mission))

    checkIns.failure = null
    moveTo(NEAR)

    assertNull(state.whyNotCheckedIn(mission))
  }

  @Test
  fun unexpectedFailure_isWhyTheMissionIsNotCheckedIn() = runTest {
    val mission = addMission()
    open()
    checkIns.failure = IllegalStateException("boom")

    moveTo(NEAR)

    assertEquals(NotCheckedIn.UNEXPECTED, state.whyNotCheckedIn(mission))
  }

  @Test
  fun checkedInByTheManager_clearsWhy() = runTest {
    val mission = addMission()
    open()
    moveTo(FAR)

    checkIns.add(CheckIn(EVENT, mission.id, USER, NOW, CheckInMethod.MANAGER, "manager"))
    runCurrent()

    assertNull(state.whyNotCheckedIn(mission))
  }

  @Test
  fun missionNoLongerOpen_dropsWhy() = runTest {
    val mission = addMission()
    open()
    moveTo(FAR)

    missions.updateAssignees(EVENT, mission.id, emptyList())
    runCurrent()

    assertNull(state.whyNotCheckedIn(mission))
  }

  @Test
  fun refusal_staysOnLaterPositions() = runTest {
    val mission = addMission()
    open()
    checkIns.failure = CheckInRepositoryException.NotAllowed()
    moveTo(NEAR)

    moveTo(FAR)

    assertEquals(NotCheckedIn.REFUSED, state.whyNotCheckedIn(mission))
  }

  @Test
  fun refusedThenCheckedInByTheManager_clearsWhy() = runTest {
    val mission = addMission()
    open()
    checkIns.failure = CheckInRepositoryException.NotAllowed()
    moveTo(NEAR)
    checkIns.failure = null

    checkIns.add(CheckIn(EVENT, mission.id, USER, NOW, CheckInMethod.MANAGER, "manager"))
    runCurrent()

    assertNull(state.whyNotCheckedIn(mission))
  }

  @Test
  fun offlineThenOutsideTheZone_isOutsideTheZone() = runTest {
    val mission = addMission()
    open()
    checkIns.failure = CheckInRepositoryException.Unavailable()
    moveTo(NEAR)
    checkIns.failure = null

    moveTo(FAR)

    assertEquals(NotCheckedIn.OUTSIDE_ZONE, state.whyNotCheckedIn(mission))
  }

  @Test
  fun retryInFlight_keepsOffline() = runTest {
    val mission = addMission()
    open(NeverAnswers(checkIns))
    moveTo(NEAR)
    advanceTimeBy(AutoCheckInViewModel.CHECK_IN_TIMEOUT_MILLIS + 1)

    moveTo(NEAR)
    advanceTimeBy(AutoCheckInViewModel.CHECK_IN_TIMEOUT_MILLIS / 2)

    assertEquals(NotCheckedIn.OFFLINE, state.whyNotCheckedIn(mission))
  }

  @Test
  fun failureAfterTheMissionLeft_isNotRecorded() = runTest {
    val mission = addMission()
    open(NeverAnswers(checkIns))
    moveTo(NEAR)

    missions.updateAssignees(EVENT, mission.id, emptyList())
    runCurrent()
    advanceTimeBy(AutoCheckInViewModel.CHECK_IN_TIMEOUT_MILLIS + 1)

    assertNull(state.whyNotCheckedIn(mission))
  }

  @Test
  fun timeoutAfterBeingCheckedIn_isNotRecorded() = runTest {
    val mission = addMission()
    open(NeverAnswers(checkIns))
    moveTo(NEAR)

    // The pending write shows in the check-ins, as Firestore does offline.
    checkIns.add(CheckIn(EVENT, mission.id, USER, NOW, CheckInMethod.SELF, USER))
    runCurrent()
    advanceTimeBy(AutoCheckInViewModel.CHECK_IN_TIMEOUT_MILLIS + 1)

    assertNull(state.whyNotCheckedIn(mission))
  }

  @Test
  fun locationTurnedOff_dropsOutsideTheZone() = runTest {
    val mission = addMission()
    open()
    moveTo(FAR)

    location.setStatus(LocationStatus.LOCATION_OFF)
    runCurrent()

    assertNull(state.whyNotCheckedIn(mission))
  }

  @Test
  fun missionsUpdateFailure_dropsTheReasons() = runTest {
    val mission = addMission()
    open()
    moveTo(FAR)

    missions.failure = MissionRepositoryException.Unavailable()
    runCurrent()

    assertNull(state.whyNotCheckedIn(mission))
  }

  @Test
  fun positionsWhileCheckingIn_areIgnored() = runTest {
    addMission()
    val slow = NeverAnswers(checkIns)
    open(slow)

    repeat(3) { moveTo(NEAR) }

    assertEquals(1, slow.calls)
  }

  @Test
  fun missionsLoadFailure_readsNoPosition() = runTest {
    addMission()
    missions.failure = MissionRepositoryException.Unavailable()

    open()

    assertEquals(0, location.activeCollectors)
  }

  @Test
  fun missionAssignedAfterOpening_isCheckedIn() = runTest {
    open()

    addMission()
    runCurrent()
    moveTo(NEAR)

    assertEquals(1, checkIns.checkIns.size)
  }

  @Test
  fun noMissionAtOpening_readsNoPositionUntilOneIsAssigned() = runTest {
    open()
    assertEquals(0, location.activeCollectors)

    addMission()
    runCurrent()

    assertEquals(1, location.activeCollectors)
  }

  @Test
  fun missionOfANewEventAlreadyCheckedIn_isNotTriedAgain() = runTest {
    val bar = addMission("Bar")
    val stage = addMission("Stage", eventId = "other-event", assignees = emptyList())
    checkIns.add(CheckIn("other-event", stage.id, USER, NOW, CheckInMethod.MANAGER, "manager"))
    val counting = CountsCalls(checkIns)
    open(counting)

    missions.updateAssignees("other-event", stage.id, listOf(USER))
    runCurrent()
    moveTo(NEAR)

    // Only Bar is tried: the check-ins of the new event were read before the position.
    assertEquals(1, counting.calls)
    assertTrue(isCheckedIn(bar))
  }

  @Test
  fun missionOfANewEvent_waitsForItsCheckIns() = runTest {
    val bar = addMission("Bar")
    val counting = CountsCalls(SilentEvent(checkIns, "other-event"))
    open(counting)

    addMission("Stage", eventId = "other-event")
    runCurrent()
    moveTo(NEAR)

    // Stage could already be checked in to: it is not tried before its check-ins are known.
    assertEquals(1, counting.calls)
    assertTrue(isCheckedIn(bar))
  }

  @Test
  fun positionBeforeTheCheckInsAreKnown_checksInWhenTheyArrive() = runTest {
    val mission = addMission()
    val late = LateCheckIns(checkIns)
    open(late)

    moveTo(NEAR)
    assertTrue(checkIns.checkIns.isEmpty())
    late.arrive()
    runCurrent()

    // Checked in without waiting for the next position.
    assertEquals(1, checkIns.checkIns.size)
    assertTrue(isCheckedIn(mission))
  }

  @Test
  fun missionRefusedThenAssignedAgain_isTriedAgain() = runTest {
    val mission = addMission()
    open()
    checkIns.failure = CheckInRepositoryException.NotAllowed()
    moveTo(NEAR)
    checkIns.failure = null

    // Unassigned by mistake, then assigned again by the manager.
    missions.updateAssignees(EVENT, mission.id, emptyList())
    runCurrent()
    missions.updateAssignees(EVENT, mission.id, listOf(USER))
    runCurrent()
    moveTo(NEAR)

    assertEquals(1, checkIns.checkIns.size)
    assertTrue(isCheckedIn(mission))
  }

  @Test
  fun missionUnassignedWhileOpen_isNotTried() = runTest {
    val mission = addMission()
    open()

    missions.updateAssignees(EVENT, mission.id, emptyList())
    runCurrent()
    moveTo(NEAR)

    assertTrue(checkIns.checkIns.isEmpty())
    assertEquals(0, location.activeCollectors)
  }

  @Test
  fun missionsUpdateFailure_stopsReadingPositions() = runTest {
    addMission()
    open()
    assertEquals(1, location.activeCollectors)

    // The listener closes while the screen is open, as Firestore does mid-session.
    missions.failure = MissionRepositoryException.Unavailable()
    runCurrent()

    assertEquals(0, location.activeCollectors)
  }

  @Test
  fun notSignedIn_readsNoPosition() = runTest {
    addMission()
    userId = null

    open()

    assertEquals(0, location.activeCollectors)
  }

  @Test
  fun locationStatus_followsTheProvider() = runTest {
    open()
    location.deviceStatus = LocationStatus.DENIED

    viewModel.refreshLocation()
    runCurrent()

    assertEquals(LocationStatus.DENIED, state.locationStatus)
  }

  @Test
  fun leavingTheScreen_stopsThePositions() = runTest {
    addMission()
    open()
    assertEquals(1, location.activeCollectors)

    screen.cancel()
    advanceTimeBy(AutoCheckInViewModel.STOP_TIMEOUT_MILLIS + 1)

    assertEquals(0, location.activeCollectors)
  }

  @Test
  fun factory_checksInWhereTheEligibilityRuleSays() = runTest {
    // The factory reads the real clock: the mission is going on now.
    addMission(startAt = Instant.now())
    viewModel =
        AutoCheckInViewModel.factory(missions, checkIns, location, { userId })
            .create(AutoCheckInViewModel::class.java, CreationExtras.Empty)
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    runCurrent()

    moveTo(FAR)
    assertTrue(checkIns.checkIns.isEmpty())
    moveTo(NEAR)

    assertEquals(1, checkIns.checkIns.size)
  }

  /** Delegates to [checkIns], counting the check-ins asked for. */
  private class CountsCalls(private val checkIns: CheckInRepository) :
      CheckInRepository by checkIns {
    /** How many check-ins were asked for. */
    var calls = 0

    override suspend fun checkIn(
        eventId: String,
        missionId: String,
        userId: String,
        method: CheckInMethod,
    ) {
      calls++
      checkIns.checkIn(eventId, missionId, userId, method)
    }
  }

  /** Delegates to [checkIns], except that the check-in to [missionId] is refused. */
  private class RefusesMission(checkIns: CheckInRepository, private val missionId: String) :
      CheckInRepository by checkIns {
    private val delegate = checkIns

    override suspend fun checkIn(
        eventId: String,
        missionId: String,
        userId: String,
        method: CheckInMethod,
    ) {
      if (missionId == this.missionId) throw CheckInRepositoryException.NotAllowed()
      delegate.checkIn(eventId, missionId, userId, method)
    }
  }

  /** Delegates to [checkIns], except that the check-ins of [eventId] are never emitted. */
  private class SilentEvent(checkIns: CheckInRepository, private val eventId: String) :
      CheckInRepository by checkIns {
    private val delegate = checkIns

    override fun observeMyCheckIns(eventId: String, userId: String): Flow<List<CheckIn>> =
        if (eventId == this.eventId) flow { awaitCancellation() }
        else delegate.observeMyCheckIns(eventId, userId)
  }

  /** Delegates to [checkIns], except that no check-ins are emitted before [arrive] is called. */
  private class LateCheckIns(checkIns: CheckInRepository) : CheckInRepository by checkIns {
    private val delegate = checkIns
    private val arrived = CompletableDeferred<Unit>()

    /** Lets the check-ins through, as when Firestore answers. */
    fun arrive() = arrived.complete(Unit)

    override fun observeMyCheckIns(eventId: String, userId: String): Flow<List<CheckIn>> = flow {
      arrived.await()
      emitAll(delegate.observeMyCheckIns(eventId, userId))
    }
  }

  /** Delegates to [checkIns], except that a check-in never answers, like an offline write. */
  private class NeverAnswers(checkIns: CheckInRepository) : CheckInRepository by checkIns {
    /** How many check-ins were asked for. */
    var calls = 0

    override suspend fun checkIn(
        eventId: String,
        missionId: String,
        userId: String,
        method: CheckInMethod,
    ): Unit {
      calls++
      awaitCancellation()
    }
  }

  private companion object {
    const val USER = "volunteer"
    const val EVENT = "event"
    val NOW: Instant = Instant.parse("2027-06-20T18:02:00Z")
    val PLACE = MissionLocation("Bar tent", GeoPosition(46.52, 6.57))
    val NEAR = GeoPosition(46.52, 6.57)
    val FAR = GeoPosition(46.0, 6.0)
  }
}
