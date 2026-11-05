// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FakeEventRepository
import com.swent.shifter.model.location.FakeLocationProvider
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.location.LocationProvider
import com.swent.shifter.model.location.LocationStatus
import com.swent.shifter.model.location.FakeLiveLocationRepository
import com.swent.shifter.model.location.LiveLocation
import com.swent.shifter.model.location.LiveLocationRepository
import com.swent.shifter.model.location.LiveLocationRepositoryException
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionLocation
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.User
import com.swent.shifter.model.user.UserRepositoryException
import com.swent.shifter.utils.MainDispatcherRule
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset.UTC
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.coroutineScope
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val events = FakeEventRepository().apply { this.events += EVENT }
  private val missions = FakeMissionRepository()

  private var userId: String? = USER

  private fun viewModel(location: LocationProvider? = null) =
      MapViewModel(
          EVENT.id,
          events,
          missions,
          { userId },
          { DAY.plusSeconds(12 * 3600L) },
          UTC,
          location,
      )

  /** Collects the position in the background of the test, like the screen, until cancelled. */
  private fun TestScope.collectPosition(viewModel: MapViewModel) =
      backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        viewModel.myPosition.collect {}
      }

  @Test
  fun myPosition_followsTheVolunteerWithTheLocationGranted() = runTest {
    val location = FakeLocationProvider()
    val viewModel = viewModel(location)
    collectPosition(viewModel)
    assertNull(viewModel.myPosition.value)

    location.emit(TENT.position)
    assertEquals(TENT.position, viewModel.myPosition.value)

    location.emit(STAND.position)
    assertEquals(STAND.position, viewModel.myPosition.value)
  }

  @Test
  fun myPosition_staysUnknownWithTheLocationDeniedAndTheMapStillLoads() = runTest {
    val location = FakeLocationProvider(LocationStatus.DENIED)
    missions.createMission(mission("Water", startHour = 10, endHour = 11, at = STAND))
    val viewModel = viewModel(location)
    collectPosition(viewModel)

    location.emit(TENT.position)

    assertNull(viewModel.myPosition.value)
    assertEquals(1, viewModel.uiState.value.markers.size)
  }

  @Test
  fun myPosition_isForgottenWhenTheLocationIsTurnedOff() = runTest {
    val location = FakeLocationProvider()
    val viewModel = viewModel(location)
    collectPosition(viewModel)
    location.emit(TENT.position)

    location.setStatus(LocationStatus.LOCATION_OFF)

    assertNull(viewModel.myPosition.value)
  }

  @Test
  fun myPosition_waitsForANewPositionWhenTheLocationIsTurnedBackOn() = runTest {
    val location = FakeLocationProvider()
    val viewModel = viewModel(location)
    collectPosition(viewModel)
    location.emit(TENT.position)
    location.setStatus(LocationStatus.LOCATION_OFF)

    location.setStatus(LocationStatus.GRANTED)
    assertNull(viewModel.myPosition.value)

    location.emit(STAND.position)
    assertEquals(STAND.position, viewModel.myPosition.value)
  }

  @Test
  fun myPosition_ignoresAPositionArrivingAfterTheAccessIsLost() = runTest {
    val late = MutableSharedFlow<GeoPosition>()
    val location =
        object : LocationProvider by FakeLocationProvider(LocationStatus.DENIED) {
          override fun positions() = late
        }
    val viewModel = viewModel(location)
    collectPosition(viewModel)

    late.emit(TENT.position)

    assertNull(viewModel.myPosition.value)
  }

  @Test
  fun myPosition_staysUnknownWithNoProvider() = runTest {
    val viewModel = viewModel()
    collectPosition(viewModel)

    assertNull(viewModel.myPosition.value)
  }

  @Test
  fun myPosition_staysUnknownWhenTheGpsFailsAndTheMapStillLoads() = runTest {
    val failing =
        object : LocationProvider by FakeLocationProvider() {
          override fun positions() = flow<GeoPosition> { throw IllegalStateException("no GPS") }
        }
    val viewModel = viewModel(failing)
    collectPosition(viewModel)

    assertNull(viewModel.myPosition.value)
    assertEquals(EVENT.title, viewModel.uiState.value.eventTitle)
  }

  @Test
  fun myPosition_stopsTheGpsAndForgetsThePositionOnceNotCollected() = runTest {
    val location = FakeLocationProvider()
    val viewModel = viewModel(location)
    assertEquals(0, location.activeCollectors)
    val screen = collectPosition(viewModel)
    location.emit(TENT.position)
    assertEquals(1, location.activeCollectors)

    screen.cancel()
    advanceTimeBy(MapViewModel.STOP_TIMEOUT_MILLIS + 1)

    assertEquals(0, location.activeCollectors)
    assertNull(viewModel.myPosition.value)
  }

  private val liveLocations = FakeLiveLocationRepository()
  private val teams =
      FakeTeamRepository(
          listOf(
              team("bar", manager = USER, "alice", "bob"),
              team("stage", manager = USER, "carol"),
              team("parking", manager = "someone-else", "dave"),
          )
      )
  private val users = FakeUserRepository(listOf(User("alice", "Alice", "alice@example.com")))

  /** The clock of the volunteers' map, moved by the tests to age the positions. */
  private var clock: Instant = NOON

  /** The map of [USER], who manages the bar and the stage. */
  private fun volunteerMap(repository: LiveLocationRepository = liveLocations) =
      MapViewModel(
          EVENT.id,
          events,
          missions,
          { userId },
          { clock },
          UTC,
          liveLocations = repository,
          watchedTeams = { eventId, user -> teams.getTeamsManagedBy(eventId, user) },
          userRepository = users,
      )

  /**
   * Opens the map: the positions are only followed while [MapViewModel.volunteers] is collected.
   */
  private fun TestScope.open(viewModel: MapViewModel): MapViewModel {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      viewModel.volunteers.collect {}
    }
    runCurrent()
    return viewModel
  }

  /** Shares [position] as [volunteer]'s in [teamId], measured [minutesAgo] before [clock]. */
  private suspend fun share(
      volunteer: String,
      teamId: String,
      position: GeoPosition,
      minutesAgo: Long = 0,
  ) {
    liveLocations.currentUserId = volunteer
    liveLocations.shareLocation(EVENT.id, teamId, position, clock.minusSeconds(minutesAgo * 60))
  }

  @Test
  fun load_showsTheEventAndOneMarkerPerLocatedMissionInTimeOrder() = runTest {
    missions.createMission(mission("Cleanup", startHour = 14, endHour = 15, at = TENT))
    missions.createMission(mission("No place", startHour = 9, endHour = 10, at = null))
    missions.createMission(mission("Water", startHour = 10, endHour = 11, at = STAND))

    val state = viewModel().uiState.value

    assertEquals(EVENT.title, state.eventTitle)
    assertEquals(EVENT.location.address, state.venue)
    assertEquals(GeoPosition(46.52, 6.57), state.center)
    assertEquals(
        listOf(
            MapMarker("mission-3", STAND.position, "Water", "10:00–11:00 · Stand"),
            MapMarker("mission-1", TENT.position, "Cleanup", "14:00–15:00 · Tent"),
        ),
        state.markers,
    )
    assertFalse(state.isLoading)
    assertNull(state.error)
  }

  @Test
  fun load_centersOnTheFirstMissionWhenTheVenueHasNoCoordinates() = runTest {
    events.events[0] = EVENT.copy(location = EventLocation("Somewhere"))
    missions.createMission(mission("Water", startHour = 10, endHour = 11, at = STAND))

    assertEquals(STAND.position, viewModel().uiState.value.center)
  }

  @Test
  fun load_reportsAnEventThatDoesNotExist() {
    events.events.clear()

    val state = viewModel().uiState.value

    assertEquals(MapError.EVENT_NOT_FOUND, state.error)
    assertFalse(state.isLoading)
  }

  @Test
  fun load_reportsEachFailureAndRetryClearsIt() {
    events.failure = EventRepositoryException.Unavailable()
    val viewModel = viewModel()
    assertEquals(MapError.OFFLINE, viewModel.uiState.value.error)

    events.failure = null
    missions.failure = IllegalStateException("boom")
    viewModel.load()
    assertEquals(MapError.UNEXPECTED, viewModel.uiState.value.error)

    missions.failure = null
    viewModel.load()
    assertNull(viewModel.uiState.value.error)
    assertEquals(EVENT.title, viewModel.uiState.value.eventTitle)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun load_highlightsTheNextMissionTheUserIsAssignedTo() = runTest {
    // At noon: the morning mission is over, the evening one is not theirs.
    missions.createMission(mission("Morning", 9, 10, STAND, assignees = listOf(USER)))
    missions.createMission(mission("Evening", 18, 19, TENT, assignees = listOf("someone")))
    missions.createMission(mission("Afternoon", 14, 15, TENT, assignees = listOf(USER)))

    assertEquals(
        NextMission("mission-3", "14:00", "15:00", isOngoing = false),
        viewModel().uiState.value.next,
    )
  }

  @Test
  fun load_keepsHighlightingTheMissionTheUserIsDoingAsOngoing() = runTest {
    // At noon, the user is in the middle of it.
    missions.createMission(mission("Lunch", 11, 13, STAND, assignees = listOf(USER)))
    missions.createMission(mission("Afternoon", 14, 15, TENT, assignees = listOf(USER)))

    assertEquals(
        NextMission("mission-1", "11:00", "13:00", isOngoing = true),
        viewModel().uiState.value.next,
    )
  }

  @Test
  fun load_hasNoNextMissionWithoutAMissionLeftOrAUser() = runTest {
    missions.createMission(mission("Morning", 9, 10, STAND, assignees = listOf(USER)))
    assertNull(viewModel().uiState.value.next)

    missions.createMission(mission("Afternoon", 14, 15, TENT, assignees = listOf(USER)))
    userId = null
    assertNull(viewModel().uiState.value.next)
  }

  @Test
  fun selectMarker_showsItsCardAndClearSelectionClosesIt() = runTest {
    missions.createMission(mission("Water", startHour = 10, endHour = 11, at = STAND))
    val viewModel = viewModel()
    val marker = viewModel.uiState.value.markers.single()

    viewModel.selectMarker(marker)
    assertEquals(marker, viewModel.uiState.value.selected)

    viewModel.clearSelection()
    assertNull(viewModel.uiState.value.selected)
  }

  @Test
  fun volunteers_showsTheSharedPositionsOfTheManagedTeamsOnly() = runTest {
    share("alice", "bar", TENT.position)
    share("carol", "stage", STAND.position)
    share("dave", "parking", STAND.position)

    val volunteers = open(volunteerMap()).volunteers.value

    assertEquals(
        listOf(
            VolunteerPin("alice", "Alice", TENT.position, minutesAgo = 0, isStale = false),
            VolunteerPin("carol", null, STAND.position, minutesAgo = 0, isStale = false),
        ),
        volunteers,
    )
  }

  @Test
  fun volunteers_followAVolunteerWhoMoves() = runTest {
    share("alice", "bar", TENT.position)
    val viewModel = open(volunteerMap())

    clock = clock.plusSeconds(30)
    share("alice", "bar", STAND.position)
    runCurrent()

    assertEquals(STAND.position, viewModel.volunteers.value.single().position)
  }

  @Test
  fun volunteers_whoLeftTheTeam_areNotShown() = runTest {
    // The rules let a position stay readable for a while after its volunteer left the team.
    share("erin", "bar", TENT.position)

    assertTrue(open(volunteerMap()).volunteers.value.isEmpty())
  }

  @Test
  fun volunteers_inTwoManagedTeams_areShownOnceAtTheirLatestPosition() = runTest {
    teams.addMember(EVENT.id, "stage", "alice")
    share("alice", "bar", TENT.position, minutesAgo = 3)
    share("alice", "stage", STAND.position, minutesAgo = 1)

    val alice = open(volunteerMap()).volunteers.value.single()

    assertEquals(STAND.position, alice.position)
    assertEquals(1, alice.minutesAgo)
  }

  @Test
  fun volunteers_olderThanTenMinutes_areStale() = runTest {
    share("alice", "bar", TENT.position, minutesAgo = 10)
    share("carol", "stage", STAND.position, minutesAgo = 11)

    val volunteers = open(volunteerMap()).volunteers.value

    assertEquals(listOf(false, true), volunteers.map { it.isStale })
    assertEquals(listOf(10L, 11L), volunteers.map { it.minutesAgo })
  }

  @Test
  fun volunteers_ageEveryMinuteWithoutANewPosition() = runTest {
    share("alice", "bar", TENT.position, minutesAgo = 9)
    val viewModel = open(volunteerMap())
    assertFalse(viewModel.volunteers.value.single().isStale)

    clock = clock.plus(Duration.ofMinutes(2))
    advanceTimeBy(MapViewModel.AGE_REFRESH_MILLIS)
    runCurrent()

    val alice = viewModel.volunteers.value.single()
    assertEquals(11, alice.minutesAgo)
    assertTrue(alice.isStale)
  }

  @Test
  fun volunteers_whenReadingIsRefused_areEmptyAndTheMapStillLoads() = runTest {
    missions.createMission(mission("Water", startHour = 10, endHour = 11, at = STAND))
    share("alice", "bar", TENT.position)
    liveLocations.failure = LiveLocationRepositoryException.PermissionDenied()

    val viewModel = open(volunteerMap())

    assertTrue(viewModel.volunteers.value.isEmpty())
    assertEquals(1, viewModel.uiState.value.markers.size)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun volunteers_forAUserWhoManagesNoTeam_readNoPosition() = runTest {
    userId = "dave"
    val counting = CountsObservations(liveLocations)

    val volunteers = open(volunteerMap(counting)).volunteers.value

    assertTrue(volunteers.isEmpty())
    assertEquals(0, counting.observations)
  }

  @Test
  fun volunteers_withoutLiveLocations_areEmpty() = runTest {
    // The organizer's map, which is given no live locations for now.
    share("alice", "bar", TENT.position)

    assertTrue(open(viewModel()).volunteers.value.isEmpty())
  }

  @Test
  fun volunteers_stopBeingFollowedWhenTheMapLeaves() = runTest {
    val counting = CountsObservations(liveLocations)
    val viewModel = volunteerMap(counting)
    val screen =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          viewModel.volunteers.collect {}
        }
    runCurrent()
    assertEquals(2, counting.active)

    screen.cancel()
    advanceTimeBy(MapViewModel.STOP_TIMEOUT_MILLIS + 1)

    assertEquals(0, counting.active)
  }

  @Test
  fun volunteers_whenOneTeamIsRefused_keepShowingTheOtherTeams() = runTest {
    share("alice", "bar", TENT.position)
    share("carol", "stage", STAND.position)
    val refusing =
        FailsTeam(liveLocations, "bar", LiveLocationRepositoryException.PermissionDenied())
    val viewModel = open(volunteerMap(refusing))
    assertEquals(listOf("alice", "carol"), viewModel.volunteers.value.map { it.userId })

    // The organizer gives the bar to another manager while the map is open.
    refusing.fail()
    runCurrent()

    assertEquals(listOf("carol"), viewModel.volunteers.value.map { it.userId })
  }

  @Test
  fun volunteers_ofARefusedTeam_areNotReadAgain() = runTest {
    val refusing =
        FailsTeam(liveLocations, "bar", LiveLocationRepositoryException.PermissionDenied())
    open(volunteerMap(refusing))

    refusing.fail()
    advanceTimeBy(3 * MapViewModel.AGE_REFRESH_MILLIS)
    runCurrent()

    assertEquals(1, refusing.observations)
  }

  @Test
  fun volunteers_duringAFailure_keepTheirLastPositionAndAge() = runTest {
    share("alice", "bar", TENT.position, minutesAgo = 8)
    val viewModel = open(volunteerMap())
    assertFalse(viewModel.volunteers.value.single().isStale)

    // Offline: the teams can no longer be read, so no position comes.
    teams.failure = IllegalStateException("offline")
    clock = clock.plus(Duration.ofMinutes(3))
    advanceTimeBy(3 * MapViewModel.AGE_REFRESH_MILLIS)
    runCurrent()

    val alice = viewModel.volunteers.value.single()
    assertEquals(TENT.position, alice.position)
    assertEquals(11, alice.minutesAgo)
    assertTrue(alice.isStale)
  }

  @Test
  fun volunteers_afterAFailure_comeBackOnTheNextTry() = runTest {
    teams.failure = IllegalStateException("offline")
    val viewModel = open(volunteerMap())
    assertTrue(viewModel.volunteers.value.isEmpty())

    teams.failure = null
    share("alice", "bar", TENT.position)
    advanceTimeBy(MapViewModel.RETRY_DELAY_MILLIS)
    runCurrent()

    assertEquals("alice", viewModel.volunteers.value.single().userId)
  }

  @Test
  fun volunteers_whoLeaveTheTeamWhileTheMapIsOpen_goAfterTheNextRefresh() = runTest {
    share("alice", "bar", TENT.position)
    val viewModel = open(volunteerMap())

    teams.removeMember(EVENT.id, "bar", "alice")
    advanceTimeBy(MapViewModel.AGE_REFRESH_MILLIS)
    runCurrent()

    assertTrue(viewModel.volunteers.value.isEmpty())
  }

  @Test
  fun volunteers_whoJoinTheTeamWhileTheMapIsOpen_showUpAfterTheNextRefresh() = runTest {
    share("erin", "bar", TENT.position)
    val viewModel = open(volunteerMap())
    assertTrue(viewModel.volunteers.value.isEmpty())

    teams.addMember(EVENT.id, "bar", "erin")
    advanceTimeBy(MapViewModel.AGE_REFRESH_MILLIS)
    runCurrent()

    assertEquals("erin", viewModel.volunteers.value.single().userId)
  }

  @Test
  fun volunteers_ofATeamAssignedWhileTheMapIsOpen_showUpAfterTheNextRefresh() = runTest {
    share("dave", "parking", STAND.position)
    val viewModel = open(volunteerMap())
    assertTrue(viewModel.volunteers.value.isEmpty())

    teams.setManager(EVENT.id, "parking", USER)
    advanceTimeBy(MapViewModel.AGE_REFRESH_MILLIS)
    runCurrent()

    assertEquals("dave", viewModel.volunteers.value.single().userId)
  }

  @Test
  fun volunteers_ofATeamNoLongerManaged_goAfterTheNextRefresh() = runTest {
    share("carol", "stage", STAND.position)
    val viewModel = open(volunteerMap())
    assertEquals("carol", viewModel.volunteers.value.single().userId)

    teams.setManager(EVENT.id, "stage", "someone-else")
    advanceTimeBy(MapViewModel.AGE_REFRESH_MILLIS)
    runCurrent()

    assertTrue(viewModel.volunteers.value.isEmpty())
  }

  @Test
  fun volunteers_whenTheTeamsDoNotChange_areNotObservedAgain() = runTest {
    val counting = CountsObservations(liveLocations)
    open(volunteerMap(counting))
    assertEquals(2, counting.observations)

    advanceTimeBy(3 * MapViewModel.AGE_REFRESH_MILLIS)
    runCurrent()

    assertEquals(2, counting.observations)
  }

  @Test
  fun volunteers_afterLeavingTheMap_areForgotten() = runTest {
    share("alice", "bar", TENT.position)
    val viewModel = volunteerMap()
    val screen =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          viewModel.volunteers.collect {}
        }
    runCurrent()
    assertEquals(1, viewModel.volunteers.value.size)

    screen.cancel()
    advanceTimeBy(MapViewModel.STOP_TIMEOUT_MILLIS + 1)

    // Coming back must not show the old positions and ages before the new read.
    assertTrue(viewModel.volunteers.value.isEmpty())
  }

  @Test
  fun volunteers_whoseNameCouldNotBeRead_getItOnALaterRefresh() = runTest {
    users.failure = UserRepositoryException.Unavailable()
    share("alice", "bar", TENT.position)
    val viewModel = open(volunteerMap())
    assertNull(viewModel.volunteers.value.single().name)

    users.failure = null
    advanceTimeBy(MapViewModel.AGE_REFRESH_MILLIS)
    runCurrent()

    assertEquals("Alice", viewModel.volunteers.value.single().name)
  }

  @Test
  fun volunteers_measuredAheadOfOurClock_areJustMeasured() = runTest {
    // The volunteer's phone is 3 minutes ahead of ours.
    share("alice", "bar", TENT.position, minutesAgo = -3)

    val alice = open(volunteerMap()).volunteers.value.single()

    assertEquals(0, alice.minutesAgo)
    assertFalse(alice.isStale)
  }

  /** Delegates to [repository], counting the team observations started and still running. */
  private class CountsObservations(private val repository: LiveLocationRepository) :
      LiveLocationRepository by repository {
    var observations = 0
    var active = 0

    override fun observeTeamLocations(eventId: String, teamId: String): Flow<List<LiveLocation>> =
        flow {
          observations++
          active++
          try {
            emitAll(repository.observeTeamLocations(eventId, teamId))
          } finally {
            active--
          }
        }
  }

  /**
   * Delegates to [repository], except that the observation of [teamId] running when [fail] is
   * called fails with [failure]. Later observations of it work again, like a connection that comes
   * back.
   */
  private class FailsTeam(
      private val repository: LiveLocationRepository,
      private val teamId: String,
      private val failure: Exception,
  ) : LiveLocationRepository by repository {
    /** How many times [teamId] was observed. */
    var observations = 0
    private var trigger: CompletableDeferred<Unit>? = CompletableDeferred()

    fun fail() {
      trigger?.complete(Unit)
    }

    override fun observeTeamLocations(eventId: String, teamId: String): Flow<List<LiveLocation>> =
        if (teamId != this.teamId) repository.observeTeamLocations(eventId, teamId)
        else
            flow {
              observations++
              val failing = trigger
              coroutineScope {
                if (failing != null) {
                  launch {
                    failing.await()
                    trigger = null
                    throw failure
                  }
                }
                emitAll(repository.observeTeamLocations(eventId, teamId))
              }
            }
  }

  private fun team(id: String, manager: String, vararg members: String) =
      Team(
          id = id,
          eventId = EVENT.id,
          name = id,
          description = "",
          icon = "",
          managerId = manager,
          memberIds = members.toList(),
          createdAt = DAY,
      )

  private fun mission(
      title: String,
      startHour: Int,
      endHour: Int,
      at: MissionLocation?,
      assignees: List<String> = emptyList(),
  ) =
      Mission(
          eventId = EVENT.id,
          title = title,
          description = "",
          volunteersNeeded = 2,
          startAt = DAY.plusSeconds(startHour * 3600L),
          endAt = DAY.plusSeconds(endHour * 3600L),
          location = at,
          assigneeIds = assignees,
          createdAt = DAY,
      )

  private companion object {
    const val USER = "volunteer"
    val DAY: Instant = Instant.parse("2026-06-01T00:00:00Z")
    val NOON: Instant = DAY.plusSeconds(12 * 3600L)
    val TENT = MissionLocation("Tent", GeoPosition(46.521, 6.571))
    val STAND = MissionLocation("Stand", GeoPosition(46.519, 6.569))
    val EVENT =
        Event(
            id = "event-1",
            organizerId = "organizer",
            title = "City Marathon",
            description = "",
            type = EventType.SPORT,
            startAt = DAY,
            endAt = DAY.plusSeconds(86_400),
            location = EventLocation("Place Bellecour", GeoPosition(46.52, 6.57)),
            createdAt = DAY,
        )
  }
}
