// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.qr

/**
 * The modules of a QR code: a square of [size] × [size] modules, each dark or light, quiet zone
 * included. Free of any library type, so the UI draws it without knowing how it was made.
 *
 * @param dark the modules row by row, `true` for a dark one; its size must be [size] × [size].
 */
class QrMatrix(val size: Int, private val dark: BooleanArray) {

  init {
    require(size > 0) { "A QR matrix has at least one module" }
    require(dark.size == size * size) { "A QR matrix of size $size has ${size * size} modules" }
  }

  /** Whether the module at column [x] and row [y], counted from the top left, is dark. */
  fun isDark(x: Int, y: Int): Boolean {
    require(x in 0 until size && y in 0 until size) { "($x, $y) is outside the QR matrix" }
    return dark[y * size + x]
  }
}
