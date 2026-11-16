// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.time

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import com.swent.shifter.R
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The time of day of this instant in [zone], as the app writes it everywhere: "14:00". */
fun Instant.formatTime(zone: ZoneId): String = TIME.format(atZone(zone))

/** This local time as the app writes it everywhere, like [Instant.formatTime]: "14:00". */
fun LocalTime.formatTime(): String = TIME.format(this)

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

/**
 * "21 Jul", or "21 Jul 2026" [withYear]. The caller converts to its zone and decides when the year
 * is needed, since each screen has its own rule.
 */
fun LocalDate.formatDay(withYear: Boolean = false): String =
    (if (withYear) DAY_YEAR else DAY).format(this)

/**
 * "Mon 22 Jun", or "Mon 22 Jun 2026" [withYear], like [formatDay]. The app writes dates in English;
 * [locale] is only for the mission form, which keeps the device's.
 */
fun LocalDate.formatWeekday(withYear: Boolean = false, locale: Locale = Locale.ENGLISH): String =
    (if (withYear) WEEKDAY_YEAR else WEEKDAY).withLocale(locale).format(this)

/** The day of the week alone, short: "Sun". */
fun LocalDate.formatDayOfWeek(): String = DAY_OF_WEEK.format(this)

/** The whole date, without the year: "Sunday 15 June". */
fun LocalDate.formatFullDate(): String = FULL_DATE.format(this)

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val DAY_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH)
private val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val WEEKDAY_YEAR: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM uuuu", Locale.ENGLISH)
private val DAY_OF_WEEK: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val FULL_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)

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
