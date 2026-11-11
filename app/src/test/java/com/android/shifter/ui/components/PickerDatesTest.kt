// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** The UTC-midnight conversions the date pickers of the event and the mission forms share. */
class PickerDatesTest {

  @Test
  fun toPickerMillis_isTheUtcMidnightOfTheDay() {
    assertEquals(
        Instant.parse("2027-07-21T00:00:00Z").toEpochMilli(),
        LocalDate.of(2027, 7, 21).toPickerMillis(),
    )
  }

  @Test
  fun toPickerDate_readsBackTheDayOfToPickerMillis() {
    val day = LocalDate.of(2027, 7, 21)

    assertEquals(day, day.toPickerMillis().toPickerDate())
  }

  @Test
  fun toPickerDate_keepsTheDayWhateverTheTimeOfIt() {
    val lastMillisOfTheDay = Instant.parse("2027-07-21T23:59:59.999Z").toEpochMilli()

    assertEquals(LocalDate.of(2027, 7, 21), lastMillisOfTheDay.toPickerDate())
  }

  @Test
  fun toPickerDate_beforeTheEpoch_roundsDownToTheDay() {
    // -1 ms is the last instant of 1969-12-31: a plain division would answer 1970-01-01.
    assertEquals(LocalDate.of(1969, 12, 31), (-1L).toPickerDate())
  }
}
