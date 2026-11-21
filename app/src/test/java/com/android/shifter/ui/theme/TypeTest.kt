// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.theme

import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import com.swent.shifter.R
import org.junit.Assert.assertEquals
import org.junit.Test

class TypeTest {

  private val fonts = (SatoshiFontFamily as FontListFontFamily).fonts.map { it as ResourceFont }

  @Test
  fun satoshiFontFamily_drawsEveryWeightFromTheVariableFont() {
    assertEquals(SatoshiWeights.keys, fonts.map { it.weight }.toSet())
    fonts.forEach {
      assertEquals(R.font.satoshi_variable, it.resId)
      assertEquals(FontStyle.Normal, it.style)
    }
  }

  @Test
  fun satoshiFontFamily_setsEachWeightOnTheAxis() {
    val expected =
        mapOf(
            FontWeight.Thin to 300,
            FontWeight.ExtraLight to 300,
            FontWeight.Light to 300,
            FontWeight.Normal to 400,
            FontWeight.Medium to 500,
            FontWeight.SemiBold to 600,
            FontWeight.Bold to 700,
            FontWeight.ExtraBold to 800,
            FontWeight.Black to 900,
        )
    expected.forEach { (weight, value) ->
      val font = fonts.single { it.weight == weight }
      assertEquals(
          "$weight",
          FontVariation.Settings(FontVariation.weight(value)),
          font.variationSettings,
      )
    }
  }

  @Test
  fun shifterTypography_drawsEveryStyleInSatoshi() {
    val styles =
        with(ShifterTypography) {
          listOf(
              displayLarge,
              displayMedium,
              displaySmall,
              headlineLarge,
              headlineMedium,
              headlineSmall,
              titleLarge,
              titleMedium,
              titleSmall,
              bodyLarge,
              bodyMedium,
              bodySmall,
              labelLarge,
              labelMedium,
              labelSmall,
          )
        }
    styles.forEach { assertEquals(SatoshiFontFamily, it.fontFamily) }
  }
}
