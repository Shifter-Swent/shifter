// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.ui.theme.shifter_success
import com.swent.shifter.ui.theme.shifter_successContainer
import com.swent.shifter.ui.theme.shifter_warning
import com.swent.shifter.ui.theme.shifter_warningContainer

/** The content and container colors of a [Pill]. */
data class PillColors(val content: Color, val container: Color)

/**
 * A short [text] in a rounded, colored capsule, such as a status.
 *
 * @param large the bigger variant, for a pill shown on its own rather than inside a card.
 */
@Composable
fun Pill(text: String, colors: PillColors, modifier: Modifier = Modifier, large: Boolean = false) {
  Text(
      text = text,
      color = colors.content,
      fontSize = if (large) 13.sp else 11.sp,
      fontWeight = FontWeight.SemiBold,
      modifier =
          modifier
              .clip(RoundedCornerShape(20.dp))
              .background(colors.container)
              .padding(
                  horizontal = if (large) 12.dp else 10.dp,
                  vertical = if (large) 6.dp else 4.dp,
              ),
  )
}

/** The colors of a membership request [status], the same wherever the request is shown. */
@Composable
fun membershipStatusColors(status: MembershipRequestStatus): PillColors =
    when (status) {
      MembershipRequestStatus.PENDING -> PillColors(shifter_warning, shifter_warningContainer)
      MembershipRequestStatus.ACCEPTED -> PillColors(shifter_success, shifter_successContainer)
      MembershipRequestStatus.REJECTED ->
          PillColors(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer)
    }
