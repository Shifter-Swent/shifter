// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.team

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepository
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
  private fun TestScope.open(repository: TeamRepository = teams) {
    viewModel = TeamSetupViewModel(eventId, repository)
    advanceUntilIdle()
  }

  /** Collects every deletion the ViewModel reports as failed, for the rest of the test. */
  private fun TestScope.deleteFailures(): List<Team> {
    val failures = mutableListOf<Team>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      viewModel.deleteFailures.toList(failures)
    }
    return failures
  }

  /** A [FakeTeamRepository] whose loads read its teams at once but answer only after [answer]. */
  private class SlowReads(private val teams: FakeTeamRepository) : TeamRepository by teams {
    var answer: CompletableDeferred<Unit>? = null

    override suspend fun getTeamsByEvent(eventId: String): List<Team> {
      val read = teams.getTeamsByEvent(eventId)
      answer?.await()
      return read
    }
  }

  /** A [FakeTeamRepository] whose deletions wait for [answer] to complete, and count. */
  private class PendingDeletions(private val teams: FakeTeamRepository) : TeamRepository by teams {
    val answer = CompletableDeferred<Unit>()
    var deletions = 0

    override suspend fun deleteTeam(eventId: String, teamId: String) {
      deletions++
      answer.await()
      teams.deleteTeam(eventId, teamId)
    }
  }

  @Test
  fun init_startsLoadingWithoutClaimingTheEventHasNoTeam() {
    viewModel = TeamSetupViewModel(eventId, teams)

    assertTrue(state.isLoading)
    assertFalse(state.isEmpty)
    assertFalse(state.canProceed)
  }

  @Test
  fun init_listsTheTeamsOfThisEventOnly() = runTest {
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
  fun eventWithoutTeams_isEmptyAndCannotProceed() = runTest {
    open()

    assertTrue(state.isEmpty)
    assertFalse(state.canProceed)
    assertTrue(state.teams.isEmpty())
  }

  @Test
  fun failedLoad_isReportedAndRetrySucceeds() = runTest {
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
  fun failedFirstLoad_isNotTheEmptyState() = runTest {
    teams.failure = IllegalStateException("offline")

    open()

    assertTrue(state.loadFailed)
    assertFalse(state.isEmpty)
    assertFalse(state.canProceed)
  }

  @Test
  fun refresh_onFailure_keepsTheTeamsAlreadyListed() = runTest {
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
  fun refresh_showsATeamCreatedSince() = runTest {
    open()
    assertTrue(state.isEmpty)

    teams.teams += team("logistics", "Logistics")
    viewModel.refresh()
    advanceUntilIdle()

    assertEquals(listOf("Logistics"), state.teams.map { it.name })
    assertFalse(state.isEmpty)
  }

  @Test
  fun onTeamCreated_listsTheTeamBeforeTheReloadAnswers() = runTest {
    open()

    viewModel.onTeamCreated(team("team-1", "Logistics"))

    // No advanceUntilIdle: the reload is deliberately still in flight.
    assertTrue(state.isLoading)
    assertTrue(state.canProceed)
    assertEquals(listOf("Logistics"), state.teams.map { it.name })
  }

  @Test
  fun onTeamCreated_withAFailingReload_keepsTheTeam() = runTest {
    open()

    teams.failure = IllegalStateException("offline")
    viewModel.onTeamCreated(team("team-1", "Logistics"))
    advanceUntilIdle()

    assertTrue(state.loadFailed)
    assertEquals(listOf("Logistics"), state.teams.map { it.name })
    assertTrue(state.canProceed)
  }

  @Test
  fun onTeamCreated_onceTheReloadBringsItBack_listsItOnce() = runTest {
    open()
    val created = team("team-1", "Logistics")
    teams.teams += created

    viewModel.onTeamCreated(created)
    advanceUntilIdle()

    assertEquals(listOf("Logistics"), state.teams.map { it.name })
  }

  @Test
  fun onTeamCreated_withATeamOfAnotherEvent_doesNotListIt() = runTest {
    open()

    viewModel.onTeamCreated(team("team-1", "Elsewhere", eventId = "event-2"))
    advanceUntilIdle()

    assertTrue(state.teams.isEmpty())
    assertFalse(state.canProceed)
  }

  @Test
  fun deleteTeam_takesTheTeamOutOfTheList() = runTest {
    teams.teams += team("logistics", "Logistics")
    teams.teams += team("bar", "Bar")
    open()

    viewModel.deleteTeam(state.teams.first())
    advanceUntilIdle()

    assertEquals(listOf("Bar"), state.teams.map { it.name })
    assertEquals(listOf("Bar"), teams.teams.map { it.name })
    assertTrue(state.deletingTeamIds.isEmpty())
  }

  @Test
  fun deleteTeam_whileRunning_marksTheTeamAndIsNotStartedTwice() = runTest {
    teams.teams += team("logistics", "Logistics")
    val pending = PendingDeletions(teams)
    open(pending)
    val logistics = state.teams.single()

    viewModel.deleteTeam(logistics)
    viewModel.deleteTeam(logistics)
    advanceUntilIdle()

    assertEquals(setOf("logistics"), state.deletingTeamIds)
    assertEquals(listOf("Logistics"), state.teams.map { it.name })
    assertEquals(1, pending.deletions)

    pending.answer.complete(Unit)
    advanceUntilIdle()

    assertTrue(state.teams.isEmpty())
    assertTrue(state.deletingTeamIds.isEmpty())
  }

  @Test
  fun deleteTeam_ofTheLastTeam_cannotProceedAnymore() = runTest {
    teams.teams += team("logistics", "Logistics")
    open()

    viewModel.deleteTeam(state.teams.single())
    advanceUntilIdle()

    assertFalse(state.canProceed)
    assertTrue(state.isEmpty)
  }

  @Test
  fun deleteTeam_ofTheOnlyTeam_cannotProceedWhileItRuns() = runTest {
    teams.teams += team("logistics", "Logistics")
    val pending = PendingDeletions(teams)
    open(pending)

    viewModel.deleteTeam(state.teams.single())
    runCurrent()

    assertFalse(state.canProceed)

    // A deletion that fails gives the team back.
    teams.failure = IllegalStateException("denied")
    pending.answer.complete(Unit)
    advanceUntilIdle()

    assertTrue(state.canProceed)
  }

  @Test
  fun deleteTeam_onFailure_keepsTheTeamAndReportsIt() = runTest {
    teams.teams += team("logistics", "Logistics")
    open()
    val failures = deleteFailures()
    val logistics = state.teams.single()

    teams.failure = IllegalStateException("offline")
    viewModel.deleteTeam(logistics)
    advanceUntilIdle()

    assertEquals(listOf(logistics), failures)
    assertEquals(listOf("Logistics"), state.teams.map { it.name })
    assertTrue(state.deletingTeamIds.isEmpty())
  }

  @Test
  fun deleteTeam_onceStarted_runsToItsEndAfterTheScreenIsLeft() = runTest {
    teams.teams += team("logistics", "Logistics")
    val pending = PendingDeletions(teams)
    open(pending)

    viewModel.deleteTeam(state.teams.single())
    runCurrent()
    // What leaving the screen does to its ViewModel.
    viewModel.viewModelScope.cancel()
    pending.answer.complete(Unit)
    advanceUntilIdle()

    assertTrue(teams.teams.isEmpty())
  }

  @Test
  fun refresh_thatReadADeletedTeam_doesNotBringItBack() = runTest {
    teams.teams += team("logistics", "Logistics")
    teams.teams += team("bar", "Bar")
    val reads = SlowReads(teams)
    open(reads)

    // The load reads both teams, then answers only once Logistics is deleted.
    val answer = CompletableDeferred<Unit>()
    reads.answer = answer
    viewModel.refresh()
    runCurrent()
    viewModel.deleteTeam(state.teams.first())
    advanceUntilIdle()
    answer.complete(Unit)
    advanceUntilIdle()

    assertEquals(listOf("Bar"), state.teams.map { it.name })
  }

  @Test
  fun deleteTeam_again_afterAFailure_deletesIt() = runTest {
    teams.teams += team("logistics", "Logistics")
    open()
    val logistics = state.teams.single()
    teams.failure = IllegalStateException("offline")
    viewModel.deleteTeam(logistics)
    advanceUntilIdle()

    teams.failure = null
    viewModel.deleteTeam(logistics)
    advanceUntilIdle()

    assertTrue(state.teams.isEmpty())
    assertTrue(teams.teams.isEmpty())
  }

  @Test
  fun newTeamFactory_createsTeamsUnderThisEvent() = runTest {
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
  fun editTeamFactory_editsThatTeam() = runTest {
    val logistics = team("logistics", "Logistics").copy(description = "Move the equipment")
    teams.teams += logistics
    open()

    val editTeam =
        viewModel
            .editTeamFactory(logistics)
            .create(CreateTeamViewModel::class.java, CreationExtras.Empty)
    editTeam.onNameChange("Supply")
    editTeam.saveChanges()
    advanceUntilIdle()

    assertEquals(listOf("Supply"), teams.teams.map { it.name })
  }

  @Test
  fun factory_createsAViewModelLoadingTheTeams() = runTest {
    teams.teams += team("logistics", "Logistics")

    viewModel =
        TeamSetupViewModel.factory(eventId, teams)
            .create(TeamSetupViewModel::class.java, CreationExtras.Empty)
    advanceUntilIdle()

    assertEquals(listOf("Logistics"), state.teams.map { it.name })
  }
}
