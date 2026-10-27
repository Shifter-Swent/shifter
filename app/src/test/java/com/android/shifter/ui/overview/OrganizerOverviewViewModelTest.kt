// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.overview

import androidx.lifecycle.viewmodel.CreationExtras
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.mission.MissionRepositoryException
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.ui.mission.AssigneeCandidates
import com.swent.shifter.ui.mission.AssigneeOption
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
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
  private lateinit var repository: SlowMissionRepository
  private lateinit var teams: FakeTeamRepository
  private lateinit var viewModel: OrganizerOverviewViewModel

  /** Who may be assigned to a mission of each team; null is "General". */
  private val candidatesByTeam =
      mapOf(
          "logistics" to listOf(AssigneeOption("ana", "Ana"), AssigneeOption("ben", "Ben")),
          null to listOf(AssigneeOption("eva", "Eva")),
      )

  /** Thrown by every load of the candidates when set. */
  private var candidatesFailure: Exception? = null

  private val candidates = AssigneeCandidates { _, teamId ->
    candidatesFailure?.let { throw it }
    candidatesByTeam[teamId].orEmpty()
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    missions = FakeMissionRepository()
    repository = SlowMissionRepository(missions)
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
      volunteersNeeded: Int = 1,
      assigneeIds: List<String> = emptyList(),
  ) =
      missions.createMission(
          Mission(
              eventId = eventId,
              title = title,
              description = "",
              teamId = teamId,
              volunteersNeeded = volunteersNeeded,
              startAt = start.plus(Duration.ofHours(hour)),
              endAt = start.plus(Duration.ofHours(hour + 1)),
              assigneeIds = assigneeIds,
              createdAt = start,
          )
      )

  private val assigning: AssignState?
    get() = viewModel.uiState.value.assigning

  private fun TestScope.open() {
    viewModel = OrganizerOverviewViewModel(eventId, repository, teams, candidates)
    advanceUntilIdle()
  }

  @Test
  fun init_startsLoading() {
    viewModel = OrganizerOverviewViewModel(eventId, missions, teams, candidates)

    assertTrue(viewModel.uiState.value.isLoading)
  }

  @Test
  fun init_loadsTheEventsTeamsAndItsMissionsByStartTime() = runTest {
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
  fun teamName_ofAnUnknownTeam_isNull() = runTest {
    addMission("Orphan", "deleted", hour = 1)

    open()

    val state = viewModel.uiState.value
    assertEquals("deleted", state.missions.single().teamId)
    assertNull(state.teamName("deleted"))
  }

  @Test
  fun selectTeam_listsOnlyItsMissions() = runTest {
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
  fun selectTeam_withAll_listsEveryMissionAgain() = runTest {
    addMission("Set up", "logistics", hour = 1)
    addMission("Serve drinks", "bar", hour = 2)
    open()
    viewModel.selectTeam("bar")

    viewModel.selectTeam(null)

    assertNull(viewModel.uiState.value.selectedTeamId)
    assertEquals(2, viewModel.uiState.value.visibleMissions.size)
  }

  @Test
  fun selectTeam_withUnknownTeam_isIgnored() = runTest {
    open()
    viewModel.selectTeam("bar")

    viewModel.selectTeam("other")

    assertEquals("bar", viewModel.uiState.value.selectedTeamId)
  }

  @Test
  fun progress_coversEveryMissionWhateverTheFilter() = runTest {
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
  fun progress_ofCompletedMissions_isTheirShare() {
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
  fun eventWithoutMissions_hasNoProgress() = runTest {
    open()

    assertEquals(0f, viewModel.uiState.value.progress)
    assertTrue(viewModel.uiState.value.visibleMissions.isEmpty())
  }

  @Test
  fun failedLoad_isReportedAndRetrySucceeds() = runTest {
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
  fun refresh_onFailure_keepsWhatWasShown() = runTest {
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
  fun refresh_showsAMissionAddedSince() = runTest {
    open()

    addMission("Set up", "logistics", hour = 1)
    viewModel.refresh()
    advanceUntilIdle()

    assertEquals(listOf("Set up"), viewModel.uiState.value.missions.map { it.title })
  }

  @Test
  fun refresh_dropsTheFilterOfARemovedTeam() = runTest {
    open()
    viewModel.selectTeam("bar")

    teams.teams.removeAll { it.id == "bar" }
    viewModel.refresh()
    advanceUntilIdle()

    assertNull(viewModel.uiState.value.selectedTeamId)
  }

  @Test
  fun init_loadsWhoIsAssignedAndHowManyAreNeeded() = runTest {
    addMission("Set up", "logistics", hour = 1, volunteersNeeded = 3, assigneeIds = listOf("ana"))

    open()

    val mission = viewModel.uiState.value.missions.single()
    assertEquals(3, mission.volunteersNeeded)
    assertEquals(listOf("ana"), mission.assigneeIds)
  }

  @Test
  fun openAssign_preselectsTheAssigneesAndLoadsTheTeamsCandidates() = runTest {
    val mission =
        addMission(
            "Set up",
            "logistics",
            hour = 1,
            volunteersNeeded = 2,
            assigneeIds = listOf("ben"),
        )
    open()

    viewModel.openAssign(mission.id)
    assertTrue("the candidates are loading", assigning!!.selection.isLoading)
    advanceUntilIdle()

    val state = assigning!!
    assertEquals(mission.id, state.missionId)
    assertEquals("Set up", state.title)
    assertEquals(2, state.limit)
    assertEquals(listOf("ben"), state.selection.selectedIds)
    assertEquals(candidatesByTeam["logistics"], state.selection.candidates)
    assertFalse(state.selection.isLoading)
  }

  @Test
  fun openAssign_forAGeneralMission_loadsTheParticipants() = runTest {
    val mission = addMission("Welcome", null, hour = 1)
    open()

    viewModel.openAssign(mission.id)
    advanceUntilIdle()

    assertEquals(listOf(AssigneeOption("eva", "Eva")), assigning!!.selection.candidates)
  }

  @Test
  fun openAssign_withAFormerMember_keepsThemToBeUnassigned() = runTest {
    val mission =
        addMission(
            "Set up",
            "logistics",
            hour = 1,
            volunteersNeeded = 2,
            assigneeIds = listOf("ana", "left"),
        )
    open()

    viewModel.openAssign(mission.id)
    advanceUntilIdle()
    viewModel.toggleAssignee("left")

    assertEquals(AssigneeOption("left", null), assigning!!.selection.candidates.last())
    assertEquals(listOf("ana"), assigning!!.selection.selectedIds)
  }

  @Test
  fun openAssign_withUnknownMission_isIgnored() = runTest {
    open()

    viewModel.openAssign("missing")

    assertNull(assigning)
  }

  @Test
  fun openAssign_onCandidatesFailure_canBeRetried() = runTest {
    val mission =
        addMission(
            "Set up",
            "logistics",
            hour = 1,
            volunteersNeeded = 2,
            assigneeIds = listOf("ana"),
        )
    open()
    candidatesFailure = IllegalStateException("offline")

    viewModel.openAssign(mission.id)
    advanceUntilIdle()
    assertTrue(assigning!!.selection.loadFailed)

    candidatesFailure = null
    viewModel.retryAssignees()
    advanceUntilIdle()

    assertFalse(assigning!!.selection.loadFailed)
    assertEquals(candidatesByTeam["logistics"], assigning!!.selection.candidates)
    assertEquals(listOf("ana"), assigning!!.selection.selectedIds)
  }

  @Test
  fun toggleAssignee_picksNoMorePeopleThanNeeded() = runTest {
    val mission = addMission("Set up", "logistics", hour = 1)
    open()
    viewModel.openAssign(mission.id)
    advanceUntilIdle()

    viewModel.toggleAssignee("ana")
    viewModel.toggleAssignee("ben")

    assertEquals(listOf("ana"), assigning!!.selection.selectedIds)
  }

  @Test
  fun saveAssignees_storesTheSelectionAndRefreshes() = runTest {
    val mission = addMission("Set up", "logistics", hour = 1, volunteersNeeded = 2)
    open()
    viewModel.openAssign(mission.id)
    advanceUntilIdle()
    viewModel.toggleAssignee("ben")
    viewModel.toggleAssignee("ana")

    viewModel.saveAssignees()
    advanceUntilIdle()

    assertEquals(listOf("ben", "ana"), missions.created.single().assigneeIds)
    assertNull(assigning)
    assertEquals(listOf("ben", "ana"), viewModel.uiState.value.missions.single().assigneeIds)
  }

  @Test
  fun saveAssignees_onFailure_keepsTheDialogAndTheSelection() = runTest {
    val mission = addMission("Set up", "logistics", hour = 1)
    open()
    viewModel.openAssign(mission.id)
    advanceUntilIdle()
    viewModel.toggleAssignee("ana")
    missions.failure = IllegalStateException("offline")

    viewModel.saveAssignees()
    advanceUntilIdle()

    val state = assigning!!
    assertTrue(state.saveFailed)
    assertFalse(state.isSaving)
    assertEquals(listOf("ana"), state.selection.selectedIds)
    assertEquals(emptyList<String>(), missions.created.single().assigneeIds)
  }

  @Test
  fun dismissAssign_writesNothing() = runTest {
    val mission = addMission("Set up", "logistics", hour = 1)
    open()
    viewModel.openAssign(mission.id)
    advanceUntilIdle()
    viewModel.toggleAssignee("ana")

    viewModel.dismissAssign()
    advanceUntilIdle()

    assertNull(assigning)
    assertEquals(emptyList<String>(), missions.created.single().assigneeIds)
  }

  @Test
  fun saveAssignees_beforeTheReload_updatesTheMission() = runTest {
    val mission = addMission("Set up", "logistics", hour = 1, volunteersNeeded = 2)
    open()
    viewModel.openAssign(mission.id)
    advanceUntilIdle()
    viewModel.toggleAssignee("ana")
    repository.reload = CompletableDeferred()

    viewModel.saveAssignees()
    advanceUntilIdle()
    // The reload has not answered yet: reopening must not offer the old assignees.
    viewModel.openAssign(mission.id)

    assertEquals(listOf("ana"), assigning!!.selection.selectedIds)
    repository.reload!!.complete(Unit)
  }

  @Test
  fun dismissAssign_whileSaving_isIgnored() = runTest {
    val mission = addMission("Set up", "logistics", hour = 1)
    open()
    viewModel.openAssign(mission.id)
    advanceUntilIdle()
    viewModel.toggleAssignee("ana")
    repository.save = CompletableDeferred()
    viewModel.saveAssignees()
    advanceUntilIdle()

    viewModel.dismissAssign()

    assertTrue(assigning!!.isSaving)
    repository.save!!.complete(Unit)
    advanceUntilIdle()
    assertNull(assigning)
    assertEquals(listOf("ana"), missions.created.single().assigneeIds)
  }

  @Test
  fun saveAssignees_afterAnotherMissionOpened_leavesItsDialog() = runTest {
    val first = addMission("Set up", "logistics", hour = 1)
    val second = addMission("Pack up", "logistics", hour = 3)
    open()
    viewModel.openAssign(first.id)
    advanceUntilIdle()
    repository.save = CompletableDeferred()
    viewModel.saveAssignees()
    advanceUntilIdle()

    viewModel.openAssign(second.id)
    advanceUntilIdle()
    repository.save!!.completeExceptionally(IllegalStateException("offline"))
    advanceUntilIdle()

    assertEquals(second.id, assigning!!.missionId)
    assertFalse(assigning!!.saveFailed)
  }

  @Test
  fun saveAssignees_whenTheCandidatesFailed_isIgnored() = runTest {
    val mission = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ana"))
    open()
    candidatesFailure = IllegalStateException("offline")
    viewModel.openAssign(mission.id)
    advanceUntilIdle()

    viewModel.saveAssignees()
    advanceUntilIdle()

    assertTrue(assigning!!.selection.loadFailed)
    assertFalse(assigning!!.isSaving)
  }

  @Test
  fun saveAssignees_ofAChangedMission_closesAndReloads() = runTest {
    val mission = addMission("Set up", "logistics", hour = 1)
    // The mission was deleted, or now needs fewer people than picked: trying again cannot work.
    for (failure in listOf(MissionRepositoryException.NotFound(), IllegalArgumentException())) {
      open()
      viewModel.openAssign(mission.id)
      advanceUntilIdle()
      missions.failure = failure

      viewModel.saveAssignees()
      advanceUntilIdle()

      assertNull(assigning)
      missions.failure = null
    }
  }

  @Test
  fun retryAssignees_afterAReload_keepsTheFormerPick() = runTest {
    val mission =
        addMission(
            "Set up",
            "logistics",
            hour = 1,
            volunteersNeeded = 2,
            assigneeIds = listOf("left"),
        )
    open()
    candidatesFailure = IllegalStateException("offline")
    viewModel.openAssign(mission.id)
    advanceUntilIdle()
    // Someone else unassigned them meanwhile; they are still picked here.
    missions.updateAssignees(eventId, mission.id, emptyList())
    viewModel.refresh()
    advanceUntilIdle()

    candidatesFailure = null
    viewModel.retryAssignees()
    advanceUntilIdle()

    assertEquals(listOf("left"), assigning!!.selection.selectedIds)
    assertEquals(AssigneeOption("left", null), assigning!!.selection.candidates.last())
  }

  @Test
  fun factory_createsAViewModelLoadingTheEvent() = runTest {
    addMission("Set up", "logistics", hour = 1)

    viewModel =
        OrganizerOverviewViewModel.factory(eventId, missions, teams, candidates)
            .create(OrganizerOverviewViewModel::class.java, CreationExtras.Empty)
    advanceUntilIdle()

    assertEquals(listOf("Set up"), viewModel.uiState.value.missions.map { it.title })
  }
}

/**
 * Delegates to [missions], and lets a test hold the reload of the missions or the save of the
 * assignees, as a slow network would.
 */
private class SlowMissionRepository(private val missions: FakeMissionRepository) :
    MissionRepository by missions {

  var reload: CompletableDeferred<Unit>? = null
  var save: CompletableDeferred<Unit>? = null

  override suspend fun getMissionsByEvent(eventId: String): List<Mission> {
    reload?.await()
    return missions.getMissionsByEvent(eventId)
  }

  override suspend fun updateAssignees(
      eventId: String,
      missionId: String,
      assigneeIds: List<String>,
  ) {
    save?.await()
    missions.updateAssignees(eventId, missionId, assigneeIds)
  }
}
