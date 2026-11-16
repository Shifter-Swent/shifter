// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.scan

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.TorchState
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [QrScanner] backed by CameraX, with ML Kit keeping only QR codes. Its model is bundled in the
 * app, so it works offline from the first launch.
 *
 * It outlives the screen, so it holds only its states: the camera is opened when [Preview] enters
 * the composition and released when it leaves. The back camera is used, or the front one on a
 * device without.
 *
 * @param now the current time in milliseconds, for the 2-second window of [codes].
 */
class CameraQrScanner(
    context: Context,
    now: () -> Long = SystemClock::elapsedRealtime,
) : QrScanner {

  private val hasCameraFeature =
      context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

  private val sightings = MutableSharedFlow<String>(extraBufferCapacity = SIGHTINGS_BUFFER)
  override val codes: Flow<String> = sightings.distinctWithin(REPEAT_WINDOW_MILLIS, now)

  private val mutableError = MutableStateFlow<ScanError?>(null)
  override val error: StateFlow<ScanError?> = mutableError.asStateFlow()

  private val mutableHasTorch = MutableStateFlow(false)
  override val hasTorch: StateFlow<Boolean> = mutableHasTorch.asStateFlow()

  private val mutableTorchOn = MutableStateFlow(false)
  override val torchOn: StateFlow<Boolean> = mutableTorchOn.asStateFlow()

  /** The camera while [Preview] shows it, to turn its torch on. */
  private var camera: CameraController? = null

  override fun torch(on: Boolean) {
    if (mutableHasTorch.value) camera?.enableTorch(on)
  }

  /** The texts ML Kit read in one frame; dropped when nobody collects [codes]. */
  internal fun onSightings(texts: List<String>) {
    texts.forEach { sightings.tryEmit(it) }
  }

  /** The camera started; the torch can be used if it has a flash. */
  internal fun onStarted(camera: CameraController?, hasFlash: Boolean) {
    this.camera = camera
    mutableHasTorch.value = hasFlash
  }

  /** CameraX turned the torch on or off, e.g. off when the app goes to the background. */
  internal fun onTorch(on: Boolean) {
    mutableTorchOn.value = on
  }

  /** The camera cannot read codes any more. */
  internal fun onCameraError(error: ScanError) {
    mutableError.value = error
    onStopped()
  }

  /** The camera was released: no torch until it starts again. */
  internal fun onStopped() {
    camera = null
    mutableHasTorch.value = false
    mutableTorchOn.value = false
  }

  @Composable
  override fun Preview(modifier: Modifier) {
    DisposableEffect(Unit) {
      // A new try: the error of a previous camera no longer applies.
      mutableError.value = null
      if (!hasCameraFeature) onCameraError(ScanError.NO_CAMERA)
      onDispose {}
    }
    if (hasCameraFeature) CameraPreview(modifier) else Box(modifier)
  }

  /** Binds the camera to the screen while shown, and reports what happens to it. */
  @Composable
  private fun CameraPreview(modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { LifecycleCameraController(context) }

    DisposableEffect(controller, lifecycleOwner) {
      val executor = ContextCompat.getMainExecutor(context)
      val barcodes =
          BarcodeScanning.getClient(
              BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
          )
      controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
      controller.setImageAnalysisAnalyzer(
          executor,
          MlKitAnalyzer(listOf(barcodes), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, executor) {
            onSightings(it.getValue(barcodes).orEmpty().mapNotNull(Barcode::getRawValue))
          },
      )

      var disposed = false
      var cameraState: LiveData<CameraState>? = null
      val stateObserver = Observer<CameraState> { scanError(it.error)?.let(::onCameraError) }
      val torchObserver = Observer<Int> { onTorch(it == TorchState.ON) }

      fun start() {
        if (disposed) return
        val selector =
            listOf(CameraSelector.DEFAULT_BACK_CAMERA, CameraSelector.DEFAULT_FRONT_CAMERA)
                .firstOrNull(controller::hasCamera)
        if (selector == null) {
          onCameraError(ScanError.NO_CAMERA)
          return
        }
        controller.cameraSelector = selector
        controller.bindToLifecycle(lifecycleOwner)
        controller.torchState.observe(lifecycleOwner, torchObserver)
        cameraState = controller.cameraInfo?.cameraState
        cameraState?.observe(lifecycleOwner, stateObserver)
        onStarted(controller, controller.cameraInfo?.hasFlashUnit() == true)
      }

      controller.initializationFuture.addListener(
          {
            // Never a crash: a camera that cannot start (no permission, in use, CameraX failing
            // to initialize) is reported, and the screen shows the typed code instead.
            try {
              controller.initializationFuture.get()
              start()
            } catch (e: Exception) {
              onCameraError(ScanError.CAMERA_FAILED)
            }
          },
          executor,
      )

      onDispose {
        disposed = true
        cameraState?.removeObserver(stateObserver)
        controller.torchState.removeObserver(torchObserver)
        controller.clearImageAnalysisAnalyzer()
        controller.unbind()
        barcodes.close()
        onStopped()
      }
    }

    AndroidView(
        factory = { PreviewView(it).apply { this.controller = controller } },
        modifier = modifier,
    )
  }

  internal companion object {
    /** How long a code must be out of view before it is emitted again. */
    const val REPEAT_WINDOW_MILLIS = 2_000L

    /** Sightings waiting for the collector; a frame holds a few codes at most. */
    const val SIGHTINGS_BUFFER = 64

    /**
     * The error to report for a camera state: any CameraX error stops the scan, even one CameraX
     * may recover from (e.g. the camera in use by another app), since the volunteer can always type
     * the code instead of staring at a black preview.
     */
    fun scanError(stateError: CameraState.StateError?): ScanError? = stateError?.let {
      ScanError.CAMERA_FAILED
    }
  }
}
