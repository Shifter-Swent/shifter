// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.authentication

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.swent.shifter.firebase.AuthEmulator
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [AuthRepositoryFirebase], running the real Firebase Auth SDK and the
 * production [DefaultGoogleSignInHelper] against the Auth emulator through [AuthEmulator].
 *
 * Only Credential Manager is bypassed: each test builds the [GoogleIdTokenCredential] it would have
 * returned, around an unsigned token of a fake Google account.
 *
 * Isolation: every test signs in with fresh random Google accounts, and the emulator's accounts are
 * wiped before and after each test so no session or user leaks from one test into the next.
 */
@RunWith(AndroidJUnit4::class)
class AuthRepositoryFirebaseEmulatorTest {

  private val auth = AuthEmulator.auth
  private val repository = AuthRepositoryFirebase(auth, DefaultGoogleSignInHelper())

  @Before
  fun setUp() {
    auth.signOut()
    AuthEmulator.clearAccounts()
  }

  @After
  fun tearDown() {
    auth.signOut()
    AuthEmulator.clearAccounts()
  }

  @Test
  fun signInWithGoogle_signsInAFirebaseUserBuiltFromTheGoogleAccount() = emulatorTest {
    val email = uniqueEmail()

    val user = repository.signInWithGoogle(googleCredential(uniqueSubject(), email)).getOrThrow()

    assertEquals(email, user.email)
    assertEquals(DISPLAY_NAME, user.displayName)
    val currentUser = auth.currentUser
    assertEquals("the session must be the signed-in user", user.uid, currentUser?.uid)
    assertTrue(
        "the user must be linked to the Google provider",
        currentUser!!.providerData.any { it.providerId == GOOGLE_PROVIDER_ID },
    )
  }

  @Test
  fun signInWithGoogle_returnsTheSameUserForTheSameGoogleAccount() = emulatorTest {
    val subject = uniqueSubject()
    val email = uniqueEmail()

    val first = repository.signInWithGoogle(googleCredential(subject, email)).getOrThrow()
    repository.signOut().getOrThrow()
    val second = repository.signInWithGoogle(googleCredential(subject, email)).getOrThrow()

    // A returning volunteer must find their account, not get a fresh empty one.
    assertEquals(first.uid, second.uid)
  }

  @Test
  fun signInWithGoogle_createsDistinctUsersForDistinctGoogleAccounts() = emulatorTest {
    val first =
        repository.signInWithGoogle(googleCredential(uniqueSubject(), uniqueEmail())).getOrThrow()
    repository.signOut().getOrThrow()
    val second =
        repository.signInWithGoogle(googleCredential(uniqueSubject(), uniqueEmail())).getOrThrow()

    assertNotEquals(first.uid, second.uid)
  }

  @Test
  fun currentUser_returnsTheSignedInUserUntilSignOut() = emulatorTest {
    assertNull("no session before sign-in", repository.currentUser())

    val user =
        repository.signInWithGoogle(googleCredential(uniqueSubject(), uniqueEmail())).getOrThrow()
    assertEquals(user, repository.currentUser())

    repository.signOut().getOrThrow()
    assertNull(repository.currentUser())
  }

  @Test
  fun signOut_clearsTheCurrentSession() = emulatorTest {
    repository.signInWithGoogle(googleCredential(uniqueSubject(), uniqueEmail())).getOrThrow()

    val result = repository.signOut()

    assertTrue(result.isSuccess)
    assertNull(auth.currentUser)
  }

  /** The credential Credential Manager returns after the user picked the given Google account. */
  private fun googleCredential(subject: String, email: String) =
      GoogleIdTokenCredential.Builder()
          .setId(email)
          .setIdToken(AuthEmulator.fakeGoogleIdToken(subject, email, DISPLAY_NAME))
          .build()

  private fun uniqueSubject(): String = "google-" + UUID.randomUUID()

  private fun uniqueEmail(): String = "volunteer-${UUID.randomUUID()}@example.com"

  /** Fails the test instead of hanging when the emulator cannot be reached. */
  private fun emulatorTest(block: suspend () -> Unit) = runBlocking {
    withTimeout(TIMEOUT_MILLIS) { block() }
  }

  private companion object {
    const val TIMEOUT_MILLIS = 20_000L
    const val DISPLAY_NAME = "Test Volunteer"
    const val GOOGLE_PROVIDER_ID = "google.com"
  }
}
