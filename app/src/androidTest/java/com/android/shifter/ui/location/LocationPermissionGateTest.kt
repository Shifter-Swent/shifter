// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.location

import android.Manifest
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.location.LocationStatus
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers what the gate shows for a status and what its buttons do. The system permission dialog is
 * replaced by a registry where the user refuses, and the settings screens are recorded instead of
 * opened.
 */
@RunWith(AndroidJUnit4::class)
class LocationPermissionGateTest {
  @get:Rule val compose = createComposeRule()

  private var status by mutableStateOf(LocationStatus.DENIED)
  private var refreshes = 0
  private val requested = mutableListOf<List<String>>()
  private val started = mutableListOf<Intent>()

  private val refusingRegistry =
      object : ActivityResultRegistryOwner {
        override val activityResultRegistry =
            object : ActivityResultRegistry() {
              override fun <I, O> onLaunch(
                  requestCode: Int,
                  contract: ActivityResultContract<I, O>,
                  input: I,
                  options: ActivityOptionsCompat?,
              ) {
                val permissions = (input as Array<*>).map { it as String }
                requested += permissions
                dispatchResult(requestCode, permissions.associateWith { false })
              }
            }
      }

  private fun setContent() {
    compose.setContent {
      val context = LocalContext.current
      val recording = remember {
        object : ContextWrapper(context) {
          override fun startActivity(intent: Intent) {
            started += intent
          }
        }
      }
      CompositionLocalProvider(
          LocalContext provides recording,
          LocalActivityResultRegistryOwner provides refusingRegistry,
      ) {
        ShifterTheme { LocationPermissionGate(status = status, onRefreshStatus = { refreshes++ }) }
      }
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

  @Test
  fun allow_requestsPreciseAndApproximateLocation() {
    setContent()

    compose.onNodeWithText("Allow location").performClick()

    // Android 12+ ignores a request for the precise location without the approximate one.
    assertEquals(
        listOf(
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        ),
        requested,
    )
  }

  @Test
  fun refusalWithoutRationale_refreshesAndOpensTheAppSettings() {
    setContent()
    compose.waitForIdle()
    val refreshesBefore = refreshes

    // Without a rationale after a request, the dialog no longer shows: only the settings can help.
    compose.onNodeWithText("Allow location").performClick()
    compose.onNodeWithText("Open settings").performClick()

    assertTrue(refreshes > refreshesBefore)
    val intent = started.single()
    assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
    assertEquals("package", intent.data?.scheme)
  }

  @Test
  fun turnOnLocation_opensTheLocationSettings() {
    status = LocationStatus.LOCATION_OFF
    setContent()

    compose.onNodeWithText("Turn on location").performClick()

    assertEquals(Settings.ACTION_LOCATION_SOURCE_SETTINGS, started.single().action)
  }
}
