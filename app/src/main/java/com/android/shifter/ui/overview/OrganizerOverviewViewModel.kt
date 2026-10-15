// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.team.TeamRepository
import kotlin.coroutines.cancellation.CancellationException
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
 */
data class OverviewMission(
    val id: String,
    val title: String,
    val teamId: String?,
    val isCompleted: Boolean,
)

/**
 * State of the organizer's management screen of an event.
 *
 * @property missions every mission of the event, by start time.
 * @property selectedTeamId the team whose missions are listed, or null to list them all.
 * @property loadFailed whether the last load failed. Data from an earlier load is kept and shown.
 */
data class OrganizerOverviewUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val teams: List<OverviewTeam> = emptyList(),
    val missions: List<OverviewMission> = emptyList(),
    val selectedTeamId: String? = null,
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
 * Loads the missions and teams of the event [eventId] for its management screen, and filters the
 * missions by team.
 */
class OrganizerOverviewViewModel(
    private val eventId: String,
    private val missionRepository: MissionRepository,
    private val teamRepository: TeamRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(OrganizerOverviewUiState())
  val uiState: StateFlow<OrganizerOverviewUiState> = _uiState.asStateFlow()

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
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { OrganizerOverviewViewModel(eventId, missionRepository, teamRepository) }
    }
  }
}
