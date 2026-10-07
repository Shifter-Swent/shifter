// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequestRepositoryException
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the join screen shows: the code being typed, the event it found, and whether the volunteer
 * applied.
 */
data class JoinEventUiState(
    val joinCode: String = "",
    val event: Event? = null,
    val isLoading: Boolean = false,
    val applied: Boolean = false,
    val errorMsg: String? = null,
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
    private val currentUserId: () -> String?,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

  private val _uiState = MutableStateFlow(JoinEventUiState())
  val uiState: StateFlow<JoinEventUiState> = _uiState.asStateFlow()

  fun onJoinCodeChange(joinCode: String) = _uiState.update {
    it.copy(joinCode = joinCode, errorMsg = null)
  }

  /** Looks up the event volunteers join with the code typed so far. */
  fun findEvent() {
    val joinCode = _uiState.value.joinCode.trim()
    if (joinCode.isEmpty() || _uiState.value.isLoading) return
    launchLoading {
      val event = eventRepository.getEventByJoinCode(joinCode)
      _uiState.update {
        it.copy(event = event, errorMsg = if (event == null) "No event uses this code" else null)
      }
    }
  }

  /**
   * Applies to the loaded event. Until the volunteer can enter their hours, the request declares
   * the whole event as their availability.
   */
  fun apply() {
    val state = _uiState.value
    val event = state.event ?: return
    if (state.isLoading || state.applied) return
    val userId = currentUserId() ?: return showError("You must be signed in to apply")
    launchLoading {
      val request =
          MembershipRequest(
              userId = userId,
              availability = listOf(AvailabilitySlot(event.startAt, event.endAt)),
              createdAt = now(),
          )
      membershipRequestRepository.apply(event.id, request)
      _uiState.update { it.copy(applied = true) }
    }
  }

  /** Runs [block] with the loading flag set, turning failures into an error message. */
  private fun launchLoading(block: suspend () -> Unit) {
    _uiState.update { it.copy(isLoading = true, errorMsg = null) }
    viewModelScope.launch {
      try {
        block()
      } catch (e: CancellationException) {
        throw e
      } catch (e: MembershipRequestRepositoryException.Unavailable) {
        showError("You are offline, try again later")
      } catch (e: MembershipRequestRepositoryException.PermissionDenied) {
        showError("You cannot apply to this event")
      } catch (e: Exception) {
        showError("Something went wrong, try again")
      } finally {
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  private fun showError(message: String) = _uiState.update { it.copy(errorMsg = message) }
}
