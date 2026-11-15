// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.time

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.time.FakeMinuteClock
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** [rememberNow]: follows the clock while the screen is started, and lets go of it otherwise. */
@RunWith(AndroidJUnit4::class)
class RememberNowTest {

  @get:Rule val compose = createComposeRule()

  private val start = Instant.parse("2027-06-20T08:00:30Z")
  private val clock = FakeMinuteClock(start)
  private val screen = ScreenLifecycle()

  private fun show() {
    compose.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides screen) {
        Text(rememberNow(clock).toString(), Modifier.testTag(NOW))
      }
    }
  }

  @Test
  fun firstFrame_showsTheTimeAtOnce() {
    show()

    compose.onNodeWithTag(NOW).assertTextEquals(start.toString())
  }

  @Test
  fun clockMovingOn_movesTheScreenOn() {
    show()

    clock.advance(Duration.ofMinutes(1))

    compose.onNodeWithTag(NOW).assertTextEquals(start.plusSeconds(60).toString())
  }

  @Test
  fun screenStopped_letsGoOfTheClock() {
    show()
    compose.waitForIdle()
    assertEquals(1, clock.subscriptionCount.value)

    compose.runOnIdle { screen.lifecycle.currentState = Lifecycle.State.CREATED }
    compose.waitForIdle()

    assertEquals(0, clock.subscriptionCount.value)
  }

  @Test
  fun screenStartedAgain_showsTheTimeOfTheReturn() {
    show()
    compose.runOnIdle { screen.lifecycle.currentState = Lifecycle.State.CREATED }
    compose.waitForIdle()

    clock.advance(Duration.ofHours(2))
    compose.runOnIdle { screen.lifecycle.currentState = Lifecycle.State.RESUMED }

    compose.onNodeWithTag(NOW).assertTextEquals(start.plus(Duration.ofHours(2)).toString())
    assertEquals(1, clock.subscriptionCount.value)
  }

  private class ScreenLifecycle : LifecycleOwner {
    override val lifecycle: LifecycleRegistry =
        LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
  }

  private companion object {
    const val NOW = "now"
  }
}
