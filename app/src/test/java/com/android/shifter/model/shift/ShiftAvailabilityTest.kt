// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import com.swent.shifter.model.membership.AvailabilitySlot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftAvailabilityTest {

  @Test
  fun availabilityOf_joinsBackToBackShifts() {
    val availability = availabilityOf(listOf(shift(SATURDAY, 10), shift(SATURDAY, 12)))

    assertEquals(listOf(stretch(SATURDAY, 10, SATURDAY, 14)), availability)
  }

  @Test
  fun availabilityOf_keepsShiftsApartWhenAHourSeparatesThem() {
    val availability = availabilityOf(listOf(shift(SATURDAY, 10), shift(SATURDAY, 14)))

    assertEquals(
        listOf(stretch(SATURDAY, 10, SATURDAY, 12), stretch(SATURDAY, 14, SATURDAY, 16)),
        availability,
    )
  }

  @Test
  fun availabilityOf_joinsShiftsAcrossMidnight() {
    val availability = availabilityOf(listOf(shift(FRIDAY, 22), shift(SATURDAY, 0)))

    assertEquals(listOf(stretch(FRIDAY, 22, SATURDAY, 2)), availability)
  }

  @Test
  fun availabilityOf_onTwoDays_sortsThemByStart() {
    val availability =
        availabilityOf(listOf(shift(SATURDAY, 10), shift(FRIDAY, 18), shift(SATURDAY, 12)))

    assertEquals(
        listOf(stretch(FRIDAY, 18, FRIDAY, 20), stretch(SATURDAY, 10, SATURDAY, 14)),
        availability,
    )
  }

  @Test
  fun availabilityOf_withNoShift_isEmpty() {
    assertEquals(emptyList<AvailabilitySlot>(), availabilityOf(emptyList()))
  }

  @Test
  fun formatShiftHours_withinADay_writesTheDayAndTheHours() {
    assertEquals("Fri 18–20", stretch(FRIDAY, 18, FRIDAY, 20).formatShiftHours(ZURICH))
  }

  @Test
  fun formatShiftHours_upToMidnight_endsAt24() {
    assertEquals("Fri 22–24", stretch(FRIDAY, 22, SATURDAY, 0).formatShiftHours(ZURICH))
  }

  @Test
  fun formatShiftHours_intoAnotherDay_namesBothDays() {
    assertEquals("Fri 22–Sat 02", stretch(FRIDAY, 22, SATURDAY, 2).formatShiftHours(ZURICH))
  }

  private fun shift(day: LocalDate, hour: Int): Shift {
    val slot = slotOf(day, hour, ZURICH)
    return Shift(
        eventId = "event-1",
        startAt = slot.startAt,
        endAt = slot.endAt,
        createdAt = Instant.EPOCH,
    )
  }

  private fun stretch(from: LocalDate, fromHour: Int, to: LocalDate, toHour: Int) =
      AvailabilitySlot(at(from, fromHour), at(to, toHour))

  private fun at(day: LocalDate, hour: Int): Instant =
      day.atTime(hour, 0).atZone(ZURICH).toInstant()

  private companion object {
    val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")
    val FRIDAY: LocalDate = LocalDate.parse("2026-06-19")
    val SATURDAY: LocalDate = FRIDAY.plusDays(1)
  }
}
