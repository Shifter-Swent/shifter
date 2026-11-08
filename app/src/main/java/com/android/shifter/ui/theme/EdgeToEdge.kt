// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * Draws [this] activity behind the system bars, which become transparent, with light icons since
 * the app is always dark. Each screen keeps its content clear of the bars with the window insets.
 * Called before `setContent`.
 */
fun ComponentActivity.enableShifterEdgeToEdge() {
  val style = SystemBarStyle.dark(Color.TRANSPARENT)
  enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}
