// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.mission

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
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * When [AddMissionScreen] reports a saved mission: only once it is resumed, since going back is
 * ignored before and the form would stay open with nothing left to save (#467).
 */
@RunWith(AndroidJUnit4::class)
class AddMissionScreenLifecycleTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val zone = ZoneId.of("Europe/Zurich")
  private val eventDay = LocalDate.of(2027, 6, 20)
  private val event =
      Event(
          id = "event-1",
          organizerId = "organizer-1",
          title = "Local Food Drive",
          description = "Collect food",
          type = EventType.FOOD,
          startAt = eventDay.atTime(10, 0).atZone(zone).toInstant(),
          endAt = eventDay.atTime(18, 0).atZone(zone).toInstant(),
          location = EventLocation(address = "Grand Place, Lille"),
          createdAt = Instant.parse("2027-06-01T10:00:00Z"),
      )
  private val viewModel =
      AddMissionViewModel(
          eventId = event.id,
          missionRepository = FakeMissionRepository(),
          eventRepository = FakeEventRepository().apply { events += event },
          teamRepository = FakeTeamRepository(),
          assigneeCandidates = { _, _ -> emptyList() },
          currentUserId = { event.organizerId },
          clock = Clock.fixed(Instant.parse("2027-06-01T10:00:00Z"), zone),
      )

  private val saved = mutableListOf<Mission>()
  private val screen = ScreenLifecycle()
  private var shown by mutableStateOf(true)

  @Test
  fun save_beforeResumed_reportsOnceResumed() {
    screen.lifecycle.currentState = Lifecycle.State.STARTED
    showScreen()

    saveAMission()

    // Going back would be ignored now, so the save waits, still to be reported.
    assertEquals(emptyList<Mission>(), saved)
    assertFalse(viewModel.uiState.value.savedMissionHandled)

    composeTestRule.runOnIdle { screen.lifecycle.currentState = Lifecycle.State.RESUMED }
    composeTestRule.waitForIdle()

    assertEquals(listOf(viewModel.uiState.value.savedMission), saved)
    assertTrue(viewModel.uiState.value.savedMissionHandled)
  }

  @Test
  fun save_whileResumed_reportsAtOnce() {
    showScreen()

    saveAMission()

    assertEquals(1, saved.size)
    assertEquals("Sort the donations", saved.single().title)
    assertTrue(viewModel.uiState.value.savedMissionHandled)
  }

  @Test
  fun save_screenLeftBeforeResumed_reportsNothing() {
    screen.lifecycle.currentState = Lifecycle.State.STARTED
    showScreen()
    saveAMission()

    // The form is left, e.g. by going back, before it is shown again.
    composeTestRule.runOnIdle { shown = false }
    composeTestRule.runOnIdle { screen.lifecycle.currentState = Lifecycle.State.RESUMED }
    composeTestRule.waitForIdle()

    assertEquals(emptyList<Mission>(), saved)
    assertFalse(viewModel.uiState.value.savedMissionHandled)
  }

  /** The screen with [viewModel], while [shown], with the lifecycle of [screen]. */
  private fun showScreen() {
    composeTestRule.setContent {
      ShifterTheme {
        CompositionLocalProvider(LocalLifecycleOwner provides screen) {
          if (shown) AddMissionScreen(viewModel, onMissionSaved = { saved += it }, onBack = {})
        }
      }
    }
    composeTestRule.waitForIdle()
  }

  /** Fills a valid mission within the event's hours and saves it. */
  private fun saveAMission() {
    composeTestRule.runOnIdle {
      viewModel.onTitleChange("Sort the donations")
      viewModel.onDescriptionChange("Check the dates")
      viewModel.onStartTimeChange(LocalTime.of(11, 0))
      viewModel.onEndTimeChange(LocalTime.of(12, 0))
      viewModel.save()
    }
    composeTestRule.waitForIdle()
    // Saved, whether reported yet or not.
    assertTrue(viewModel.uiState.value.savedMission != null)
  }

  /** A lifecycle the tests move by hand, without checking it is on the main thread. */
  private class ScreenLifecycle : LifecycleOwner {
    override val lifecycle: LifecycleRegistry =
        LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
  }
}
