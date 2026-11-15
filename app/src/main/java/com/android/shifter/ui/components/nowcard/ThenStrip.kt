// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.nowcard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.ui.components.time.formatDuration
import java.time.Duration

/**
 * What comes next, at the foot of the now card and edge to edge: "Then, in 30 min", then the
 * mission's [title] and its [place]. Pass it as the `strip` of [NowCardContainer], which leaves it
 * unpadded.
 *
 * @param startsIn how long until it starts; give it
 *   [com.swent.shifter.ui.components.time.minutesLeft] so it never reads "0 min" while the mission
 *   has not started.
 * @param note a short line right under the mission, such as how many volunteers are missing.
 * @param action the card's action, when the strip takes it.
 */
@Composable
fun ThenStrip(
    startsIn: Duration,
    title: String,
    place: String?,
    modifier: Modifier = Modifier,
    note: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .testTag(NowCardTestTags.NEXT)
              .background(MaterialTheme.colorScheme.surface.copy(alpha = STRIP_ALPHA))
              .padding(horizontal = 20.dp, vertical = 16.dp)
  ) {
    Text(
        text = stringResource(R.string.now_card_then_in, formatDuration(startsIn)),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        lineHeight = 15.sp,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = place?.let { stringResource(R.string.now_card_then_what, title, it) } ?: title,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 15.sp,
        lineHeight = 19.sp,
    )
    note?.let {
      Spacer(Modifier.height(4.dp))
      it()
    }
    action?.let {
      Spacer(Modifier.height(16.dp))
      it()
    }
  }
}

/** Darker than the card's field, as the design separates what is next from what is now. */
private const val STRIP_ALPHA = 0.4f
