// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.scan

import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.TorchState
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.common.Barcode
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * Covers how the camera scanner drives CameraX, with a mocked camera controller: Robolectric has no
 * camera, and the real one is checked by hand on a phone.
 */
@RunWith(AndroidJUnit4::class)
class CameraQrScannerAttachTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val owner = mockk<LifecycleOwner>(relaxed = true)
  private val barcodes = mockk<BarcodeScanner>(relaxed = true)

  /** Runs each task right away, as the main thread would. */
  private val executor = Executor { it.run() }

  private val initialization = mockk<ListenableFuture<Void>>()
  private var whenReady: Runnable? = null

  private val torchState = mockk<LiveData<Int>>(relaxed = true)
  private val torchObserver = slot<Observer<Int>>()
  private val cameraState = mockk<LiveData<CameraState>>(relaxed = true)
  private val stateObserver = slot<Observer<CameraState>>()
  private val cameraInfo = mockk<CameraInfo>()
  private val controller = mockk<LifecycleCameraController>(relaxed = true)

  private lateinit var scanner: CameraQrScanner

  @Before
  fun setUp() {
    shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, true)
    scanner = CameraQrScanner(context) { 0L }

    every { initialization.addListener(any(), any()) } answers { whenReady = firstArg() }
    every { initialization.get() } returns null
    every { controller.initializationFuture } returns initialization
    every { controller.hasCamera(any()) } returns false
    every { controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) } returns true
    every { controller.torchState } returns torchState
    every { torchState.observe(any(), capture(torchObserver)) } just runs
    every { controller.cameraInfo } returns cameraInfo
    every { cameraInfo.cameraState } returns cameraState
    every { cameraState.observe(any(), capture(stateObserver)) } just runs
    every { cameraInfo.hasFlashUnit() } returns true
  }

  /** Attaches the camera, then lets CameraX finish initializing. */
  private fun attachAndStart(): () -> Unit {
    val detach = scanner.attach(controller, owner, executor, barcodes)
    whenReady?.run()
    return detach
  }

  @Test
  fun attach_withABackCamera_bindsIt() {
    attachAndStart()

    verify { controller.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA }
    verify { controller.bindToLifecycle(owner) }
    assertTrue(scanner.hasTorch.value)
    assertNull(scanner.error.value)
  }

  @Test
  fun attach_whenNoCameraIsBound_reportsCameraFailed() {
    every { controller.cameraInfo } returns null

    attachAndStart()

    assertEquals(ScanError.CAMERA_FAILED, scanner.error.value)
    assertFalse(scanner.hasTorch.value)
  }

  @Test
  fun attach_withOnlyAFrontCamera_bindsIt() {
    every { controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) } returns false
    every { controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) } returns true

    attachAndStart()

    verify { controller.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA }
    verify { controller.bindToLifecycle(owner) }
  }

  @Test
  fun attach_withoutACamera_reportsNoCamera() {
    every { controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) } returns false

    attachAndStart()

    assertEquals(ScanError.NO_CAMERA, scanner.error.value)
    verify(exactly = 0) { controller.bindToLifecycle(any()) }
  }

  @Test
  fun attach_whenCameraXFailsToStart_reportsCameraFailed() {
    every { initialization.get() } throws ExecutionException(IllegalStateException("no camera"))

    attachAndStart()

    assertEquals(ScanError.CAMERA_FAILED, scanner.error.value)
    verify(exactly = 0) { controller.bindToLifecycle(any()) }
  }

  @Test
  fun attach_whenBindingThrows_reportsCameraFailed() {
    every { controller.bindToLifecycle(any()) } throws SecurityException("no permission")

    attachAndStart()

    assertEquals(ScanError.CAMERA_FAILED, scanner.error.value)
  }

  @Test
  fun attach_detachedBeforeCameraXIsReady_bindsNothing() {
    val detach = scanner.attach(controller, owner, executor, barcodes)

    detach()
    whenReady?.run()

    verify(exactly = 0) { controller.bindToLifecycle(any()) }
  }

  @Test
  fun attach_detachedBeforeCameraXFails_reportsNothing() {
    every { initialization.get() } throws ExecutionException(IllegalStateException("no camera"))
    val detach = scanner.attach(controller, owner, executor, barcodes)

    detach()
    whenReady?.run()

    assertNull(scanner.error.value)
  }

  @Test
  fun detach_releasesTheCamera() {
    val detach = attachAndStart()
    torchObserver.captured.onChanged(TorchState.ON)

    detach()

    verify { controller.unbind() }
    verify { controller.clearImageAnalysisAnalyzer() }
    verify { barcodes.close() }
    verify { torchState.removeObserver(torchObserver.captured) }
    verify { cameraState.removeObserver(stateObserver.captured) }
    assertFalse(scanner.hasTorch.value)
    assertFalse(scanner.torchOn.value)
  }

  @Test
  fun cameraState_withAnError_reportsCameraFailed() {
    attachAndStart()

    stateObserver.captured.onChanged(
        CameraState.create(
            CameraState.Type.CLOSED,
            CameraState.StateError.create(CameraState.ERROR_CAMERA_DISABLED),
        )
    )

    assertEquals(ScanError.CAMERA_FAILED, scanner.error.value)
  }

  @Test
  fun cameraState_withoutError_reportsNothing() {
    attachAndStart()

    stateObserver.captured.onChanged(CameraState.create(CameraState.Type.OPEN))

    assertNull(scanner.error.value)
  }

  @Test
  fun torchState_followsTheCameraTorch() {
    attachAndStart()

    torchObserver.captured.onChanged(TorchState.ON)
    assertTrue(scanner.torchOn.value)

    torchObserver.captured.onChanged(TorchState.OFF)
    assertFalse(scanner.torchOn.value)
  }

  @Test
  fun codesOf_emitsTheTextsOfTheQrCodes() = runTest {
    val received = mutableListOf<String>()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { scanner.codes.toList(received) }
    val read = mockk<Barcode> { every { rawValue } returns CODE }
    val unreadable = mockk<Barcode> { every { rawValue } returns null }
    val result =
        mockk<MlKitAnalyzer.Result> {
          every { getValue(barcodes) } returns listOf(read, unreadable)
        }

    scanner.codesOf(barcodes).accept(result)

    assertEquals(listOf(CODE), received)
    job.cancel()
  }

  @Test
  fun codes_withTheDeviceClock_areEmitted() = runTest {
    val deviceScanner = CameraQrScanner(context)
    val received = mutableListOf<String>()
    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) { deviceScanner.codes.toList(received) }

    deviceScanner.onSightings(listOf(CODE))

    assertEquals(listOf(CODE), received)
    job.cancel()
  }

  private companion object {
    const val CODE = "AB23CD"
  }
}
