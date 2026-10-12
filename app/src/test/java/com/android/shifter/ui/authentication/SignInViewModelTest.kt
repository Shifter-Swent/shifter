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
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.swent.shifter.R
import com.swent.shifter.model.authentication.AuthUser
import com.swent.shifter.model.authentication.FakeAuthRepository
import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.UserRepositoryException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.coroutines.cancellation.CancellationException
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
  private val repository = FakeAuthRepository()
  private val userRepository = FakeUserRepository()
  private val viewModel = SignInViewModel(repository, userRepository)

  // Set up the mock context to return a default web client ID for testing
  init {
    every { context.getString(R.string.default_web_client_id) } returns "web-client-id"
  }

  @Test
  fun signIn_acceptsGoogleAccountDifferentFromDeviceAccount() {
    val newGoogleAccountCredential = mockk<Credential>()
    val user = AuthUser(uid = "uid-1", email = "ada@example.com", displayName = "Ada")
    repository.signInResult = Result.success(user)
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
    repository.signInResult =
        Result.success(AuthUser(uid = "uid-1", email = null, displayName = null))

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
    val user = AuthUser(uid = "uid-1", email = "ada@example.com", displayName = "Ada")
    repository.signInResult = Result.success(user)
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
    repository.signInResult = Result.failure(IllegalStateException("Repository unavailable"))
    stubCredentialManager(mockk<Credential>())

    viewModel.signIn(context, credentialManager)
    shadowOf(android.os.Looper.getMainLooper()).idle()

    assertFalse(viewModel.uiState.value.signedIn)
  }

  @Test
  fun signIn_repositoryFailure_setsErrorAndClearsUser() {
    val credential = mockk<Credential>()
    repository.signInResult = Result.failure(IllegalStateException("Repository unavailable"))
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
  fun signIn_userCancellation_clearsLoadingWithoutError() {
    coEvery { credentialManager.getCredential(context, any<GetCredentialRequest>()) } throws
        GetCredentialCancellationException()

    viewModel.signIn(context, credentialManager)
    shadowOf(android.os.Looper.getMainLooper()).idle()

    assertNull(viewModel.uiState.value.errorMsg)
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
  fun signIn_coroutineCancellation_doesNotShowError() {
    coEvery { credentialManager.getCredential(context, any<GetCredentialRequest>()) } throws
        CancellationException("ViewModel cleared")

    viewModel.signIn(context, credentialManager)
    shadowOf(android.os.Looper.getMainLooper()).idle()

    // Coroutine cancellation is not a sign-in error
    assertNull(viewModel.uiState.value.errorMsg)
  }

  @Test
  fun signIn_repositoryFailureWithoutMessage_usesGenericSignInError() {
    repository.signInResult = Result.failure(IllegalStateException())
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

  @Test
  fun factory_createsViewModelOnTheGivenRepository() {
    val credential = mockk<Credential>()
    val user = AuthUser(uid = "uid-1", email = "ada@example.com", displayName = "Ada")
    repository.signInResult = Result.success(user)
    stubCredentialManager(credential)
    val built =
        SignInViewModel.factory(repository, userRepository)
            .create(SignInViewModel::class.java, CreationExtras.Empty)

    built.signIn(context, credentialManager)
    shadowOf(android.os.Looper.getMainLooper()).idle()

    assertEquals(credential, repository.receivedCredential)
    assertEquals(user, built.uiState.value.user)
  }

  @Test
  fun signIn_success_createsProfileOnFirstSignIn() {
    val user = AuthUser(uid = "uid-1", email = "ada@example.com", displayName = "Ada")
    repository.signInResult = Result.success(user)
    stubCredentialManager(mockk<Credential>())

    viewModel.signIn(context, credentialManager)
    shadowOf(android.os.Looper.getMainLooper()).idle()

    val profile = userRepository.users.getValue("uid-1")
    assertEquals("Ada", profile.displayName)
    assertEquals("ada@example.com", profile.email)
  }

  @Test
  fun signIn_accountWithoutNameOrEmail_createsProfileWithEmptyFields() {
    repository.signInResult =
        Result.success(AuthUser(uid = "uid-1", email = null, displayName = null))
    stubCredentialManager(mockk<Credential>())

    viewModel.signIn(context, credentialManager)
    shadowOf(android.os.Looper.getMainLooper()).idle()

    val profile = userRepository.users.getValue("uid-1")
    assertEquals("", profile.displayName)
    assertEquals("", profile.email)
    assertTrue(viewModel.uiState.value.signedIn)
  }

  @Test
  fun signIn_profileFailure_signsOutAndShowsError() {
    repository.signInResult =
        Result.success(AuthUser(uid = "uid-1", email = "ada@example.com", displayName = "Ada"))
    userRepository.failure = UserRepositoryException.Unavailable()
    stubCredentialManager(mockk<Credential>())

    viewModel.signIn(context, credentialManager)
    shadowOf(android.os.Looper.getMainLooper()).idle()

    // A session without a profile must not survive, or the next launch would skip sign-in.
    assertEquals(1, repository.signOutCalls)
    assertNull(repository.currentUser())
    assertEquals(
        "Could not load your profile: The user profile service is unavailable",
        viewModel.uiState.value.errorMsg,
    )
    assertFalse(viewModel.uiState.value.signedIn)
    assertNull(viewModel.uiState.value.user)
    assertFalse(viewModel.uiState.value.isLoading)
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
}
