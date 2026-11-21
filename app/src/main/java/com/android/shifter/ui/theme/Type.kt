// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.swent.shifter.R

/**
 * The value on the weight axis of Satoshi's variable font that draws each [FontWeight].
 *
 * The axis goes from 300 to 900, so Thin and ExtraLight are drawn as Light. A weight that renders
 * badly can be tuned here (e.g. semi-bold at 620) without another file.
 */
internal val SatoshiWeights: Map<FontWeight, Int> =
    mapOf(
        FontWeight.W100 to 300,
        FontWeight.W200 to 300,
        FontWeight.W300 to 300,
        FontWeight.W400 to 400,
        FontWeight.W500 to 500,
        FontWeight.W600 to 600,
        FontWeight.W700 to 700,
        FontWeight.W800 to 800,
        FontWeight.W900 to 900,
    )

/**
 * Satoshi (Fontshare, by Indian Type Foundry), the app's typeface. Every weight is a point on the
 * same variable font, set explicitly since the axis defaults to 900. The font file is downloaded by
 * the build (see `downloadSatoshiFont` in `app/build.gradle.kts`), never committed.
 */
val SatoshiFontFamily: FontFamily =
    FontFamily(
        SatoshiWeights.map { (weight, value) ->
          Font(
              resId = R.font.satoshi_variable,
              weight = weight,
              variationSettings = FontVariation.Settings(FontVariation.weight(value)),
          )
        }
    )

private val MaterialDefaults = Typography()

private fun TextStyle.inSatoshi() = copy(fontFamily = SatoshiFontFamily)

/** Every Material style in [SatoshiFontFamily]; sizes and weights stay Material's defaults. */
val ShifterTypography: Typography =
    with(MaterialDefaults) {
      Typography(
          displayLarge = displayLarge.inSatoshi(),
          displayMedium = displayMedium.inSatoshi(),
          displaySmall = displaySmall.inSatoshi(),
          headlineLarge = headlineLarge.inSatoshi(),
          headlineMedium = headlineMedium.inSatoshi(),
          headlineSmall = headlineSmall.inSatoshi(),
          titleLarge = titleLarge.inSatoshi(),
          titleMedium = titleMedium.inSatoshi(),
          titleSmall = titleSmall.inSatoshi(),
          bodyLarge = bodyLarge.inSatoshi(),
          bodyMedium = bodyMedium.inSatoshi(),
          bodySmall = bodySmall.inSatoshi(),
          labelLarge = labelLarge.inSatoshi(),
          labelMedium = labelMedium.inSatoshi(),
          labelSmall = labelSmall.inSatoshi(),
      )
    }
