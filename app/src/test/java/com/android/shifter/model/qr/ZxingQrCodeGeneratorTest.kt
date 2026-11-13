// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.swent.shifter.model.event.JoinCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ZxingQrCodeGeneratorTest {

  /** Reads [matrix] back with ZXing, each module drawn as a square of [scale] pixels. */
  private fun decode(matrix: QrMatrix, scale: Int = 4): String {
    val side = matrix.size * scale
    val pixels =
        IntArray(side * side) {
          if (matrix.isDark((it % side) / scale, (it / side) / scale)) BLACK else WHITE
        }
    val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
    return QRCodeReader().decode(bitmap).text
  }

  @Test
  fun matrix_ofAJoinCode_decodesBackToTheCode() {
    val code = "AB23CD"
    assertTrue(JoinCode.isValid(code))

    assertEquals(code, decode(ZxingQrCodeGenerator.matrix(code)))
  }

  @Test
  fun matrix_ofEveryJoinCodeCharacter_decodesBack() {
    JoinCode.ALPHABET.chunked(JoinCode.LENGTH).forEach { chunk ->
      val code = chunk.padEnd(JoinCode.LENGTH, JoinCode.ALPHABET.first())
      assertEquals(code, decode(ZxingQrCodeGenerator.matrix(code)))
    }
  }

  @Test
  fun matrix_hasAQuietZoneOfFourModules() {
    val matrix = ZxingQrCodeGenerator.matrix("AB23CD")
    val zone = ZxingQrCodeGenerator.QUIET_ZONE
    val border = (0 until zone) + (matrix.size - zone until matrix.size)

    for (i in 0 until matrix.size) {
      for (b in border) {
        assertFalse(matrix.isDark(i, b))
        assertFalse(matrix.isDark(b, i))
      }
    }
    // The top left finder pattern starts right after the quiet zone.
    assertTrue(matrix.isDark(zone, zone))
  }

  @Test
  fun matrix_ofAnEmptyText_isRefused() {
    assertThrows(IllegalArgumentException::class.java) { ZxingQrCodeGenerator.matrix("") }
  }

  @Test
  fun isDark_outsideTheMatrix_throws() {
    val matrix = QrMatrix(2, booleanArrayOf(true, false, false, true))

    assertTrue(matrix.isDark(1, 1))
    assertFalse(matrix.isDark(1, 0))
    assertThrows(IllegalArgumentException::class.java) { matrix.isDark(2, 0) }
    assertThrows(IllegalArgumentException::class.java) { matrix.isDark(0, -1) }
  }

  @Test
  fun qrMatrix_withTheWrongNumberOfModules_isRefused() {
    assertThrows(IllegalArgumentException::class.java) { QrMatrix(2, BooleanArray(3)) }
    assertThrows(IllegalArgumentException::class.java) { QrMatrix(0, BooleanArray(0)) }
  }

  private companion object {
    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
  }
}
