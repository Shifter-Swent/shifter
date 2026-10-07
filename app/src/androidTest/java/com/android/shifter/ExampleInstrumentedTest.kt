// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import com.swent.shifter.screen.MainScreen
import io.github.kakaocup.compose.node.element.ComposeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that launching the activity opens the sign-in destination. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest : TestCase() {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

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
