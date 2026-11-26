// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.schedule

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FakeEventRepository
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.FakeMembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.time.FakeMinuteClock
import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.User
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * When [AutoScheduleScreen] reports the proposal saved: only once it is resumed, since going back
 * is ignored before and the organizer would stay on a proposal already saved, as on the mission
 * form (#467).
 */
@RunWith(AndroidJUnit4::class)
class AutoScheduleScreenLifecycleTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val eventId = "event-1"
  private val start = Instant.parse("2027-06-20T08:00:00Z")
  private val missions = FakeMissionRepository()
  private val requests = FakeMembershipRequestRepository()
  // Built once the mission and the request exist, since it reads them as soon as it is made.
  private val viewModel by lazy {
    AutoScheduleViewModel(
        eventId,
        missions,
        FakeTeamRepository(listOf(team())),
        requests,
        FakeUserRepository(listOf(User("ben", "Ben", "ben@x.ch"))),
        FakeEventRepository().apply { events += event() },
        { _, _ -> emptyList() },
    ) {
      start
    }
  }

  /** How many people each report of the save said were assigned. */
  private val saved = mutableListOf<Int>()
  private val screen = ScreenLifecycle()
  private var shown by mutableStateOf(true)

  @Before
  fun setUp() {
    // "Bar" needs one person, and Ben, free all day, is proposed for it.
    runBlocking { missions.createMission(mission()) }
    requests.add(eventId, request())
  }

  @Test
  fun save_beforeResumed_reportsOnceResumed() {
    screen.lifecycle.currentState = Lifecycle.State.STARTED
    showScreen()

    accept()

    // Going back would be ignored now, so the save waits, still to be reported.
    assertEquals(emptyList<Int>(), saved)

    composeTestRule.runOnIdle { screen.lifecycle.currentState = Lifecycle.State.RESUMED }
    composeTestRule.waitForIdle()

    assertEquals(listOf(1), saved)
  }

  @Test
  fun save_whileResumed_reportsAtOnce() {
    showScreen()

    accept()

    assertEquals(listOf(1), saved)
  }

  @Test
  fun save_screenLeftBeforeResumed_reportsNothing() {
    screen.lifecycle.currentState = Lifecycle.State.STARTED
    showScreen()
    accept()

    // The screen is left, e.g. by going back, before it is shown again.
    composeTestRule.runOnIdle { shown = false }
    composeTestRule.runOnIdle { screen.lifecycle.currentState = Lifecycle.State.RESUMED }
    composeTestRule.waitForIdle()

    assertEquals(emptyList<Int>(), saved)
  }

  /** The screen with [viewModel], while [shown], with the lifecycle of [screen]. */
  private fun showScreen() {
    composeTestRule.setContent {
      ShifterTheme {
        CompositionLocalProvider(LocalLifecycleOwner provides screen) {
          if (shown) {
            AutoScheduleScreen(
                viewModel,
                FakeMinuteClock(start),
                onCancel = {},
                onSaved = { saved += it },
            )
          }
        }
      }
    }
    composeTestRule.waitUntil { viewModel.uiState.value.canAccept }
  }

  /** Accepts the proposal, saved whether reported yet or not. */
  private fun accept() {
    composeTestRule.runOnIdle { viewModel.accept() }
    composeTestRule.waitUntil { viewModel.uiState.value.saved }
    composeTestRule.waitForIdle()
    assertTrue(viewModel.uiState.value.saved)
  }

  private fun mission() =
      Mission(
          eventId = eventId,
          title = "Bar",
          description = "",
          teamId = "bar",
          volunteersNeeded = 1,
          startAt = start.plus(Duration.ofHours(2)),
          endAt = start.plus(Duration.ofHours(3)),
          createdAt = start,
      )

  private fun request() =
      MembershipRequest(
          id = "ben",
          userId = "ben",
          teamId = "bar",
          availability = listOf(AvailabilitySlot(start, start.plus(Duration.ofHours(12)))),
          status = MembershipRequestStatus.ACCEPTED,
          createdAt = start,
      )

  private fun team() =
      Team(
          id = "bar",
          eventId = eventId,
          name = "Bar",
          description = "",
          icon = "truck",
          memberIds = listOf("ben"),
          createdAt = start,
      )

  private fun event() =
      Event(
          id = eventId,
          organizerId = "organizer-1",
          title = "Paléo Festival",
          description = "",
          type = EventType.MUSIC,
          startAt = start,
          endAt = start.plus(Duration.ofDays(1)),
          location = EventLocation("Nyon"),
          joinCode = "ABC234",
          createdAt = start,
      )

  /** A lifecycle the tests move by hand, without checking it is on the main thread. */
  private class ScreenLifecycle : LifecycleOwner {
    override val lifecycle: LifecycleRegistry =
        LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
  }
}
