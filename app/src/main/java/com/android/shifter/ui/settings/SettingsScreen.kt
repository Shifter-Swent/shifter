// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.ui.navigation.NavigationTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.shifter_errorOutline
import com.swent.shifter.ui.theme.shifter_headerBackground

object SettingsScreenTestTags {
  const val SCREEN = "SettingsScreen"
  const val SIGN_OUT_BUTTON = "SettingsSignOutButton"
}

/**
 * Profile & Settings screen (Figma "Shifter · Settings"). For now it only holds the header and
 * the Sign out action; the preference sections come later.
 *
 * Stateless: the caller signs the user out and navigates (e.g. `enterApp(SignedOut)`).
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .testTag(SettingsScreenTestTags.SCREEN)
  ) {
    SettingsHeader(onBack = onBack)
    Column(modifier = Modifier.padding(16.dp)) { SignOutButton(onClick = onSignOut) }
  }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .height(72.dp)
              .background(shifter_headerBackground)
              .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(
                    color = colors.outlineVariant,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
              }
              .padding(horizontal = 18.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(
        onClick = onBack,
        modifier = Modifier.size(38.dp).testTag(NavigationTestTags.GO_BACK_BUTTON),
        shape = CircleShape,
        colors =
            IconButtonDefaults.iconButtonColors(
                containerColor = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer,
            ),
    ) {
      Icon(
          painter = painterResource(R.drawable.ic_chevron_left),
          contentDescription = "Back",
          modifier = Modifier.size(18.dp),
      )
    }
    Text(
        text = "Settings",
        modifier = Modifier.weight(1f).testTag(NavigationTestTags.TOP_BAR_TITLE),
        color = colors.onSurface,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    // Keeps the title centered against the back button.
    Spacer(modifier = Modifier.size(38.dp))
  }
}

@Composable
private fun SignOutButton(onClick: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  OutlinedButton(
      onClick = onClick,
      modifier =
          Modifier.fillMaxWidth().height(48.dp).testTag(SettingsScreenTestTags.SIGN_OUT_BUTTON),
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, shifter_errorOutline),
      colors =
          ButtonDefaults.outlinedButtonColors(
              containerColor = colors.errorContainer,
              contentColor = colors.onErrorContainer,
          ),
  ) {
    Text(text = "Sign out", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
  }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
  ShifterTheme { SettingsScreen(onBack = {}, onSignOut = {}) }
}
