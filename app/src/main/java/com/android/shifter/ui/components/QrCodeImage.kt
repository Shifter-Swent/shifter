// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.swent.shifter.R
import com.swent.shifter.model.qr.QrCodeGenerator
import com.swent.shifter.model.qr.ZxingQrCodeGenerator
import com.swent.shifter.ui.theme.shifter_qrDark
import com.swent.shifter.ui.theme.shifter_qrLight

/**
 * The QR code of the join [code], dark modules on a light square whatever the theme, so every
 * scanner reads it. It fills the width it is given, as a square; the caller chooses the size.
 *
 * @param generator makes the modules of the code; tests may pass a fake.
 */
@Composable
fun QrCodeImage(
    code: String,
    modifier: Modifier = Modifier,
    generator: QrCodeGenerator = ZxingQrCodeGenerator,
) {
  val matrix = remember(code, generator) { generator.matrix(code) }
  val description = stringResource(R.string.event_join_code_qr_description, code)
  Canvas(modifier.aspectRatio(1f).semantics { contentDescription = description }) {
    drawRect(shifter_qrLight)
    val module = size.minDimension / matrix.size
    val moduleSize = Size(module, module)
    for (y in 0 until matrix.size) {
      for (x in 0 until matrix.size) {
        if (matrix.isDark(x, y)) {
          drawRect(shifter_qrDark, topLeft = Offset(x * module, y * module), size = moduleSize)
        }
      }
    }
  }
}
