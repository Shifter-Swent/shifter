// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.swent.shifter.firebase.AuthEmulator
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.model.authentication.AuthRepositoryFirebase
import com.swent.shifter.model.authentication.DefaultGoogleSignInHelper
import com.swent.shifter.ui.authentication.SignInScreenTestTags
import com.swent.shifter.ui.events.MyEventsTestTags
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/** Verifies that launching the activity with a restored session skips sign-in. */
@RunWith(AndroidJUnit4::class)
class MainActivitySignedInTest {

  // Runs before the activity launches, since MainActivity reads the session in onCreate. It also
  // wires the shared Firestore instance to the emulator before RepositoryProviderFactory.production
  // can reach it, so a later useEmulator call in this process does not throw.
  @get:Rule(order = 0)
  val signedIn =
      object : ExternalResource() {
        override fun before() {
          FirestoreEmulator.firestore
          runBlocking {
            AuthRepositoryFirebase(AuthEmulator.auth, DefaultGoogleSignInHelper())
                .signInWithGoogle(googleCredential())
                .getOrThrow()
          }
        }

        override fun after() {
          AuthEmulator.auth.signOut()
          AuthEmulator.clearAccounts()
        }
      }

  @get:Rule(order = 1) val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun launch_withRestoredSession_opensTheVolunteerEvents() {
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOGIN_BUTTON).assertDoesNotExist()
  }

  /** The credential Credential Manager would return for a fresh fake Google account. */
  private fun googleCredential(): GoogleIdTokenCredential {
    val email = "volunteer-${UUID.randomUUID()}@example.com"
    return GoogleIdTokenCredential.Builder()
        .setId(email)
        .setIdToken(AuthEmulator.fakeGoogleIdToken("google-" + UUID.randomUUID(), email, "Ada"))
        .build()
  }
}
