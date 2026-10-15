// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the team setup step of the organizer's event creation flow.
 *
 * @property loadFailed whether the last load failed. The teams of an earlier load are kept and stay
 *   listed.
 * @property teams the teams of the event, in the order the backend returns them.
 */
data class TeamSetupUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val teams: List<Team> = emptyList(),
) {
  /** Whether the event is known to have no team: not while a load runs, nor after one failed. */
  val isEmpty: Boolean
    get() = !isLoading && !loadFailed && teams.isEmpty()

  /** Whether the organizer can move on to the join code: the event has a team, new or older. */
  val canProceed: Boolean
    get() = teams.isNotEmpty()
}

/**
 * Lists the teams of the event [eventId] while the organizer sets them up.
 *
 * Creating one team belongs to [CreateTeamViewModel]: this ViewModel holds no form and never writes
 * a team. The screen hands it each creation through [onTeamCreated].
 *
 * The event itself is never loaded: the teams hang under its id, so that id is all this ViewModel
 * needs and it stays free of [com.swent.shifter.model.event.EventRepository].
 */
class TeamSetupViewModel(
    private val eventId: String,
    private val teamRepository: TeamRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(TeamSetupUiState())
  val uiState: StateFlow<TeamSetupUiState> = _uiState.asStateFlow()

  init {
    refresh()
  }

  /** Loads the teams of the event again, e.g. after a team was created or a load failed. */
  fun refresh() {
    _uiState.update { it.copy(isLoading = true, loadFailed = false) }
    viewModelScope.launch {
      try {
        val teams = teamRepository.getTeamsByEvent(eventId)
        _uiState.update { it.copy(isLoading = false, teams = teams) }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _uiState.update { it.copy(isLoading = false, loadFailed = true) }
      }
    }
  }

  /**
   * Takes in the [team] a [CreateTeamViewModel] has just persisted, then loads the list again.
   *
   * It joins the list right away rather than only once that load answers, so the organizer can move
   * on at once and a load that then fails still leaves it listed. A team of another event, or one
   * already listed, only triggers the load.
   */
  fun onTeamCreated(team: Team) {
    if (team.eventId == eventId && _uiState.value.teams.none { it.id == team.id }) {
      _uiState.update { it.copy(teams = it.teams + team) }
    }
    refresh()
  }

  /**
   * Builds the factory of the [CreateTeamViewModel] for one more team of this event. Each team
   * needs a fresh instance: that ViewModel refuses to create a second one.
   */
  fun newTeamFactory(): ViewModelProvider.Factory =
      CreateTeamViewModel.factory(eventId, teamRepository)

  companion object {
    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(eventId: String, teamRepository: TeamRepository): ViewModelProvider.Factory =
        viewModelFactory {
          initializer { TeamSetupViewModel(eventId, teamRepository) }
        }
  }
}
