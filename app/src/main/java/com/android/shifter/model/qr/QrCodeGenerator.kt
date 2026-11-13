// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.qr

/**
 * Turns a text into the modules of its QR code.
 *
 * The QR code of an event holds only its join code (see `JoinCode`), not a link: only Shifter's own
 * scanner reads it.
 */
interface QrCodeGenerator {

  /**
   * The QR code of [text], with its quiet zone.
   *
   * @throws IllegalArgumentException if [text] is empty.
   */
  fun matrix(text: String): QrMatrix
}
