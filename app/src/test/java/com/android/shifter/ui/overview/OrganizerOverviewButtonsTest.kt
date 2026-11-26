// Co-authored-by: Claude Opus 5 (1M context) <noreply@anthropic.com>
package com.swent.shifter.ui.overview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The side-by-side "Manage teams" and "Shifts" buttons of the organizer's Overview. */
@RunWith(AndroidJUnit4::class)
class OrganizerOverviewButtonsTest {

  @get:Rule val compose = createComposeRule()

  /** The Overview at [fontScale], padded by the 16 dp `PlaceholderPage` gives the tab. */
  private fun show(fontScale: Float) {
    compose.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        ShifterTheme {
          Box(Modifier.padding(16.dp)) {
            OrganizerOverviewContent(
                OrganizerOverviewUiState(isLoading = false),
                OrganizerOverviewActions(),
                now = Instant.EPOCH,
                zone = ZoneOffset.UTC,
            )
          }
        }
      }
    }
  }

  private fun heightOf(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().height

  @Test
  fun buttons_atTheDefaultFontScale_areTheHeightOfTheDesign() {
    show(fontScale = 1f)

    assertEquals(48.dp, heightOf(TEAMS))
    assertEquals(48.dp, heightOf(SHIFTS))
  }

  // Robolectric's default graphics mode lays no text out, so it would never report a cut label.
  // The phone width is the default of robolectric.properties.
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  @Test
  fun buttons_withLargerFonts_growTogetherRatherThanCuttingTheirLabels() {
    show(fontScale = 1.3f)

    val layouts = mutableListOf<TextLayoutResult>()
    compose
        .onNodeWithText("Manage teams", useUnmergedTree = true)
        .fetchSemanticsNode()
        .config[SemanticsActions.GetTextLayoutResult]
        .action!!
        .invoke(layouts)
    assertFalse("the label is cut off", layouts.single().didOverflowHeight)
    assertEquals(heightOf(TEAMS), heightOf(SHIFTS))
  }

  private companion object {
    const val TEAMS = OrganizerOverviewScreenTestTags.MANAGE_TEAMS_BUTTON
    const val SHIFTS = OrganizerOverviewScreenTestTags.SHIFTS_BUTTON
  }
}
