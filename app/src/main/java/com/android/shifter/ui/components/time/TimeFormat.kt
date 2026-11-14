// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.time

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import com.swent.shifter.R
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The time of day of this instant in [zone], as the app writes it everywhere: "14:00". */
fun Instant.formatTime(zone: ZoneId): String = TIME.format(atZone(zone))

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

/**
 * The [duration] as the now card writes it: "25 min", "1 h", "1 h 05". Rounded down to the minute
 * and never negative; for a countdown, give it [minutesLeft] so it does not read "0 min" while time
 * is left.
 */
fun formatDuration(resources: Resources, duration: Duration): String {
  val minutes = duration.toMinutes().coerceAtLeast(0)
  val hours = minutes / MINUTES_PER_HOUR
  val rest = minutes % MINUTES_PER_HOUR
  return when {
    hours == 0L -> resources.getString(R.string.now_card_minutes, rest)
    rest == 0L -> resources.getString(R.string.now_card_hours, hours)
    else -> resources.getString(R.string.now_card_hours_minutes, hours, rest)
  }
}

/** [formatDuration] with the composition's resources, updated when the configuration changes. */
@Composable
@ReadOnlyComposable
fun formatDuration(duration: Duration): String = formatDuration(LocalResources.current, duration)

/**
 * The time from [now] until [to], rounded up to the minute, and zero once [to] has passed: a
 * countdown reads "1 min" in its last seconds, then nothing.
 */
fun minutesLeft(now: Instant, to: Instant): Duration {
  val left = Duration.between(now, to)
  if (!left.isPositive) return Duration.ZERO
  val minutes = left.toMinutes()
  return Duration.ofMinutes(if (left == Duration.ofMinutes(minutes)) minutes else minutes + 1)
}

private const val MINUTES_PER_HOUR = 60L
