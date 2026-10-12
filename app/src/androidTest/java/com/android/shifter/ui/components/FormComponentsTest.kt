// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormComponentsTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun formHeader_showsTitleAndCallsOnBack() {
    var backCalls = 0
    composeTestRule.setContent {
      ShifterTheme {
        FormHeader(
            backLabel = "My events",
            title = "Create an event",
            backButtonTestTag = BACK,
            onBack = { backCalls++ },
        )
      }
    }

    composeTestRule.onNodeWithText("Create an event").assertIsDisplayed()
    composeTestRule.onNodeWithTag(BACK).assertTextContains("My events").performClick()

    assertEquals(1, backCalls)
  }

  @Test
  fun formTextField_showsLabelAndReportsInput() {
    composeTestRule.setContent {
      ShifterTheme {
        var value by remember { mutableStateOf("") }
        FormTextField(
            value = value,
            onValueChange = { value = it },
            label = "Title",
            isError = false,
            enabled = true,
            testTag = FIELD,
        )
      }
    }

    composeTestRule.onNodeWithText("Title").assertIsDisplayed()
    composeTestRule.onNodeWithTag(FIELD).performTextInput("Food drive")

    composeTestRule.onNodeWithTag(FIELD).assertTextContains("Food drive")
  }

  @Test
  fun formTextField_showsSupportingTextAndCanBeDisabled() {
    composeTestRule.setContent {
      ShifterTheme {
        FormTextField(
            value = "",
            onValueChange = {},
            label = "Title",
            isError = true,
            enabled = false,
            testTag = FIELD,
            supportingText = { Text("Required") },
        )
      }
    }

    composeTestRule.onNodeWithText("Required").assertIsDisplayed()
    composeTestRule.onNodeWithTag(FIELD).assertIsNotEnabled()
  }

  @Test
  fun formPrimaryButton_showsTextAndCallsOnClick() {
    var clicks = 0
    composeTestRule.setContent {
      ShifterTheme { FormPrimaryButton(text = "Save", onClick = { clicks++ }, testTag = BUTTON) }
    }

    composeTestRule.onNodeWithTag(BUTTON).assertIsEnabled().performClick()

    composeTestRule.onNodeWithText("Save").assertIsDisplayed()
    assertEquals(1, clicks)
  }

  @Test
  fun formPrimaryButton_whileLoading_showsIndicatorInsteadOfText() {
    composeTestRule.setContent {
      ShifterTheme {
        FormPrimaryButton(
            text = "Save",
            onClick = {},
            testTag = BUTTON,
            enabled = false,
            isLoading = true,
            loadingIndicatorTestTag = LOADING,
        )
      }
    }

    composeTestRule.onNodeWithTag(LOADING, useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Save").assertDoesNotExist()
    composeTestRule.onNodeWithTag(BUTTON).assertIsNotEnabled()
  }

  private companion object {
    const val BACK = "back"
    const val FIELD = "field"
    const val BUTTON = "button"
    const val LOADING = "loading"
  }
}
