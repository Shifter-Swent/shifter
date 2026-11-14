// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.time

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The time of day of this instant in [zone], as the app writes it everywhere: "14:00". */
fun Instant.formatTime(zone: ZoneId): String = TIME.format(atZone(zone))

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
