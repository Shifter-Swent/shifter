// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.settings

import android.os.Looper
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialUnknownException
import com.swent.shifter.model.authentication.FakeAuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

  private val repository = FakeAuthRepository()
  private val credentialManager = mockk<CredentialManager>()
  private val viewModel = SettingsViewModel(repository)

  init {
    coEvery { credentialManager.clearCredentialState(any<ClearCredentialStateRequest>()) } just runs
  }

  @Test
  fun signOut_clearsTheSessionAndTheCredentialsThenReportsIt() {
    viewModel.signOut(credentialManager)
    idle()

    assertEquals(1, repository.signOutCalls)
    coVerify(exactly = 1) {
      credentialManager.clearCredentialState(any<ClearCredentialStateRequest>())
    }
    assertTrue(viewModel.uiState.value.signedOut)
    assertNull(viewModel.uiState.value.errorMsg)
  }

  @Test
  fun signOut_onFirebaseFailure_staysSignedInAndKeepsTheCredentials() {
    repository.signOutResult = Result.failure(IllegalStateException("Network down"))

    viewModel.signOut(credentialManager)
    idle()

    coVerify(exactly = 0) {
      credentialManager.clearCredentialState(any<ClearCredentialStateRequest>())
    }
    assertFalse(viewModel.uiState.value.signedOut)
    assertEquals("Network down", viewModel.uiState.value.errorMsg)
  }

  @Test
  fun signOut_whenClearingTheCredentialsFails_stillReportsTheSignOut() {
    coEvery { credentialManager.clearCredentialState(any<ClearCredentialStateRequest>()) } throws
        ClearCredentialUnknownException("Provider unavailable")

    viewModel.signOut(credentialManager)
    idle()

    assertTrue(viewModel.uiState.value.signedOut)
    assertNull(viewModel.uiState.value.errorMsg)
  }

  @Test
  fun onSignedOutHandled_resetsTheEvent() {
    viewModel.signOut(credentialManager)
    idle()

    viewModel.onSignedOutHandled()

    assertFalse(viewModel.uiState.value.signedOut)
  }

  @Test
  fun clearErrorMsg_removesTheShownError() {
    repository.signOutResult = Result.failure(IllegalStateException("Network down"))
    viewModel.signOut(credentialManager)
    idle()

    viewModel.clearErrorMsg()

    assertNull(viewModel.uiState.value.errorMsg)
  }

  /** Runs the coroutines launched in viewModelScope, which use the main looper. */
  private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
