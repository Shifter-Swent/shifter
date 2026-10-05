package com.android.shifter.authentication

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class SignInViewModel(initialState: AuthUiState = AuthUiState()) : ViewModel() {
  private val _uiState = MutableStateFlow(initialState)
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

  fun updateUiState(state: AuthUiState) {
    _uiState.value = state
  }
}
