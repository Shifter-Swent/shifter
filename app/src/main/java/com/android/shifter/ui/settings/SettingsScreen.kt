// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.swent.shifter.R
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.ui.navigation.NavigationTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.shifter_signoutOutline

object SettingsScreenTestTags {
  const val SCREEN = "SettingsScreen"
  const val SIGN_OUT_BUTTON = "SettingsSignOutButton"
  const val CHANGE_MODE_BUTTON = "SettingsChangeModeButton"
}

/**
 * [SettingsScreen] wired to its [SettingsViewModel]: signs the user out, then calls [onSignedOut]
 * so the caller can leave the signed-in graph, or changes their mode, then calls [onModeChanged]
 * with the new one. A failed sign-out is shown as a toast, a failed change of mode in a Snackbar
 * with Retry.
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onModeChanged: (UserMode) -> Unit,
    viewModel: SettingsViewModel,
    credentialManager: CredentialManager = CredentialManager.create(LocalContext.current),
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val modeChangeError = stringResource(R.string.settings_change_mode_error)
  val retry = stringResource(R.string.settings_retry)

  LaunchedEffect(uiState.modeChanged) {
    uiState.modeChanged?.let {
      onModeChanged(it)
      viewModel.onModeChangeHandled()
    }
  }

  LaunchedEffect(uiState.modeChangeFailed) {
    if (uiState.modeChangeFailed) {
      // Reported once dismissed: resetting the flag earlier would restart this effect, cancelling
      // the Snackbar.
      val result = snackbarHostState.showSnackbar(modeChangeError, actionLabel = retry)
      viewModel.onModeChangeErrorShown()
      if (result == SnackbarResult.ActionPerformed) viewModel.changeMode()
    }
  }

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

  SettingsScreen(
      mode = uiState.mode,
      changingMode = uiState.changingMode,
      onBack = onBack,
      onChangeMode = viewModel::changeMode,
      onSignOut = { viewModel.signOut(credentialManager) },
      snackbarHostState = snackbarHostState,
  )
}

/**
 * Profile & Settings screen (Figma "Shifter · Settings"). For now it holds the header, the change
 * of [mode] and the Sign out action; the preference sections come later.
 *
 * Stateless: the caller changes the mode, signs the user out and navigates.
 */
@Composable
fun SettingsScreen(
    mode: UserMode,
    onBack: () -> Unit,
    onChangeMode: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    changingMode: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  Scaffold(
      modifier = modifier.fillMaxSize().testTag(SettingsScreenTestTags.SCREEN),
      containerColor = MaterialTheme.colorScheme.background,
      snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { padding ->
    Column(modifier = Modifier.padding(padding)) {
      SettingsHeader(onBack = onBack)
      Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        ChangeModeButton(mode = mode, enabled = !changingMode, onClick = onChangeMode)
        SignOutButton(onClick = onSignOut)
      }
    }
  }
}

/** Switches to the mode other than [mode]. */
@Composable
private fun ChangeModeButton(mode: UserMode, enabled: Boolean, onClick: () -> Unit) {
  OutlinedButton(
      onClick = onClick,
      enabled = enabled,
      modifier =
          Modifier.fillMaxWidth().height(48.dp).testTag(SettingsScreenTestTags.CHANGE_MODE_BUTTON),
      shape = RoundedCornerShape(16.dp),
  ) {
    Text(
        text =
            stringResource(
                when (mode) {
                  UserMode.VOLUNTEER -> R.string.settings_switch_to_organizer
                  UserMode.ORGANIZER -> R.string.settings_switch_to_volunteer
                }
            ),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Column(modifier = Modifier.fillMaxWidth().background(colors.surfaceContainer)) {
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 18.dp),
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
            contentDescription = stringResource(R.string.content_description_back),
            modifier = Modifier.size(18.dp),
        )
      }
      Text(
          text = stringResource(R.string.settings_title),
          modifier = Modifier.weight(1f).testTag(NavigationTestTags.TOP_BAR_TITLE),
          color = colors.onSurface,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
      )
      // Keeps the title centered against the back button.
      Spacer(modifier = Modifier.size(38.dp))
    }
    HorizontalDivider(color = colors.outlineVariant)
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
  ShifterTheme {
    SettingsScreen(mode = UserMode.VOLUNTEER, onBack = {}, onChangeMode = {}, onSignOut = {})
  }
}
