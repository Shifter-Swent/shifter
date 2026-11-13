// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.qr.QrCodeGenerator
import com.swent.shifter.model.qr.QrMatrix
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.shifter_qrDark
import com.swent.shifter.ui.theme.shifter_qrLight
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QrCodeImageTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val image
    get() = composeTestRule.onNodeWithContentDescription("QR code of the join code $CODE")

  private fun setImage(side: Dp = 120.dp, generator: QrCodeGenerator? = null) {
    composeTestRule.setContent {
      // ShifterTheme is dark: the code must still be dark on light.
      ShifterTheme {
        val modifier = Modifier.size(side)
        if (generator == null) QrCodeImage(CODE, modifier)
        else QrCodeImage(CODE, modifier, generator)
      }
    }
  }

  @Test
  fun qrCode_hasTheContentDescriptionOfTheCode() {
    setImage()

    image.assertExists()
  }

  @Test
  fun qrCode_isDrawnAtTheGivenSize() {
    setImage(side = 300.dp)

    image.assertWidthIsEqualTo(300.dp).assertHeightIsEqualTo(300.dp)
  }

  @Test
  fun qrCode_drawsDarkModulesOnLight() {
    setImage()

    val pixels = image.captureToImage().toPixelMap()
    // The corner is in the quiet zone; the middle of the top left finder pattern is dark.
    assertEquals(shifter_qrLight, pixels[0, 0])
    val centre = ((FINDER_CENTER + 0.5f) * pixels.width / FINDER_MATRIX_SIZE).toInt()
    assertEquals(shifter_qrDark, pixels[centre, centre])
  }

  @Test
  fun qrCode_drawsTheMatrixOfItsGenerator() {
    val checkerboard =
        object : QrCodeGenerator {
          override fun matrix(text: String) = QrMatrix(2, booleanArrayOf(true, false, false, true))
        }
    setImage(side = 100.dp, generator = checkerboard)

    val pixels = image.captureToImage().toPixelMap()
    val quarter = pixels.width / 4
    assertEquals(shifter_qrDark, pixels[quarter, quarter])
    assertEquals(shifter_qrLight, pixels[3 * quarter, quarter])
    assertEquals(shifter_qrLight, pixels[quarter, 3 * quarter])
    assertEquals(shifter_qrDark, pixels[3 * quarter, 3 * quarter])
  }

  private companion object {
    const val CODE = "AB23CD"

    /** A 6-character code fits a version 1 code: 21 modules, plus 4 of quiet zone on each side. */
    const val FINDER_MATRIX_SIZE = 29

    /** The centre of the top left finder pattern: 4 modules of quiet zone, then 3 of the 7. */
    const val FINDER_CENTER = 7
  }
}
