// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

package com.swent.shifter.ui.authentication

import android.content.Context
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.firebase.auth.FirebaseUser
import com.swent.shifter.R
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SignInViewModelTest {

  private val context = mockk<Context>()
  private val credentialManager = mockk<CredentialManager>()
  private val repository = RecordingAuthRepository()
  private val viewModel = SignInViewModel(repository)

  // Set up the mock context to return a default web client ID for testing
  init {
    every { context.getString(R.string.default_web_client_id) } returns "web-client-id"
  }

  @Test
  fun signIn_acceptsGoogleAccountDifferentFromDeviceAccount() {
    val newGoogleAccountCredential = mockk<Credential>()
    val user = mockk<FirebaseUser>()
    repository.result = Result.success(user)
    stubCredentialManager(newGoogleAccountCredential)

    viewModel.signIn(context, credentialManager)

    // Wait for the coroutine to complete and the state to be updated
    shadowOf(android.os.Looper.getMainLooper()).idle()

    assertEquals(newGoogleAccountCredential, repository.receivedCredential)
    assertEquals(user, viewModel.uiState.value.user)
    assertTrue(viewModel.uiState.value.errorMsg == null)
  }

  @Test
  fun signIn_acceptsAnyGoogleAccountReturnedByCredentialManager() {
    val firstAccountCredential = mockk<Credential>()
    val secondAccountCredential = mockk<Credential>()
    repository.result = Result.success(mockk<FirebaseUser>())

    stubCredentialManager(firstAccountCredential)
    viewModel.signIn(context, credentialManager)
    shadowOf(android.os.Looper.getMainLooper()).idle()
    assertEquals(firstAccountCredential, repository.receivedCredential)

    stubCredentialManager(secondAccountCredential)
    viewModel.signIn(context, credentialManager)
    shadowOf(android.os.Looper.getMainLooper()).idle()
    assertEquals(secondAccountCredential, repository.receivedCredential)

    // Verify that the CredentialManager was called twice with the correct request type
    val requests = mutableListOf<GetCredentialRequest>()
    coVerify(exactly = 2) { credentialManager.getCredential(context, capture(requests)) }
    assertTrue(requests.all { it.credentialOptions.single() is GetSignInWithGoogleOption })
  }

  @Test
  fun signIn_success_raisesSignedInEventUntilHandled() {
    val user = mockk<FirebaseUser>()
    repository.result = Result.success(user)
    stubCredentialManager(mockk<Credential>())

    viewModel.signIn(context, credentialManager)
    shadowOf(android.os.Looper.getMainLooper()).idle()
    assertTrue(viewModel.uiState.value.signedIn)

    viewModel.onSignedInHandled()

    // The event is consumed, but the user stays signed in.
    assertFalse(viewModel.uiState.value.signedIn)
    assertEquals(user, viewModel.uiState.value.user)
  }

  @Test
  fun signIn_failure_doesNotRaiseSignedInEvent() {
    repository.result = Result.failure(IllegalStateException("Repository unavailable"))
    stubCredentialManager(mockk<Credential>())

    viewModel.signIn(context, credentialManager)
    shadowOf(android.os.Looper.getMainLooper()).idle()

    assertFalse(viewModel.uiState.value.signedIn)
  }

  @Test
  fun signIn_repositoryFailure_setsErrorAndClearsUser() {
    val credential = mockk<Credential>()
    repository.result = Result.failure(IllegalStateException("Repository unavailable"))
    stubCredentialManager(credential)

    viewModel.signIn(context, credentialManager)
    shadowOf(android.os.Looper.getMainLooper()).idle()

    assertEquals("Repository unavailable", viewModel.uiState.value.errorMsg)
    assertNull(viewModel.uiState.value.user)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun signIn_credentialManagerFailure_setsErrorAndClearsLoading() {
    coEvery { credentialManager.getCredential(context, any<GetCredentialRequest>()) } throws
        GetCredentialUnknownException("Credential provider unavailable")

    viewModel.signIn(context, credentialManager)
    shadowOf(android.os.Looper.getMainLooper()).idle()

    assertEquals(
        "Failed to get credentials: Credential provider unavailable",
        viewModel.uiState.value.errorMsg,
    )
    assertNull(viewModel.uiState.value.user)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun clearErrorMsg_clearsCredentialManagerError() {
    coEvery { credentialManager.getCredential(context, any<GetCredentialRequest>()) } throws
        GetCredentialUnknownException("Credential provider unavailable")

    viewModel.signIn(context, credentialManager)
    shadowOf(android.os.Looper.getMainLooper()).idle()
    assertTrue(viewModel.uiState.value.errorMsg != null)

    viewModel.clearErrorMsg()

    assertNull(viewModel.uiState.value.errorMsg)
  }

  @Test
  fun signIn_userCancellation_setsCancellationErrorAndClearsLoading() {
    coEvery { credentialManager.getCredential(context, any<GetCredentialRequest>()) } throws
        GetCredentialCancellationException()

    viewModel.signIn(context, credentialManager)
    shadowOf(android.os.Looper.getMainLooper()).idle()

    assertEquals("Sign-in cancelled", viewModel.uiState.value.errorMsg)
    assertNull(viewModel.uiState.value.user)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun signIn_noCredential_setsNoAccountErrorAndClearsLoading() {
    coEvery { credentialManager.getCredential(context, any<GetCredentialRequest>()) } throws
        NoCredentialException()

    viewModel.signIn(context, credentialManager)
    shadowOf(android.os.Looper.getMainLooper()).idle()

    assertEquals("No Google credential is available for sign-in", viewModel.uiState.value.errorMsg)
    assertNull(viewModel.uiState.value.user)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun signIn_setsLoadingWhileWaitingForCredentialThenClearsIt() {
    // Use a CompletableDeferred to simulate a long-running credential retrieval
    val credentialResponse = CompletableDeferred<GetCredentialResponse>()
    // Stub the CredentialManager to return the CompletableDeferred when getCredential is called
    coEvery { credentialManager.getCredential(context, any<GetCredentialRequest>()) } coAnswers
        {
          credentialResponse.await()
        }

    // Start the sign-in process
    viewModel.signIn(context, credentialManager)
    assertTrue(viewModel.uiState.value.isLoading)

    // Complete the credential retrieval with a mock credential
    credentialResponse.complete(mockk(relaxed = true))
    shadowOf(android.os.Looper.getMainLooper()).idle()

    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun signIn_unexpectedException_setsUnexpectedErrorAndClearsLoading() {
    coEvery { credentialManager.getCredential(context, any<GetCredentialRequest>()) } throws
        IllegalStateException("Play services missing")

    viewModel.signIn(context, credentialManager)
    shadowOf(android.os.Looper.getMainLooper()).idle()

    assertEquals("Unexpected error: Play services missing", viewModel.uiState.value.errorMsg)
    assertNull(viewModel.uiState.value.user)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun signIn_repositoryFailureWithoutMessage_usesGenericSignInError() {
    repository.result = Result.failure(IllegalStateException())
    stubCredentialManager(mockk<Credential>())

    viewModel.signIn(context, credentialManager)
    shadowOf(android.os.Looper.getMainLooper()).idle()

    assertEquals("Sign-in failed", viewModel.uiState.value.errorMsg)
  }

  @Test
  fun signIn_whileAlreadySigningIn_doesNotRequestASecondCredential() {
    val credentialResponse = CompletableDeferred<GetCredentialResponse>()
    coEvery { credentialManager.getCredential(context, any<GetCredentialRequest>()) } coAnswers
        {
          credentialResponse.await()
        }

    // A double tap on the button must not open the account picker twice.
    viewModel.signIn(context, credentialManager)
    viewModel.signIn(context, credentialManager)
    credentialResponse.complete(mockk(relaxed = true))
    shadowOf(android.os.Looper.getMainLooper()).idle()

    coVerify(exactly = 1) { credentialManager.getCredential(context, any<GetCredentialRequest>()) }
  }

  // Helper method to stub the CredentialManager to return a specific credential
  private fun stubCredentialManager(credential: Credential) {
    // Create a mock GetCredentialResponse that returns the provided credential
    val response = mockk<GetCredentialResponse>()
    every { response.credential } returns credential
    // Stub the CredentialManager to return the response when getCredential is called
    coEvery { credentialManager.getCredential(context, any<GetCredentialRequest>()) } returns
        response
  }

  // A (Fake) implementation of AuthRepository that records the received credential and returns a
  // configurable result.
  private class RecordingAuthRepository : com.swent.shifter.model.authentication.AuthRepository {
    var receivedCredential: Credential? = null
    var result: Result<FirebaseUser> = Result.failure(IllegalStateException("No result configured"))

    override suspend fun signInWithGoogle(credential: Credential): Result<FirebaseUser> {
      receivedCredential = credential
      return result
    }

    override fun signOut(): Result<Unit> = Result.success(Unit)
  }
}
