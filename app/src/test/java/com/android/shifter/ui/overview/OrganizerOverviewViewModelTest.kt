// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.overview

import androidx.lifecycle.viewmodel.CreationExtras
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrganizerOverviewViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private val eventId = "event-1"
  private val start = Instant.parse("2027-06-20T08:00:00Z")

  private lateinit var missions: FakeMissionRepository
  private lateinit var teams: FakeTeamRepository
  private lateinit var viewModel: OrganizerOverviewViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    missions = FakeMissionRepository()
    teams =
        FakeTeamRepository(
            listOf(team("logistics", "Logistics"), team("bar", "Bar"), team("other", "Other", "x"))
        )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun team(id: String, name: String, eventId: String = this.eventId) =
      Team(
          id = id,
          eventId = eventId,
          name = name,
          description = "",
          icon = "truck",
          createdAt = start,
      )

  /** Adds a mission starting [hour] hours after [start], so tests control the order. */
  private suspend fun addMission(
      title: String,
      teamId: String?,
      hour: Long,
      eventId: String = this.eventId,
  ) =
      missions.createMission(
          Mission(
              eventId = eventId,
              title = title,
              description = "",
              teamId = teamId,
              volunteersNeeded = 1,
              startAt = start.plus(Duration.ofHours(hour)),
              endAt = start.plus(Duration.ofHours(hour + 1)),
              createdAt = start,
          )
      )

  private fun TestScope.open() {
    viewModel = OrganizerOverviewViewModel(eventId, missions, teams)
    advanceUntilIdle()
  }

  @Test
  fun startsLoading() {
    viewModel = OrganizerOverviewViewModel(eventId, missions, teams)

    assertTrue(viewModel.uiState.value.isLoading)
  }

  @Test
  fun loadsTheEventsTeamsAndItsMissionsByStartTime() = runTest {
    addMission("Serve drinks", "bar", hour = 3)
    addMission("Set up", "logistics", hour = 1)
    addMission("Welcome", null, hour = 2)
    addMission("Elsewhere", null, hour = 0, eventId = "event-2")

    open()

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertFalse(state.loadFailed)
    assertEquals(
        listOf(OverviewTeam("logistics", "Logistics"), OverviewTeam("bar", "Bar")),
        state.teams,
    )
    assertEquals(listOf("Set up", "Welcome", "Serve drinks"), state.missions.map { it.title })
    assertEquals(
        listOf("Logistics", null, "Bar"),
        state.missions.map { state.teamName(it.teamId) },
    )
  }

  @Test
  fun aMissionOfAnUnknownTeamHasNoTeamName() = runTest {
    addMission("Orphan", "deleted", hour = 1)

    open()

    val state = viewModel.uiState.value
    assertEquals("deleted", state.missions.single().teamId)
    assertNull(state.teamName("deleted"))
  }

  @Test
  fun selectingATeamListsOnlyItsMissions() = runTest {
    addMission("Set up", "logistics", hour = 1)
    addMission("Serve drinks", "bar", hour = 2)
    addMission("Welcome", null, hour = 3)
    open()

    viewModel.selectTeam("bar")

    val state = viewModel.uiState.value
    assertEquals("bar", state.selectedTeamId)
    assertEquals(listOf("Serve drinks"), state.visibleMissions.map { it.title })
  }

  @Test
  fun selectingAllListsEveryMissionAgain() = runTest {
    addMission("Set up", "logistics", hour = 1)
    addMission("Serve drinks", "bar", hour = 2)
    open()
    viewModel.selectTeam("bar")

    viewModel.selectTeam(null)

    assertNull(viewModel.uiState.value.selectedTeamId)
    assertEquals(2, viewModel.uiState.value.visibleMissions.size)
  }

  @Test
  fun selectingAnUnknownTeamIsIgnored() = runTest {
    open()
    viewModel.selectTeam("bar")

    viewModel.selectTeam("other")

    assertEquals("bar", viewModel.uiState.value.selectedTeamId)
  }

  @Test
  fun theProgressCoversEveryMissionWhateverTheFilter() = runTest {
    addMission("Set up", "logistics", hour = 1)
    addMission("Serve drinks", "bar", hour = 2)
    open()

    viewModel.selectTeam("bar")

    val state = viewModel.uiState.value
    assertEquals(2, state.missions.size)
    // No mission can be marked completed yet.
    assertEquals(0, state.completedCount)
    assertEquals(0f, state.progress)
  }

  @Test
  fun theProgressOfCompletedMissionsIsTheirShare() {
    val state =
        OrganizerOverviewUiState(
            missions =
                listOf(
                    OverviewMission("1", "A", null, isCompleted = true),
                    OverviewMission("2", "B", null, isCompleted = false),
                    OverviewMission("3", "C", null, isCompleted = true),
                    OverviewMission("4", "D", null, isCompleted = false),
                )
        )

    assertEquals(2, state.completedCount)
    assertEquals(0.5f, state.progress)
  }

  @Test
  fun anEventWithoutMissionsHasNoProgress() = runTest {
    open()

    assertEquals(0f, viewModel.uiState.value.progress)
    assertTrue(viewModel.uiState.value.visibleMissions.isEmpty())
  }

  @Test
  fun aFailedLoadIsReportedAndRetrySucceeds() = runTest {
    addMission("Set up", "logistics", hour = 1)
    missions.failure = IllegalStateException("offline")
    open()

    assertTrue(viewModel.uiState.value.loadFailed)
    assertFalse(viewModel.uiState.value.isLoading)

    missions.failure = null
    viewModel.refresh()
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.loadFailed)
    assertEquals(listOf("Set up"), viewModel.uiState.value.missions.map { it.title })
  }

  @Test
  fun aFailedRefreshKeepsWhatWasShown() = runTest {
    addMission("Set up", "logistics", hour = 1)
    open()

    teams.failure = IllegalStateException("offline")
    viewModel.refresh()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state.loadFailed)
    assertEquals(listOf("Set up"), state.missions.map { it.title })
    assertEquals(2, state.teams.size)
  }

  @Test
  fun refreshShowsAMissionAddedSince() = runTest {
    open()

    addMission("Set up", "logistics", hour = 1)
    viewModel.refresh()
    advanceUntilIdle()

    assertEquals(listOf("Set up"), viewModel.uiState.value.missions.map { it.title })
  }

  @Test
  fun refreshDropsTheFilterOfARemovedTeam() = runTest {
    open()
    viewModel.selectTeam("bar")

    teams.teams.removeAll { it.id == "bar" }
    viewModel.refresh()
    advanceUntilIdle()

    assertNull(viewModel.uiState.value.selectedTeamId)
  }

  @Test
  fun factoryCreatesAViewModelLoadingTheEvent() = runTest {
    addMission("Set up", "logistics", hour = 1)

    viewModel =
        OrganizerOverviewViewModel.factory(eventId, missions, teams)
            .create(OrganizerOverviewViewModel::class.java, CreationExtras.Empty)
    advanceUntilIdle()

    assertEquals(listOf("Set up"), viewModel.uiState.value.missions.map { it.title })
  }
}
