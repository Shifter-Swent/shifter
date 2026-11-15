// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.planning

import com.swent.shifter.model.checkin.CheckIn
import com.swent.shifter.model.checkin.CheckInMethod
import com.swent.shifter.model.checkin.CheckInRepository
import com.swent.shifter.model.checkin.CheckInRepositoryException
import com.swent.shifter.model.checkin.FakeCheckInRepository
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionLocation
import com.swent.shifter.model.mission.MissionRepositoryException
import com.swent.shifter.model.time.FakeMinuteClock
import com.swent.shifter.ui.components.nowcard.NowState
import com.swent.shifter.utils.MainDispatcherRule
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlanningViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val missions = FakeMissionRepository()
  private val checkIns = FakeCheckInRepository(currentUserId = USER)

  private val clock = FakeMinuteClock(START.minus(Duration.ofHours(1)))

  private var userId: String? = USER

  private fun viewModel() =
      PlanningViewModel(EVENT, missions, checkIns, clock, ZoneOffset.UTC) { userId }

  /** Collects the state as the screen would, which makes the ViewModel follow the clock. */
  private fun TestScope.collect(viewModel: PlanningViewModel) =
      backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        viewModel.uiState.collect {}
      }

  private suspend fun create(
      title: String,
      eventId: String = EVENT,
      assignees: List<String> = listOf(USER),
      location: MissionLocation? = null,
      startAt: Instant = START,
      endAt: Instant = startAt.plusSeconds(3600),
  ): Mission =
      missions.createMission(
          Mission(
              eventId = eventId,
              title = title,
              description = "",
              volunteersNeeded = 2,
              startAt = startAt,
              endAt = endAt,
              location = location,
              assigneeIds = assignees,
              createdAt = START,
          )
      )

  private fun checkIn(mission: Mission, at: Instant) =
      checkIns.add(CheckIn(mission.eventId, mission.id, USER, at, CheckInMethod.SELF, USER))

  @Test
  fun load_keepsOnlyTheMissionsOfTheEventTheUserIsAssignedTo() = runTest {
    val mine = create("Hand out water")
    create("Another event", eventId = "other-event")
    create("Someone else's", assignees = listOf("someone-else"))

    val state = viewModel().uiState.value

    assertEquals(listOf(mine.id), state.missions.map { it.id })
    assertFalse(state.isLoading)
    assertNull(state.error)
  }

  @Test
  fun load_mapsTheMissionWithItsAddressAndCheckIn() = runTest {
    val located = create("Hand out water", location = STATION)
    val unlocated = create("Break")
    checkIn(located, CHECKED_IN_AT)

    val shown = viewModel().uiState.value.missions.associateBy { it.id }

    assertEquals(
        PlanningMission(
            located.id,
            "Hand out water",
            START,
            located.endAt,
            STATION.address,
            CHECKED_IN_AT,
        ),
        shown[located.id],
    )
    assertEquals(
        PlanningMission(unlocated.id, "Break", START, unlocated.endAt, null, null),
        shown[unlocated.id],
    )
  }

  @Test
  fun load_withoutMissions_showsAnEmptyList() {
    val state = viewModel().uiState.value

    assertTrue(state.missions.isEmpty())
    assertFalse(state.isLoading)
    assertNull(state.error)
  }

  @Test
  fun checkIn_whileThePlanningIsOpen_showsUp() = runTest {
    val mission = create("Hand out water")
    val viewModel = viewModel()
    assertNull(viewModel.uiState.value.missions.single().checkedInAt)

    checkIn(mission, CHECKED_IN_AT)

    assertEquals(CHECKED_IN_AT, viewModel.uiState.value.missions.single().checkedInAt)
  }

  @Test
  fun load_whenOffline_showsTheOfflineError() {
    missions.failure = MissionRepositoryException.Unavailable()

    val state = viewModel().uiState.value

    assertEquals(PlanningError.OFFLINE, state.error)
    assertFalse(state.isLoading)
  }

  @Test
  fun load_whenTheBackendFails_showsTheUnexpectedError() {
    missions.failure = MissionRepositoryException.Unknown()

    assertEquals(PlanningError.UNEXPECTED, viewModel().uiState.value.error)
  }

  @Test
  fun load_withoutSignedInUser_showsTheUnexpectedError() {
    userId = null

    assertEquals(PlanningError.UNEXPECTED, viewModel().uiState.value.error)
  }

  @Test
  fun load_afterAnError_showsTheMissions() = runTest {
    val mission = create("Hand out water")
    missions.failure = MissionRepositoryException.Unavailable()
    val viewModel = viewModel()

    missions.failure = null
    viewModel.load()

    assertNull(viewModel.uiState.value.error)
    assertEquals(listOf(mission.id), viewModel.uiState.value.missions.map { it.id })
  }

  @Test
  fun load_again_stillUpdatesTheCheckIns() = runTest {
    val mission = create("Hand out water")
    val viewModel = viewModel()

    viewModel.load()
    checkIn(mission, CHECKED_IN_AT)

    assertEquals(CHECKED_IN_AT, viewModel.uiState.value.missions.single().checkedInAt)
  }

  @Test
  fun load_whenTheCheckInsFail_showsTheMissionsWithoutStatus() = runTest {
    val mission = create("Hand out water")
    checkIns.failure = CheckInRepositoryException.Unavailable()

    val state = viewModel().uiState.value

    assertEquals(listOf(mission.id), state.missions.map { it.id })
    assertNull(state.missions.single().checkedInAt)
    assertFalse(state.isLoading)
    assertNull(state.error)
    assertTrue(state.checkInsUnavailable)
  }

  @Test
  fun load_whenTheCheckInsFailAfterAStatus_keepsTheStatus() = runTest {
    val mission = create("Hand out water")
    // Emits the check-in, then fails, as a listener that loses the backend would.
    val failingAfterAStatus =
        object : CheckInRepository by checkIns {
          override fun observeMyCheckIns(eventId: String, userId: String) = flow {
            emit(listOf(CheckIn(EVENT, mission.id, USER, CHECKED_IN_AT, CheckInMethod.SELF, USER)))
            throw CheckInRepositoryException.Unavailable()
          }
        }

    val state =
        PlanningViewModel(EVENT, missions, failingAfterAStatus, clock) { userId }.uiState.value

    assertEquals(CHECKED_IN_AT, state.missions.single().checkedInAt)
    assertTrue(state.checkInsUnavailable)
  }

  @Test
  fun load_afterTheCheckInsFailed_clearsTheMessage() = runTest {
    create("Hand out water")
    checkIns.failure = CheckInRepositoryException.Unavailable()
    val viewModel = viewModel()

    checkIns.failure = null
    viewModel.load()

    assertFalse(viewModel.uiState.value.checkInsUnavailable)
  }

  @Test
  fun now_whileCollected_followsTheClock() = runTest {
    val mission = create("Hand out water")
    val viewModel = viewModel()
    collect(viewModel)
    assertEquals(NowState.Later(mission), viewModel.uiState.value.now)

    clock.set(START.minus(Duration.ofMinutes(20)))
    assertEquals(NowState.Soon(mission), viewModel.uiState.value.now)

    clock.set(START.plusSeconds(60))
    assertEquals(NowState.Late(mission, null), viewModel.uiState.value.now)
  }

  @Test
  fun now_whenTheVolunteerChecksIn_isOngoing() = runTest {
    val mission = create("Hand out water")
    clock.set(START.plusSeconds(60))
    val viewModel = viewModel()
    collect(viewModel)

    checkIn(mission, CHECKED_IN_AT)

    assertEquals(NowState.Ongoing(mission, null), viewModel.uiState.value.now)
  }

  @Test
  fun now_withoutMissions_isDoneForToday() = runTest {
    val viewModel = viewModel()
    collect(viewModel)

    assertEquals(NowState.DoneForToday, viewModel.uiState.value.now)
  }

  @Test
  fun now_onlyWhileCollected_followsTheClock() = runTest {
    create("Hand out water")
    val viewModel = viewModel()
    assertEquals(0, clock.subscriptionCount.value)
    assertNull(viewModel.uiState.value.now)

    val screen = collect(viewModel)
    assertTrue(clock.subscriptionCount.value > 0)

    screen.cancel()
    assertEquals(0, clock.subscriptionCount.value)
  }

  @Test
  fun now_whenTheCheckInsFail_isUnknown() = runTest {
    create("Hand out water")
    checkIns.failure = CheckInRepositoryException.Unavailable()
    val viewModel = viewModel()
    collect(viewModel)

    assertNull(viewModel.uiState.value.now)
  }

  @Test
  fun now_whenTheMissionsFail_isUnknown() = runTest {
    missions.failure = MissionRepositoryException.Unavailable()
    val viewModel = viewModel()
    collect(viewModel)

    assertNull(viewModel.uiState.value.now)
  }

  @Test
  fun load_afterAnError_showsTheNowState() = runTest {
    val mission = create("Hand out water")
    missions.failure = MissionRepositoryException.Unavailable()
    val viewModel = viewModel()
    collect(viewModel)

    missions.failure = null
    viewModel.load()

    assertEquals(NowState.Later(mission), viewModel.uiState.value.now)
  }

  @Test
  fun load_afterASuccess_showsTheNowStateAgain() = runTest {
    val mission = create("Hand out water")
    val viewModel = viewModel()
    collect(viewModel)

    viewModel.load()

    assertEquals(NowState.Later(mission), viewModel.uiState.value.now)
  }

  @Test
  fun load_whenAReloadFails_keepsTheNowStateUnknown() = runTest {
    create("Hand out water")
    val viewModel = viewModel()
    collect(viewModel)

    missions.failure = MissionRepositoryException.Unavailable()
    viewModel.load()
    clock.set(START.plusSeconds(60))

    assertNull(viewModel.uiState.value.now)
  }

  @Test
  fun now_leavesOutTheMissionsOfAnotherDay() = runTest {
    create("Tomorrow", startAt = START.plus(Duration.ofDays(1)))
    val viewModel = viewModel()
    collect(viewModel)

    assertEquals(NowState.DoneForToday, viewModel.uiState.value.now)
  }

  @Test
  fun now_atMidnight_showsTheMissionsOfTheNewDay() = runTest {
    val tomorrow = create("Tomorrow", startAt = START.plus(Duration.ofDays(1)))
    clock.set(Instant.parse("2025-06-14T23:59:00Z"))
    val viewModel = viewModel()
    collect(viewModel)

    clock.set(Instant.parse("2025-06-15T00:00:00Z"))

    assertEquals(NowState.Later(tomorrow), viewModel.uiState.value.now)
  }

  @Test
  fun now_afterMidnight_keepsTheMissionRunningAcrossIt() = runTest {
    val night =
        create(
            "Night shift",
            startAt = Instant.parse("2025-06-14T22:00:00Z"),
            endAt = Instant.parse("2025-06-15T02:00:00Z"),
        )
    clock.set(Instant.parse("2025-06-15T01:00:00Z"))
    val viewModel = viewModel()
    collect(viewModel)

    assertEquals(NowState.Late(night, null), viewModel.uiState.value.now)
  }

  private companion object {
    const val EVENT = "event-1"
    const val USER = "volunteer-1"
    val START: Instant = Instant.parse("2025-06-14T08:30:00Z")
    val CHECKED_IN_AT: Instant = Instant.parse("2025-06-14T08:31:00Z")
    val STATION = MissionLocation("Hydration station A", GeoPosition(45.75, 4.83))
  }
}
