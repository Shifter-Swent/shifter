// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.swent.shifter.R
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.ui.components.ShifterTopBar
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.shifter_signoutOutline

object SettingsScreenTestTags {
  const val SCREEN = "SettingsScreen"
  const val SIGN_OUT_BUTTON = "SettingsSignOutButton"
  const val CHANGE_MODE_BUTTON = "SettingsChangeModeButton"
  const val LOCATION_SHARING_SWITCH = "SettingsLocationSharingSwitch"
  const val LOCATION_SHARING_NOTICE = "SettingsLocationSharingNotice"
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
      // Long: offline, the user needs time to get the network back before tapping Retry.
      val result =
          snackbarHostState.showSnackbar(
              modeChangeError,
              actionLabel = retry,
              duration = SnackbarDuration.Long,
          )
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
      state = uiState,
      onBack = onBack,
      onChangeMode = viewModel::changeMode,
      onSignOut = { viewModel.signOut(credentialManager) },
      onLocationSharingChange = viewModel::setLocationSharing,
      snackbarHostState = snackbarHostState,
  )
}

/**
 * Profile & Settings screen (Figma "Shifter · Settings"): the location sharing switch, the change
 * of mode and the Sign out action.
 *
 * Stateless: the caller changes the mode, signs the user out and navigates, and saves the switch.
 * The switch is disabled while the setting is unknown or being saved.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onChangeMode: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    onLocationSharingChange: (Boolean) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  Scaffold(
      modifier = modifier.fillMaxSize().testTag(SettingsScreenTestTags.SCREEN),
      containerColor = MaterialTheme.colorScheme.background,
      topBar = { ShifterTopBar(title = stringResource(R.string.settings_title), onBack = onBack) },
      snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { padding ->
    Column(
        modifier = Modifier.padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      LocationSharingSetting(state, onLocationSharingChange)
      ChangeModeButton(mode = state.mode, enabled = !state.changingMode, onClick = onChangeMode)
      SignOutButton(onClick = onSignOut)
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
private fun LocationSharingSetting(state: SettingsUiState, onChange: (Boolean) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
          text = stringResource(R.string.settings_location_sharing_title),
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.weight(1f),
      )
      Switch(
          checked = state.locationSharing == true,
          onCheckedChange = onChange,
          enabled = state.locationSharing != null && !state.savingLocationSharing,
          modifier = Modifier.testTag(SettingsScreenTestTags.LOCATION_SHARING_SWITCH),
      )
    }
    Text(
        text = stringResource(R.string.settings_location_sharing_explanation),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.locationSharingNotice?.let { notice ->
      Text(
          text = stringResource(notice.message),
          style = MaterialTheme.typography.bodySmall,
          color =
              if (notice == LocationSharingNotice.SAVED_OFFLINE) MaterialTheme.colorScheme.onSurface
              else MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag(SettingsScreenTestTags.LOCATION_SHARING_NOTICE),
      )
    }
  }
}

private val LocationSharingNotice.message: Int
  get() =
      when (this) {
        LocationSharingNotice.LOAD_FAILED -> R.string.settings_location_sharing_load_failed
        LocationSharingNotice.SAVE_FAILED -> R.string.settings_location_sharing_save_failed
        LocationSharingNotice.SAVED_OFFLINE -> R.string.settings_location_sharing_saved_offline
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
    SettingsScreen(
        state = SettingsUiState(locationSharing = true),
        onBack = {},
        onChangeMode = {},
        onSignOut = {},
    )
  }
}
