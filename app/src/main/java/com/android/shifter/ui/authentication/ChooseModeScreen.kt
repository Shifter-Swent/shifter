// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.authentication

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.model.user.UserMode

object ChooseModeTestTags {
  const val SCREEN = "chooseModeScreen"
  const val TITLE = "chooseModeTitle"
  const val VOLUNTEER = "chooseModeVolunteer"
  const val ORGANIZER = "chooseModeOrganizer"
  const val SAVING = "chooseModeSaving"
}

/**
 * Asks a new user whether they are a Volunteer or an Organizer, then [onModeChosen] enters the mode
 * saved on the profile [viewModel] creates.
 */
@Composable
fun ChooseModeScreen(viewModel: ChooseModeViewModel, onModeChosen: (UserMode) -> Unit) {
  val context = LocalContext.current
  val colors = MaterialTheme.colorScheme
  val uiState by viewModel.uiState.collectAsState()

  LaunchedEffect(uiState.entered) {
    uiState.entered?.let { mode ->
      onModeChosen(mode)
      viewModel.onEnteredHandled()
    }
  }
  // Shown before signing out, which takes the user back to sign-in.
  LaunchedEffect(uiState.errorMsg) {
    uiState.errorMsg?.let {
      Toast.makeText(context, it, Toast.LENGTH_LONG).show()
      viewModel.onErrorShown()
    }
  }

  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(colors.background)
              .safeDrawingPadding()
              .padding(horizontal = 43.dp)
              .testTag(ChooseModeTestTags.SCREEN),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    Text(
        text = stringResource(R.string.choose_mode_title),
        color = colors.onBackground,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(ChooseModeTestTags.TITLE),
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.choose_mode_explanation),
        color = colors.secondary,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(32.dp))

    if (uiState.saving) {
      CircularProgressIndicator(
          modifier = Modifier.size(48.dp).testTag(ChooseModeTestTags.SAVING),
          color = colors.primary,
      )
    } else {
      Button(
          onClick = { viewModel.choose(UserMode.VOLUNTEER) },
          modifier = Modifier.fillMaxWidth().height(58.dp).testTag(ChooseModeTestTags.VOLUNTEER),
          shape = RoundedCornerShape(18.dp),
      ) {
        Text(stringResource(R.string.choose_mode_volunteer), fontSize = 16.sp)
      }
      Spacer(modifier = Modifier.height(16.dp))
      OutlinedButton(
          onClick = { viewModel.choose(UserMode.ORGANIZER) },
          modifier = Modifier.fillMaxWidth().height(58.dp).testTag(ChooseModeTestTags.ORGANIZER),
          shape = RoundedCornerShape(18.dp),
      ) {
        Text(stringResource(R.string.choose_mode_organizer), fontSize = 16.sp)
      }
    }
  }
}
