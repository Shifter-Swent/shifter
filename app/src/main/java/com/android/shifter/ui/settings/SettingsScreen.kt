// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.swent.shifter.R
import com.swent.shifter.ui.components.ShifterTopBar
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.shifter_signoutOutline

object SettingsScreenTestTags {
  const val SCREEN = "SettingsScreen"
  const val SIGN_OUT_BUTTON = "SettingsSignOutButton"
}

/**
 * [SettingsScreen] wired to its [SettingsViewModel]: signs the user out, then calls [onSignedOut]
 * so the caller can leave the signed-in graph. A failed sign-out is shown as a toast.
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel,
    credentialManager: CredentialManager = CredentialManager.create(LocalContext.current),
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsState()

  LaunchedEffect(uiState.errorMsg) {
    uiState.errorMsg?.let {
      Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
      viewModel.clearErrorMsg()
    }
  }

  LaunchedEffect(uiState.signedOut) {
    if (uiState.signedOut) {
      onSignedOut()
      viewModel.onSignedOutHandled()
    }
  }

  SettingsScreen(onBack = onBack, onSignOut = { viewModel.signOut(credentialManager) })
}

/**
 * Profile & Settings screen (Figma "Shifter · Settings"). For now it only holds the header and the
 * Sign out action; the preference sections come later.
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
    ShifterTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
    Column(modifier = Modifier.padding(16.dp)) { SignOutButton(onClick = onSignOut) }
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
      border = BorderStroke(1.dp, shifter_signoutOutline),
      colors =
          ButtonDefaults.outlinedButtonColors(
              containerColor = colors.errorContainer,
              contentColor = colors.onErrorContainer,
          ),
  ) {
    Text(
        text = stringResource(R.string.settings_sign_out),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )
  }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
  ShifterTheme { SettingsScreen(onBack = {}, onSignOut = {}) }
}
