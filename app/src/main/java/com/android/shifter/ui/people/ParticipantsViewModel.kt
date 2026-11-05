// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequestRepositoryException
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.model.team.TeamRepositoryException
import com.swent.shifter.model.user.UserRepository
import com.swent.shifter.model.user.displayNames
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Why the participants list shows an error, above the list when one was loaded. The screen turns
 * each into a translated message.
 */
enum class ParticipantsError {
  OFFLINE,
  PERMISSION_DENIED,
  UNEXPECTED,
  /** Moving a volunteer to another team failed. */
  MOVE_FAILED,
}

/**
 * A change of a team's manager that failed, until the screen has reported it.
 *
 * @property managerId the manager the change tried to set, or null when it tried to remove one.
 * @property canRetry whether trying again can help: not when the change no longer applies, as the
 *   volunteer left or the team was deleted, nor when the organizer may not make it.
 */
data class ManagerChangeFailure(val teamId: String, val managerId: String?, val canRetry: Boolean)

/**
 * A volunteer whose request to join the event was accepted.
 *
 * @property name their display name, or null when their profile is missing or cannot be read, so
 *   the screen shows a placeholder instead.
 * @property isManager whether they manage the team of their [ParticipantGroup].
 */
data class ParticipantItem(val userId: String, val name: String?, val isManager: Boolean)

/**
 * The participants of one team, its manager first, or those of no team when [team] is null.
 *
 * A participant is in no team when their team was deleted after they were accepted.
 */
data class ParticipantGroup(val team: Team?, val participants: List<ParticipantItem>)

/**
 * State of the organizer's Participants tab.
 *
 * @property groups one group per team of the event, in the order the repository returns them and
 *   including the teams nobody has joined yet, then the participants of no team when there are any.
 * @property isLoading whether there is no list to show yet because it is being read, which shows
 *   the spinner. It stays false during a reload: the list stays on screen until the answer replaces
 *   it.
 * @property isUpdating whether a team's manager is being changed, or a volunteer moved.
 * @property managerChangeFailure the last change of manager that failed, until it has been shown.
 *   Loading the list does not clear it, so the reload that follows the failure cannot hide it.
 */
data class ParticipantsUiState(
    val groups: List<ParticipantGroup> = emptyList(),
    val isLoading: Boolean = false,
    val error: ParticipantsError? = null,
    val isUpdating: Boolean = false,
    val managerChangeFailure: ManagerChangeFailure? = null,
)

/**
 * Loads the participants of the event [eventId], grouped by the team they belong to.
 *
 * The participants are the volunteers whose request was accepted. A volunteer belongs to at most
 * one team per event: either among its [Team.memberIds], or as its [Team.managerId], since a
 * manager is promoted from the members and leaves them. Someone appointed manager without an
 * accepted request is not a participant, so they are not listed.
 *
 * It also lets the organizer choose the manager of each team, the only one who sees this list.
 */
