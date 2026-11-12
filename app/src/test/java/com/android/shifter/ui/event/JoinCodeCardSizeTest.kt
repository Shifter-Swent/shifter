// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The compact join code card keeps the same size in every state, so nothing on the Overview moves
 * when the code arrives or turns out to be unavailable.
 */
@RunWith(AndroidJUnit4::class)
class JoinCodeCardSizeTest {

  @get:Rule val compose = createComposeRule()

  /** Shows the three states one above the other, at [fontScale]. */
  private fun showEveryState(fontScale: Float = 1f) {
    compose.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        ShifterTheme {
          Column(Modifier.width(360.dp)) {
            JoinCodeCard(
                joinCode = JOIN_CODE_PLACEHOLDER,
                onCopy = {},
                size = JoinCodeCardSize.Compact,
                copyEnabled = false,
                modifier = Modifier.testTag(LOADING),
            )
            JoinCodeCard(
                joinCode = "ABC234",
                onCopy = {},
                size = JoinCodeCardSize.Compact,
                modifier = Modifier.testTag(READY),
            )
            JoinCodeUnavailableCard(onRetry = {}, modifier = Modifier.testTag(UNAVAILABLE))
          }
        }
      }
    }
  }

  private fun heightOf(tag: String) =
      compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().size.height

  private fun assertSameHeights() {
    val loading = heightOf(LOADING)
    assertEquals("ready card", loading, heightOf(READY))
    assertEquals("unavailable card", loading, heightOf(UNAVAILABLE))
  }

  @Test
  fun compactCard_hasTheSameHeightInEveryState() {
    showEveryState()

    assertSameHeights()
  }

  @Test
  fun compactCard_withLargeFonts_hasTheSameHeightInEveryState() {
    showEveryState(fontScale = 2f)

    assertSameHeights()
  }

  @Test
  fun copyAndRetryButtons_haveTheSameSize() {
    showEveryState()

    // The ready card's button: the loading card's one is the same, only disabled.
    val copy =
        compose
            .onAllNodesWithTag(JoinCodeCardTestTags.COPY_BUTTON, useUnmergedTree = true)[1]
            .fetchSemanticsNode()
            .size
    val retry =
        compose
            .onNodeWithTag(JoinCodeCardTestTags.RETRY_BUTTON, useUnmergedTree = true)
            .fetchSemanticsNode()
            .size
    assertEquals(copy, retry)
  }

  private companion object {
    const val LOADING = "loading"
    const val READY = "ready"
    const val UNAVAILABLE = "unavailable"
  }
}
