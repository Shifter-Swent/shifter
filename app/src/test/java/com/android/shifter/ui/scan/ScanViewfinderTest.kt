// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.scan

import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The viewfinder alone, in each of its states, with and without the system animations. */
@RunWith(AndroidJUnit4::class)
class ScanViewfinderTest {
  @get:Rule val compose = createComposeRule()

  private val resolver =
      ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver

  private var state by mutableStateOf(ViewfinderState.SEARCHING)
  private var refusals by mutableIntStateOf(0)

  @After
  fun restoreAnimations() {
    Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
  }

  private fun show(initial: ViewfinderState) {
    state = initial
    compose.setContent { ShifterTheme { ScanViewfinder(state = state, refusals = refusals) } }
  }

  private fun tag(tag: String) = compose.onNodeWithTag(tag)

  /** The scan screen's camera view, without a camera, on a screen of [width] by [height]. */
  private fun showOnScreen(width: Dp, height: Dp) {
    compose.setContent {
      ShifterTheme {
        Box(Modifier.requiredSize(width, height)) {
          ScanCamera(viewfinder = ViewfinderState.SEARCHING, refusals = 0, onEnterCode = {}) {}
        }
      }
    }
  }

  @Test
  fun searching_saysWhereToPointTheCamera() {
    show(ViewfinderState.SEARCHING)

    tag(ScanScreenTestTags.VIEWFINDER).assertIsDisplayed()
    tag(ScanScreenTestTags.HINT).assertTextEquals(HINT)
    tag(ScanScreenTestTags.INVALID_CODE).assertDoesNotExist()
  }

  @Test
  fun found_saysTheCodeWasRead() {
    show(ViewfinderState.FOUND)

    tag(ScanScreenTestTags.HINT).assertTextEquals(CODE_READ)
  }

  @Test
  fun unknown_saysItIsNotAShifterEvent() {
    show(ViewfinderState.UNKNOWN)

    tag(ScanScreenTestTags.INVALID_CODE).assertTextEquals(NOT_SHIFTER)
    tag(ScanScreenTestTags.HINT).assertDoesNotExist()
  }

  @Test
  fun hint_isALiveRegion() {
    show(ViewfinderState.SEARCHING)

    tag(ScanScreenTestTags.HINT)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
  }

  @Test
  fun hint_staysOneNodeAcrossStates() {
    show(ViewfinderState.SEARCHING)
    val searching = tag(ScanScreenTestTags.HINT).fetchSemanticsNode().id

    state = ViewfinderState.UNKNOWN
    refusals++
    compose.waitForIdle()

    // The same node: TalkBack only reads a live region whose text changes, not a new one.
    assertEquals(searching, tag(ScanScreenTestTags.INVALID_CODE).fetchSemanticsNode().id)
  }

  @Test
  fun portrait_drawsTheFrameAsInTheFigma() {
    showOnScreen(width = 390.dp, height = 844.dp)

    tag(ScanScreenTestTags.VIEWFINDER).assertWidthIsEqualTo(265.dp).assertHeightIsEqualTo(265.dp)
  }

  @Test
  fun landscape_keepsTheFrameAndTheButtonOnScreen() {
    showOnScreen(width = 844.dp, height = 390.dp)

    val frame = tag(ScanScreenTestTags.VIEWFINDER).getUnclippedBoundsInRoot()
    val button = tag(ScanScreenTestTags.ENTER_CODE).getUnclippedBoundsInRoot()
    assertEquals(frame.right - frame.left, frame.bottom - frame.top)
    assertTrue(frame.bottom - frame.top < 265.dp)
    assertTrue(frame.bottom <= button.top)
    assertTrue(button.bottom <= 390.dp)
    tag(ScanScreenTestTags.HINT).assertIsDisplayed()
  }

  @Test
  fun changingState_endsOnTheNewHint() {
    show(ViewfinderState.SEARCHING)

    state = ViewfinderState.UNKNOWN
    refusals++
    compose.waitForIdle()
    tag(ScanScreenTestTags.INVALID_CODE).assertTextEquals(NOT_SHIFTER)

    state = ViewfinderState.FOUND
    compose.waitForIdle()
    tag(ScanScreenTestTags.INVALID_CODE).assertDoesNotExist()
    tag(ScanScreenTestTags.HINT).assertTextEquals(CODE_READ)
  }

  @Test
  fun withAnimationsOff_stillShowsEachState() {
    Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    show(ViewfinderState.SEARCHING)
    tag(ScanScreenTestTags.HINT).assertTextEquals(HINT)

    state = ViewfinderState.UNKNOWN
    refusals++
    compose.waitForIdle()
    tag(ScanScreenTestTags.INVALID_CODE).assertTextEquals(NOT_SHIFTER)

    state = ViewfinderState.FOUND
    compose.waitForIdle()
    tag(ScanScreenTestTags.HINT).assertTextEquals(CODE_READ)
  }

  private companion object {
    const val HINT = "Point your camera at the event's QR code"
    const val CODE_READ = "Code read"
    const val NOT_SHIFTER = "This QR code is not a Shifter event"
  }
}
