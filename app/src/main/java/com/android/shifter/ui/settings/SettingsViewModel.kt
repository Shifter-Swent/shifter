// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.settings

import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.authentication.AuthRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val errorMsg: String? = null,
    /** One-shot event: set once signed out, reset once the screen has navigated. */
    val signedOut: Boolean = false,
)

class SettingsViewModel(private val repository: AuthRepository) : ViewModel() {
  private val _uiState = MutableStateFlow(SettingsUiState())
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  /**
   * Closes the Firebase session, then clears the Credential Manager state so the next sign-in
   * offers the account picker again. If Firebase fails the user stays signed in and nothing is
   * cleared; a failure to clear the credentials does not keep a signed-out user on this screen.
   */
  fun signOut(credentialManager: CredentialManager) {
    viewModelScope.launch {
      repository
          .signOut()
          .onFailure { error ->
            _uiState.update { it.copy(errorMsg = error.localizedMessage ?: "Sign-out failed") }
          }
          .onSuccess {
            try {
              // The auth-state listener leaves this screen as soon as Firebase signs out, which
              // cancels viewModelScope: the cleanup must still finish.
              withContext(NonCancellable) {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
              }
            } catch (e: ClearCredentialException) {
              Log.w(TAG, "Could not clear the credential state", e)
            }
            _uiState.update { it.copy(signedOut = true) }
          }
    }
  }

  /** Marks the sign-out event as handled, so the screen does not navigate again. */
  fun onSignedOutHandled() {
    _uiState.update { it.copy(signedOut = false) }
  }

  /** Clears the sign-out error once it has been shown. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsg = null) }
  }

  companion object {
    private const val TAG = "SettingsViewModel"

    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(repository: AuthRepository): ViewModelProvider.Factory = viewModelFactory {
      initializer { SettingsViewModel(repository) }
    }
  }
}
