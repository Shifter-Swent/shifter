// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.qr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QrMatrixTest {

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
}
