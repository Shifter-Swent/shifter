// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h720dp")
class RefreshableListTest {

  @get:Rule val composeTestRule = createComposeRule()

  private var refreshes = 0
  /** Whether the screen loads, which a refresh starts as a ViewModel would. */
  private var loading by mutableStateOf(false)

  private fun show(hasContent: Boolean = true) {
    composeTestRule.setContent {
      ShifterTheme {
        RefreshableList(
            isLoading = loading,
            hasContent = hasContent,
            onRefresh = {
              refreshes++
              loading = true
            },
            modifier = Modifier.fillMaxSize(),
        ) {
          LazyColumn(Modifier.fillMaxSize().testTag(LIST)) {
            items(3) { Text("Item $it", Modifier.refreshAction()) }
          }
        }
      }
    }
  }

  private fun pull() = composeTestRule.onNodeWithTag(LIST).performTouchInput { swipeDown() }

  @Test
  fun pull_callsOnRefreshAndShowsTheSpinnerUntilTheLoadEnds() {
    show()
    composeTestRule.onNode(isIndeterminateSpinner).assertDoesNotExist()

    pull()

    composeTestRule.runOnIdle { assertEquals(1, refreshes) }
    composeTestRule.onNode(isIndeterminateSpinner).assertExists()
    loading = false
    composeTestRule.onNode(isIndeterminateSpinner).assertDoesNotExist()
  }

  @Test
  fun automaticLoad_staysSilent() {
    show()

    loading = true

    composeTestRule.onNode(isIndeterminateSpinner).assertDoesNotExist()
  }

  @Test
  fun pull_whileALoadRuns_waitsForItWithTheSpinner() {
    show()
    loading = true

    pull()

    composeTestRule.runOnIdle { assertEquals(0, refreshes) }
    composeTestRule.onNode(isIndeterminateSpinner).assertExists()
  }

  @Test
  fun pull_withoutContentWhileALoadRuns_neitherRestartsItNorShowsTheSpinner() {
    show(hasContent = false)
    loading = true

    pull()

    composeTestRule.runOnIdle { assertEquals(0, refreshes) }
    composeTestRule.onNode(isIndeterminateSpinner).assertDoesNotExist()
  }

  @Test
  @OptIn(ExperimentalTestApi::class)
  fun refreshAction_onAnItem_callsOnRefresh() {
    show()

    composeTestRule.onNodeWithText("Item 1").performCustomAccessibilityActionWithLabel("Refresh")

    composeTestRule.runOnIdle { assertEquals(1, refreshes) }
    composeTestRule.onNode(isIndeterminateSpinner).assertExists()
  }

  private companion object {
    const val LIST = "list"
  }
}
