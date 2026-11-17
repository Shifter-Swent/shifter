// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.planning

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.checkin.FakeCheckInRepository
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.time.FakeMinuteClock
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [PlanningScreen] collects with its lifecycle, so the clock of the now state stops when the screen
 * is in the background.
 */
@RunWith(AndroidJUnit4::class)
class PlanningScreenLifecycleTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val clock = FakeMinuteClock()
  private val owner = TestLifecycleOwner(Lifecycle.State.RESUMED)

  @Test
  fun background_releasesTheClock() {
    val viewModel =
        PlanningViewModel(
            "event-1",
            FakeMissionRepository(),
            FakeCheckInRepository(currentUserId = USER),
            clock,
        ) {
          USER
        }
    composeTestRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides owner) {
        ShifterTheme { PlanningScreen(viewModel) }
      }
    }
    composeTestRule.waitForIdle()
    assertTrue(clock.subscriptionCount.value > 0)

    composeTestRule.runOnIdle { owner.currentState = Lifecycle.State.CREATED }
    composeTestRule.waitForIdle()

    assertEquals(0, clock.subscriptionCount.value)
  }

  private companion object {
    const val USER = "volunteer-1"
  }
}
