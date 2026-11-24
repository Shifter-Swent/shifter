// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.location

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationPermissionCardTest {
  @get:Rule val compose = createComposeRule()

  private var requests = 0
  private var appSettings = 0
  private var locationSettings = 0

  private fun setContent(
      access: LocationAccess,
      compact: Boolean = false,
      reason: LocationReason = LocationReason.CHECK_IN,
  ) {
    compose.setContent {
      ShifterTheme {
        LocationPermissionCard(
            access = access,
            onRequest = { requests++ },
            onOpenAppSettings = { appSettings++ },
            onOpenLocationSettings = { locationSettings++ },
            reason = reason,
            compact = compact,
        )
      }
    }
  }

  @Test
  fun notRequested_explainsAndAllowRequestsThePermission() {
    setContent(LocationAccess.NOT_REQUESTED)

    compose.onNodeWithText("check you in automatically", substring = true).assertIsDisplayed()
    compose.onNodeWithText("Allow location").performClick()

    assertEquals(1, requests)
  }

  @Test
  fun denied_showsAccessDeniedAndAsksAgain() {
    setContent(LocationAccess.DENIED)

    compose.onNodeWithText("Location access denied", substring = true).assertIsDisplayed()
    compose.onNodeWithText("Allow location").performClick()

    assertEquals(1, requests)
  }

  @Test
  fun approximateOnly_showsAccessDeniedAndAsksForThePreciseLocation() {
    setContent(LocationAccess.APPROXIMATE_ONLY)

    compose.onNodeWithText("approximate location", substring = true).assertIsDisplayed()
    compose.onNodeWithText("Allow location").performClick()

    assertEquals(1, requests)
  }

  @Test
  fun permanentlyDenied_showsTheSettingsLink() {
    setContent(LocationAccess.PERMANENTLY_DENIED)

    compose.onNodeWithText("Location access denied", substring = true).assertIsDisplayed()
    compose.onNodeWithTag(LocationPermissionTestTags.ACTION).assertTextEquals("Open settings")
    compose.onNodeWithTag(LocationPermissionTestTags.ACTION).performClick()

    assertEquals(1, appSettings)
    assertEquals(0, requests)
  }

  @Test
  fun locationOff_opensTheLocationSettings() {
    setContent(LocationAccess.LOCATION_OFF)

    compose.onNodeWithText("Location is turned off", substring = true).assertIsDisplayed()
    compose.onNodeWithText("Turn on location").performClick()

    assertEquals(1, locationSettings)
  }

  @Test
  fun granted_showsNothing() {
    setContent(LocationAccess.GRANTED)

    compose.onNodeWithTag(LocationPermissionTestTags.CARD).assertDoesNotExist()
  }

  @Test
  fun compact_showsTheMessageAndItsAction() {
    setContent(LocationAccess.LOCATION_OFF, compact = true)

    compose.onNodeWithText("Location is turned off", substring = true).assertIsDisplayed()
    compose.onNodeWithTag(LocationPermissionTestTags.ACTION).assertTextEquals("Turn on location")
    compose.onNodeWithTag(LocationPermissionTestTags.ACTION).performClick()

    assertEquals(1, locationSettings)
  }

  @Test
  fun compact_whenGranted_showsNothing() {
    setContent(LocationAccess.GRANTED, compact = true)

    compose.onNodeWithTag(LocationPermissionTestTags.CARD).assertDoesNotExist()
  }

  @Test
  fun mapReason_explainsEachAccessForTheMap() {
    var access by mutableStateOf(LocationAccess.NOT_REQUESTED)
    compose.setContent {
      ShifterTheme {
        LocationPermissionCard(
            access = access,
            onRequest = { requests++ },
            onOpenAppSettings = { appSettings++ },
            onOpenLocationSettings = { locationSettings++ },
            reason = LocationReason.MAP,
            compact = true,
        )
      }
    }
    val messages =
        mapOf(
            LocationAccess.NOT_REQUESTED to "Allow the location to see where you are.",
            LocationAccess.DENIED to "Location denied: the map cannot show you.",
            LocationAccess.APPROXIMATE_ONLY to "Allow the precise location to see where you are.",
            LocationAccess.PERMANENTLY_DENIED to "Location denied: allow it in the settings.",
            LocationAccess.LOCATION_OFF to "Location is off: the map cannot show you.",
        )

    for ((shown, message) in messages) {
      access = shown
      compose.onNodeWithTag(LocationPermissionTestTags.MESSAGE).assertTextEquals(message)
    }
  }

  @Test
  fun mapReason_whenGranted_showsNothing() {
    setContent(LocationAccess.GRANTED, compact = true, reason = LocationReason.MAP)

    compose.onNodeWithTag(LocationPermissionTestTags.CARD).assertDoesNotExist()
  }
}
