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
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.model.user.UserRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class SettingsUiState(
    /** The mode the user is in, which the change of mode leaves. */
    val mode: UserMode = UserMode.VOLUNTEER,
    val errorMsg: String? = null,
    /** One-shot event: set once signed out, reset once the screen has navigated. */
    val signedOut: Boolean = false,
    /** Waiting for the server to confirm a change of mode. */
    val changingMode: Boolean = false,
    /** One-shot event: the mode changed to, set once confirmed, reset once the screen has left. */
    val modeChanged: UserMode? = null,
    /** The last change of mode failed or timed out, until the screen has shown it. */
    val modeChangeFailed: Boolean = false,
)

/** Signs the user out, or changes the mode saved on their profile from [mode] to the other one. */
class SettingsViewModel(
    private val repository: AuthRepository,
    private val userRepository: UserRepository,
    mode: UserMode,
) : ViewModel() {
  private val _uiState = MutableStateFlow(SettingsUiState(mode = mode))
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  /**
   * Saves the other mode on the profile, then raises [SettingsUiState.modeChanged]. The screen only
   * changes once the server has confirmed: offline, the write would only answer on reconnection, so
   * it stops waiting after [MODE_CHANGE_TIMEOUT_MILLIS] and reports a failure, the mode unchanged.
   */
  fun changeMode() {
    val state = _uiState.value
    val uid = repository.currentUser()?.uid
    if (state.changingMode || uid == null) return
    val target = if (state.mode == UserMode.VOLUNTEER) UserMode.ORGANIZER else UserMode.VOLUNTEER

    viewModelScope.launch {
      _uiState.update { it.copy(changingMode = true, modeChangeFailed = false) }
      try {
        withTimeout(MODE_CHANGE_TIMEOUT_MILLIS) { userRepository.setMode(uid, target) }
      } catch (e: TimeoutCancellationException) {
        // The write stays queued and would reach the server on reconnection: queue the current
        // mode after it, so the profile ends where the user still is.
        viewModelScope.launch { restoreMode(uid, state.mode) }
        failModeChange(e)
        return@launch
      } catch (e: CancellationException) {
        _uiState.update { it.copy(changingMode = false) }
        throw e
      } catch (e: Exception) {
        failModeChange(e)
        return@launch
      }
      _uiState.update { it.copy(mode = target, changingMode = false, modeChanged = target) }
    }
  }

  /** Marks the change of mode as handled, so the screen does not navigate again. */
  fun onModeChangeHandled() {
    _uiState.update { it.copy(modeChanged = null) }
  }

  /** Marks the failed change of mode as shown. */
  fun onModeChangeErrorShown() {
    _uiState.update { it.copy(modeChangeFailed = false) }
  }

  private suspend fun restoreMode(uid: String, mode: UserMode) {
    try {
      userRepository.setMode(uid, mode)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Could not restore the mode after a timeout", e)
    }
  }

  private fun failModeChange(e: Exception) {
    Log.w(TAG, "Could not change the mode", e)
    _uiState.update { it.copy(changingMode = false, modeChangeFailed = true) }
  }

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

    /** How long a change of mode may wait for the server before it is reported as failed. */
    const val MODE_CHANGE_TIMEOUT_MILLIS = 10_000L

    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(
        repository: AuthRepository,
        userRepository: UserRepository,
        mode: UserMode,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { SettingsViewModel(repository, userRepository, mode) }
    }
  }
}
