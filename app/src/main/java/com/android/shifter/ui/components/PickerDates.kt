// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
package com.swent.shifter.ui.components

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
