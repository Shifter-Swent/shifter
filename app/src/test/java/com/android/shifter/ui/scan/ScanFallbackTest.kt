// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.scan

import android.Manifest
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.scan.FakeQrScanner
import com.swent.shifter.model.scan.ScanError
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the fallback and the gate that shows it. As in `LocationPermissionGateTest`, the system
 * dialog is replaced by a registry where the user refuses, the settings are recorded instead of
 * opened, and the granted permissions are faked.
 */
@RunWith(AndroidJUnit4::class)
class ScanFallbackTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val scanner = FakeQrScanner()
  private val requested = mutableListOf<String>()
  private val started = mutableListOf<Intent>()
  private val grantedPermissions = mutableSetOf<String>()
  private var enterCodeClicks = 0
  private var allowClicks = 0

  /** What the gate last told its overlay, null before it drew it. */
  private var overlayCameraShown: Boolean? = null

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
                requested += input as String
                dispatchResult(requestCode, false)
              }
            }
      }

  private fun setFallback(reason: ScanFallbackReason) {
    compose.setContent {
      ShifterTheme {
        ScanFallback(
            reason = reason,
            onEnterCode = { enterCodeClicks++ },
            onAllowCamera = { allowClicks++ },
        )
      }
    }
  }

  private fun setGate() {
    compose.setContent {
      val context = LocalContext.current
      val recording = remember {
        object : ContextWrapper(context) {
          override fun startActivity(intent: Intent) {
            started += intent
          }

          override fun checkPermission(permission: String, pid: Int, uid: Int) =
              if (permission in grantedPermissions) PackageManager.PERMISSION_GRANTED
              else PackageManager.PERMISSION_DENIED
        }
      }
      CompositionLocalProvider(
          LocalContext provides recording,
          LocalActivityResultRegistryOwner provides refusingRegistry,
      ) {
        ShifterTheme {
          CameraGate(
              error = scanner.error.collectAsState().value,
              onEnterCode = { enterCodeClicks++ },
              overlay = { overlayCameraShown = it },
          ) {
            // The fake's preview is an empty box: give it a size, or it never counts as displayed.
            scanner.Preview(Modifier.fillMaxSize().testTag(CAMERA))
          }
        }
      }
    }
  }

  private fun tag(tag: String) = compose.onNodeWithTag(tag)

  @Test
  fun permissionRefused_showsTheFallbackWithBothButtons() {
    setFallback(ScanFallbackReason.PERMISSION_REFUSED)

    compose.onNodeWithText("Shifter can't use the camera").assertIsDisplayed()
    tag(ScanScreenTestTags.FALLBACK_ENTER_CODE).assertIsDisplayed()
    tag(ScanScreenTestTags.ALLOW_CAMERA).performClick()
    assertEquals(1, allowClicks)
  }

  @Test
  fun cameraError_showsTheFallbackWithoutAllowTheCamera() {
    setFallback(ScanFallbackReason.CAMERA_ERROR)

    compose.onNodeWithText("The camera isn't working").assertIsDisplayed()
    tag(ScanScreenTestTags.FALLBACK_ENTER_CODE).assertIsDisplayed()
    tag(ScanScreenTestTags.ALLOW_CAMERA).assertDoesNotExist()
  }

  @Test
  fun enterTheCode_goesBackToTheTypedCode() {
    setFallback(ScanFallbackReason.CAMERA_ERROR)

    tag(ScanScreenTestTags.FALLBACK_ENTER_CODE).performClick()

    assertEquals(1, enterCodeClicks)
  }

  @Test
  fun open_withoutPermission_asksForItThenShowsTheFallback() {
    setGate()

    compose.waitForIdle()
    assertEquals(listOf(Manifest.permission.CAMERA), requested)
    tag(ScanScreenTestTags.ALLOW_CAMERA).assertIsDisplayed()
    tag(CAMERA).assertDoesNotExist()
    assertEquals(false, overlayCameraShown)
  }

  @Test
  fun allowTheCamera_afterARefusalForGood_opensTheAppSettings() {
    setGate()

    // Without a rationale after a request, the dialog no longer shows: only the settings can help.
    tag(ScanScreenTestTags.ALLOW_CAMERA).performClick()

    val intent = started.single()
    assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
    assertEquals("package", intent.data?.scheme)
  }

  @Test
  fun permissionGrantedInTheSettings_showsTheCameraOnResume() {
    setGate()
    tag(ScanScreenTestTags.FALLBACK).assertIsDisplayed()

    grantedPermissions += Manifest.permission.CAMERA
    compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
    compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

    tag(CAMERA).assertIsDisplayed()
    tag(ScanScreenTestTags.FALLBACK).assertDoesNotExist()
  }

  @Test
  fun granted_showsTheCameraWithoutAsking() {
    grantedPermissions += Manifest.permission.CAMERA
    setGate()

    tag(CAMERA).assertIsDisplayed()
    assertEquals(emptyList<String>(), requested)
  }

  @Test
  fun scannerFails_showsTheErrorFallback() {
    grantedPermissions += Manifest.permission.CAMERA
    setGate()
    tag(CAMERA).assertIsDisplayed()

    scanner.fail(ScanError.NO_CAMERA)

    compose.onNodeWithText("The camera isn't working").assertIsDisplayed()
    tag(ScanScreenTestTags.ALLOW_CAMERA).assertDoesNotExist()
    tag(CAMERA).assertDoesNotExist()
  }

  @Test
  fun overlay_isToldWhetherTheCameraIsShown() {
    grantedPermissions += Manifest.permission.CAMERA
    setGate()
    compose.waitForIdle()
    assertEquals(true, overlayCameraShown)

    scanner.fail(ScanError.NO_CAMERA)
    compose.waitForIdle()

    assertEquals(false, overlayCameraShown)
  }

  @Test
  fun noCamera_keepsTheErrorFallback() {
    grantedPermissions += Manifest.permission.CAMERA
    scanner.errorOnPreview = ScanError.NO_CAMERA
    setGate()

    // The preview leaving clears the error: the gate must not bring the camera back for it.
    compose.onNodeWithText("The camera isn't working").assertIsDisplayed()
    assertNull(scanner.error.value)
    tag(CAMERA).assertDoesNotExist()
  }

  private companion object {
    const val CAMERA = "camera"
  }
}
