package com.swent.shifter.authentication

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

@RunWith(AndroidJUnit4::class)
class SignInScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun signInScreen_displaysSignInContent() {
    composeTestRule.setContent { ShifterTheme { SignInScreen() } }

    composeTestRule.onNodeWithTag(SignInScreenTestTags.APP_NAME).assertTextEquals("Shifter")
    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOGIN_TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithText("Continue with Google").assertIsDisplayed()
  }

  @Test
  fun signInButton_showsLoadingStateAfterClick() {
    composeTestRule.setContent { ShifterTheme { SignInScreen() } }

    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOGIN_BUTTON).performClick()

    assertTrue(
        composeTestRule
            .onAllNodesWithTag(SignInScreenTestTags.LOGIN_BUTTON)
            .fetchSemanticsNodes()
            .isEmpty()
    )
    composeTestRule.onNodeWithTag(SignInScreenTestTags.APP_NAME).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOGIN_TITLE).assertIsDisplayed()
  }
}
