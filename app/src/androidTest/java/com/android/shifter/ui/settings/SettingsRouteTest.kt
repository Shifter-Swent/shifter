// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.swent.shifter.firebase.AuthEmulator
import com.swent.shifter.model.authentication.AuthRepositoryFirebase
import com.swent.shifter.model.authentication.DefaultGoogleSignInHelper
import com.swent.shifter.ui.theme.ShifterTheme
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests [SettingsRoute] with the production ViewModel and repository, wired to the Auth emulator
 * through [AuthEmulator], so signing out really clears a Firebase session.
 */
@RunWith(AndroidJUnit4::class)
class SettingsRouteTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val repository = AuthRepositoryFirebase(AuthEmulator.auth, DefaultGoogleSignInHelper())
  private val credentialManager = RecordingCredentialManager()

  @Before
  fun setUp() {
    AuthEmulator.auth.signOut()
    AuthEmulator.clearAccounts()
  }

  @After
  fun tearDown() {
    AuthEmulator.auth.signOut()
    AuthEmulator.clearAccounts()
  }

  @Test
  fun signOut_clearsTheSessionAndTheCredentialsThenReportsIt() {
    runBlocking { repository.signInWithGoogle(googleCredential()).getOrThrow() }
    assertNotNull(AuthEmulator.auth.currentUser)
    var signedOut = 0
    val viewModel = SettingsViewModel(repository)
    composeTestRule.setContent {
      ShifterTheme {
        SettingsRoute(
            onBack = {},
            onSignedOut = { signedOut++ },
            viewModel = viewModel,
            credentialManager = credentialManager,
        )
      }
    }

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.SIGN_OUT_BUTTON).performClick()

    composeTestRule.waitUntil(TIMEOUT_MILLIS) { signedOut == 1 }
    assertNull(AuthEmulator.auth.currentUser)
    assertEquals(1, credentialManager.clearCalls)
  }

  /** The credential Credential Manager would return for a fresh fake Google account. */
  private fun googleCredential(): GoogleIdTokenCredential {
    val email = "volunteer-${UUID.randomUUID()}@example.com"
    return GoogleIdTokenCredential.Builder()
        .setId(email)
        .setIdToken(AuthEmulator.fakeGoogleIdToken("google-" + UUID.randomUUID(), email, "Ada"))
        .build()
  }

  /** A [CredentialManager] that only records that its state was cleared. */
  private class RecordingCredentialManager :
      CredentialManager by CredentialManager.create(
          InstrumentationRegistry.getInstrumentation().targetContext
      ) {
    var clearCalls = 0

    override suspend fun clearCredentialState(request: ClearCredentialStateRequest) {
      clearCalls++
    }
  }

  private companion object {
    const val TIMEOUT_MILLIS = 20_000L
  }
}
