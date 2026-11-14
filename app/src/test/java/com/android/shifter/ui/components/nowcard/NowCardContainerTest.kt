// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.nowcard

import android.graphics.Color as AndroidColor
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** What the card under test shows; changed to drive its transitions. */
internal data class Shown(
    val tone: NowTone = NowTone.SOON,
    val status: String = "Starting soon",
    val headline: String = "In 20 min",
    val body: String = "Food sorting",
    val next: String? = null,
)

/** Shows the card for [shown], its content and strip drawing the state they are given. */
internal fun ComposeContentTestRule.setCard(shown: () -> Shown) {
  setContent {
    val card = shown()
    ShifterTheme {
      NowCardContainer(
          tone = card.tone,
          status = card.status,
          headline = card.headline,
          state = card,
          strip = { it.next?.let { next -> Text(next) } },
      ) {
        Text(it.body)
      }
    }
  }
}

/**
 * How bright the card's field is right of its text, where only the field and the flash are drawn:
 * the sum of the pixel's channels, from 0 to 765. Needs the native graphics to draw.
 */
internal fun ComposeContentTestRule.fieldBrightness(): Int {
  val card = onNodeWithTag(NowCardTestTags.CARD).captureToImage().asAndroidBitmap()
  val pixel = card.getPixel(card.width - 30, card.height / 2)
  return AndroidColor.red(pixel) + AndroidColor.green(pixel) + AndroidColor.blue(pixel)
}

/** Changes the card to [next] and draws its first frames, the flash just started. */
internal fun ComposeContentTestRule.changeTone(next: Shown, show: (Shown) -> Unit) {
  mainClock.autoAdvance = false
  show(next)
  Snapshot.sendApplyNotifications()
  mainClock.advanceTimeBy(32)
}

@RunWith(AndroidJUnit4::class)
class NowCardContainerTest {

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun toneChange_flashesTheCardInTheNewTone() {
    var shown by mutableStateOf(Shown(tone = NowTone.CALM))
    composeTestRule.setCard { shown }

    composeTestRule.changeTone(Shown(tone = NowTone.ALERT)) { shown = it }
    val flashing = composeTestRule.fieldBrightness()
    composeTestRule.mainClock.autoAdvance = true
    composeTestRule.waitForIdle()
    val settled = composeTestRule.fieldBrightness()

    assertTrue("flashing $flashing, settled $settled", flashing > settled + 40)
  }

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun nowCardContainer_showsTheStatusHeadlineContentAndStrip() {
    var shown by mutableStateOf(Shown(next = "Then, in 30 min"))
    composeTestRule.setCard { shown }

    NowTone.entries.forEach { tone ->
      shown =
          Shown(tone = tone, status = "Status $tone", headline = "Headline $tone", next = "Next")
      composeTestRule.waitForIdle()

      composeTestRule.onNodeWithTag(NowCardTestTags.CARD).assertIsDisplayed()
      composeTestRule.onNodeWithTag(NowCardTestTags.STATUS).assertTextEquals("Status $tone")
      composeTestRule
          .onNodeWithTag(NowCardTestTags.HEADLINE)
          .assertTextEquals("Headline $tone")
          .assert(isHeading())
      composeTestRule.onNodeWithText("Food sorting").assertIsDisplayed()
      composeTestRule.onNodeWithText("Next").assertIsDisplayed()
    }
  }

  @Test
  fun nowCardContainer_withoutContent_showsTheStatusAndTheHeadline() {
    composeTestRule.setContent {
      ShifterTheme {
        NowCardContainer(tone = NowTone.CALM, status = "Today", headline = "Done", state = Unit)
      }
    }

    composeTestRule.onNodeWithTag(NowCardTestTags.HEADLINE).assertTextEquals("Done")
    composeTestRule.onNodeWithTag(NowCardTestTags.STATUS).assertTextEquals("Today")
  }

  @Test
  fun toneChange_slidesTheOldContentOut() {
    var shown by mutableStateOf(Shown(body = "Old body"))
    composeTestRule.setCard { shown }
    composeTestRule.mainClock.autoAdvance = false

    shown = Shown(tone = NowTone.ALERT, headline = "Started 6 min ago", body = "New body")
    Snapshot.sendApplyNotifications()
    composeTestRule.mainClock.advanceTimeBy(100)

    composeTestRule.onNodeWithText("Old body").assertExists()
    composeTestRule.onNodeWithText("New body").assertExists()

    composeTestRule.mainClock.autoAdvance = true
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Old body").assertDoesNotExist()
    composeTestRule.onNodeWithText("New body").assertIsDisplayed()
  }

  @Test
  fun toneChange_slidesUpWhenMoreUrgentAndDownWhenLess() {
    var shown by mutableStateOf(Shown(tone = NowTone.SOON, body = "Soon body"))
    composeTestRule.setCard { shown }
    val restingTop = composeTestRule.onNodeWithText("Soon body").getUnclippedBoundsInRoot().top

    composeTestRule.mainClock.autoAdvance = false
    shown = Shown(tone = NowTone.ALERT, body = "Alert body")
    Snapshot.sendApplyNotifications()
    composeTestRule.mainClock.advanceTimeBy(100)
    val risingTop = composeTestRule.onNodeWithText("Alert body").getUnclippedBoundsInRoot().top
    assertTrue("more urgent content comes from below", risingTop > restingTop)

    composeTestRule.mainClock.autoAdvance = true
    composeTestRule.waitForIdle()
    composeTestRule.mainClock.autoAdvance = false
    shown = Shown(tone = NowTone.CALM, body = "Calm body")
    Snapshot.sendApplyNotifications()
    composeTestRule.mainClock.advanceTimeBy(100)
    val fallingTop = composeTestRule.onNodeWithText("Calm body").getUnclippedBoundsInRoot().top
    assertTrue("less urgent content comes from above", fallingTop < restingTop)
  }

  @Test
  fun headlineChange_withSameTone_updatesInPlace() {
    var shown by mutableStateOf(Shown(tone = NowTone.LIVE, headline = "50 min left"))
    composeTestRule.setCard { shown }
    composeTestRule.mainClock.autoAdvance = false

    shown = shown.copy(headline = "49 min left")
    Snapshot.sendApplyNotifications()
    composeTestRule.mainClock.advanceTimeByFrame()

    composeTestRule.onAllNodesWithTag(NowCardTestTags.HEADLINE).assertCountEquals(1)
    composeTestRule.onNodeWithTag(NowCardTestTags.HEADLINE).assertTextEquals("49 min left")
  }

  @Test
  fun toneChange_manyTimesQuickly_keepsTheLastState() {
    var shown by mutableStateOf(Shown())
    composeTestRule.setCard { shown }
    composeTestRule.mainClock.autoAdvance = false

    listOf(NowTone.ALERT, NowTone.LIVE, NowTone.SOON, NowTone.CALM).forEach { tone ->
      shown =
          Shown(tone = tone, status = "Status $tone", headline = "Headline $tone", body = "$tone")
      Snapshot.sendApplyNotifications()
      composeTestRule.mainClock.advanceTimeBy(100)
    }
    composeTestRule.mainClock.autoAdvance = true
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(NowCardTestTags.STATUS).assertTextEquals("Status CALM")
    composeTestRule.onAllNodesWithTag(NowCardTestTags.HEADLINE).assertCountEquals(1)
    composeTestRule.onNodeWithTag(NowCardTestTags.HEADLINE).assertTextEquals("Headline CALM")
    composeTestRule.onNodeWithText("CALM").assertIsDisplayed()
  }
}
