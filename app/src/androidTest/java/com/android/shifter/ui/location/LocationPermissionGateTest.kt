// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.location

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.location.LocationStatus
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers what the gate shows for a status. The permission dialog itself is the system's, so asking
 * is checked by hand on the emulator.
 */
@RunWith(AndroidJUnit4::class)
class LocationPermissionGateTest {
  @get:Rule val compose = createComposeRule()

  private var status by mutableStateOf(LocationStatus.DENIED)
  private var refreshes = 0

  private fun setContent() {
    compose.setContent {
      ShifterTheme { LocationPermissionGate(status = status, onRefreshStatus = { refreshes++ }) }
    }
  }

  @Test
  fun denied_beforeTheFirstRequest_explainsAndOffersToAllow() {
    setContent()

    compose.onNodeWithText("check you in automatically", substring = true).assertIsDisplayed()
    compose.onNodeWithText("Allow location").assertIsDisplayed()
  }

  @Test
  fun locationOff_offersToTurnItOn() {
    status = LocationStatus.LOCATION_OFF
    setContent()

    compose.onNodeWithText("Turn on location").assertIsDisplayed()
  }

  @Test
  fun granted_showsNothing() {
    status = LocationStatus.GRANTED
    setContent()

    compose.onNodeWithTag(LocationPermissionTestTags.CARD).assertDoesNotExist()
  }

  @Test
  fun statusBecomesGranted_hidesTheCard() {
    setContent()
    compose.onNodeWithTag(LocationPermissionTestTags.CARD).assertIsDisplayed()

    status = LocationStatus.GRANTED

    compose.onNodeWithTag(LocationPermissionTestTags.CARD).assertDoesNotExist()
  }

  @Test
  fun resume_refreshesTheStatus() {
    setContent()
    compose.waitForIdle()

    assertTrue(refreshes >= 1)
  }
}
