// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The bottom-left button that switches between the volunteer and the organizer views ("Organizer
 * View" / "Volunteer View"). It only reports the click: switching is left to the navigation.
 */
@Composable
fun BoxScope.SwitchViewButton(label: String, testTag: String, onClick: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Surface(
      onClick = onClick,
      shape = RoundedCornerShape(16.dp),
      color = colors.surfaceVariant,
      border = BorderStroke(1.dp, colors.outlineVariant),
      modifier =
          Modifier.align(Alignment.BottomStart)
              .padding(start = 18.dp, bottom = 80.dp)
              .testTag(testTag),
  ) {
    Text(
        text = label,
        color = colors.onSurface,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
  }
}
