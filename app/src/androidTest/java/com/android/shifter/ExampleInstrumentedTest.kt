// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import com.swent.shifter.firebase.AuthEmulator
import com.swent.shifter.screen.MainScreen
import io.github.kakaocup.compose.node.element.ComposeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/** Verifies that launching the activity opens the sign-in destination. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest : TestCase() {

  // Runs before the activity launches, since MainActivity reads the session in onCreate.
  @get:Rule(order = 0)
  val signedOut =
      object : ExternalResource() {
        override fun before() = AuthEmulator.auth.signOut()
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
}
