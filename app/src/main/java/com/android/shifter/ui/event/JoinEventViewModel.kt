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
 * What the join screen shows: the code being typed, the event it found, and where the volunteer's
 * request stands.
 *
 * @property requestStatus the status of the volunteer's request once they applied, null before. A
 *   volunteer who had already applied gets back their existing request, possibly decided.
 */
data class JoinEventUiState(
    val joinCode: String = "",
    val event: Event? = null,
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
    private val currentUserId: () -> String?,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

  private val _uiState = MutableStateFlow(JoinEventUiState())
  val uiState: StateFlow<JoinEventUiState> = _uiState.asStateFlow()

  fun onJoinCodeChange(joinCode: String) = _uiState.update {
    it.copy(joinCode = joinCode, error = null)
  }

  /**
   * Looks up the event volunteers join with the code typed so far. Join codes are upper-case, so
   * the code is matched whatever case the volunteer typed it in.
   */
  fun findEvent() {
    val joinCode = _uiState.value.joinCode.trim().uppercase()
    if (joinCode.isEmpty() || _uiState.value.isLoading) return
    launchLoading {
      val event = eventRepository.getEventByJoinCode(joinCode)
      _uiState.update {
        it.copy(event = event, error = if (event == null) JoinEventError.UNKNOWN_CODE else null)
      }
    }
  }

  /**
   * Leaves the event details and goes back to typing a code, keeping the code typed so far. Ignored
   * while a request is being sent, so its result cannot land on the code step.
   */
  fun changeCode() {
    if (_uiState.value.isLoading) return
    _uiState.update { it.copy(event = null, requestStatus = null, error = null) }
  }

  /**
   * Applies to the loaded event. Until the volunteer can enter their hours, the request declares
   * the whole event as their availability.
   */
  fun applyToEvent() {
    val state = _uiState.value
    val event = state.event ?: return
    if (state.isLoading || state.requestStatus != null) return
    val userId = currentUserId() ?: return showError(JoinEventError.NOT_SIGNED_IN)
    launchLoading {
      val request =
          MembershipRequest(
              userId = userId,
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
        currentUserId: () -> String?,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        JoinEventViewModel(eventRepository, membershipRequestRepository, currentUserId)
      }
    }
  }
}
