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
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.Executor
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
 * @param context any context, e.g. the application's: only its package manager is read, and it is
 *   not kept, so a scanner that outlives the screen leaks nothing.
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
      if (!hasCameraFeature) onCameraError(ScanError.NO_CAMERA)
      // The error belongs to this preview: the next one starts without it.
      onDispose { mutableError.value = null }
    }
    if (hasCameraFeature) CameraPreview(modifier) else Box(modifier)
  }

  /** The camera image, bound to the screen while shown. */
  @Composable
  private fun CameraPreview(modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { LifecycleCameraController(context) }
    DisposableEffect(controller, lifecycleOwner) {
      val detach =
          attach(controller, lifecycleOwner, ContextCompat.getMainExecutor(context), qrDetector())
      onDispose { detach() }
    }
    AndroidView(
        factory = { PreviewView(it).apply { this.controller = controller } },
        modifier = modifier,
    )
  }

  /**
   * Starts [controller] for [lifecycleOwner] once CameraX is ready, reads the QR codes of each
   * frame with [barcodes], and reports what happens to the camera. Returns what releases them all.
   *
   * Never a crash: a camera that cannot start (no permission, in use, CameraX failing to
   * initialize) is reported as a [ScanError], and the screen shows the typed code instead.
   */
  internal fun attach(
      controller: LifecycleCameraController,
      lifecycleOwner: LifecycleOwner,
      executor: Executor,
      barcodes: BarcodeScanner,
  ): () -> Unit {
    controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
    controller.setImageAnalysisAnalyzer(
        executor,
        MlKitAnalyzer(
            listOf(barcodes),
            ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL,
            executor,
            codesOf(barcodes),
        ),
    )

    var detached = false
    var cameraState: LiveData<CameraState>? = null
    val stateObserver = Observer<CameraState> { scanError(it)?.let(::onCameraError) }
    val torchObserver = Observer<Int> { onTorch(it == TorchState.ON) }

    fun start() {
      if (detached) return
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
          try {
            controller.initializationFuture.get()
            start()
          } catch (e: Exception) {
            // Once detached, the error would outlive the preview it belongs to.
            if (!detached) onCameraError(ScanError.CAMERA_FAILED)
          }
        },
        executor,
    )

    return {
      detached = true
      cameraState?.removeObserver(stateObserver)
      controller.torchState.removeObserver(torchObserver)
      controller.clearImageAnalysisAnalyzer()
      controller.unbind()
      barcodes.close()
      onStopped()
    }
  }

  /** Passes the texts of the QR codes [barcodes] read in each frame to [codes]. */
  internal fun codesOf(barcodes: BarcodeScanner) =
      Consumer<MlKitAnalyzer.Result> { result ->
        onSightings(result.getValue(barcodes).orEmpty().mapNotNull(Barcode::getRawValue))
      }

  internal companion object {
    /** How long a code must be out of view before it is emitted again. */
    const val REPEAT_WINDOW_MILLIS = 2_000L

    /** Sightings waiting for the collector; a frame holds a few codes at most. */
    const val SIGHTINGS_BUFFER = 64

    /**
     * The error to report for a camera [state]: a critical error, or one CameraX gave up on (the
     * camera closed). A recoverable error CameraX is still retrying, e.g. the camera in use by
     * another app while coming back from the background, is not reported: the screen keeps the
     * first error, so a short hiccup would hide the camera until it is opened again.
     */
    fun scanError(state: CameraState): ScanError? {
      val error = state.error ?: return null
      val givenUp =
          error.type == CameraState.ErrorType.CRITICAL || state.type == CameraState.Type.CLOSED
      return if (givenUp) ScanError.CAMERA_FAILED else null
    }

    /** ML Kit's barcode scanner, keeping only QR codes. */
    fun qrDetector(): BarcodeScanner =
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )
  }
}
