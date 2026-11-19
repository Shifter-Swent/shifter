// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.ui.components.PrimaryButton
import com.swent.shifter.ui.event.JoinEventError
import com.swent.shifter.ui.event.formatEventPeriod
import com.swent.shifter.ui.event.messageRes
import com.swent.shifter.ui.theme.shifter_mutedText
import com.swent.shifter.ui.theme.shifter_success
import com.swent.shifter.ui.theme.shifter_warning

/**
 * What became of the code read, in a card under the frame: the event found, with Continue, or why
 * none was, with Scan again. While the event is looked up, the code read and a spinner.
 */
@Composable
fun ScanResultSheet(
    result: ScanResult,
    onContinue: () -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(20.dp))
              .background(MaterialTheme.colorScheme.surfaceContainer)
              .padding(20.dp)
              .testTag(ScanScreenTestTags.RESULT_SHEET),
      verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    when (result) {
      is ScanResult.Looking -> CodeRead(result.code, found = false)
      is ScanResult.Found -> EventFound(result, onContinue)
      is ScanResult.Failed -> NoEventFound(result.error, onScanAgain)
    }
  }
}

/** The code read, after a check once its event is found, or a spinner while it is looked up. */
@Composable
private fun CodeRead(code: String, found: Boolean) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (found) {
      Icon(
          imageVector = Icons.Filled.CheckCircle,
          contentDescription = null,
          tint = shifter_success,
          modifier = Modifier.size(ICON_SIZE),
      )
    } else {
      CircularProgressIndicator(
          modifier = Modifier.size(ICON_SIZE),
          color = MaterialTheme.colorScheme.primary,
          strokeWidth = 2.5.dp,
      )
    }
    Text(
        text = stringResource(R.string.scan_result_code),
        color = shifter_mutedText,
        fontSize = 14.sp,
    )
    Text(
        text = code,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 3.sp,
    )
  }
}

@Composable
private fun EventFound(result: ScanResult.Found, onContinue: () -> Unit) {
  val event = result.event
  CodeRead(result.code, found = true)
  Text(
      text = event.title,
      color = MaterialTheme.colorScheme.onSurface,
      fontSize = 22.sp,
      fontWeight = FontWeight.Bold,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
  )
  Text(
      text = "${formatEventPeriod(event.startAt, event.endAt)} · ${event.location.address}",
      color = shifter_mutedText,
      fontSize = 14.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
  )
  PrimaryButton(
      text = stringResource(R.string.scan_continue),
      onClick = onContinue,
      testTag = ScanScreenTestTags.CONTINUE,
  )
}

@Composable
private fun NoEventFound(error: JoinEventError, onScanAgain: () -> Unit) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(
        imageVector = Icons.Filled.Warning,
        contentDescription = null,
        tint = shifter_warning,
        modifier = Modifier.size(ICON_SIZE),
    )
    Text(
        text = stringResource(error.messageRes()),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    )
  }
  // Only a code no event uses calls for another one: the other errors are worth a new try.
  if (error == JoinEventError.UNKNOWN_CODE) {
    Text(
        text = stringResource(R.string.scan_unknown_code_help),
        color = shifter_mutedText,
        fontSize = 14.sp,
    )
  }
  PrimaryButton(
      text = stringResource(R.string.scan_again),
      onClick = onScanAgain,
      testTag = ScanScreenTestTags.RETRY,
  )
}

private val ICON_SIZE = 22.dp
