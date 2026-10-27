// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.mission.MissionRepositoryException
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.ui.mission.AssigneeCandidates
import com.swent.shifter.ui.mission.AssigneeOption
import com.swent.shifter.ui.mission.AssigneeSelection
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A team ("pôle") of the event, offered as a filter of the missions. */
data class OverviewTeam(val id: String, val name: String)

/**
 * A mission as the management screen lists it.
 *
 * @property teamId id of the team the mission belongs to, or null for "General", no team.
 * @property volunteersNeeded how many people the mission needs, the most that can be assigned.
 * @property assigneeIds ids of the people assigned to the mission.
 */
data class OverviewMission(
    val id: String,
    val title: String,
    val teamId: String?,
    val isCompleted: Boolean,
    val volunteersNeeded: Int = 1,
    val assigneeIds: List<String> = emptyList(),
)

/**
 * Who is being assigned to the mission [missionId], while the organizer edits it.
 *
 * @property limit the most people that can be picked, the mission's volunteers needed.
 * @property isSaving whether the picked people are being stored.
 * @property saveFailed whether storing them failed; the selection is kept to try again.
 */
data class AssignState(
    val missionId: String,
    val title: String,
    val limit: Int,
    val selection: AssigneeSelection,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
)

/**
 * State of the organizer's management screen of an event.
 *
 * @property missions every mission of the event, by start time.
 * @property selectedTeamId the team whose missions are listed, or null to list them all.
 * @property loadFailed whether the last load failed. Data from an earlier load is kept and shown.
 * @property assigning the assignees being edited, or null when no mission is being assigned.
 */
data class OrganizerOverviewUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val teams: List<OverviewTeam> = emptyList(),
    val missions: List<OverviewMission> = emptyList(),
    val selectedTeamId: String? = null,
    val assigning: AssignState? = null,
) {
  /** The missions of the selected team, or all of them when none is selected. */
  val visibleMissions: List<OverviewMission>
    get() =
        if (selectedTeamId == null) missions else missions.filter { it.teamId == selectedTeamId }

  /** Name of the team [teamId], or null for "General" or a team that no longer exists. */
  fun teamName(teamId: String?): String? = teams.firstOrNull { it.id == teamId }?.name

  /** Completed missions of the whole event: the progress ignores the team filter. */
  val completedCount: Int
    get() = missions.count { it.isCompleted }

  /** Share of the event's missions that are completed, from 0 to 1; 0 when there is none. */
  val progress: Float
    get() = if (missions.isEmpty()) 0f else completedCount.toFloat() / missions.size
}

/**
 * Loads the missions and teams of the event [eventId] for its management screen, filters the
 * missions by team, and assigns people to a mission.
 */
