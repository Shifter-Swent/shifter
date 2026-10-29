// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.location

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

  private fun setContent(access: LocationAccess) {
    compose.setContent {
      ShifterTheme {
        LocationPermissionCard(
            access = access,
            onRequest = { requests++ },
            onOpenAppSettings = { appSettings++ },
            onOpenLocationSettings = { locationSettings++ },
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
}
