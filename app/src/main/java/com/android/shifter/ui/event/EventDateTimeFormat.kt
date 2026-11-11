// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/**
 * How a date and time is written in the event form, e.g. `24/12/2027 18:30`: what the organizer
 * types, what the pickers write back, and what [CreateEventViewModel] validates. One definition, so
 * the screen never reads a value differently from the ViewModel.
 *
 * Strict, so an impossible date such as 31/02 is rejected rather than adjusted to another day: the
 * default resolver would read `31/02/2027` as the 28th, and a picker preselecting that would let
 * two taps write a day the organizer never chose.
 */
internal val EVENT_DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm").withResolverStyle(ResolverStyle.STRICT)

/**
 * [text] read with [EVENT_DATE_TIME_FORMATTER], surrounding spaces ignored, or null when it is not
 * a complete, possible date and time in that format.
 */
internal fun parseEventDateTime(text: String): LocalDateTime? =
    try {
      LocalDateTime.parse(text.trim(), EVENT_DATE_TIME_FORMATTER)
    } catch (_: DateTimeParseException) {
      null
    }
