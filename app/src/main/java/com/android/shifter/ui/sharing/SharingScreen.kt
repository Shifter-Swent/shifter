// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.sharing

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.ui.components.QrCodeImage
import com.swent.shifter.ui.event.EventCreatedUiState
import com.swent.shifter.ui.event.EventCreatedViewModel
import com.swent.shifter.ui.event.JOIN_CODE_PLACEHOLDER
import com.swent.shifter.ui.event.JoinCodeCard
import com.swent.shifter.ui.event.JoinCodeCardSize
import com.swent.shifter.ui.event.JoinCodeCardTestTags
import com.swent.shifter.ui.event.JoinCodeUnavailableCard
import com.swent.shifter.ui.event.rememberCopyJoinCode
import com.swent.shifter.ui.theme.shifter_mutedText
import com.swent.shifter.ui.theme.shifter_qrLight

object SharingScreenTestTags {
  const val SCREEN = "SharingScreen"
  const val QR_CODE = "SharingQrCode"
  const val LOADING_INDICATOR = "SharingLoadingIndicator"
  const val JOIN_CODE = JoinCodeCardTestTags.CODE
  const val COPY_BUTTON = JoinCodeCardTestTags.COPY_BUTTON
  const val UNAVAILABLE = JoinCodeCardTestTags.UNAVAILABLE
  const val RETRY_BUTTON = JoinCodeCardTestTags.RETRY_BUTTON
  const val SHARE_BUTTON = "SharingShareCodeButton"
  const val EXPORT_BUTTON = "SharingExportQrButton"
  const val SNACKBAR = "SharingSnackbar"
}

/**
 * The Sharing tab of the organizer's event: its QR code, its join code to copy, and the buttons to
 * share them, so volunteers can join at any time, not only right after the event was created.
 *
 * @param sharer sends the code to another app when the organizer taps Share.
 */
@Composable
fun SharingScreen(viewModel: EventCreatedViewModel, sharer: EventSharer) {
  val state by viewModel.uiState.collectAsState()
  SharingContent(
      state = state,
      onShare = sharer::share,
      // Exporting the QR code as an image comes with #506.
      onExport = {},
      onRetry = viewModel::retry,
  )
}

/**
 * Stateless content of [SharingScreen]: renders [state] and reports every interaction. The copy of
 * the code is done here, as it concerns the clipboard and no ViewModel.
 *
 * @param onShare called with the event's name and its code when the organizer taps Share.
 */
@Composable
fun SharingContent(
    state: EventCreatedUiState,
    onShare: (eventTitle: String, joinCode: String) -> Unit,
    onExport: () -> Unit,
    onRetry: () -> Unit,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val copyJoinCode = rememberCopyJoinCode(snackbarHostState)
  val ready = !state.isLoading && state.error == null
  BoxWithConstraints(Modifier.fillMaxSize().testTag(SharingScreenTestTags.SCREEN)) {
    Column(
        // At least as tall as the page, so the buttons sit at its bottom, yet scrolls on a short
        // screen rather than hiding them.
        modifier =
            Modifier.verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        QrCodeSquare(state, ready)
        Text(
            text = stringResource(R.string.sharing_hint),
            style = MaterialTheme.typography.labelSmall,
            color = shifter_mutedText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        when {
          state.isLoading ->
              JoinCodeCard(
                  joinCode = JOIN_CODE_PLACEHOLDER,
                  onCopy = {},
                  size = JoinCodeCardSize.Compact,
                  copyEnabled = false,
              )
          state.error != null -> JoinCodeUnavailableCard(onRetry)
          else ->
              JoinCodeCard(
                  joinCode = state.joinCode,
                  onCopy = { copyJoinCode(state.joinCode) },
                  size = JoinCodeCardSize.Compact,
              )
        }
      }
      Column(
          modifier = Modifier.padding(top = 16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
            onClick = { onShare(state.eventTitle, state.joinCode) },
            enabled = ready,
            shape = MaterialTheme.shapes.medium,
            modifier =
                Modifier.fillMaxWidth().height(48.dp).testTag(SharingScreenTestTags.SHARE_BUTTON),
        ) {
          ButtonLabel(Icons.Outlined.Share, R.string.sharing_share_code)
        }
        FilledTonalButton(
            onClick = onExport,
            enabled = ready,
            shape = MaterialTheme.shapes.medium,
            modifier =
                Modifier.fillMaxWidth().height(48.dp).testTag(SharingScreenTestTags.EXPORT_BUTTON),
        ) {
          ButtonLabel(Icons.Outlined.Image, R.string.sharing_export_qr)
        }
      }
    }
    SnackbarHost(
        snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).testTag(SharingScreenTestTags.SNACKBAR),
    )
  }
}

/**
 * The QR code on a light square, which scanners need whatever the theme. The square keeps its size
 * while the code loads or cannot be read, so nothing below it moves.
 */
@Composable
private fun QrCodeSquare(state: EventCreatedUiState, ready: Boolean) {
  Box(
      modifier =
          Modifier.size(272.dp)
              .clip(MaterialTheme.shapes.large)
              .background(shifter_qrLight)
              .padding(16.dp),
      contentAlignment = Alignment.Center,
  ) {
    when {
      // QrCodeImage cannot draw an empty code, which an event has until the backend assigns one.
      ready && state.joinCode.isNotEmpty() ->
          QrCodeImage(
              state.joinCode,
              Modifier.fillMaxSize().testTag(SharingScreenTestTags.QR_CODE),
          )
      state.isLoading ->
          CircularProgressIndicator(Modifier.testTag(SharingScreenTestTags.LOADING_INDICATOR))
    }
  }
}

@Composable
private fun ButtonLabel(icon: ImageVector, @StringRes text: Int) {
  Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
  Text(
      text = stringResource(text),
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(start = 8.dp),
  )
}
