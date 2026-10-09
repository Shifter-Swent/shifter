// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.swent.shifter.firebase.AuthEmulator
import com.swent.shifter.model.authentication.AuthRepositoryFirebase
import com.swent.shifter.model.authentication.DefaultGoogleSignInHelper
import com.swent.shifter.ui.authentication.SignInScreenTestTags
import com.swent.shifter.ui.events.MyEventsTestTags
import com.swent.shifter.ui.navigation.NavigationTestTags
import com.swent.shifter.ui.settings.SettingsScreenTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that [ShifterApp] starts on the destination matching the restored session. */
@RunWith(AndroidJUnit4::class)
class ShifterAppTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Before
  fun setUp() {
    // The screens build the production repository: keep it on the emulator, signed out.
    AuthEmulator.auth.signOut()
  }

  @After
  fun tearDown() {
    AuthEmulator.auth.signOut()
    AuthEmulator.clearAccounts()
  }

  @Test
  fun signedOut_startsOnSignIn() {
    composeTestRule.setContent { ShifterTheme { ShifterApp(isSignedIn = false) } }

    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOGIN_BUTTON).assertIsDisplayed()
  }

  @Test
  fun signedIn_skipsSignInAndStartsOnVolunteerEvents() {
    composeTestRule.setContent { ShifterTheme { ShifterApp(isSignedIn = true) } }

    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOGIN_BUTTON).assertDoesNotExist()
  }

  @Test
  fun staffEvents_avatarOpensSettingsAndBackReturns() {
    composeTestRule.setContent { ShifterTheme { ShifterApp(isSignedIn = true) } }

    composeTestRule.onNodeWithTag(MyEventsTestTags.AVATAR).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.SCREEN).assertIsDisplayed()

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
  }

  @Test
  fun settings_signOutClearsTheSessionAndReturnsToSignIn() {
    runBlocking {
      AuthRepositoryFirebase(AuthEmulator.auth, DefaultGoogleSignInHelper())
          .signInWithGoogle(googleCredential())
          .getOrThrow()
    }
    composeTestRule.setContent { ShifterTheme { ShifterApp(isSignedIn = true) } }

    composeTestRule.onNodeWithTag(MyEventsTestTags.AVATAR).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.SIGN_OUT_BUTTON).performClick()

    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule
          .onAllNodesWithTag(SignInScreenTestTags.LOGIN_BUTTON)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    assertNull(AuthEmulator.auth.currentUser)
  }

  @Test
  fun staffEvents_switchesToThePastTab() {
    composeTestRule.setContent { ShifterTheme { ShifterApp(isSignedIn = true) } }

    composeTestRule.onNodeWithTag(MyEventsTestTags.PAST_TAB).performClick()

    composeTestRule.onNodeWithTag(MyEventsTestTags.PAST_TAB).assertIsSelected()
  }

  /** The credential Credential Manager would return for a fresh fake Google account. */
  private fun googleCredential(): GoogleIdTokenCredential {
    val email = "volunteer-${UUID.randomUUID()}@example.com"
    return GoogleIdTokenCredential.Builder()
        .setId(email)
        .setIdToken(AuthEmulator.fakeGoogleIdToken("google-" + UUID.randomUUID(), email, "Ada"))
        .build()
  }

  private companion object {
    const val TIMEOUT_MILLIS = 20_000L
  }
}
