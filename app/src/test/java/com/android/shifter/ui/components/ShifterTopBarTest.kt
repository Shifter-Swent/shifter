// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.navigation.NavigationTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class ShifterTopBarTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val title
    get() = composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)

  private val backButton
    get() = composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON)

  private fun setBar(text: String = "Title", onBack: () -> Unit = {}) {
    composeTestRule.setContent {
      ShifterTheme {
        ShifterTopBar(title = text, onBack = onBack, modifier = Modifier.testTag(BAR))
      }
    }
  }

  private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
    return results.single()
  }

  @Test
  fun shifterTopBar_showsTheTitle() {
    setBar(text = "Create an event")

    title.assertTextEquals("Create an event")
  }

  @Test
  fun shifterTopBar_callsOnBack() {
    var backCalls = 0
    setBar(onBack = { backCalls++ })

    backButton.performClick()

    assertEquals(1, backCalls)
  }

  @Test
  fun shifterTopBar_hasTheHeightOfTheBarAndItsDivider() {
    setBar()

    composeTestRule.onNodeWithTag(BAR).assertHeightIsEqualTo(73.dp)
  }

  @Test
  fun shifterTopBar_centersTheTitle() {
    setBar()

    val bar = composeTestRule.onNodeWithTag(BAR).getUnclippedBoundsInRoot()
    val text = title.getUnclippedBoundsInRoot()
    assertEquals((bar.left + bar.right).value, (text.left + text.right).value, 0.5f)
  }

  // Robolectric's default graphics mode lays no text out, so it would never report an ellipsis.
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  @Test
  fun shifterTopBar_withALongTitle_staysOnOneLineAndEllipsizes() {
    setBar(text = LONG_TITLE)

    val layout = title.textLayout()
    assertEquals(1, layout.lineCount)
    assertTrue(layout.isLineEllipsized(0))
    composeTestRule.onNodeWithTag(BAR).assertHeightIsEqualTo(73.dp)
  }

  @Test
  fun shifterTopBar_withALongTitle_doesNotCoverTheBackButton() {
    setBar(text = LONG_TITLE)

    assertTrue(title.getUnclippedBoundsInRoot().left >= backButton.getUnclippedBoundsInRoot().right)
    backButton.performClick()
  }

  private companion object {
    const val BAR = "bar"
    val LONG_TITLE = "A very long event title ".repeat(10)
  }
}
