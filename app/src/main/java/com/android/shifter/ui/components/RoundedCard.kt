// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * A rounded, outlined block of a screen, as on the organizer's Overview and the proposed schedule.
 *
 * @param borderColor the outline's colour, e.g. amber for a mission still short of people.
 */
@Composable
fun RoundedCard(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    content: @Composable () -> Unit,
) {
  val shape = RoundedCornerShape(18.dp)
  Box(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(shape)
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .border(1.dp, borderColor, shape)
              .padding(16.dp)
  ) {
    content()
  }
}

/**
 * Why a screen could not be loaded, in red, with a button to try again. Each screen gives its own
 * texts and test tags.
 *
 * @param messageTag the test tag of the message.
 * @param retryTag the test tag of the button.
 */
@Composable
fun LoadErrorRow(
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    messageTag: String,
    retryTag: String,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.weight(1f).testTag(messageTag),
    )
    TextButton(onClick = onRetry, modifier = Modifier.testTag(retryTag)) { Text(retryLabel) }
  }
}
