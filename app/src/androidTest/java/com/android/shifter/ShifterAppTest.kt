// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.firebase.AuthEmulator
import com.swent.shifter.ui.authentication.SignInScreenTestTags
import com.swent.shifter.ui.events.MyEventsTestTags
import com.swent.shifter.ui.navigation.NavigationTestTags
import com.swent.shifter.ui.settings.SettingsScreenTestTags
import com.swent.shifter.ui.theme.ShifterTheme
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
    // The sign-in screen builds the production repository: keep it on the emulator, signed out.
    AuthEmulator.auth.signOut()
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
  fun staffEvents_switchesToThePastTab() {
    composeTestRule.setContent { ShifterTheme { ShifterApp(isSignedIn = true) } }

    composeTestRule.onNodeWithTag(MyEventsTestTags.PAST_TAB).performClick()

    composeTestRule.onNodeWithTag(MyEventsTestTags.PAST_TAB).assertIsSelected()
  }
}