class ParticipantsViewModel(
    private val eventId: String,
    private val membershipRequestRepository: MembershipRequestRepository,
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ParticipantsUiState())
  val uiState: StateFlow<ParticipantsUiState> = _uiState.asStateFlow()

  /** The load running, if any: a change of manager replaces it, as it may predate the write. */
  private var loadJob: Job? = null

  /** Whether a load succeeded, so there is a list to keep on screen during the next ones. */
  private var loaded = false

  init {
    load()
  }

  /** Loads the participants again, e.g. to retry after an error. Ignored while loading. */
  fun load() {
    if (loadJob?.isActive == true) return
    _uiState.update { it.copy(isLoading = !loaded, error = null) }
    loadJob = viewModelScope.launch {
      try {
        val participantIds =
            membershipRequestRepository
                .getMembershipRequestsByEId(eventId)
                .filter { it.status == MembershipRequestStatus.ACCEPTED }
                .map { it.userId }
        val teams = teamRepository.getTeamsByEvent(eventId)
        val names = userRepository.displayNames(participantIds)
        _uiState.update { it.copy(groups = group(participantIds, teams, names)) }
        loaded = true
      } catch (e: CancellationException) {
        throw e
      } catch (e: MembershipRequestRepositoryException.Unavailable) {
        showError(ParticipantsError.OFFLINE)
      } catch (e: MembershipRequestRepositoryException.PermissionDenied) {
        showError(ParticipantsError.PERMISSION_DENIED)
      } catch (e: Exception) {
        showError(ParticipantsError.UNEXPECTED)
      } finally {
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  /**
   * Makes [userId], a member of the team [teamId], its manager. The previous manager, if any, goes
   * back to the members. Ignored while another change runs.
   */
  fun appointManager(teamId: String, userId: String) = changeManager(teamId, userId)

  /**
   * Leaves the team [teamId] without a manager, who goes back to its members. Ignored while another
   * change runs.
   */
  fun removeManager(teamId: String) = changeManager(teamId, null)

  /**
   * Moves [userId], a member of a team or of none, into the team [teamId]. They leave their team
   * and the missions of it they were assigned to. Ignored while another change runs.
   */
  fun moveToTeam(userId: String, teamId: String) =
      change(
          write = { membershipRequestRepository.moveToTeam(eventId, userId, teamId) },
          onFailure = { _uiState.update { it.copy(error = ParticipantsError.MOVE_FAILED) } },
      )

  /**
   * Writes [managerId] as the manager of the team [teamId], waiting at most
   * [MANAGER_CHANGE_TIMEOUT_MILLIS] for the backend. A failure is reported through
   * [ParticipantsUiState.managerChangeFailure], and the list is loaded again too, so a volunteer
   * who left or a deleted team disappears from it.
   */
  private fun changeManager(teamId: String, managerId: String?) {
    if (_uiState.value.isUpdating) return
    // A failure equal to one still shown must be reported again.
    _uiState.update { it.copy(managerChangeFailure = null) }
    change(
        write = {
          withTimeout(MANAGER_CHANGE_TIMEOUT_MILLIS) {
            teamRepository.setManager(eventId, teamId, managerId)
          }
        },
        onFailure = { e ->
          val failure = ManagerChangeFailure(teamId, managerId, canRetry = canRetryAfter(e))
          _uiState.update { it.copy(managerChangeFailure = failure) }
        },
        reloadOnFailure = true,
    )
  }

  /** Marks the failed change of manager as shown, so it is not reported again. */
  fun onManagerChangeFailureShown() {
    _uiState.update { it.copy(managerChangeFailure = null) }
  }

  /**
   * Runs [write], then loads the list again to show it, or reports the failure with [onFailure],
   * loading the list again too when [reloadOnFailure]. A load still running may have read the teams
   * before the write, so it is cancelled first rather than left to show them.
   */
  private fun change(
      write: suspend () -> Unit,
      onFailure: (Exception) -> Unit,
      reloadOnFailure: Boolean = false,
  ) {
    if (_uiState.value.isUpdating) return
    _uiState.update { it.copy(isUpdating = true, error = null) }
    viewModelScope.launch {
      val failure =
          try {
            write()
            null
          } catch (e: TimeoutCancellationException) {
            // Before CancellationException, which it extends: this is not the screen leaving.
            e
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            e
          }
      if (failure != null && !reloadOnFailure) {
        _uiState.update { it.copy(isUpdating = false) }
        onFailure(failure)
        return@launch
      }
      loadJob?.cancelAndJoin()
      _uiState.update { it.copy(isUpdating = false) }
      failure?.let(onFailure)
      load()
    }
  }

  private fun showError(error: ParticipantsError) = _uiState.update { it.copy(error = error) }

  companion object {
    /**
     * How long a change of manager waits for the backend before reporting a failure. Longer than
     * for a plain write, since a transaction retries by itself before giving up.
     */
    const val MANAGER_CHANGE_TIMEOUT_MILLIS = 15_000L

    /** Whether trying the change again can help after [e], per [TeamRepository.setManager]. */
    private fun canRetryAfter(e: Exception): Boolean =
        when (e) {
          is IllegalArgumentException,
          is TeamRepositoryException.NotFound,
          is TeamRepositoryException.PermissionDenied -> false
          else -> true
        }

    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(
        eventId: String,
        membershipRequestRepository: MembershipRequestRepository,
        teamRepository: TeamRepository,
        userRepository: UserRepository,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        ParticipantsViewModel(eventId, membershipRequestRepository, teamRepository, userRepository)
      }
    }

    /**
     * Puts each participant in the first team that has them as its manager or among its members, or
     * in a last group of no team.
     */
    private fun group(
        participantIds: List<String>,
        teams: List<Team>,
        names: Map<String, String?>,
    ): List<ParticipantGroup> {
      val teamOf = participantIds.associateWith { id ->
        teams.firstOrNull { it.managerId == id || id in it.memberIds }
      }
      fun itemsOf(team: Team?) =
          participantIds
              .filter { teamOf[it] == team }
              .map { ParticipantItem(it, names[it], isManager = team?.managerId == it) }
              .sortedByDescending { it.isManager }
      val teamGroups = teams.map { ParticipantGroup(it, itemsOf(it)) }
      val withoutTeam = itemsOf(null)
      return if (withoutTeam.isEmpty()) teamGroups
      else teamGroups + ParticipantGroup(null, withoutTeam)
    }
  }
}
