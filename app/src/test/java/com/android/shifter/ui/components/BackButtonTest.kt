// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.navigation.NavigationTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackButtonTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun backButton_callsOnClick() {
    var clicks = 0
    composeTestRule.setContent { ShifterTheme { BackButton(onClick = { clicks++ }) } }

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()

    assertEquals(1, clicks)
  }

  @Test
  fun backButton_isDescribedAsBack() {
    composeTestRule.setContent { ShifterTheme { BackButton(onClick = {}) } }

    composeTestRule
        .onNode(hasTestTag(NavigationTestTags.GO_BACK_BUTTON) and hasContentDescription("Back"))
        .assertIsDisplayed()
  }

  @Test
  fun backButton_hasATouchTargetOf48Dp() {
    composeTestRule.setContent { ShifterTheme { BackButton(onClick = {}) } }

    composeTestRule
        .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON)
        .assertTouchWidthIsEqualTo(48.dp)
        .assertTouchHeightIsEqualTo(48.dp)
  }

  @Test
  fun backButton_drawsACircleOf40Dp() {
    composeTestRule.setContent { ShifterTheme { BackButton(onClick = {}) } }

    composeTestRule
        .onNodeWithTag(BackButtonTestTags.CIRCLE, useUnmergedTree = true)
        .assertWidthIsEqualTo(40.dp)
        .assertHeightIsEqualTo(40.dp)
  }
}
