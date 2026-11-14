// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.nowcard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The card with the system animations off, as the test rule's effects see them. */
@RunWith(AndroidJUnit4::class)
class NowCardContainerAnimationsOffTest {

  @get:Rule
  val composeTestRule =
      createComposeRule(
          effectContext =
              object : MotionDurationScale {
                override val scaleFactor = 0f
              }
      )

  @Test
  fun toneChange_withAnimationsOff_showsTheNewContentAtOnce() {
    var shown by mutableStateOf(Shown(body = "Old body"))
    composeTestRule.setCard { shown }
    composeTestRule.mainClock.autoAdvance = false

    shown = Shown(tone = NowTone.ALERT, body = "New body")
    Snapshot.sendApplyNotifications()
    composeTestRule.mainClock.advanceTimeBy(50)

    composeTestRule.onNodeWithText("Old body").assertDoesNotExist()
    composeTestRule.onNodeWithText("New body").assertExists()
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun toneChange_withAnimationsOff_doesNotFlash() {
    var shown by mutableStateOf(Shown(tone = NowTone.CALM))
    composeTestRule.setCard { shown }

    composeTestRule.changeTone(Shown(tone = NowTone.ALERT)) { shown = it }
    val first = composeTestRule.fieldBrightness()
    composeTestRule.mainClock.autoAdvance = true
    composeTestRule.waitForIdle()

    assertEquals(composeTestRule.fieldBrightness().toDouble(), first.toDouble(), 3.0)
  }
}
