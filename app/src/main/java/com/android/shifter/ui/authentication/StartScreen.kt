// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.authentication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.swent.shifter.model.user.UserMode

object StartScreenTestTags {
  const val LOADING = "startLoading"
}

/** A spinner while [viewModel] reads the user's mode, then [onModeRead] enters it. */
@Composable
fun StartScreen(viewModel: StartViewModel, onModeRead: (UserMode) -> Unit) {
  val mode by viewModel.mode.collectAsState()
  LaunchedEffect(mode) { mode?.let(onModeRead) }
  Box(
      modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
      contentAlignment = Alignment.Center,
  ) {
    CircularProgressIndicator(
        modifier = Modifier.size(48.dp).testTag(StartScreenTestTags.LOADING),
        color = MaterialTheme.colorScheme.primary,
    )
  }
}
