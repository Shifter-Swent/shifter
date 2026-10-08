// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.authentication

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.swent.shifter.firebase.AuthEmulator
import com.swent.shifter.model.authentication.AuthRepositoryFirebase
import com.swent.shifter.model.authentication.DefaultGoogleSignInHelper
import com.swent.shifter.ui.theme.ShifterTheme
import java.util.UUID
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for [SignInScreen].
 *
 * Only Credential Manager is replaced, by a [FakeCredentialManager] answering in place of the
 * Google account picker. The ViewModel and the repository are the production ones, wired to the
 * Auth emulator through [AuthEmulator], so a successful sign-in really goes through Firebase.
 */
@RunWith(AndroidJUnit4::class)
class SignInScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val viewModel =
      SignInViewModel(AuthRepositoryFirebase(AuthEmulator.auth, DefaultGoogleSignInHelper()))

  private var signedInCalls = 0

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
  fun signInScreen_displaysSignInContent() {
    composeTestRule.setContent { ShifterTheme { SignInScreen() } }

    composeTestRule.onNodeWithTag(SignInScreenTestTags.APP_NAME).assertTextEquals("Shifter")
    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOGIN_TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOGIN_BUTTON).assertIsDisplayed()
  }

  @Test
  fun signInScreen_showsLoadingIndicatorInsteadOfButtonWhileWaitingForGoogle() {
    // The user has the account picker open and has not chosen yet.
    setSignInContent(FakeCredentialManager { awaitCancellation() })

    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOGIN_BUTTON).performClick()

    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOADING_INDICATOR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOGIN_BUTTON).assertDoesNotExist()
  }

  @Test
  fun signInScreen_cancelledSignIn_showsButtonAgainAndDoesNotNavigate() {
    setSignInContent(FakeCredentialManager { throw GetCredentialCancellationException() })

    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOGIN_BUTTON).performClick()
    composeTestRule.waitForIdle()

    // The user can retry, and the error has been consumed by the screen's toast.
    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOGIN_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOADING_INDICATOR).assertDoesNotExist()
    assertNull(viewModel.uiState.value.errorMsg)
    assertEquals(0, signedInCalls)
    assertNull(AuthEmulator.auth.currentUser)
  }

  @Test
  fun signInScreen_successfulGoogleSignIn_signsInWithFirebaseAndNavigates() {
    val email = "volunteer-${UUID.randomUUID()}@example.com"
    val credential =
        GoogleIdTokenCredential.Builder()
            .setId(email)
            .setIdToken(
                AuthEmulator.fakeGoogleIdToken("google-${UUID.randomUUID()}", email, DISPLAY_NAME)
            )
            .build()
    val restorationTester = StateRestorationTester(composeTestRule)
    restorationTester.setContent {
      SignInContent(FakeCredentialManager { GetCredentialResponse(credential) })
    }

    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOGIN_BUTTON).performClick()
    composeTestRule.waitUntil(TIMEOUT_MILLIS) { signedInCalls > 0 }

    assertEquals(email, AuthEmulator.auth.currentUser?.email)
    assertEquals(email, viewModel.uiState.value.user?.email)

    // Recreating the screen, as on a rotation, must not navigate a second time.
    restorationTester.emulateSavedInstanceStateRestore()
    composeTestRule.waitForIdle()

    assertEquals(1, signedInCalls)
    assertFalse(viewModel.uiState.value.signedIn)
  }

  private fun setSignInContent(credentialManager: CredentialManager) {
    composeTestRule.setContent { SignInContent(credentialManager) }
  }

  @Composable
  private fun SignInContent(credentialManager: CredentialManager) {
    ShifterTheme {
      SignInScreen(
          authViewModel = viewModel,
          credentialManager = credentialManager,
          onSignedIn = { signedInCalls++ },
      )
    }
  }

  /**
   * A [CredentialManager] whose account picker is replaced by [answer]; every other operation is
   * left to the real implementation, which these tests never reach.
   */
  private class FakeCredentialManager(private val answer: suspend () -> GetCredentialResponse) :
      CredentialManager by CredentialManager.create(
          InstrumentationRegistry.getInstrumentation().targetContext
      ) {
    override suspend fun getCredential(
        context: Context,
        request: GetCredentialRequest,
    ): GetCredentialResponse = answer()
  }

  private companion object {
    const val TIMEOUT_MILLIS = 20_000L
    const val DISPLAY_NAME = "Test Volunteer"
  }
}
