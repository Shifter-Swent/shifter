// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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

  /** The spinner shown while the list reloads: its only node without a known progress. */
  private val spinner =
      SemanticsMatcher.expectValue(
          SemanticsProperties.ProgressBarRangeInfo,
          ProgressBarRangeInfo.Indeterminate,
      )

  @Test
  fun pull_callsOnRefreshAndShowsTheSpinner() {
    var refreshes = 0
    composeTestRule.setContent {
      var refreshing by remember { mutableStateOf(false) }
      ShifterTheme {
        RefreshableList(
            isRefreshing = refreshing,
            onRefresh = {
              refreshes++
              refreshing = true
            },
            modifier = Modifier.fillMaxSize(),
        ) {
          LazyColumn(Modifier.fillMaxSize().testTag(LIST)) { items(3) { Text("Item $it") } }
        }
      }
    }
    composeTestRule.onNode(spinner).assertDoesNotExist()

    composeTestRule.onNodeWithTag(LIST).performTouchInput { swipeDown() }

    composeTestRule.runOnIdle { assertEquals(1, refreshes) }
    composeTestRule.onNode(spinner).assertExists()
  }

  @Test
  @OptIn(ExperimentalTestApi::class)
  fun refreshAction_callsOnRefresh() {
    var refreshes = 0
    composeTestRule.setContent {
      ShifterTheme {
        RefreshableList(
            isRefreshing = false,
            onRefresh = { refreshes++ },
            modifier = Modifier.fillMaxSize().testTag(BOX),
        ) {
          LazyColumn(Modifier.fillMaxSize()) { items(3) { Text("Item $it") } }
        }
      }
    }

    composeTestRule.onNodeWithTag(BOX).performCustomAccessibilityActionWithLabel("Refresh")

    composeTestRule.runOnIdle { assertEquals(1, refreshes) }
  }

  private companion object {
    const val LIST = "list"
    const val BOX = "box"
  }
}
