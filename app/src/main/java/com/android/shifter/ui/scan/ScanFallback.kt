// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.ui.components.PrimaryButton

/**
 * Shown instead of the camera when it cannot be used: says why, and leads to the typed code.
 *
 * @param onEnterCode goes back to the typed code.
 * @param onAllowCamera asks for the permission again; its button only shows for
 *   [ScanFallbackReason.PERMISSION_REFUSED].
 */
@Composable
fun ScanFallback(
    reason: ScanFallbackReason,
    onEnterCode: () -> Unit,
    onAllowCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val refused = reason == ScanFallbackReason.PERMISSION_REFUSED
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(colors.background)
              .padding(16.dp)
              .testTag(ScanScreenTestTags.FALLBACK)
  ) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
      Box(
          modifier = Modifier.size(72.dp).background(colors.surfaceVariant, CircleShape),
          contentAlignment = Alignment.Center,
      ) {
        Icon(
            imageVector = Icons.Outlined.NoPhotography,
            contentDescription = null,
            tint = colors.onSurface,
            modifier = Modifier.size(32.dp),
        )
      }
      Spacer(Modifier.height(18.dp))
      Text(
          text =
              stringResource(
                  if (refused) R.string.scan_fallback_refused_title
                  else R.string.scan_fallback_error_title
              ),
          color = colors.onSurface,
          fontSize = 22.sp,
          fontWeight = FontWeight.Bold,
      )
      Spacer(Modifier.height(18.dp))
      Text(
          text =
              stringResource(
                  if (refused) R.string.scan_fallback_refused_message
                  else R.string.scan_fallback_error_message
              ),
          color = colors.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium,
      )
    }
    PrimaryButton(
        text = stringResource(R.string.scan_fallback_enter_code),
        onClick = onEnterCode,
        testTag = ScanScreenTestTags.FALLBACK_ENTER_CODE,
    )
    if (refused) {
      TextButton(
          onClick = onAllowCamera,
          modifier =
              Modifier.fillMaxWidth()
                  .padding(top = 6.dp)
                  .height(46.dp)
                  .testTag(ScanScreenTestTags.ALLOW_CAMERA),
      ) {
        Text(
            text = stringResource(R.string.scan_allow_camera),
            color = colors.primary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}
