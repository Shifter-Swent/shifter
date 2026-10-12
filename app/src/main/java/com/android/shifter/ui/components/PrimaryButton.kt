// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Full-width button styled like the sign-in button. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    testTag: String,
    enabled: Boolean = true,
) {
  val colors = MaterialTheme.colorScheme
  Button(
      onClick = onClick,
      enabled = enabled,
      shape = RoundedCornerShape(18.dp),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = colors.primary,
              contentColor = colors.onPrimary,
          ),
      modifier = Modifier.fillMaxWidth().height(58.dp).testTag(testTag),
  ) {
    Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
  }
}
