// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.nowcard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.model.time.formatTime
import com.swent.shifter.ui.components.time.formatDuration
import com.swent.shifter.ui.components.time.minutesLeft
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * A mission's time: filled from [from] to [to] as [now] moves, empty before and full after, with
 * its start and end times under it ("14:00" … "15:30") and, once it runs and in its last hour, the
 * time left ("10 min left · 15:30").
 *
 * The bar is one node for accessibility and tests, tagged [NowCardTestTags.TIME_BAR]: its title and
 * labels are found on it, and its progress is read from it. Do not tag it again through [modifier].
 *
 * @param color the fill, the card's tone ([NowTone.accent]).
 * @param title the mission's title above the bar, when the card shows several.
 */
@Composable
fun TimeBar(
    from: Instant,
    to: Instant,
    now: Instant,
    color: Color,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
  val end = to.formatTime(zone)
  val left = minutesLeft(now, to)
  Track(
      progress = progress(from, to, now),
      color = color,
      startLabel = from.formatTime(zone),
      endLabel =
          if (now >= from && left.isPositive && left < SHOWS_TIME_LEFT_UNDER) {
            stringResource(R.string.now_card_time_left, formatDuration(left), end)
          } else {
            end
          },
      title = title,
      modifier = modifier,
  )
}

/**
 * The wait before a mission's [start]: filled over the [window] before it ("Now" … "Starts 14:00"),
 * empty before the window and full once it starts. Like [TimeBar], it is one tagged node.
 *
 * @param window how long before its start a mission is soon, as the card's state decides it.
 * @param color the fill, the card's tone ([NowTone.accent]).
 */
@Composable
fun CountdownBar(
    start: Instant,
    now: Instant,
    window: Duration,
    color: Color,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
  Track(
      progress = progress(start.minus(window), start, now),
      color = color,
      startLabel = stringResource(R.string.now_card_now),
      endLabel = stringResource(R.string.now_card_starts_at, start.formatTime(zone)),
      modifier = modifier,
  )
}

/** How much of the time from [from] to [to] has passed at [now], from 0 to 1. */
private fun progress(from: Instant, to: Instant, now: Instant): Float {
  val total = Duration.between(from, to).toMillis()
  if (total <= 0) return if (now < to) 0f else 1f
  return (Duration.between(from, now).toMillis().toFloat() / total).coerceIn(0f, 1f)
}

/**
 * The bar itself: the [title], a rounded track filled to [progress] in [color], and the labels
 * under its two ends. The fill animates when [progress] changes; accessibility services and tests
 * read [progress] itself.
 */
@Composable
private fun Track(
    progress: Float,
    color: Color,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
  val fill by animateFloatAsState(progress, label = "timeBarFill")
  val track = MaterialTheme.colorScheme.onSurface.copy(alpha = TRACK_ALPHA)
  Column(
      modifier =
          modifier.fillMaxWidth().testTag(NowCardTestTags.TIME_BAR).semantics(
              mergeDescendants = true
          ) {
            progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
          },
      verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    title?.let {
      Text(
          text = it,
          color = MaterialTheme.colorScheme.onSurface,
          fontSize = 13.sp,
          lineHeight = 17.sp,
          fontWeight = FontWeight.SemiBold,
      )
    }
    Box(
        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).drawBehind {
          drawRect(track)
          val width = size.width * fill
          val left = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
          drawRect(color, topLeft = Offset(left, 0f), size = Size(width, size.height))
        }
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Label(startLabel)
      Label(endLabel)
    }
  }
}

@Composable
private fun Label(text: String) {
  Text(
      text = text,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = 12.sp,
      lineHeight = 15.sp,
  )
}

/** Below this, a running bar says how long is left next to its end. */
private val SHOWS_TIME_LEFT_UNDER: Duration = Duration.ofHours(1)

private const val TRACK_ALPHA = 0.12f
