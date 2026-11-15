// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * [QrCodeGenerator] made with ZXing, on the device. ZXing stays in this file: callers only see a
 * [QrMatrix].
 */
object ZxingQrCodeGenerator : QrCodeGenerator {

  /** The light border, in modules, that the QR specification asks scanners to find the code. */
  const val QUIET_ZONE = 4

  override fun matrix(text: String): QrMatrix {
    require(text.isNotEmpty()) { "A QR code needs a text" }
    val hints =
        mapOf(
            // Level M: about 15% of the code can be damaged or hidden and still read.
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to QUIET_ZONE,
        )
    // A requested size of 0 gives the smallest matrix: one bit per module, quiet zone included.
    val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    val size = bits.width
    return QrMatrix(size, BooleanArray(size * size) { bits.get(it % size, it / size) })
  }
}
