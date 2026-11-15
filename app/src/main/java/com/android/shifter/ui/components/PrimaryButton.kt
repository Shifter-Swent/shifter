// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-width button styled like the sign-in button, for standalone actions such as Apply. For the
 * submit button of a form, use FormPrimaryButton
 *
 * @param icon drawn before the text; decorative, the text says what the button does.
 * @param colors the primary colors by default, e.g. the warning ones for an action that needs the
 *   user.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    testTag: String,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    colors: ButtonColors =
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
) {
  Button(
      onClick = onClick,
      enabled = enabled,
      shape = RoundedCornerShape(18.dp),
      colors = colors,
      modifier = Modifier.fillMaxWidth().height(58.dp).testTag(testTag),
  ) {
    icon?.let {
      Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
    }
    Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
  }
}
