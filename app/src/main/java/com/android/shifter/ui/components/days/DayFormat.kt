// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.days

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The day of the week, short: "Sun". */
fun LocalDate.shortWeekday(): String = SHORT_WEEKDAY.format(this)

/** The whole date: "Sunday 15 June". */
fun LocalDate.fullDate(): String = FULL_DATE.format(this)

private val SHORT_WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val FULL_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)
