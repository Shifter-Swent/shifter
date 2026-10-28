// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.attendance

import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FakeEventRepository
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.User
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelTest {

  private val start = Instant.parse("2027-06-20T08:00:00Z")
  private val events = FakeEventRepository()
  private val missions = FakeMissionRepository()
  private val teams = FakeTeamRepository()
  private val users =
      FakeUserRepository(listOf(User("ann", "Ann", "ann@x.org"), User("bob", "Bob", "bob@x.org")))

  private lateinit var viewModel: AttendanceViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
    events.events +=
        Event(
            id = "event-1",
            organizerId = "organizer",
            title = "Festival",
            description = "",
            type = EventType.MUSIC,
            startAt = start,
            endAt = start.plus(Duration.ofDays(1)),
            location = EventLocation("Lausanne"),
            createdAt = start,
        )
    // "manager" manages two of the teams, "other-manager" the third.
    teams.teams +=
        listOf(
            team("logistics", "Logistics", "manager"),
            team("bar", "Bar", "manager"),
            team("security", "Security", "other-manager"),
        )
  }

  @After fun tearDown() = Dispatchers.resetMain()

  private fun team(id: String, name: String, managerId: String) =
      Team(
          id = id,
          eventId = "event-1",
          name = name,
          description = "",
          icon = "truck",
          managerId = managerId,
          createdAt = start,
      )

  /** Adds a mission starting [hour] hours after [start], so a test controls the order. */
  private suspend fun addMission(
      title: String,
      teamId: String?,
      hour: Long,
      assigneeIds: List<String> = emptyList(),
      eventId: String = "event-1",
  ) =
      missions.createMission(
          Mission(
              eventId = eventId,
              title = title,
              description = "",
              teamId = teamId,
              volunteersNeeded = 3,
              startAt = start.plus(Duration.ofHours(hour)),
              endAt = start.plus(Duration.ofHours(hour + 1)),
              assigneeIds = assigneeIds,
              createdAt = start,
          )
      )

  private fun TestScope.open(userId: String) {
    viewModel = AttendanceViewModel("event-1", userId, events, missions, teams, users)
    advanceUntilIdle()
  }

  private fun titles() = viewModel.uiState.value.missions.map { it.title }

  @Test
  fun load_asOrganizer_listsEveryMissionByStartTimeWithItsTeamAndAssignees() = runTest {
    addMission("Serve drinks", "bar", hour = 3)
    addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("bob", "ann"))
    addMission("Welcome", null, hour = 2)
    // Starts first, so it would head the list if the event were not filtered on.
    addMission("Elsewhere", "logistics", hour = 0, eventId = "event-2")

    open("organizer")

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertFalse(state.loadFailed)
    assertTrue(state.isOrganizer)
    assertEquals(listOf("Set up", "Welcome", "Serve drinks"), titles())
    assertEquals(listOf("Logistics", null, "Bar"), state.missions.map { it.teamName })
    val setUp = state.missions.first()
    // The assignees keep the order of the mission, and are not a count of who is present.
    assertEquals(
        listOf(AttendanceVolunteer("bob", "Bob"), AttendanceVolunteer("ann", "Ann")),
        setUp.assignees,
    )
    assertEquals(3, setUp.volunteersNeeded)
  }

  @Test
  fun load_asManager_listsOnlyTheMissionsOfTheirTeams() = runTest {
    addMission("Set up", "logistics", hour = 1)
    addMission("Serve drinks", "bar", hour = 2)
    addMission("Guard the gate", "security", hour = 3)
    addMission("Welcome", null, hour = 4)
    addMission("Elsewhere", "logistics", hour = 0, eventId = "event-2")

    open("manager")

    assertFalse(viewModel.uiState.value.isOrganizer)
    assertEquals(listOf("Set up", "Serve drinks"), titles())
  }

  @Test
  fun load_asManager_queriesTheirTeamsMissionsAndNotTheWholeEvent() = runTest {
    addMission("Set up", "logistics", hour = 1)
    val recording = RecordingMissions()

    viewModel = AttendanceViewModel("event-1", "manager", events, recording, teams, users)
    advanceUntilIdle()

    assertEquals(listOf(setOf("logistics", "bar")), recording.byTeams)
    assertEquals(0, recording.byEventCalls)
    assertEquals(listOf("Set up"), titles())
  }

  @Test
  fun load_withoutAnyManagedTeam_queriesNoMissionAndListsNone() = runTest {
    addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann"))
    addMission("Welcome", null, hour = 2)
    val recording = RecordingMissions()

    viewModel = AttendanceViewModel("event-1", "ann", events, recording, teams, users)
    advanceUntilIdle()

    // A volunteer manages no team, so there is nothing to ask the mission repository for.
    assertEquals(emptyList<Set<String>>(), recording.byTeams)
    assertEquals(0, recording.byEventCalls)
    val state = viewModel.uiState.value
    assertFalse(state.isOrganizer)
    assertFalse(state.loadFailed)
    assertTrue(state.isEmpty)
  }

  @Test
  fun load_withoutAPublicProfile_keepsTheAssigneeWithoutAName() = runTest {
    addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann", "ghost"))

    open("manager")

    assertFalse(viewModel.uiState.value.loadFailed)
    assertEquals(
        listOf(AttendanceVolunteer("ann", "Ann"), AttendanceVolunteer("ghost", null)),
        viewModel.uiState.value.missions.single().assignees,
    )
  }

  @Test
  fun load_withoutAnyMission_loadsThenShowsAnEmptyState() = runTest {
    viewModel = AttendanceViewModel("event-1", "organizer", events, missions, teams, users)
    assertTrue(viewModel.uiState.value.isLoading)
    assertFalse(viewModel.uiState.value.isEmpty)

    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertFalse(state.loadFailed)
    assertTrue(state.isEmpty)
  }

  @Test
  fun load_whenAReadFails_showsTheFailureAndNoMission() = runTest {
    addMission("Set up", "logistics", hour = 1)
    open("manager")
    assertEquals(listOf("Set up"), titles())
    events.failure = EventRepositoryException.Unavailable()

    viewModel.refresh()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertTrue(state.loadFailed)
    assertFalse(state.isEmpty)
    assertTrue(state.missions.isEmpty())
  }

  @Test
  fun refresh_listsTheMissionsAddedSince() = runTest {
    addMission("Set up", "logistics", hour = 1)
    open("manager")
    addMission("Serve drinks", "bar", hour = 2, assigneeIds = listOf("ann"))

    viewModel.refresh()
    advanceUntilIdle()

    assertEquals(listOf("Set up", "Serve drinks"), titles())
    assertEquals(
        listOf(AttendanceVolunteer("ann", "Ann")),
        viewModel.uiState.value.missions.last().assignees,
    )
  }

  @Test
  fun refresh_whileALoadRuns_keepsOnlyTheNewestMissions() = runTest {
    addMission("Set up", "logistics", hour = 1)
    // The first load reads the missions, then is held long enough to be overtaken.
    viewModel = AttendanceViewModel("event-1", "manager", events, SlowFirstRead(), teams, users)
    advanceTimeBy(500)
    addMission("Serve drinks", "bar", hour = 2)

    viewModel.refresh()
    advanceUntilIdle()

    assertEquals(listOf("Set up", "Serve drinks"), titles())
  }

  /** Records which mission query a load used, to pin down what each role asks the backend for. */
  private inner class RecordingMissions : MissionRepository by missions {
    var byEventCalls = 0
    val byTeams = mutableListOf<Set<String>>()

    override suspend fun getMissionsByEvent(eventId: String): List<Mission> {
      byEventCalls++
      return missions.getMissionsByEvent(eventId)
    }

    override suspend fun getMissionsByTeams(eventId: String, teamIds: Set<String>): List<Mission> {
      byTeams += teamIds
      return missions.getMissionsByTeams(eventId, teamIds)
    }
  }

  /**
   * A [MissionRepository] whose first read answers late, after a newer load has published. It reads
   * before waiting, so the held load carries the missions as they were when it started.
   */
  private inner class SlowFirstRead : MissionRepository by missions {
    private var reads = 0

    override suspend fun getMissionsByTeams(eventId: String, teamIds: Set<String>): List<Mission> {
      val read = missions.getMissionsByTeams(eventId, teamIds)
      if (reads++ == 0) delay(1_000)
      return read
    }
  }
}
