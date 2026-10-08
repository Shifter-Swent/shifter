// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.shifter.R
import com.swent.shifter.ui.navigation.NavigationTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private var backClicks = 0
  private var signOutClicks = 0

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private fun setScreen() {
    composeTestRule.setContent {
      ShifterTheme { SettingsScreen(onBack = { backClicks++ }, onSignOut = { signOutClicks++ }) }
    }
  }

  @Test
  fun displaysHeaderAndSignOut() {
    setScreen()

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.SCREEN).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertTextEquals(context.getString(R.string.settings_title))
    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.settings_sign_out))
        .assertIsDisplayed()
  }

  @Test
  fun signOutClick_callsOnSignOutOnly() {
    setScreen()

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.SIGN_OUT_BUTTON).performClick()

    assertEquals(1, signOutClicks)
    assertEquals(0, backClicks)
  }

  @Test
  fun backClick_callsOnBackOnly() {
    setScreen()

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()

    assertEquals(1, backClicks)
    assertEquals(0, signOutClicks)
  }
}
