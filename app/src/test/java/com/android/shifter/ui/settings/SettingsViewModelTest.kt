// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.settings

import androidx.credentials.Credential
import com.swent.shifter.model.authentication.AuthRepository
import com.swent.shifter.model.authentication.AuthUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsViewModelTest {

  private val repository = FakeAuthRepository()
  private val viewModel = SettingsViewModel(repository)

  @Test
  fun signOut_clearsTheSessionThenReportsIt() {
    var signedOut = 0

    viewModel.signOut { signedOut++ }

    assertEquals(1, repository.signOutCalls)
    assertEquals(1, signedOut)
    assertNull(viewModel.uiState.value.errorMsg)
  }

  @Test
  fun signOut_onFailure_staysAndShowsTheError() {
    repository.signOutResult = Result.failure(IllegalStateException("Network down"))
    var signedOut = 0

    viewModel.signOut { signedOut++ }

    assertEquals(0, signedOut)
    assertEquals("Network down", viewModel.uiState.value.errorMsg)
  }

  @Test
  fun clearErrorMsg_removesTheShownError() {
    repository.signOutResult = Result.failure(IllegalStateException("Network down"))
    viewModel.signOut {}

    viewModel.clearErrorMsg()

    assertNull(viewModel.uiState.value.errorMsg)
  }

  private class FakeAuthRepository : AuthRepository {
    var signOutResult: Result<Unit> = Result.success(Unit)
    var signOutCalls = 0

    override suspend fun signInWithGoogle(credential: Credential): Result<AuthUser> =
        Result.failure(UnsupportedOperationException())

    override fun signOut(): Result<Unit> {
      signOutCalls++
      return signOutResult
    }
  }
}
