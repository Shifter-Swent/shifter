// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.authentication

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.authentication.AuthRepository
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.model.user.UserRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The state of the mode choice.
 *
 * @property saving whether the profile is being created with the mode chosen; it stays true once
 *   created, until the screen has left, so the choices are not offered again.
 * @property entered one-shot event: the mode to enter once the profile exists, reset once the
 *   screen has navigated.
 * @property errorMsg why the profile could not be created; the user is signed out once it is shown.
 */
data class ChooseModeUiState(
    val saving: Boolean = false,
    val entered: UserMode? = null,
    val errorMsg: String? = null,
)

/**
 * Asks a signed-in user without a profile whether they are a Volunteer or an Organizer, once, then
 * creates their profile with it. The mode is changed later in Settings.
 */
class ChooseModeViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ChooseModeUiState())
  val uiState: StateFlow<ChooseModeUiState> = _uiState.asStateFlow()

  /**
   * Creates the signed-in user's profile with [mode], then enters it. A profile created meanwhile,
   * from another device, keeps its own mode, which is the one entered.
   */
  fun choose(mode: UserMode) {
    val state = _uiState.value
    if (state.saving || state.entered != null) return
    val user = authRepository.currentUser() ?: return
    _uiState.update { it.copy(saving = true, errorMsg = null) }
    viewModelScope.launch {
      try {
        val profile =
            userRepository.getOrCreateUser(
                user.uid,
                user.displayName.orEmpty(),
                user.email.orEmpty(),
                mode,
            )
        // Still saving until the screen has left, so the choices do not show again meanwhile.
        _uiState.update { it.copy(entered = profile.mode ?: mode) }
      } catch (e: CancellationException) {
        _uiState.update { it.copy(saving = false) }
        throw e
      } catch (e: Exception) {
        val reason = e.localizedMessage ?: "Unknown error"
        _uiState.value = ChooseModeUiState(errorMsg = "Could not create your profile: $reason")
      }
    }
  }

  /** Marks the mode entered as handled, so the screen does not navigate again. */
  fun onEnteredHandled() {
    _uiState.update { it.copy(entered = null) }
  }

  /**
   * Signs the user out once the error is shown: without a profile the app cannot work. If even that
   * fails, they stay here and may choose again.
   */
  fun onErrorShown() {
    _uiState.update { it.copy(errorMsg = null) }
    authRepository.signOut().onFailure { Log.e(TAG, "Could not sign out without a profile", it) }
  }

  companion object {
    private const val TAG = "ChooseModeViewModel"

    fun factory(
        authRepository: AuthRepository,
        userRepository: UserRepository,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { ChooseModeViewModel(authRepository, userRepository) }
    }
  }
}
