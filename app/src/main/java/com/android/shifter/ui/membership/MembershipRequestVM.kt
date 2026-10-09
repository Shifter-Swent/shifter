// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.ui.membership

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequestRepositoryException
import com.swent.shifter.model.membership.MembershipRequestStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MembershipRequestUIState(
    val requests: List<MembershipRequest> = emptyList(),
    val isLoading: Boolean = true,
    val processingRequestIds: Set<String> = emptySet(),
    val errorMsg: String? = null,
)

class MembershipRequestVM(
    private val eventId: String,
    private val membershipRequestRepository: MembershipRequestRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(MembershipRequestUIState(isLoading = false))
  val uiState: StateFlow<MembershipRequestUIState> = _uiState.asStateFlow()

  init {
    loadRequests()
  }

  fun loadRequests() {
    // Avoid overlapping reads or a stale reload replacing a decision in progress.
    if (_uiState.value.isLoading || _uiState.value.processingRequestIds.isNotEmpty()) return
    _uiState.update { it.copy(isLoading = true, errorMsg = null) }
    viewModelScope.launch {
      try {
        val requests = membershipRequestRepository.getMembershipRequestsByEId(eventId)
        _uiState.update { it.copy(requests = requests) }
      } catch (e: MembershipRequestRepositoryException) {
        _uiState.update { it.copy(errorMsg = e.toMessage()) }
      } finally {
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  fun accept(userId: String) = decide(userId, MembershipRequestStatus.ACCEPTED)

  fun reject(userId: String) = decide(userId, MembershipRequestStatus.REJECTED)

  fun clearError() {
    _uiState.update { it.copy(errorMsg = null) }
  }

  private fun decide(userId: String, status: MembershipRequestStatus) {
    val state = _uiState.value
    val request = state.requests.firstOrNull { it.userId == userId } ?: return
    if (state.isLoading || request.id in state.processingRequestIds) return
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
                  state.requests.map { if (it.id == request.id) it.copy(status = status) else it }
          )
        }
      } catch (e: MembershipRequestRepositoryException) {
        _uiState.update { it.copy(errorMsg = e.toMessage()) }
      } finally {
        _uiState.update { it.copy(processingRequestIds = it.processingRequestIds - request.id) }
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
}
