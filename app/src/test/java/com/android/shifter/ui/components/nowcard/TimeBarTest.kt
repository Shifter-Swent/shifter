// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.nowcard

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimeBarTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun show(content: @Composable () -> Unit) {
    composeTestRule.setContent { ShifterTheme { content() } }
  }

  private fun showBar(now: Instant, from: Instant = START, to: Instant = END) = show {
    TimeBar(from = from, to = to, now = now, color = Color.Green, zone = ZoneOffset.UTC)
  }

  private fun showCountdown(now: Instant) = show {
    CountdownBar(
        start = START,
        now = now,
        window = WINDOW,
        color = Color.Blue,
        zone = ZoneOffset.UTC,
    )
  }

  private val bar: SemanticsNodeInteraction
    get() = composeTestRule.onNodeWithTag(NowCardTestTags.TIME_BAR)

  private fun SemanticsNodeInteraction.assertFilled(progress: Float) =
      assertRangeInfoEquals(ProgressBarRangeInfo(progress, 0f..1f))

  @Test
  fun timeBar_beforeItsStart_isEmpty() {
    showBar(now = START.minusSeconds(600))

    bar.assertFilled(0f)
    composeTestRule.onNodeWithText("14:00", substring = true).assertExists()
  }

  @Test
  fun timeBar_afterItsEnd_isFull() {
    showBar(now = END.plusSeconds(60))

    bar.assertFilled(1f)
    bar.assert(hasText("15:30"))
  }

  @Test
  fun timeBar_halfway_isHalfFull() {
    showBar(now = START.plus(Duration.ofMinutes(45)))

    bar.assertFilled(0.5f)
  }

  @Test
  fun timeBar_inItsLastHour_showsTheTimeLeft() {
    showBar(now = END.minus(Duration.ofMinutes(10)))

    bar.assert(hasText("10 min left · 15:30"))
  }

  @Test
  fun timeBar_withAnHourLeft_showsTheEnd() {
    showBar(now = END.minus(Duration.ofMinutes(60)))

    bar.assert(hasText("15:30"))
    composeTestRule.onNodeWithText("left", substring = true).assertDoesNotExist()
  }

  @Test
  fun timeBar_inItsLastSeconds_showsOneMinuteLeft() {
    showBar(now = END.minusSeconds(20))

    bar.assert(hasText("1 min left · 15:30"))
  }

  @Test
  fun timeBar_withJustUnderAnHourLeft_showsTheEnd() {
    showBar(now = END.minus(Duration.ofMinutes(59)).minusSeconds(30))

    bar.assert(hasText("15:30"))
    composeTestRule.onNodeWithText("left", substring = true).assertDoesNotExist()
  }

  @Test
  fun timeBar_beforeItsShortMissionStarts_showsTheEnd() {
    val end = START.plus(Duration.ofMinutes(30))
    showBar(now = START.minus(Duration.ofMinutes(10)), to = end)

    bar.assert(hasText("14:30"))
    composeTestRule.onNodeWithText("left", substring = true).assertDoesNotExist()
  }

  @Test
  fun timeBar_withoutDuration_isFullAfterItsEnd() {
    showBar(now = START.plusSeconds(1), from = START, to = START)

    bar.assertFilled(1f)
  }

  @Test
  fun timeBar_withoutDuration_isEmptyBeforeItsEnd() {
    showBar(now = START.minusSeconds(1), from = START, to = START)

    bar.assertFilled(0f)
  }

  @Test
  fun timeBar_withTitle_showsIt() {
    show { TimeBar(START, END, START, Color.Green, ZoneOffset.UTC, title = "Food sorting") }

    bar.assert(hasText("Food sorting"))
  }

  @Test
  fun timeBar_withSeveral_isFoundByTitle() {
    show {
      Column {
        TimeBar(START, END, START, Color.Green, ZoneOffset.UTC, title = "Food sorting")
        TimeBar(START, END, END, Color.Green, ZoneOffset.UTC, title = "Bar service")
      }
    }

    composeTestRule
        .onNode(hasTestTag(NowCardTestTags.TIME_BAR) and hasText("Bar service"))
        .assertFilled(1f)
  }

  @Test
  fun countdownBar_showsNowAndTheStart() {
    showCountdown(now = START.minus(Duration.ofMinutes(20)))

    bar.assert(hasText("Now"))
    bar.assert(hasText("Starts 14:00"))
  }

  @Test
  fun countdownBar_halfwayThroughTheWindow_isHalfFull() {
    showCountdown(now = START.minus(WINDOW.dividedBy(2)))

    bar.assertFilled(0.5f)
  }

  @Test
  fun countdownBar_beforeTheWindow_isEmpty() {
    showCountdown(now = START.minus(WINDOW).minusSeconds(60))

    bar.assertFilled(0f)
  }

  @Test
  fun countdownBar_afterTheStart_isFull() {
    showCountdown(now = START.plusSeconds(60))

    bar.assertFilled(1f)
  }

  private companion object {
    val START: Instant = Instant.parse("2026-11-14T14:00:00Z")
    val END: Instant = Instant.parse("2026-11-14T15:30:00Z")
    val WINDOW: Duration = Duration.ofMinutes(45)
  }
}
