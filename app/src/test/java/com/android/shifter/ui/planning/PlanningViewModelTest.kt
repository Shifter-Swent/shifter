// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.planning

import com.swent.shifter.model.checkin.CheckIn
import com.swent.shifter.model.checkin.CheckInMethod
import com.swent.shifter.model.checkin.CheckInRepository
import com.swent.shifter.model.checkin.CheckInRepositoryException
import com.swent.shifter.model.checkin.FakeCheckInRepository
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FakeEventRepository
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionLocation
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.mission.MissionRepositoryException
import com.swent.shifter.model.time.FakeMinuteClock
import com.swent.shifter.ui.components.nowcard.NowState
import com.swent.shifter.utils.MainDispatcherRule
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlanningViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  // Its clock lets a mission of START be changed, as before it starts.
  private val missions =
      FakeMissionRepository(Clock.fixed(START.minus(Duration.ofHours(1)), ZoneOffset.UTC))
  private val checkIns = FakeCheckInRepository(currentUserId = USER)
  private val events = FakeEventRepository()

  private val clock = FakeMinuteClock(START.minus(Duration.ofHours(1)))

  private var userId: String? = USER

  private fun viewModel() =
      PlanningViewModel(EVENT, missions, checkIns, events, clock, ZoneOffset.UTC) { userId }

  /** Collects the state as the screen would, which makes the ViewModel follow the clock. */
  private fun TestScope.collect(viewModel: PlanningViewModel) =
      backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        viewModel.uiState.collect {}
      }

  private suspend fun createEvent(startAt: Instant, endAt: Instant) =
      events.createEvent(
          Event(
              id = EVENT,
              organizerId = "organizer",
              title = "City Marathon",
              description = "",
              type = EventType.MUSIC,
              startAt = startAt,
              endAt = endAt,
              location = EventLocation("Lyon"),
              createdAt = startAt,
          )
      )

  private suspend fun create(
      title: String,
      eventId: String = EVENT,
      assignees: List<String> = listOf(USER),
      location: MissionLocation? = null,
      startAt: Instant = START,
      endAt: Instant = startAt.plusSeconds(3600),
      description: String = "",
  ): Mission =
      missions.createMission(
          Mission(
              eventId = eventId,
              title = title,
              description = description,
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
  fun load_mapsTheDescription() = runTest {
    create("Hand out water", description = "Fill the cups at the stand")

    assertEquals(
        "Fill the cups at the stand",
        viewModel().uiState.value.missions.single().description,
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

    clock.set(START.plusSeconds(60))
    val viewModel =
        PlanningViewModel(EVENT, missions, failingAfterAStatus, events, clock, ZoneOffset.UTC) {
          userId
        }
    collect(viewModel)

    val state = viewModel.uiState.value
    assertEquals(CHECKED_IN_AT, state.missions.single().checkedInAt)
    assertTrue(state.checkInsUnavailable)
    assertEquals(NowState.Ongoing(mission, null), state.now)
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
  fun now_beforeMidnight_showsTheNightMissionOfTheSameDay() = runTest {
    val night =
        create(
            "Night shift",
            startAt = Instant.parse("2025-06-15T02:00:00Z"),
            endAt = Instant.parse("2025-06-15T04:00:00Z"),
        )
    clock.set(Instant.parse("2025-06-14T23:00:00Z"))
    val viewModel = viewModel()
    collect(viewModel)

    assertEquals(NowState.Later(night), viewModel.uiState.value.now)
  }

  @Test
  fun now_atFourInTheMorning_showsTheMissionsOfTheNewDay() = runTest {
    val tomorrow = create("Tomorrow", startAt = START.plus(Duration.ofDays(1)))
    clock.set(Instant.parse("2025-06-15T03:59:00Z"))
    val viewModel = viewModel()
    collect(viewModel)
    assertEquals(NowState.DoneForToday, viewModel.uiState.value.now)

    clock.set(Instant.parse("2025-06-15T04:00:00Z"))

    assertEquals(NowState.Later(tomorrow), viewModel.uiState.value.now)
  }

  @Test
  fun now_afterFourInTheMorning_keepsTheMissionRunningAcrossIt() = runTest {
    val night =
        create(
            "Night shift",
            startAt = Instant.parse("2025-06-15T02:00:00Z"),
            endAt = Instant.parse("2025-06-15T06:00:00Z"),
        )
    clock.set(Instant.parse("2025-06-15T05:00:00Z"))
    val viewModel = viewModel()
    collect(viewModel)

    assertEquals(NowState.Late(night, null), viewModel.uiState.value.now)
  }

  @Test
  fun load_showsWhenTheEventStartsAndEnds() = runTest {
    createEvent(EVENT_START, EVENT_END)

    assertEquals(EVENT_START..EVENT_END, viewModel().uiState.value.eventPeriod)
  }

  @Test
  fun load_whenTheEventFails_stillShowsTheMissions() = runTest {
    val mission = create("Hand out water")
    createEvent(EVENT_START, EVENT_END)
    events.failure = EventRepositoryException.Unavailable()

    val state = viewModel().uiState.value

    assertEquals(listOf(mission.id), state.missions.map { it.id })
    assertNull(state.eventPeriod)
    assertNull(state.error)
    assertFalse(state.isLoading)
  }

  @Test
  fun load_whileTheEventIsStillRead_showsTheMissionsAndTheirCheckIns() = runTest {
    val mission = create("Hand out water")
    val event = createEvent(EVENT_START, EVENT_END)
    // Answers only once completed, as a read stuck on a slow network would.
    val read = CompletableDeferred<Event?>()
    val slowEvents =
        object : EventRepository by events {
          override suspend fun getEvent(eventId: String) = read.await()
        }
    val viewModel =
        PlanningViewModel(EVENT, missions, checkIns, slowEvents, clock, ZoneOffset.UTC) { userId }

    checkIn(mission, CHECKED_IN_AT)

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals(CHECKED_IN_AT, state.missions.single().checkedInAt)
    assertNull(state.eventPeriod)

    read.complete(event)

    assertEquals(EVENT_START..EVENT_END, viewModel.uiState.value.eventPeriod)
  }

  @Test
  fun retry_afterBothFailed_showsTheEventDates() = runTest {
    create("Hand out water")
    createEvent(EVENT_START, EVENT_END)
    missions.failure = MissionRepositoryException.Unavailable()
    events.failure = EventRepositoryException.Unavailable()
    val viewModel = viewModel()

    missions.failure = null
    events.failure = null
    viewModel.load()

    assertEquals(EVENT_START..EVENT_END, viewModel.uiState.value.eventPeriod)
  }

  @Test
  fun missions_whenOneIsCompleted_showWithoutReload() = runTest {
    val mission = create("Hand out water")
    clock.set(START.plusSeconds(60))
    val viewModel = viewModel()
    collect(viewModel)
    assertEquals(NowState.Late(mission, null), viewModel.uiState.value.now)

    missions.markCompleted(EVENT, mission.id)

    assertEquals(NowState.DoneForToday, viewModel.uiState.value.now)
  }

  @Test
  fun missions_whenOneIsRescheduled_showWithoutReload() = runTest {
    val mission = create("Hand out water")
    val viewModel = viewModel()
    collect(viewModel)

    val soon = START.minus(Duration.ofMinutes(40))
    val rescheduled = mission.copy(startAt = soon, endAt = soon.plusSeconds(3600))
    missions.updateMission(rescheduled)

    assertEquals(soon, viewModel.uiState.value.missions.single().startAt)
    assertEquals(
        NowState.Soon(checkNotNull(missions.getMission(EVENT, mission.id))),
        viewModel.uiState.value.now,
    )
  }

  @Test
  fun missions_whenOneIsAssigned_showWithoutReload() = runTest {
    val mission = create("Hand out water", assignees = emptyList())
    val viewModel = viewModel()
    collect(viewModel)
    assertTrue(viewModel.uiState.value.missions.isEmpty())

    missions.updateAssignees(EVENT, mission.id, listOf(USER))

    assertEquals(listOf(mission.id), viewModel.uiState.value.missions.map { it.id })
    assertEquals(
        NowState.Later(mission.copy(assigneeIds = listOf(USER))),
        viewModel.uiState.value.now,
    )
  }

  @Test
  fun missions_whenOneIsUnassigned_showWithoutReload() = runTest {
    val mission = create("Hand out water")
    val viewModel = viewModel()
    collect(viewModel)

    missions.updateAssignees(EVENT, mission.id, emptyList())

    assertTrue(viewModel.uiState.value.missions.isEmpty())
    assertEquals(NowState.DoneForToday, viewModel.uiState.value.now)
  }

  @Test
  fun missions_whenTheyFailAfterAList_keepTheListAndSaySo() = runTest {
    val mission = create("Hand out water")
    val viewModel = viewModel()
    collect(viewModel)

    missions.failure = MissionRepositoryException.Unavailable()

    val state = viewModel.uiState.value
    assertEquals(listOf(mission.id), state.missions.map { it.id })
    assertNull(state.error)
    assertTrue(state.missionsOutdated)
    assertEquals(NowState.Later(mission), state.now)
  }

  @Test
  fun load_afterTheMissionsStopped_clearsTheMessage() = runTest {
    create("Hand out water")
    val viewModel = viewModel()
    missions.failure = MissionRepositoryException.Unavailable()
    assertTrue(viewModel.uiState.value.missionsOutdated)

    missions.failure = null
    viewModel.load()

    assertFalse(viewModel.uiState.value.missionsOutdated)
  }

  @Test
  fun load_again_followsTheMissionsOnce() = runTest {
    create("Hand out water")
    var followers = 0
    val counted =
        object : MissionRepository by missions {
          override fun observeMissionsAssignedTo(userId: String) =
              missions
                  .observeMissionsAssignedTo(userId)
                  .onStart { followers++ }
                  .onCompletion { followers-- }
        }
    val viewModel = PlanningViewModel(EVENT, counted, checkIns, events, clock, ZoneOffset.UTC) { userId }

    viewModel.load()
    viewModel.load()

    assertEquals(1, followers)
  }

  @Test
  fun missions_changedBeforeTheCheckInsAreRead_keepLoading() = runTest {
    val mission = create("Hand out water")
    val silent =
        object : CheckInRepository by checkIns {
          override fun observeMyCheckIns(eventId: String, userId: String) =
              flow<List<CheckIn>> { awaitCancellation() }
        }
    val viewModel = PlanningViewModel(EVENT, missions, silent, events, clock, ZoneOffset.UTC) { userId }

    missions.markCompleted(EVENT, mission.id)

    assertTrue(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.now)
  }

  @Test
  fun checkIn_afterAMissionChange_keepsTheChangedMission() = runTest {
    val mission = create("Hand out water")
    val viewModel = viewModel()
    collect(viewModel)
    val soon = START.minus(Duration.ofMinutes(40))
    missions.updateMission(mission.copy(startAt = soon, endAt = soon.plusSeconds(3600)))

    checkIn(mission, CHECKED_IN_AT)

    val shown = viewModel.uiState.value.missions.single()
    assertEquals(soon, shown.startAt)
    assertEquals(CHECKED_IN_AT, shown.checkedInAt)
  }

  @Test
  fun load_again_followsTheCheckInsOnce() = runTest {
    create("Hand out water")
    var followers = 0
    val counted =
        object : CheckInRepository by checkIns {
          override fun observeMyCheckIns(eventId: String, userId: String) =
              checkIns
                  .observeMyCheckIns(eventId, userId)
                  .onStart { followers++ }
                  .onCompletion { followers-- }
        }
    val viewModel = PlanningViewModel(EVENT, missions, counted, events, clock, ZoneOffset.UTC) { userId }

    viewModel.load()
    viewModel.load()

    assertEquals(1, followers)
  }

  @Test
  fun checkIn_afterTheMissionsStopped_stillShows() = runTest {
    val mission = create("Hand out water")
    val viewModel = viewModel()
    missions.failure = MissionRepositoryException.Unavailable()

    checkIn(mission, CHECKED_IN_AT)

    assertTrue(viewModel.uiState.value.missionsOutdated)
    assertEquals(CHECKED_IN_AT, viewModel.uiState.value.missions.single().checkedInAt)
  }

  /** Check-ins that come, or fail with [failure], only once [gate] is completed. */
  private fun checkInsAfter(gate: CompletableDeferred<Unit>, failure: Exception? = null) =
      object : CheckInRepository by checkIns {
        override fun observeMyCheckIns(eventId: String, userId: String) =
            flow<List<CheckIn>> {
              gate.await()
              failure?.let { throw it }
              emit(emptyList())
            }
      }

  @Test
  fun missions_changedBeforeTheCheckInsAreRead_showOnceTheyAre() = runTest {
    val mission = create("Hand out water")
    val gate = CompletableDeferred<Unit>()
    val viewModel =
        PlanningViewModel(EVENT, missions, checkInsAfter(gate), events, clock, ZoneOffset.UTC) { userId }
    collect(viewModel)
    missions.updateAssignees(EVENT, mission.id, emptyList())

    gate.complete(Unit)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertTrue(state.missions.isEmpty())
    assertEquals(NowState.DoneForToday, state.now)
  }

  @Test
  fun missionsAndThenCheckIns_failing_keepTheListWithBothMessages() = runTest {
    val mission = create("Hand out water")
    val gate = CompletableDeferred<Unit>()
    val failing = checkInsAfter(gate, CheckInRepositoryException.Unavailable())
    val viewModel = PlanningViewModel(EVENT, missions, failing, events, clock, ZoneOffset.UTC) { userId }
    collect(viewModel)
    missions.failure = MissionRepositoryException.Unavailable()

    gate.complete(Unit)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertTrue(state.missionsOutdated)
    assertTrue(state.checkInsUnavailable)
    assertEquals(listOf(mission.id), state.missions.map { it.id })
    assertNull(state.now)
  }

  private companion object {
    const val EVENT = "event-1"
    const val USER = "volunteer-1"
    val START: Instant = Instant.parse("2025-06-14T08:30:00Z")
    val EVENT_START: Instant = Instant.parse("2025-06-13T08:00:00Z")
    val EVENT_END: Instant = Instant.parse("2025-06-19T22:00:00Z")
    val CHECKED_IN_AT: Instant = Instant.parse("2025-06-14T08:31:00Z")
    val STATION = MissionLocation("Hydration station A", GeoPosition(45.75, 4.83))
  }
}
