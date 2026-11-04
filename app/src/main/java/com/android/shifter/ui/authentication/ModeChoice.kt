// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.authentication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.model.user.UserMode

/**
 * Asks how the user will use Shifter, once, after their first sign-in. Stateless: [onChoose]
 * reports the mode picked.
 */
@Composable
fun ModeChoice(onChoose: (UserMode) -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Column(
      modifier = modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
        text = stringResource(R.string.sign_in_mode_title),
        color = colors.onBackground,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.testTag(SignInScreenTestTags.MODE_TITLE),
    )
    Button(
        onClick = { onChoose(UserMode.VOLUNTEER) },
        shape = RoundedCornerShape(18.dp),
        modifier =
            Modifier.fillMaxWidth().height(58.dp).testTag(SignInScreenTestTags.MODE_VOLUNTEER),
    ) {
      Text(stringResource(R.string.sign_in_mode_volunteer), fontSize = 16.sp)
    }
    OutlinedButton(
        onClick = { onChoose(UserMode.ORGANIZER) },
        shape = RoundedCornerShape(18.dp),
        modifier =
            Modifier.fillMaxWidth().height(58.dp).testTag(SignInScreenTestTags.MODE_ORGANIZER),
    ) {
      Text(stringResource(R.string.sign_in_mode_organizer), fontSize = 16.sp)
    }
    Text(
        text = stringResource(R.string.sign_in_mode_final),
        color = colors.secondary,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
    )
  }
}
