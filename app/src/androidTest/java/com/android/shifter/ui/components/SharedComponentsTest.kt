// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedComponentsTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun underlinedTabs_selectTheClickedTab() {
    composeTestRule.setContent {
      ShifterTheme {
        var selected by remember { mutableIntStateOf(0) }
        UnderlinedTabRow {
          listOf("Participants", "Requests").forEachIndexed { index, label ->
            UnderlinedTab(
                label = label,
                isSelected = index == selected,
                onClick = { selected = index },
                width = 150.dp,
                modifier = Modifier.testTag("tab$index"),
            )
          }
        }
      }
    }

    composeTestRule.onNodeWithTag("tab0").assertIsSelected()
    composeTestRule.onNodeWithTag("tab1").assertIsNotSelected().performClick()

    composeTestRule.onNodeWithTag("tab1").assertIsSelected()
    composeTestRule.onNodeWithTag("tab0").assertIsNotSelected()
  }

  @Test
  fun pill_showsItsText() {
    composeTestRule.setContent {
      ShifterTheme {
        Pill(
            text = "Pending",
            colors = PillColors(Color.Black, Color.White),
            modifier = Modifier.testTag(PILL),
            large = true,
        )
      }
    }

    composeTestRule.onNodeWithTag(PILL).assertTextEquals("Pending")
  }

  @Test
  fun primaryButton_callsOnClick() {
    var clicks = 0
    composeTestRule.setContent {
      ShifterTheme { PrimaryButton(text = "Apply", onClick = { clicks++ }, testTag = BUTTON) }
    }

    composeTestRule.onNodeWithTag(BUTTON).performClick()

    composeTestRule.onNodeWithText("Apply").assertIsDisplayed()
    assertEquals(1, clicks)
  }

  @Test
  fun primaryButton_disabled_isNotEnabled() {
    composeTestRule.setContent {
      ShifterTheme {
        PrimaryButton(text = "Apply", onClick = {}, testTag = BUTTON, enabled = false)
      }
    }

    composeTestRule.onNodeWithTag(BUTTON).assertIsNotEnabled()
  }

  private companion object {
    const val PILL = "pill"
    const val BUTTON = "button"
  }
}
