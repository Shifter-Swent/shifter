// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.scan

import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraState
import androidx.camera.view.CameraController
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.google.mlkit.vision.interfaces.Detector
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * Covers what the camera scanner does with what CameraX and ML Kit report. Robolectric has no
 * camera, so the camera itself is checked by hand on a phone.
 */
@RunWith(AndroidJUnit4::class)
class CameraQrScannerTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  private var time = 0L

  private fun scanner(hasCamera: Boolean = true): CameraQrScanner {
    shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, hasCamera)
    return CameraQrScanner(context) { time }
  }

  @Test
  fun codes_aCodeSeenOnEveryFrame_isEmittedOnce() = runTest {
    val scanner = scanner()
    val received = mutableListOf<String>()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { scanner.codes.toList(received) }

    repeat(20) {
      scanner.onSightings(listOf(CODE))
      time += 100
    }
    time += CameraQrScanner.REPEAT_WINDOW_MILLIS
    scanner.onSightings(listOf(CODE, OTHER))

    assertEquals(listOf(CODE, CODE, OTHER), received)
    job.cancel()
  }

  @Test
  fun codes_whileNobodyCollects_areDropped() = runTest {
    val scanner = scanner()
    scanner.onSightings(listOf(CODE))
    val received = mutableListOf<String>()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { scanner.codes.toList(received) }

    scanner.onSightings(listOf(OTHER))

    assertEquals(listOf(OTHER), received)
    job.cancel()
  }

  @Test
  fun error_atStart_isNull() {
    assertNull(scanner().error.value)
  }

  @Test
  fun scanError_ofACameraError_isCameraFailed() {
    val inUse = CameraState.StateError.create(CameraState.ERROR_CAMERA_IN_USE)

    assertEquals(ScanError.CAMERA_FAILED, CameraQrScanner.scanError(inUse))
  }

  @Test
  fun scanError_withoutError_isNull() {
    assertNull(CameraQrScanner.scanError(null))
  }

  @Test
  fun torch_beforeTheCameraStarts_staysOff() {
    val scanner = scanner()

    scanner.torch(true)

    assertFalse(scanner.hasTorch.value)
    assertFalse(scanner.torchOn.value)
  }

  @Test
  fun torch_withAFlash_turnsTheCameraTorchOn() {
    val scanner = scanner()
    val camera = mockk<CameraController>(relaxed = true)
    scanner.onStarted(camera, hasFlash = true)

    scanner.torch(true)

    assertTrue(scanner.hasTorch.value)
    verify { camera.enableTorch(true) }
  }

  @Test
  fun torch_withoutAFlash_isIgnored() {
    val scanner = scanner()
    val camera = mockk<CameraController>(relaxed = true)
    scanner.onStarted(camera, hasFlash = false)

    scanner.torch(true)

    assertFalse(scanner.hasTorch.value)
    verify(exactly = 0) { camera.enableTorch(any()) }
  }

  @Test
  fun onTorch_followsTheCameraTorch() {
    val scanner = scanner()

    scanner.onTorch(true)
    assertTrue(scanner.torchOn.value)

    scanner.onTorch(false)
    assertFalse(scanner.torchOn.value)
  }

  @Test
  fun onCameraError_reportsItAndTurnsTheTorchOff() {
    val scanner = scanner()
    scanner.onStarted(mockk(relaxed = true), hasFlash = true)
    scanner.onTorch(true)

    scanner.onCameraError(ScanError.CAMERA_FAILED)

    assertEquals(ScanError.CAMERA_FAILED, scanner.error.value)
    assertFalse(scanner.hasTorch.value)
    assertFalse(scanner.torchOn.value)
  }

  @Test
  fun onStopped_turnsTheTorchOff() {
    val scanner = scanner()
    val camera = mockk<CameraController>(relaxed = true)
    scanner.onStarted(camera, hasFlash = true)
    scanner.onTorch(true)

    scanner.onStopped()
    scanner.torch(true)

    assertFalse(scanner.hasTorch.value)
    assertFalse(scanner.torchOn.value)
    verify(exactly = 0) { camera.enableTorch(any()) }
  }

  @Test
  fun preview_withoutACamera_reportsNoCamera() {
    val scanner = scanner(hasCamera = false)

    composeTestRule.setContent { scanner.Preview(Modifier.size(200.dp).testTag(PREVIEW)) }

    composeTestRule.onNodeWithTag(PREVIEW).assertHeightIsEqualTo(200.dp)
    assertEquals(ScanError.NO_CAMERA, scanner.error.value)
  }

  @Test
  fun preview_withACamera_showsItAndReleasesItWithoutCrashing() {
    // In the app, ML Kit initializes itself at launch; Robolectric does not run its provider.
    MlKitContext.initializeIfNeeded(context)
    val scanner = scanner(hasCamera = true)
    var shown by mutableStateOf(true)
    composeTestRule.setContent {
      if (shown) scanner.Preview(Modifier.size(200.dp).testTag(PREVIEW))
    }

    composeTestRule.onNodeWithTag(PREVIEW).assertHeightIsEqualTo(200.dp)
    shown = false
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(PREVIEW).assertDoesNotExist()
    assertFalse(scanner.hasTorch.value)
  }

  @Test
  fun qrDetector_isABarcodeScanner() {
    MlKitContext.initializeIfNeeded(context)

    val detector = CameraQrScanner.qrDetector()

    assertEquals(Detector.TYPE_BARCODE_SCANNING, detector.detectorType)
    detector.close()
  }

  @Test
  fun preview_shownAgain_clearsTheErrorBeforeTrying() {
    val scanner = scanner(hasCamera = false)
    var shown by mutableStateOf(false)
    composeTestRule.setContent { if (shown) scanner.Preview() }
    scanner.onCameraError(ScanError.CAMERA_FAILED)

    shown = true
    composeTestRule.waitForIdle()

    // The old error is dropped and the camera tried again: without one, it is NO_CAMERA now.
    assertEquals(ScanError.NO_CAMERA, scanner.error.value)
  }

  private companion object {
    const val PREVIEW = "preview"
    const val CODE = "AB23CD"
    const val OTHER = "EF45GH"
  }
}
