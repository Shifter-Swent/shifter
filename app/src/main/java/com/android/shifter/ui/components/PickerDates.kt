// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * The Material3 date picker works in UTC milliseconds at midnight, whatever the device's zone, so a
 * day is converted to and from that representation here rather than through the device's zone,
 * where it could shift by one.
 */
private const val MILLIS_PER_DAY = 86_400_000L

/** This day as the UTC-midnight milliseconds the date picker preselects. */
internal fun LocalDate.toPickerMillis(): Long = toEpochDay() * MILLIS_PER_DAY

/** The day these picker milliseconds fall on, whatever their time of day. */
internal fun Long.toPickerDate(): LocalDate =
    LocalDate.ofEpochDay(Math.floorDiv(this, MILLIS_PER_DAY))

/**
 * A window shorter than this cannot show a whole calendar: Material gives the month six 48dp rows,
 * the weekdays another 48dp and the month row 56dp, so 392dp before the dialog's header and its
 * buttons. A phone in landscape is about 360dp high.
 */
private val SHORT_WINDOW_HEIGHT = 480.dp

/** Whether the window is too short to show a date picker's calendar whole. */
@Composable
internal fun isWindowShortForACalendar(): Boolean {
  val height = LocalWindowInfo.current.containerSize.height
  return with(LocalDensity.current) { height.toDp() } < SHORT_WINDOW_HEIGHT
}