class OrganizerOverviewViewModel(
    private val eventId: String,
    private val missionRepository: MissionRepository,
    private val teamRepository: TeamRepository,
    private val assigneeCandidates: AssigneeCandidates,
) : ViewModel() {

  private val _uiState = MutableStateFlow(OrganizerOverviewUiState())
  val uiState: StateFlow<OrganizerOverviewUiState> = _uiState.asStateFlow()

  private var candidatesJob: Job? = null

  init {
    refresh()
  }

  /** Loads the missions and teams again, e.g. after a mission was added or a load failed. */
  fun refresh() {
    _uiState.update { it.copy(isLoading = true, loadFailed = false) }
    viewModelScope.launch {
      try {
        val teams = teamRepository.getTeamsByEvent(eventId).map { OverviewTeam(it.id, it.name) }
        val missions =
            missionRepository
                .getMissionsByEvent(eventId)
                .sortedBy { it.startAt }
                .map {
                  OverviewMission(
                      id = it.id,
                      title = it.title,
                      teamId = it.teamId,
                      isCompleted = it.isCompleted(),
                      volunteersNeeded = it.volunteersNeeded,
                      assigneeIds = it.assigneeIds,
                  )
                }
        _uiState.update { state ->
          state.copy(
              isLoading = false,
              teams = teams,
              missions = missions,
              // A team removed since it was selected leaves nothing to filter on.
              selectedTeamId = state.selectedTeamId?.takeIf { id -> teams.any { it.id == id } },
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _uiState.update { it.copy(isLoading = false, loadFailed = true) }
      }
    }
  }

  /** Lists the missions of the team [teamId] only, or all of them when null. Unknown: ignored. */
  fun selectTeam(teamId: String?) {
    if (teamId != null && _uiState.value.teams.none { it.id == teamId }) return
    _uiState.update { it.copy(selectedTeamId = teamId) }
  }

  /**
   * Opens the assignees of the mission [missionId], with the people already assigned picked, and
   * loads who may be assigned to it. An unknown mission is ignored.
   */
  fun openAssign(missionId: String) {
    val mission = _uiState.value.missions.firstOrNull { it.id == missionId } ?: return
    _uiState.update {
      it.copy(
          assigning =
              AssignState(
                  missionId = mission.id,
                  title = mission.title,
                  limit = mission.volunteersNeeded,
                  selection =
                      AssigneeSelection(selectedIds = mission.assigneeIds, isLoading = true),
              )
      )
    }
    loadCandidates(mission)
  }

  /** Picks or unpicks the candidate [id], within the mission's volunteers needed. */
  fun toggleAssignee(id: String) {
    editAssigning { it.copy(selection = it.selection.toggle(id, it.limit), saveFailed = false) }
  }

  /** Loads the candidates again after a failed load, keeping who is picked. */
  fun retryAssignees() {
    val missionId = _uiState.value.assigning?.missionId ?: return
    _uiState.value.missions.firstOrNull { it.id == missionId }?.let { loadCandidates(it) }
  }

  /**
   * Closes the assignees without storing anything. Ignored while they are being stored: the save
   * cannot be taken back, so they stay open to show how it ends.
   */
  fun dismissAssign() {
    if (_uiState.value.assigning?.isSaving == true) return
    candidatesJob?.cancel()
    _uiState.update { it.copy(assigning = null) }
  }

  /**
   * Stores the people picked as the mission's assignees, then closes them and reloads the missions.
   * Nothing is stored before the candidates are loaded.
   *
   * When the mission was deleted, or now needs fewer people than picked, trying again cannot work:
   * the assignees are closed and the missions reloaded. On any other failure they stay open with
   * the same people picked, to try again.
   */
  fun saveAssignees() {
    val assigning = _uiState.value.assigning ?: return
    val selection = assigning.selection
    if (assigning.isSaving || selection.isLoading || selection.loadFailed) return
    editAssigning { it.copy(isSaving = true, saveFailed = false) }
    viewModelScope.launch {
      try {
        missionRepository.updateAssignees(eventId, assigning.missionId, selection.selectedIds)
        closeAssign(assigning.missionId) { missions ->
          // Reopening the mission before the reload answers must offer who was just saved.
          missions.map {
            if (it.id == assigning.missionId) it.copy(assigneeIds = selection.selectedIds) else it
          }
        }
        refresh()
      } catch (e: CancellationException) {
        throw e
      } catch (_: MissionRepositoryException.NotFound) {
        closeAssign(assigning.missionId)
        refresh()
      } catch (_: IllegalArgumentException) {
        closeAssign(assigning.missionId)
        refresh()
      } catch (_: Exception) {
        editAssigning(assigning.missionId) { it.copy(isSaving = false, saveFailed = true) }
      }
    }
  }

  /**
   * Closes the assignees of [missionId], if they are still the ones shown, and applies [change] to
   * the missions in the same update.
   */
  private fun closeAssign(
      missionId: String,
      change: (List<OverviewMission>) -> List<OverviewMission> = { it },
  ) {
    val shown = _uiState.value.assigning?.missionId == missionId
    // The candidates still loading are those of another mission when it was opened since.
    if (shown) candidatesJob?.cancel()
    _uiState.update { state ->
      state.copy(
          missions = change(state.missions),
          assigning = state.assigning?.takeIf { it.missionId != missionId },
      )
    }
  }

  /**
   * Loads who may be assigned to [mission], keeping who is picked. Someone picked who is no longer
   * a candidate, e.g. who left the team, stays listed so they can be unpicked.
   */
  private fun loadCandidates(mission: OverviewMission) {
    candidatesJob?.cancel()
    editAssigning { it.copy(selection = it.selection.copy(isLoading = true, loadFailed = false)) }
    candidatesJob = viewModelScope.launch {
      try {
        val candidates = assigneeCandidates.load(eventId, mission.teamId)
        editAssigning {
          val former =
              it.selection.selectedIds
                  .filter { id -> candidates.none { candidate -> candidate.id == id } }
                  .map { id -> AssigneeOption(id, name = null) }
          it.copy(
              selection = it.selection.copy(candidates = candidates + former, isLoading = false)
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        editAssigning {
          it.copy(selection = it.selection.copy(isLoading = false, loadFailed = true))
        }
      }
    }
  }

  /**
   * Applies [change] to the assignees shown, if any. With [missionId], only when they are still
   * those of that mission: the end of a save must not touch another mission opened since.
   */
  private fun editAssigning(missionId: String? = null, change: (AssignState) -> AssignState) {
    _uiState.update { state ->
      val assigning = state.assigning?.takeIf { missionId == null || it.missionId == missionId }
      if (assigning == null) state else state.copy(assigning = change(assigning))
    }
  }

  // TODO: read the status set by hand by the team's manager or the volunteer once missions have
  //  one. No mission can be marked completed yet, so none is.
  private fun Mission.isCompleted(): Boolean = false

  companion object {
    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(
        eventId: String,
        missionRepository: MissionRepository,
        teamRepository: TeamRepository,
        assigneeCandidates: AssigneeCandidates,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        OrganizerOverviewViewModel(eventId, missionRepository, teamRepository, assigneeCandidates)
      }
    }
  }
}
