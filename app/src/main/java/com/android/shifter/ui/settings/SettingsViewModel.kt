// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.settings

import androidx.lifecycle.ViewModel
import com.swent.shifter.model.authentication.AuthRepository
import com.swent.shifter.model.authentication.AuthRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(val errorMsg: String? = null)

class SettingsViewModel(
    private val repository: AuthRepository = AuthRepositoryProvider.repository
) : ViewModel() {
  private val _uiState = MutableStateFlow(SettingsUiState())
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  /** Clears the session, then calls [onSignedOut]; on failure the user stays signed in. */
  fun signOut(onSignedOut: () -> Unit) {
    repository
        .signOut()
        .fold(
            onSuccess = { onSignedOut() },
            onFailure = { error ->
              _uiState.update { it.copy(errorMsg = error.localizedMessage ?: "Sign-out failed") }
            },
        )
  }

  /** Clears the sign-out error once it has been shown. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsg = null) }
  }
}
