// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.ui.navigation.NavigationTestTags

/**
 * The header of every screen with a way back: the [BackButton] calling [onBack] and the [title],
 * centered on one line, ellipsized when too long.
 */
@Composable
fun ShifterTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  // Its background runs behind the status bar, while its content stays below it.
  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .background(colors.surfaceContainer)
              .windowInsetsPadding(WindowInsets.statusBars)
  ) {
    Row(
        // 20 dp, plus the 4 dp around the circle of the button, puts the circle at 24 dp.
        modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      BackButton(onClick = onBack)
      Text(
          text = title,
          modifier =
              Modifier.weight(1f).semantics { heading() }.testTag(NavigationTestTags.TOP_BAR_TITLE),
          color = colors.onSurface,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      // Keeps the title centered against the back button.
      Spacer(modifier = Modifier.size(48.dp))
    }
    HorizontalDivider(color = colors.outlineVariant)
  }
}
