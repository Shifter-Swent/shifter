// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Based on Bootcamp authentication material.
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

package com.swent.shifter.ui.authentication

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.swent.shifter.R
import com.swent.shifter.model.authentication.AuthRepository
import com.swent.shifter.model.authentication.AuthRepositoryProvider
import com.swent.shifter.model.authentication.AuthUser
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
    /** One-shot event: set on a successful sign-in, reset once the screen has navigated. */
    val signedIn: Boolean = false,
)

class SignInViewModel(private val repository: AuthRepository = AuthRepositoryProvider.repository) :
    ViewModel() {
  private val _uiState = MutableStateFlow(AuthUIState())
  val uiState: StateFlow<AuthUIState> = _uiState.asStateFlow()

  /** Clears the current authentication error. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsg = null) }
  }

  /** Marks the sign-in event as handled, so the screen does not navigate again. */
  fun onSignedInHandled() {
    _uiState.update { it.copy(signedIn = false) }
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
                onSuccess = { user ->
                  _uiState.value =
                      AuthUIState(
                          isLoading = false,
                          user = user,
                          signedIn = true,
                      )
                },
                onFailure = { error -> showError(error.localizedMessage ?: "Sign-in failed") },
            )
      } catch (e: GetCredentialCancellationException) {
        _uiState.update { it.copy(isLoading = false) }
      } catch (e: NoCredentialException) {
        showError("No Google credential is available for sign-in")
      } catch (e: GetCredentialException) {
        showError("Failed to get credentials: ${e.localizedMessage ?: "Unknown error"}")
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        showError("Unexpected error: ${e.localizedMessage ?: "Unknown error"}")
      }
    }
  }

  private fun showError(message: String) {
    _uiState.update { it.copy(isLoading = false, errorMsg = message) }
  }
}
