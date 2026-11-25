// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import com.swent.shifter.firebase.AuthEmulator
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.screen.MainScreen
import com.swent.shifter.ui.permission.NOTIFICATION_PERMISSION_ASKED
import com.swent.shifter.ui.permission.NOTIFICATION_PERMISSION_PREFERENCES
import io.github.kakaocup.compose.node.element.ComposeScreen
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/** Verifies that launching the activity opens the sign-in destination. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest : TestCase() {

  // Runs before the activity launches, since MainActivity reads the session in onCreate. It also
  // wires the shared Firestore instance to the emulator before RepositoryProviderFactory.production
  // can reach it, so a later useEmulator call in this process does not throw.
  @get:Rule(order = 0)
  val signedOut =
      object : ExternalResource() {
        override fun before() {
          FirestoreEmulator.firestore
          AuthEmulator.auth.signOut()
          notificationPreferences().edit(commit = true) { clear() }
        }
      }

  @get:Rule(order = 1) val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun launch_displaysSignInScreen() = run {
    step("Launch the app and verify the sign-in screen") {
      ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) {
        appName {
          assertIsDisplayed()
          assertTextEquals("Shifter")
        }
        signInButton {
          assertIsDisplayed()
          assertIsEnabled()
        }
      }
    }
  }

  @Test
  fun launch_signedOut_doesNotAskForTheNotificationPermission() {
    // Asked on My Events only (MainActivityNotificationPermissionTest), never on sign-in.
    ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) {
      signInButton { assertIsDisplayed() }
    }
    composeTestRule.waitForIdle()

    assertFalse(notificationPreferences().getBoolean(NOTIFICATION_PERMISSION_ASKED, false))
  }

  private fun notificationPreferences() =
      InstrumentationRegistry.getInstrumentation()
          .targetContext
          .getSharedPreferences(NOTIFICATION_PERMISSION_PREFERENCES, Context.MODE_PRIVATE)
}
