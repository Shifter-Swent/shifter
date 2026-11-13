// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Based on Bootcamp authentication material.
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

package com.swent.shifter.ui.authentication

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.swent.shifter.R
import com.swent.shifter.model.authentication.AuthRepository
import com.swent.shifter.model.authentication.AuthUser
import com.swent.shifter.model.user.User
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.model.user.UserRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUIState(
    val isLoading: Boolean = false,
    val user: AuthUser? = null,
    val errorMsg: String? = null,
    /** One-shot event: the mode to enter after a sign-in, reset once the screen has navigated. */
    val signedInAs: UserMode? = null,
    /**
     * One-shot event: the user signed in has no profile yet, so they must choose their mode before
     * it is created. Reset once the screen has navigated.
     */
    val needsMode: Boolean = false,
) {
  /** Whether the sign-in event is waiting to be handled. */
  val signedIn: Boolean
    get() = signedInAs != null || needsMode
}

/**
 * Signs the user in with Google, then reads their Firestore profile: an existing account enters the
 * mode saved on it, and a new one is sent to choose its mode, which creates the profile.
 */
class SignInViewModel(
    private val repository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(AuthUIState())
  val uiState: StateFlow<AuthUIState> = _uiState.asStateFlow()

  /** Clears the current authentication error. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsg = null) }
  }

  /** Marks the sign-in event as handled, so the screen does not navigate again. */
  fun onSignedInHandled() {
    _uiState.update { it.copy(signedInAs = null, needsMode = false) }
  }

  /** Starts Google sign-in and updates the state with the result. */
  fun signIn(context: Context, credentialManager: CredentialManager) {
    if (_uiState.value.isLoading) return

    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMsg = null) }

      try {
        val option =
            GetSignInWithGoogleOption.Builder(
                    serverClientId = context.getString(R.string.default_web_client_id)
                )
                .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = credentialManager.getCredential(context, request).credential

        repository
            .signInWithGoogle(credential)
            .fold(
                onSuccess = { user -> loadProfile(user) },
                onFailure = { error -> showError(error.localizedMessage ?: "Sign-in failed") },
            )
      } catch (e: GetCredentialCancellationException) {
        _uiState.update { it.copy(isLoading = false) }
      } catch (e: NoCredentialException) {
        showError("No Google credential is available for sign-in")
      } catch (e: GetCredentialException) {
        showError("Failed to get credentials: ${e.localizedMessage ?: UNKNOWN_ERROR}")
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        showError("Unexpected error: ${e.localizedMessage ?: UNKNOWN_ERROR}")
      }
    }
  }

  /**
   * Reads the profile of [user]. A new account has none: nothing is created, as its mode must be
   * chosen first. An existing one goes through [UserRepository.getOrCreateUser], which also creates
   * its public profile when it predates them, and enters its mode, Volunteer when it has none.
   *
   * Without a readable profile the app cannot work, so a failure signs the user out again:
   * otherwise the next launch would skip this screen.
   */
  private suspend fun loadProfile(user: AuthUser) {
    val profile: User?
    try {
      profile =
          userRepository.getUser(user.uid)?.let {
            userRepository.getOrCreateUser(
                user.uid,
                user.displayName.orEmpty(),
                user.email.orEmpty(),
            )
          }
    } catch (e: CancellationException) {
      signOutWithoutProfile()
      _uiState.update { it.copy(isLoading = false) }
      throw e
    } catch (e: Exception) {
      val reason = e.localizedMessage ?: UNKNOWN_ERROR
      if (signOutWithoutProfile()) {
        showError("Could not load your profile: $reason")
      } else {
        showError("Could not load your profile ($reason) and could not sign you out")
      }
      return
    }
    _uiState.value =
        if (profile == null) AuthUIState(user = user, needsMode = true)
        else AuthUIState(user = user, signedInAs = profile.mode ?: UserMode.VOLUNTEER)
  }

  /** Closes the session left without a profile. Returns `false`, after logging, if it could not. */
  private fun signOutWithoutProfile(): Boolean =
      repository
          .signOut()
          .onFailure { Log.e(TAG, "Could not sign out after a profile failure", it) }
          .isSuccess

  private fun showError(message: String) {
    _uiState.update { it.copy(isLoading = false, errorMsg = message) }
  }

  companion object {
    private const val TAG = "SignInViewModel"

    /** Shown when an exception carries no message. */
    private const val UNKNOWN_ERROR = "Unknown error"

    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(
        repository: AuthRepository,
        userRepository: UserRepository,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { SignInViewModel(repository, userRepository) }
    }
  }
}
