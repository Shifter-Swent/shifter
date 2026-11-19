// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.md_theme_dark_onPrimary
import com.swent.shifter.ui.theme.shifter_success
import com.swent.shifter.ui.theme.shifter_warning
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What the viewfinder draws, read from its pixels: the corners in each state, the scan line in both
 * directions, and the camera darkened around the frame. The camera is a grey background.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScanViewfinderDrawingTest {
  @get:Rule val compose = createComposeRule()

  private fun show(state: ViewfinderState) {
    compose.setContent {
      ShifterTheme {
        Box(Modifier.size(400.dp).background(CAMERA).testTag(HOST)) {
          ScanViewfinder(state, Modifier.testTag(VIEWFINDER_COLUMN))
        }
      }
    }
    compose.waitForIdle()
  }

  /** The host's pixels, and where the frame is in them. */
  private fun capture(): Pair<PixelMap, Rect> {
    val pixels = compose.onNodeWithTag(HOST).captureToImage().toPixelMap()
    val host = compose.onNodeWithTag(HOST).getBoundsInRoot()
    val frame = compose.onNodeWithTag(ScanScreenTestTags.VIEWFINDER).getBoundsInRoot()
    val density = compose.density.density
    val rect =
        Rect(
            (frame.left - host.left).value * density,
            (frame.top - host.top).value * density,
            (frame.right - host.left).value * density,
            (frame.bottom - host.top).value * density,
        )
    return pixels to rect
  }

  /** The middle of the top left corner's top arm, scaled around the frame's centre by [scale]. */
  private fun cornerPixel(pixels: PixelMap, frame: Rect, scale: Float = 1f): Color {
    val stroke = CORNER_STROKE.value * compose.density.density
    val side = frame.width - stroke
    val x = stroke / 2 + side * (CORNER_RADIUS_RATIO + CORNER_ARM_RATIO) / 2
    val y = stroke / 2
    val centre = frame.width / 2
    return pixels[
        (frame.left + centre + scale * (x - centre)).toInt(),
        (frame.top + centre + scale * (y - centre)).toInt()]
  }

  /** Whether the frame's middle column holds a blue pixel: the scan line or its glow. */
  private fun hasScanLine(pixels: PixelMap, frame: Rect): Boolean {
    val x = frame.center.x.toInt()
    return (frame.top.toInt() until frame.bottom.toInt()).any {
      val pixel = pixels[x, it]
      pixel.blue - pixel.red > 0.15f
    }
  }

  @Test
  fun searching_drawsWhiteCornersAndTheScanLine() {
    show(ViewfinderState.SEARCHING)

    val (pixels, frame) = capture()

    assertClose(md_theme_dark_onPrimary, cornerPixel(pixels, frame))
    assertTrue(hasScanLine(pixels, frame))
  }

  // The tests' clock freezes infinite animations at their start, so the line is drawn directly at a
  // phase going down (0 to 1) and one going back up (1 to 2), on a 100 × 100 square.

  /** How opaque each row of a square is, once the scan line was drawn on it at [phase]. */
  private fun scanLineRows(phase: Float): List<Float> {
    val bitmap = ImageBitmap(SQUARE, SQUARE)
    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        Canvas(bitmap),
        Size(SQUARE.toFloat(), SQUARE.toFloat()),
    ) {
      drawScanLine(phase, Color.Blue, alpha = 1f, lineHeight = 3f, glow = 24f)
    }
    val pixels = bitmap.toPixelMap()
    return (0 until SQUARE).map { pixels[SQUARE / 2, it].alpha }
  }

  @Test
  fun scanLine_goingDown_trailsItsGlowAbove() {
    val rows = scanLineRows(phase = 0.5f)

    val line = rows.indices.maxBy { rows[it] }
    assertTrue(rows[line - 12] > 0f)
    assertEquals(0f, rows[line + 12], 0.01f)
  }

  @Test
  fun scanLine_goingBackUp_trailsItsGlowBelow() {
    val rows = scanLineRows(phase = 1.5f)

    val line = rows.indices.maxBy { rows[it] }
    assertEquals(0f, rows[line - 12], 0.01f)
    assertTrue(rows[line + 12] > 0f)
  }

  @Test
  fun scanLine_atTheSamePointBothWays_isAtTheSameHeight() {
    val down = scanLineRows(phase = 0.5f)
    val up = scanLineRows(phase = 1.5f)

    // Within a pixel: the line is 3 pixels thick, and its glow meets it on a different side.
    val downRow = down.indices.maxBy { down[it] }.toFloat()
    val upRow = up.indices.maxBy { up[it] }.toFloat()
    assertEquals(downRow, upRow, 1f)
  }

  @Test
  fun searching_darkensTheCameraAroundTheFrame() {
    show(ViewfinderState.SEARCHING)

    val (pixels, frame) = capture()

    val inside = pixels[(frame.left + frame.width / 4).toInt(), frame.center.y.toInt()]
    val outside = pixels[(frame.left / 2).toInt(), frame.center.y.toInt()]
    assertClose(CAMERA, inside)
    assertTrue(outside.red < inside.red - 0.2f)
  }

  @Test
  fun found_drawsGreenCornersTightenedAndNoScanLine() {
    show(ViewfinderState.FOUND)

    val (pixels, frame) = capture()

    assertClose(shifter_success, cornerPixel(pixels, frame, scale = FOUND_SCALE))
    assertClose(CAMERA, cornerPixel(pixels, frame))
    assertTrue(!hasScanLine(pixels, frame))
  }

  @Test
  fun unknown_drawsAmberCorners() {
    show(ViewfinderState.UNKNOWN)

    val (pixels, frame) = capture()

    assertClose(shifter_warning, cornerPixel(pixels, frame))
  }

  private fun assertClose(expected: Color, actual: Color) {
    val close =
        abs(expected.red - actual.red) < 0.12f &&
            abs(expected.green - actual.green) < 0.12f &&
            abs(expected.blue - actual.blue) < 0.12f
    assertTrue("expected $expected, was $actual", close)
  }

  private companion object {
    const val HOST = "host"
    const val VIEWFINDER_COLUMN = "viewfinderColumn"
    const val SQUARE = 100
    val CAMERA = Color(0xFF808080)
  }
}
