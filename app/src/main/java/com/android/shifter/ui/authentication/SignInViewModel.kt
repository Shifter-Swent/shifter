package com.swent.shifter.ui.authentication

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.firebase.auth.FirebaseUser
import com.swent.shifter.R
import com.swent.shifter.model.authentication.AuthRepository
import com.swent.shifter.model.authentication.AuthRepositoryFirebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Based on Bootcamp authentication material.

data class AuthUIState(
    val isLoading: Boolean = false,
    val user: FirebaseUser? = null,
    val errorMsg: String? = null,
    val signedOut: Boolean = false,
)

class SignInViewModel(private val repository: AuthRepository = AuthRepositoryFirebase()) :
    ViewModel() {
  private val _uiState = MutableStateFlow(AuthUIState())
  val uiState: StateFlow<AuthUIState> = _uiState.asStateFlow()

  /** Clears the current authentication error. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsg = null) }
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

        repository.signInWithGoogle(credential).fold(
            onSuccess = { user ->
              _uiState.value =
                  AuthUIState(isLoading = false, user = user, signedOut = false)
            },
            onFailure = { error ->
              showError(error.localizedMessage ?: "Sign-in failed")
            },
        )
      } catch (e: GetCredentialCancellationException) {
        showError("Sign-in cancelled")
      } catch (e: GetCredentialException) {
        showError("Failed to get credentials: ${e.localizedMessage ?: "Unknown error"}")
      } catch (e: Exception) {
        showError("Unexpected error: ${e.localizedMessage ?: "Unknown error"}")
      }
    }
  }

  /** Signs out the current user through the authentication repository. */
  fun signOut() {
    repository.signOut().fold(
        onSuccess = {
          _uiState.value = AuthUIState(signedOut = true)
        },
        onFailure = { error ->
          showError(error.localizedMessage ?: "Sign-out failed")
        },
    )
  }

  private fun showError(message: String) {
    _uiState.update {
      it.copy(isLoading = false, errorMsg = message, user = null, signedOut = true)
    }
  }
}
