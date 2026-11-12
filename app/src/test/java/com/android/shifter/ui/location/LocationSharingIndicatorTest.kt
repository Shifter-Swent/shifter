// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.location

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.location.LiveLocationSharingState
import com.swent.shifter.model.location.LiveLocationSharingState.NO_LOCATION_ACCESS
import com.swent.shifter.model.location.LiveLocationSharingState.OFF
import com.swent.shifter.model.location.LiveLocationSharingState.REFUSED
import com.swent.shifter.model.location.LiveLocationSharingState.SHARED
import com.swent.shifter.model.location.LiveLocationSharingState.WAITING
import com.swent.shifter.model.location.LiveLocationTarget
import com.swent.shifter.ui.components.ShifterTopBar
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The location sharing icon of the top bars and the sheet it opens (#405). */
@RunWith(AndroidJUnit4::class)
class LocationSharingIndicatorTest {

  @get:Rule val compose = createComposeRule()

  private var opened = 0
  private var settingsOpened = 0

  private fun show(states: Map<LiveLocationTarget, LiveLocationSharingState>?) {
    compose.setContent {
      ShifterTheme {
        CompositionLocalProvider(
            LocalLocationSharing provides states?.let { ui(it) },
        ) {
          ShifterTopBar(title = "City Marathon", onBack = {})
        }
      }
    }
  }

  private fun ui(states: Map<LiveLocationTarget, LiveLocationSharingState>) =
      LocationSharingUi(
          states = states,
          labels = mapOf(HYDRATION to SharingTargetLabel("City Marathon", "Hydration", "Alex")),
          onOpen = { opened++ },
          onOpenSettings = { settingsOpened++ },
      )

  @Test
  fun withoutTheSharingProvided_showsNoIcon() {
    show(null)

    compose.onNodeWithTag(LocationSharingTestTags.INDICATOR).assertDoesNotExist()
  }

  @Test
  fun withNoSessionRunning_showsNoIcon() {
    show(emptyMap())

    compose.onNodeWithTag(LocationSharingTestTags.INDICATOR).assertDoesNotExist()
  }

  @Test
  fun eachState_hasItsOwnDescription() {
    var states by mutableStateOf(mapOf(HYDRATION to SHARED))
    compose.setContent {
      ShifterTheme {
        CompositionLocalProvider(LocalLocationSharing provides ui(states)) {
          ShifterTopBar(title = "City Marathon", onBack = {})
        }
      }
    }
    mapOf(
            SHARED to "Location shared with your manager",
            WAITING to "Waiting to share your position",
            REFUSED to "Position not accepted",
            NO_LOCATION_ACCESS to "Location access is off",
            OFF to "Location sharing is off",
        )
        .forEach { (state, description) ->
          states = mapOf(HYDRATION to state)
          compose.onNodeWithContentDescription(description).assertIsDisplayed()
        }
  }

  @Test
  fun theIcon_saysWhatTappingItDoes() {
    show(mapOf(HYDRATION to SHARED))

    // The description is the state; the action is announced apart, as for the avatar.
    val onClick =
        compose
            .onNodeWithTag(LocationSharingTestTags.INDICATOR)
            .fetchSemanticsNode()
            .config[SemanticsActions.OnClick]
    assertEquals("Show details", onClick.label)
  }

  @Test
  fun theMostUrgentStateWins() {
    // One team refused is not hidden behind another whose position is shared.
    show(mapOf(HYDRATION to SHARED, FINISH to REFUSED))

    compose.onNodeWithContentDescription("Position not accepted").assertIsDisplayed()
  }

  @Test
  fun tapping_opensTheSheetWithEveryTargetAndItsState() {
    show(mapOf(HYDRATION to SHARED, FINISH to WAITING))

    compose.onNodeWithTag(LocationSharingTestTags.INDICATOR).performClick()

    assertEquals(1, opened)
    compose.onNodeWithText("Waiting to share your position").assertIsDisplayed()
    compose.onNodeWithText("Your teams right now").assertIsDisplayed()
    compose.onNodeWithText("Hydration · Manager: Alex").assertIsDisplayed()
    compose.onNode(hasTestTag(LocationSharingTestTags.targetRow(HYDRATION))).assertExists()
    compose.onNodeWithText("Shared").assertIsDisplayed()
    // A target whose names could not be read is still listed, with its state.
    compose.onNodeWithText("Event and team not available").assertIsDisplayed()
    compose.onNodeWithText("Waiting").assertIsDisplayed()
    compose.onNodeWithText("You can turn sharing off in Settings.").assertIsDisplayed()
  }

  @Test
  fun withoutLocationAccess_listsTheTargetsAsNotShared() {
    show(mapOf(HYDRATION to NO_LOCATION_ACCESS))

    compose.onNodeWithTag(LocationSharingTestTags.INDICATOR).performClick()

    compose.onNodeWithText("Location access is off").assertIsDisplayed()
    compose.onNodeWithText("Not shared").assertIsDisplayed()
  }

  @Test
  fun whenSharingIsOff_saysSoWithoutListingTheTeams() {
    show(mapOf(HYDRATION to OFF))

    compose.onNodeWithTag(LocationSharingTestTags.INDICATOR).performClick()

    compose.onNodeWithText("Location sharing is off").assertIsDisplayed()
    compose.onNodeWithText("Your teams right now").assertDoesNotExist()
    compose.onNodeWithText("You can turn sharing on in Settings.").assertIsDisplayed()
  }

  @Test
  fun openSettings_closesTheSheetAndAsksForSettings() {
    show(mapOf(HYDRATION to SHARED))
    compose.onNodeWithTag(LocationSharingTestTags.INDICATOR).performClick()

    compose.onNodeWithTag(LocationSharingTestTags.OPEN_SETTINGS).performClick()

    assertEquals(1, settingsOpened)
    compose.onNodeWithTag(LocationSharingTestTags.SHEET).assertDoesNotExist()
  }

  @Test
  fun whenTheLastSessionStops_theSheetClosesAndDoesNotReopen() {
    var states by mutableStateOf(mapOf(HYDRATION to SHARED))
    compose.setContent {
      ShifterTheme {
        CompositionLocalProvider(LocalLocationSharing provides ui(states)) {
          ShifterTopBar(title = "City Marathon", onBack = {})
        }
      }
    }
    compose.onNodeWithTag(LocationSharingTestTags.INDICATOR).performClick()
    compose.onNodeWithTag(LocationSharingTestTags.SHEET).assertExists()

    states = emptyMap()
    compose.waitForIdle()
    compose.onNodeWithTag(LocationSharingTestTags.SHEET).assertDoesNotExist()

    // The next event's session shows the icon again, but not the sheet nobody opened.
    states = mapOf(FINISH to SHARED)
    compose.onNodeWithTag(LocationSharingTestTags.INDICATOR).assertIsDisplayed()
    compose.onNodeWithTag(LocationSharingTestTags.SHEET).assertDoesNotExist()
  }

  private companion object {
    /** The event's period, which the targets carry and the indicator never reads. */
    val START: Instant = Instant.parse("2026-06-14T09:00:00Z")
    val END: Instant = START.plusSeconds(3600)
    val HYDRATION = LiveLocationTarget("marathon", "hydration", START, END)
    val FINISH = LiveLocationTarget("marathon", "finish", START, END)
  }
}
