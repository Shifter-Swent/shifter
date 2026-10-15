// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.team

import androidx.lifecycle.viewmodel.CreationExtras
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TeamSetupViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private val eventId = "event-1"
  private val createdAt = Instant.parse("2027-06-20T08:00:00Z")

  private lateinit var teams: FakeTeamRepository
  private lateinit var viewModel: TeamSetupViewModel

  private val state
    get() = viewModel.uiState.value

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    teams = FakeTeamRepository()
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
          createdAt = createdAt,
      )

  /** Opens the screen and lets its first load finish. */
  private fun TestScope.open() {
    viewModel = TeamSetupViewModel(eventId, teams)
    advanceUntilIdle()
  }

  @Test
  fun startsLoadingWithoutClaimingTheEventHasNoTeam() {
    viewModel = TeamSetupViewModel(eventId, teams)

    assertTrue(state.isLoading)
    assertFalse(state.isEmpty)
    assertFalse(state.canProceed)
  }

  @Test
  fun listsTheTeamsOfThisEventOnly() = runTest {
    teams.teams += team("logistics", "Logistics")
    teams.teams += team("bar", "Bar")
    teams.teams += team("elsewhere", "Elsewhere", eventId = "event-2")

    open()

    assertFalse(state.isLoading)
    assertFalse(state.loadFailed)
    assertEquals(listOf("Logistics", "Bar"), state.teams.map { it.name })
    assertTrue(state.canProceed)
    assertFalse(state.isEmpty)
  }

  @Test
  fun anEventWithoutTeamsIsEmptyAndCannotProceed() = runTest {
    open()

    assertTrue(state.isEmpty)
    assertFalse(state.canProceed)
    assertTrue(state.teams.isEmpty())
  }

  @Test
  fun aFailedLoadIsReportedAndRetrySucceeds() = runTest {
    teams.teams += team("logistics", "Logistics")
    teams.failure = IllegalStateException("offline")
    open()

    assertTrue(state.loadFailed)
    assertFalse(state.isLoading)

    teams.failure = null
    viewModel.refresh()
    advanceUntilIdle()

    assertFalse(state.loadFailed)
    assertEquals(listOf("Logistics"), state.teams.map { it.name })
  }

  @Test
  fun aFailedFirstLoadIsNotTheEmptyState() = runTest {
    teams.failure = IllegalStateException("offline")

    open()

    assertTrue(state.loadFailed)
    assertFalse(state.isEmpty)
    assertFalse(state.canProceed)
  }

  @Test
  fun aFailedRefreshKeepsTheTeamsAlreadyListed() = runTest {
    teams.teams += team("logistics", "Logistics")
    open()

    teams.failure = IllegalStateException("offline")
    viewModel.refresh()
    advanceUntilIdle()

    assertTrue(state.loadFailed)
    assertEquals(listOf("Logistics"), state.teams.map { it.name })
    assertTrue(state.canProceed)
  }

  @Test
  fun refreshShowsATeamCreatedSince() = runTest {
    open()
    assertTrue(state.isEmpty)

    teams.teams += team("logistics", "Logistics")
    viewModel.refresh()
    advanceUntilIdle()

    assertEquals(listOf("Logistics"), state.teams.map { it.name })
    assertFalse(state.isEmpty)
  }

  @Test
  fun aCreatedTeamIsListedBeforeTheReloadAnswers() = runTest {
    open()

    viewModel.onTeamCreated(team("team-1", "Logistics"))

    // No advanceUntilIdle: the reload is deliberately still in flight.
    assertTrue(state.isLoading)
    assertTrue(state.canProceed)
    assertEquals(listOf("Logistics"), state.teams.map { it.name })
  }

  @Test
  fun aCreatedTeamSurvivesAReloadThatFails() = runTest {
    open()

    teams.failure = IllegalStateException("offline")
    viewModel.onTeamCreated(team("team-1", "Logistics"))
    advanceUntilIdle()

    assertTrue(state.loadFailed)
    assertEquals(listOf("Logistics"), state.teams.map { it.name })
    assertTrue(state.canProceed)
  }

  @Test
  fun aCreatedTeamIsNotListedTwiceOnceTheReloadBringsItBack() = runTest {
    open()
    val created = team("team-1", "Logistics")
    teams.teams += created

    viewModel.onTeamCreated(created)
    advanceUntilIdle()

    assertEquals(listOf("Logistics"), state.teams.map { it.name })
  }

  @Test
  fun aTeamOfAnotherEventIsNotListed() = runTest {
    open()

    viewModel.onTeamCreated(team("team-1", "Elsewhere", eventId = "event-2"))
    advanceUntilIdle()

    assertTrue(state.teams.isEmpty())
    assertFalse(state.canProceed)
  }

  @Test
  fun newTeamFactoryCreatesTeamsUnderThisEvent() = runTest {
    open()

    val createTeam =
        viewModel.newTeamFactory().create(CreateTeamViewModel::class.java, CreationExtras.Empty)
    createTeam.onNameChange("Logistics")
    createTeam.onIconChange("truck")
    createTeam.onDescriptionChange("Move the equipment around the site")
    createTeam.createTeam()
    advanceUntilIdle()

    val created = checkNotNull(createTeam.uiState.value.createdTeam)
    assertEquals(eventId, created.eventId)
    assertEquals("Logistics", created.name)
    assertEquals(listOf("Logistics"), teams.teams.map { it.name })
  }

  @Test
  fun factoryCreatesAViewModelLoadingTheTeams() = runTest {
    teams.teams += team("logistics", "Logistics")

    viewModel =
        TeamSetupViewModel.factory(eventId, teams)
            .create(TeamSetupViewModel::class.java, CreationExtras.Empty)
    advanceUntilIdle()

    assertEquals(listOf("Logistics"), state.teams.map { it.name })
  }
}
