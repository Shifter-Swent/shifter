// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.overview

import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.checkin.CheckInEligibility
import com.swent.shifter.model.checkin.FakeCheckInRepository
import com.swent.shifter.model.location.FakeLocationProvider
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionLocation
import com.swent.shifter.model.time.FakeMinuteClock
import com.swent.shifter.ui.checkin.AutoCheckInViewModel
import com.swent.shifter.ui.planning.PlanningViewModel
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * [VolunteerOverview] collects its states with its lifecycle, so the clock of the now card and the
 * positions of the automatic check-in stop when the screen is in the background.
 */
@RunWith(AndroidJUnit4::class)
class VolunteerOverviewLifecycleTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val now = Instant.parse("2027-06-20T10:00:00Z")
  private val clock = FakeMinuteClock(now)
  private val missions = FakeMissionRepository()
  private val checkIns = FakeCheckInRepository(currentUserId = USER)
  private val location = FakeLocationProvider()
  private val owner = TestLifecycleOwner(Lifecycle.State.RESUMED)

  @Test
  fun background_releasesTheClockAndTheGps() {
    // A mission whose check-in is open, so the automatic check-in reads positions.
    runBlocking {
      missions.createMission(
          Mission(
              eventId = EVENT,
              title = "Bar",
              description = "",
              volunteersNeeded = 1,
              startAt = now,
              endAt = now.plus(Duration.ofHours(1)),
              location = MissionLocation("Bar", GeoPosition(46.52, 6.57)),
              assigneeIds = listOf(USER),
              createdAt = now,
          )
      )
    }
    val planning = PlanningViewModel(EVENT, missions, checkIns, clock) { USER }
    val autoCheckIn =
        AutoCheckInViewModel(
            missions,
            checkIns,
            location,
            { USER },
            { _, _, _ -> CheckInEligibility.Ineligible(CheckInEligibility.Reason.TOO_FAR) },
            { clock.now() },
        )
    composeTestRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides owner) {
        ShifterTheme {
          VolunteerOverview(
              planning = planning,
              autoCheckIn = autoCheckIn,
              clock = clock,
              onOpenOnMap = {},
              onContact = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    assertTrue(clock.subscriptionCount.value > 0)
    assertTrue(location.activeCollectors > 0)

    composeTestRule.runOnIdle { owner.currentState = Lifecycle.State.CREATED }
    composeTestRule.waitForIdle()

    assertEquals(0, clock.subscriptionCount.value)
    // The positions are kept across a configuration change, and stop STOP_TIMEOUT_MILLIS later.
    assertTrue(location.activeCollectors > 0)
    shadowOf(Looper.getMainLooper())
        .idleFor(Duration.ofMillis(AutoCheckInViewModel.STOP_TIMEOUT_MILLIS + 1))
    assertEquals(0, location.activeCollectors)
  }

  private companion object {
    const val USER = "volunteer-1"
    const val EVENT = "event-1"
  }
}
