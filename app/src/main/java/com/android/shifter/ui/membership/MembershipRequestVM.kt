// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.ui.membership

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequestRepositoryException
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.user.UserRepository
import com.swent.shifter.model.user.displayNames
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A request enriched with the public profile name needed by its card. */
data class MembershipRequestItemUIState(
    val request: MembershipRequest,
    val displayName: String,
)

/**
 * State of the organizer's requests list.
 *
 * @property isLoading whether there is no list to show yet because it is being read, which shows
 *   the spinner. It stays false during a reload: the list stays on screen until the answer replaces
 *   it.
 * @property hasEnded whether the event has ended: a request can then only be rejected (#409).
 */
data class MembershipRequestUIState(
    val requests: List<MembershipRequestItemUIState> = emptyList(),
    val isLoading: Boolean = true,
    val processingRequestIds: Set<String> = emptySet(),
    val errorMsg: String? = null,
    val hasEnded: Boolean = false,
)

class MembershipRequestVM(
    private val eventId: String,
    private val membershipRequestRepository: MembershipRequestRepository,
    private val userRepository: UserRepository,
    private val eventRepository: EventRepository,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {
  private val _uiState = MutableStateFlow(MembershipRequestUIState(isLoading = false))
  val uiState: StateFlow<MembershipRequestUIState> = _uiState.asStateFlow()

  /** The load running, if any: a decision cancels it, as it may predate the write. */
  private var loadJob: Job? = null

  /** Whether a load succeeded, so there is a list to keep on screen during the next ones. */
  private var loaded = false

  /** A load was cancelled or refused by a decision: it runs once no decision is being written. */
  private var reloadPending = false

  init {
    loadRequests()
  }

  fun loadRequests() {
    if (loadJob?.isActive == true) return
    // A read started now could answer before the decision is written and bring the old status back.
    if (_uiState.value.processingRequestIds.isNotEmpty()) {
      reloadPending = true
      return
    }
    reloadPending = false
    _uiState.update { it.copy(isLoading = !loaded, errorMsg = null) }
    loadJob = viewModelScope.launch {
      try {
        val requests = membershipRequestRepository.getMembershipRequestsByEId(eventId)
        val names = userRepository.displayNames(requests.map { it.userId })
        val items = requests.map {
          MembershipRequestItemUIState(it, names[it.userId] ?: "Unknown volunteer")
        }
        val profileError =
            if (requests.any { it.userId !in names })
                "Some applicant names could not be loaded. Please try again."
            else null
        _uiState.update {
          it.copy(requests = items, errorMsg = profileError, hasEnded = hasEnded())
        }
        loaded = true
      } catch (e: MembershipRequestRepositoryException) {
        _uiState.update { it.copy(errorMsg = e.toMessage()) }
      } finally {
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  /**
   * Whether the event has ended, read with the requests. An event that cannot be read is taken as
   * running: the rules still refuse an acceptance after its end.
   */
  private suspend fun hasEnded(): Boolean {
    val event =
        try {
          eventRepository.getEvent(eventId)
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          null
        }
    return event != null && !now().isBefore(event.endAt)
  }

  /** Accepts the request of [userId]. Ignored once the event has ended, which only rejects. */
  fun accept(userId: String) {
    if (_uiState.value.hasEnded) return
    decide(userId, MembershipRequestStatus.ACCEPTED)
  }

  fun reject(userId: String) = decide(userId, MembershipRequestStatus.REJECTED)

  fun clearError() {
    _uiState.update { it.copy(errorMsg = null) }
  }

  private fun decide(userId: String, status: MembershipRequestStatus) {
    val state = _uiState.value
    val request = state.requests.firstOrNull { it.request.userId == userId }?.request ?: return
    if (request.id in state.processingRequestIds) return
    // It read the requests before this decision, so its answer would bring the old status back.
    if (loadJob?.isActive == true) {
      loadJob?.cancel()
      reloadPending = true
    }
    _uiState.update {
      it.copy(processingRequestIds = it.processingRequestIds + request.id, errorMsg = null)
    }
    viewModelScope.launch {
      try {
        if (status == MembershipRequestStatus.ACCEPTED) {
          membershipRequestRepository.accept(eventId, userId)
        } else {
          membershipRequestRepository.reject(eventId, userId)
        }
        _uiState.update { state ->
          state.copy(
              requests =
                  state.requests.map {
                    if (it.request.id == request.id) {
                      it.copy(request = it.request.copy(status = status))
                    } else it
                  }
          )
        }
      } catch (e: MembershipRequestRepositoryException) {
        _uiState.update { it.copy(errorMsg = e.toMessage()) }
      } finally {
        _uiState.update { it.copy(processingRequestIds = it.processingRequestIds - request.id) }
        // Only the last decision to finish gets past the check in loadRequests. After a failed
        // one, the reload waits for the next call, so that it does not clear the error.
        if (reloadPending && _uiState.value.errorMsg == null) loadRequests()
      }
    }
  }

  private fun MembershipRequestRepositoryException.toMessage(): String =
      when (this) {
        is MembershipRequestRepositoryException.PermissionDenied ->
            "You do not have permission to access these requests."
        is MembershipRequestRepositoryException.Unavailable ->
            "The membership service is unavailable. Please try again."
        is MembershipRequestRepositoryException.Unknown -> "Something went wrong. Please try again."
      }

  companion object {
    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(
        eventId: String,
        membershipRequestRepository: MembershipRequestRepository,
        userRepository: UserRepository,
        eventRepository: EventRepository,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        MembershipRequestVM(eventId, membershipRequestRepository, userRepository, eventRepository)
      }
    }
  }
}
