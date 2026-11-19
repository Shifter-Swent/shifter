// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.days

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DayHeaderTest {

  @get:Rule val compose = createComposeRule()

  private var backToToday = 0

  private val filter: @Composable () -> Unit = { Text("All teams", Modifier.testTag(FILTER)) }
  private val hint: @Composable () -> Unit = { Text("2 missions", Modifier.testTag(HINT)) }
  private val strip: @Composable () -> Unit = { Text("days", Modifier.testTag(STRIP)) }

  private fun show(
      filter: (@Composable () -> Unit)? = null,
      awayFromToday: Boolean = false,
      hint: (@Composable () -> Unit)? = null,
      strip: (@Composable () -> Unit)? = null,
  ) {
    compose.setContent {
      ShifterTheme {
        DayHeader(
            title = "Missions",
            filter = filter,
            onBackToToday = { backToToday += 1 }.takeIf { awayFromToday },
            hint = hint,
            strip = strip,
        )
      }
    }
  }

  private fun node(tag: String) = compose.onNodeWithTag(tag)

  @Test
  fun showsTheTitle() {
    show()

    node(DayHeaderTestTags.HEADER).assertIsDisplayed()
    node(DayHeaderTestTags.TITLE).assertTextEquals("Missions")
  }

  @Test
  fun showsTheFilterWhenGiven_andNoneOtherwise() {
    show(filter = filter, hint = hint)

    node(FILTER).assertIsDisplayed()
    // The filter has the room: the hint is not squeezed in next to it.
    node(HINT).assertDoesNotExist()
  }

  @Test
  fun withoutAFilter_showsTheHint() {
    show(hint = hint)

    node(HINT).assertIsDisplayed()
    node(FILTER).assertDoesNotExist()
  }

  @Test
  fun onAnotherDay_withoutAFilter_offersBackToTodayInPlaceOfTheHint() {
    show(awayFromToday = true, hint = hint)

    node(HINT).assertDoesNotExist()
    node(DayHeaderTestTags.BACK_TO_TODAY).assertTextEquals("Back to today").performClick()

    compose.runOnIdle { assertEquals(1, backToToday) }
  }

  @Test
  fun onAnotherDay_withAFilter_keepsTheFilter() {
    show(filter = filter, awayFromToday = true)

    node(FILTER).assertIsDisplayed()
    node(DayHeaderTestTags.BACK_TO_TODAY).assertDoesNotExist()
  }

  @Test
  fun showsTheStripWhenGiven_andNoneOtherwise() {
    show(strip = strip)
    node(STRIP).assertIsDisplayed()
  }

  @Test
  fun withoutAStrip_showsNone() {
    show()
    node(STRIP).assertDoesNotExist()
  }

  private companion object {
    const val FILTER = "filter"
    const val HINT = "hint"
    const val STRIP = "strip"
  }
}
