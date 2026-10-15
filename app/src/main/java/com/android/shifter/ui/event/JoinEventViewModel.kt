// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequestRepositoryException
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepository
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the join screen shows an error. The screen turns each into a translated message. */
enum class JoinEventError {
  UNKNOWN_CODE,
  NOT_SIGNED_IN,
  OFFLINE,
  PERMISSION_DENIED,
  UNEXPECTED,
}

/**
 * What the join screen shows: the code being typed, the event it found, its teams and the one
 * picked, and where the volunteer's request stands.
 *
 * @property teams the teams of [event] the volunteer can apply to, by name. Empty while no event is
 *   shown, or when the event has no team yet, in which case nobody can apply.
 * @property selectedTeamId the team of [event] the volunteer applies to, null until they pick one.
 *   Once a request is sent, the team it was sent to.
 * @property requestStatus the status of the volunteer's request for the event, null while they have
 *   none. Finding an event they already applied to shows that request, possibly decided.
 */
data class JoinEventUiState(
    val joinCode: String = "",
    val event: Event? = null,
    val teams: List<Team> = emptyList(),
    val selectedTeamId: String? = null,
    val isLoading: Boolean = false,
    val requestStatus: MembershipRequestStatus? = null,
    val error: JoinEventError? = null,
)

/**
 * Finds an event by its join code, then sends the signed-in volunteer's request to join it.
 *
 * @param currentUserId returns the signed-in user's id, or null when nobody is signed in.
 * @param now the clock stamped on the request, replaceable in tests.
 */
class JoinEventViewModel(
    private val eventRepository: EventRepository,
    private val membershipRequestRepository: MembershipRequestRepository,
    private val teamRepository: TeamRepository,
    private val currentUserId: () -> String?,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

  private val _uiState = MutableStateFlow(JoinEventUiState())
  val uiState: StateFlow<JoinEventUiState> = _uiState.asStateFlow()

  fun onJoinCodeChange(joinCode: String) = _uiState.update {
    it.copy(joinCode = joinCode, error = null)
  }

  /**
   * Looks up the event volunteers join with the code typed so far and its teams, with the signed-in
   * volunteer's request for it if they already applied, so they are not offered to apply twice and
   * see the team they applied to. Join codes are upper-case, so the code is matched whatever case
   * the volunteer typed it in.
   */
  fun findEvent() {
    val joinCode = _uiState.value.joinCode.trim().uppercase()
    if (joinCode.isEmpty() || _uiState.value.isLoading) return
    launchLoading {
      val event = eventRepository.getEventByJoinCode(joinCode)
      val userId = currentUserId()
      val request =
          if (event == null || userId == null) null
          else membershipRequestRepository.getMembershipRequest(event.id, userId)
      val teams = event?.let { teamRepository.getTeamsByEvent(it.id).sortedBy(Team::name) }
      _uiState.update {
        it.copy(
            event = event,
            teams = teams.orEmpty(),
            selectedTeamId = request?.teamId,
            requestStatus = request?.status,
            error = if (event == null) JoinEventError.UNKNOWN_CODE else null,
        )
      }
    }
  }

  /**
   * Leaves the event details and goes back to typing a code, keeping the code typed so far. Ignored
   * while a request is being sent, so its result cannot land on the code step.
   */
  fun changeCode() {
    if (_uiState.value.isLoading) return
    _uiState.update {
      it.copy(
          event = null,
          teams = emptyList(),
          selectedTeamId = null,
          requestStatus = null,
          error = null,
      )
    }
  }

  /**
   * Picks the team of the loaded event to apply to, replacing any previous pick. A volunteer
   * belongs to at most one team per event, so there is a single choice. Ignored once the request is
   * sent, or while it is being sent, so the team shown always matches the request, and for a team
   * the event does not have.
   */
  fun onTeamSelected(teamId: String) {
    val state = _uiState.value
    if (state.isLoading || state.requestStatus != null) return
    if (state.teams.none { it.id == teamId }) return
    _uiState.update { it.copy(selectedTeamId = teamId) }
  }

  /**
   * Applies to the selected team of the loaded event; does nothing until a team is selected. Until
   * the volunteer can enter their hours, the request declares the whole event as their
   * availability.
   */
  fun applyToEvent() {
    val state = _uiState.value
    val event = state.event ?: return
    val teamId = state.selectedTeamId ?: return
    if (state.isLoading || state.requestStatus != null) return
    val userId = currentUserId() ?: return showError(JoinEventError.NOT_SIGNED_IN)
    launchLoading {
      val request =
          MembershipRequest(
              userId = userId,
              teamId = teamId,
              availability = listOf(AvailabilitySlot(event.startAt, event.endAt)),
              createdAt = now(),
          )
      val sent = membershipRequestRepository.apply(event.id, request)
      _uiState.update { it.copy(requestStatus = sent.status) }
    }
  }

  /** Runs [block] with the loading flag set, turning failures into an error. */
  private fun launchLoading(block: suspend () -> Unit) {
    _uiState.update { it.copy(isLoading = true, error = null) }
    viewModelScope.launch {
      try {
        block()
      } catch (e: CancellationException) {
        throw e
      } catch (e: EventRepositoryException.Unavailable) {
        showError(JoinEventError.OFFLINE)
      } catch (e: MembershipRequestRepositoryException.Unavailable) {
        showError(JoinEventError.OFFLINE)
      } catch (e: MembershipRequestRepositoryException.PermissionDenied) {
        showError(JoinEventError.PERMISSION_DENIED)
      } catch (e: Exception) {
        showError(JoinEventError.UNEXPECTED)
      } finally {
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  private fun showError(error: JoinEventError) = _uiState.update { it.copy(error = error) }

  companion object {
    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(
        eventRepository: EventRepository,
        membershipRequestRepository: MembershipRequestRepository,
        teamRepository: TeamRepository,
        currentUserId: () -> String?,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        JoinEventViewModel(
            eventRepository,
            membershipRequestRepository,
            teamRepository,
            currentUserId,
        )
      }
    }
  }
}
