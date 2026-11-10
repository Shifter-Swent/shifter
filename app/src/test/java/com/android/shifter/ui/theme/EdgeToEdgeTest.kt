// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class EdgeToEdgeTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private fun showTheApp() {
    composeTestRule.runOnUiThread { composeTestRule.activity.enableShifterEdgeToEdge() }
    composeTestRule.setContent { ShifterTheme {} }
    composeTestRule.waitForIdle()
  }

  @Suppress("DEPRECATION")
  @Test
  fun enableShifterEdgeToEdge_makesTheSystemBarsTransparent() {
    showTheApp()

    val window = composeTestRule.activity.window
    assertEquals(Color.TRANSPARENT, window.statusBarColor)
    assertEquals(Color.TRANSPARENT, window.navigationBarColor)
  }

  @Test
  @Config(qualifiers = "notnight")
  fun enableShifterEdgeToEdge_withTheSystemInLightMode_keepsTheIconsLight() {
    showTheApp()

    val window = composeTestRule.activity.window
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    assertFalse(controller.isAppearanceLightStatusBars)
    assertFalse(controller.isAppearanceLightNavigationBars)
  }
}
