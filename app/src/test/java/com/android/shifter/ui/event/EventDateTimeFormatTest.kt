// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The one date-and-time format of the event form, shared by the ViewModel and the pickers: what it
 * reads, and above all what it refuses to read.
 */
class EventDateTimeFormatTest {

  @Test
  fun parseEventDateTime_readsTheFormOfTheForm() {
    assertEquals(LocalDateTime.of(2027, 7, 21, 14, 0), parseEventDateTime("21/07/2027 14:00"))
  }

  @Test
  fun parseEventDateTime_ignoresSurroundingSpaces() {
    assertEquals(LocalDateTime.of(2027, 7, 21, 14, 0), parseEventDateTime(" 21/07/2027 14:00 "))
  }

  /**
   * The regression the pickers guard against: a lenient parser reads 31/02 as the 28th, and a
   * picker preselecting that would write a day the organizer never typed. Strict, it reads nothing.
   */
  @Test
  fun parseEventDateTime_rejectsAnImpossibleDayInsteadOfAdjustingIt() {
    assertNull(parseEventDateTime("31/02/2027 10:00"))
    assertNull(parseEventDateTime("29/02/2027 10:00"))
    assertNull(parseEventDateTime("31/04/2027 10:00"))
    assertNull(parseEventDateTime("00/07/2027 10:00"))
  }

  @Test
  fun parseEventDateTime_acceptsALeapDay() {
    assertEquals(LocalDateTime.of(2028, 2, 29, 10, 0), parseEventDateTime("29/02/2028 10:00"))
  }

  @Test
  fun parseEventDateTime_rejectsAnImpossibleTime() {
    assertNull(parseEventDateTime("21/07/2027 24:00"))
    assertNull(parseEventDateTime("21/07/2027 14:60"))
  }

  @Test
  fun parseEventDateTime_rejectsAnotherFormatOrAPartialValue() {
    assertNull(parseEventDateTime(""))
    assertNull(parseEventDateTime("21/07/2027"))
    assertNull(parseEventDateTime("21/07/27 14:00"))
    assertNull(parseEventDateTime("2027-07-21 14:00"))
    assertNull(parseEventDateTime("21/7/2027 14:00"))
  }

  @Test
  fun formatter_writesWhatParseEventDateTimeReads() {
    val written = LocalDateTime.of(2027, 7, 22, 9, 5).format(EVENT_DATE_TIME_FORMATTER)

    assertEquals("22/07/2027 09:05", written)
    assertEquals(LocalDateTime.of(2027, 7, 22, 9, 5), parseEventDateTime(written))
  }
}
