// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.checkin

import com.swent.shifter.model.checkin.CheckIn
import com.swent.shifter.model.checkin.CheckInMethod
import com.swent.shifter.model.checkin.CheckInRepository
import com.swent.shifter.model.checkin.CheckInRepositoryException
import com.swent.shifter.model.checkin.FakeCheckInRepository
import com.swent.shifter.model.location.FakeLocationProvider
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.location.LocationStatus
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionLocation
import com.swent.shifter.model.mission.MissionRepositoryException
import com.swent.shifter.utils.MainDispatcherRule
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset.UTC
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
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

  /** The instants [isEligible] was asked about, in order. */
  private val eligibleAt = mutableListOf<Instant>()

  private lateinit var viewModel: AutoCheckInViewModel
  private lateinit var screen: Job

  private val state
    get() = viewModel.uiState.value

  /** Opens the screen: the ViewModel only works while its state is collected. */
  private fun TestScope.open(repository: CheckInRepository = checkIns) {
    viewModel =
        AutoCheckInViewModel(
            missions,
            repository,
            location,
            { userId },
            { mission, time, at ->
              eligibleAt += time
              isEligible(mission, time, at)
            },
            { clock },
            UTC,
        )
    screen =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          viewModel.uiState.collect {}
        }
    runCurrent()
  }

  private suspend fun TestScope.moveTo(position: GeoPosition) {
    location.emit(position)
    runCurrent()
  }

  private suspend fun addMission(
      title: String = "Bar",
      eventId: String = EVENT,
      at: MissionLocation? = PLACE,
      endsIn: Duration = Duration.ofHours(1),
  ): Mission =
      missions.createMission(
          Mission(
              eventId = eventId,
              title = title,
              description = "",
              volunteersNeeded = 2,
              startAt = NOW,
              endAt = NOW + endsIn,
              location = at,
              assigneeIds = listOf(USER),
              createdAt = NOW,
          )
      )

  @Test
  fun eligiblePosition_checksInAndConfirms() = runTest {
    val mission = addMission()
    open()

    moveTo(NEAR)

    val checkIn = checkIns.checkIns.single()
    assertEquals(
        listOf(EVENT, mission.id, USER),
        listOf(checkIn.eventId, checkIn.missionId, checkIn.userId),
    )
    assertEquals(CheckInMethod.SELF, checkIn.method)
    assertEquals(AutoCheckedIn("Bar", "18:02"), state.checkedIn)
    assertNull(state.error)
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
    assertNull(state.checkedIn)
  }

  @Test
  fun alreadyCheckedIn_skipsTheMission() = runTest {
    val mission = addMission()
    checkIns.add(CheckIn(EVENT, mission.id, USER, NOW, CheckInMethod.MANAGER, "manager"))
    val counting = CountsCalls(checkIns)
    open(counting)

    moveTo(NEAR)

    assertEquals(0, counting.calls)
    assertNull(state.checkedIn)
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
    addMission("Over", endsIn = Duration.ofMinutes(-1))
    isEligible = { _, _, _ -> true }
    open()

    moveTo(NEAR)

    assertTrue(checkIns.checkIns.isEmpty())
  }

  @Test
  fun missionAtTheEndOfItsWindow_isStillTried() = runTest {
    addMission(endsIn = Duration.ZERO)
    open()

    moveTo(NEAR)

    assertEquals(1, checkIns.checkIns.size)
  }

  @Test
  fun notAllowed_showsTheErrorAndDoesNotRetry() = runTest {
    addMission()
    open()
    checkIns.failure = CheckInRepositoryException.NotAllowed()

    moveTo(NEAR)
    checkIns.failure = null
    moveTo(NEAR)

    assertEquals(AutoCheckInError.NOT_ALLOWED, state.error)
    assertTrue(checkIns.checkIns.isEmpty())
  }

  @Test
  fun offline_showsTheErrorAndRetriesOnTheNextPosition() = runTest {
    addMission()
    open()
    checkIns.failure = CheckInRepositoryException.Unavailable()

    moveTo(NEAR)
    assertEquals(AutoCheckInError.OFFLINE, state.error)
    checkIns.failure = null
    moveTo(NEAR)

    assertEquals(1, checkIns.checkIns.size)
    assertNull(state.error)
  }

  @Test
  fun checkInThatNeverAnswers_showsOfflineAfterTheTimeout() = runTest {
    addMission()
    open(NeverAnswers(checkIns))

    moveTo(NEAR)
    assertNull(state.error)
    advanceTimeBy(AutoCheckInViewModel.CHECK_IN_TIMEOUT_MILLIS + 1)

    assertEquals(AutoCheckInError.OFFLINE, state.error)
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
  fun missionsLoadFailure_showsTheError() = runTest {
    addMission()
    missions.failure = MissionRepositoryException.Unavailable()

    open()

    assertEquals(AutoCheckInError.OFFLINE, state.error)
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
    val PLACE = MissionLocation("Bar tent", 46.52, 6.57)
    val NEAR = GeoPosition(46.52, 6.57)
    val FAR = GeoPosition(46.0, 6.0)
  }
}
