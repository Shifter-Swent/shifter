package com.swent.shifter.model.authentication

import android.os.Bundle
import androidx.credentials.Credential
import androidx.credentials.CustomCredential
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

@RunWith(RobolectricTestRunner::class)
class AuthRepositoryFirebaseTest {

  private val auth = mockk<FirebaseAuth>()
  private val helper = mockk<GoogleSignInHelper>()
  private val repository = AuthRepositoryFirebase(auth, helper)

  @Test
  fun signInWithGoogle_rejectsUnsupportedCredential() = runBlocking {
    val result = repository.signInWithGoogle(mockk<Credential>())

    assertTrue(result.isFailure)
    assertEquals("Unsupported Google credential", result.exceptionOrNull()?.message)
  }

  @Test
  fun signInWithGoogle_returnsAuthenticatedUser() = runBlocking {
    val credential = googleCredential()
    val authCredential = mockk<AuthCredential>()
    val user = mockk<FirebaseUser>()
    val authResult = mockk<AuthResult>()
    val googleIdToken = mockk<GoogleIdTokenCredential>()

    // Mock the behavior of the helper and FirebaseAuth to simulate a successful sign-in
    every { helper.extractIdTokenCredential(any()) } returns googleIdToken
    every { googleIdToken.idToken } returns "id-token"
    every { helper.toFirebaseCredential("id-token") } returns authCredential
    every { auth.signInWithCredential(authCredential) } returns Tasks.forResult(authResult)
    every { authResult.user } returns user

    val result = repository.signInWithGoogle(credential)

    assertTrue(result.isSuccess)
    assertEquals(user, result.getOrNull())
  }

  @Test
  fun signInWithGoogle_returnsFirebaseFailure() = runBlocking {
    val credential = googleCredential()
    val failure = IllegalStateException("Firebase unavailable")

    every { helper.extractIdTokenCredential(any()) } throws failure

    val result = repository.signInWithGoogle(credential)

    // Test that the repository catches the exception (runCatching) and returns a failure result
    assertTrue(result.isFailure)
    assertEquals(failure, result.exceptionOrNull())
  }

  @Test
  fun signInWithGoogle_failsWhenFirebaseReturnsNoUser() = runBlocking {
    val credential = googleCredential()
    val authCredential = mockk<AuthCredential>()
    val authResult = mockk<AuthResult>()
    val googleIdToken = mockk<GoogleIdTokenCredential>()

    every { helper.extractIdTokenCredential(any()) } returns googleIdToken
    every { googleIdToken.idToken } returns "id-token"
    every { helper.toFirebaseCredential("id-token") } returns authCredential
    every { auth.signInWithCredential(authCredential) } returns Tasks.forResult(authResult)
    every { authResult.user } returns null

    val result = repository.signInWithGoogle(credential)

    assertTrue(result.isFailure)
    assertEquals(
        "Firebase returned no authenticated user",
        result.exceptionOrNull()?.message,
    )
  }

  @Test
  fun signOut_returnsSuccessAndDelegatesToFirebase() {
    // Mock the behavior of FirebaseAuth.signOut() to do nothing (just runs)
    every { auth.signOut() } just runs

    val result = repository.signOut()

    assertTrue(result.isSuccess)
    verify(exactly = 1) { auth.signOut() }
  }

  @Test
  fun signOut_returnsFailureWhenFirebaseThrows() {
    val failure = IllegalStateException("Sign-out failed")
    every { auth.signOut() } throws failure

    val result = repository.signOut()

    assertFalse(result.isSuccess)
    assertEquals(failure, result.exceptionOrNull())
  }

  // Helper function to create a mock Google credential for testing.
  private fun googleCredential(): CustomCredential =
      CustomCredential(TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, Bundle())
}
