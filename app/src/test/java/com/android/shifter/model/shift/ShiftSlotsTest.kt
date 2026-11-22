// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftSlotsTest {

  @Test
  fun slotsOf_givesTheTwelveSlotsOfADayFromMidnightTo22() {
    val slots = slotsOf(DAY, ZURICH)

    assertEquals(12, slots.size)
    assertEquals(
        (0..22 step 2).toList(),
        slots.map { it.startAt.atZone(ZURICH).toLocalTime().hour },
    )
    assertTrue(slots.all { Duration.between(it.startAt, it.endAt) == Duration.ofHours(2) })
  }

  @Test
  fun slotsOf_eachSlotEndsWhereTheNextStarts() {
    val slots = slotsOf(DAY, ZURICH)

    slots.zipWithNext().forEach { (slot, next) -> assertEquals(slot.endAt, next.startAt) }
    // The last one ends at midnight, where the next day starts.
    assertEquals(slotsOf(DAY.plusDays(1), ZURICH).first().startAt, slots.last().endAt)
  }

  @Test
  fun slotOf_readsTheHourInTheGivenZone() {
    val slot = slotOf(DAY, 10, ZURICH)

    // Zurich is on summer time in July, two hours ahead of UTC.
    assertEquals(Instant.parse("2026-07-18T08:00:00Z"), slot.startAt)
    assertEquals(Instant.parse("2026-07-18T10:00:00Z"), slot.endAt)
  }

  @Test
  fun slotOf_withAnOddHour_isRefused() {
    assertThrows(IllegalArgumentException::class.java) { slotOf(DAY, 9, ZURICH) }
  }

  @Test
  fun slotOf_withAnHourOutsideTheDay_isRefused() {
    assertThrows(IllegalArgumentException::class.java) { slotOf(DAY, 24, ZURICH) }
    assertThrows(IllegalArgumentException::class.java) { slotOf(DAY, -2, ZURICH) }
  }

  @Test
  fun slotsOf_onTheNightTheClocksGoForward_hasASlotOfOneHourAndNoOverlap() {
    // On 29 March 2026, 02:00 does not exist in Zurich: the clocks go from 02:00 to 03:00.
    val slots = slotsOf(LocalDate.of(2026, 3, 29), ZURICH)

    val slotOfTwo = slots[1]
    assertEquals(LocalTime.of(3, 0), slotOfTwo.startAt.atZone(ZURICH).toLocalTime())
    assertEquals(Duration.ofHours(1), Duration.between(slotOfTwo.startAt, slotOfTwo.endAt))
    slots.zipWithNext().forEach { (slot, next) -> assertEquals(slot.endAt, next.startAt) }
    // 23 hours that day, every one of them in a slot.
    assertEquals(
        Duration.ofHours(23),
        Duration.between(slots.first().startAt, slots.last().endAt),
    )
  }

  @Test
  fun slotsOf_onTheNightTheClocksGoBack_hasASlotOfThreeHoursAndNoGap() {
    // On 25 October 2026, 02:00 comes twice in Zurich: the clocks go from 03:00 back to 02:00.
    val slots = slotsOf(LocalDate.of(2026, 10, 25), ZURICH)

    val slotOfTwo = slots[1]
    assertEquals(Duration.ofHours(3), Duration.between(slotOfTwo.startAt, slotOfTwo.endAt))
    slots.zipWithNext().forEach { (slot, next) -> assertEquals(slot.endAt, next.startAt) }
    // 25 hours that day, every one of them in a slot.
    assertEquals(
        Duration.ofHours(25),
        Duration.between(slots.first().startAt, slots.last().endAt),
    )
  }

  @Test
  fun shiftIdOf_isTheEpochMillisecondsOfTheStart() {
    val startAt = Instant.parse("2026-07-18T08:00:00Z")

    assertEquals(startAt.toEpochMilli().toString(), shiftIdOf(startAt))
    assertEquals(shiftIdOf(startAt), slotOf(DAY, 10, ZURICH).shiftId)
  }

  @Test
  fun shiftIdOf_givesEachSlotOfADayItsOwnId() {
    val ids = slotsOf(DAY, ZURICH).map { it.shiftId }

    assertEquals(ids.size, ids.toSet().size)
  }

  @Test
  fun isSlotDuration_acceptsOneToThreeWholeHours() {
    val start = Instant.parse("2026-07-18T08:00:00Z")

    assertTrue(isSlotDuration(start, start.plus(Duration.ofHours(1))))
    assertTrue(isSlotDuration(start, start.plus(Duration.ofHours(2))))
    assertTrue(isSlotDuration(start, start.plus(Duration.ofHours(3))))
  }

  @Test
  fun isSlotDuration_refusesAnyOtherDuration() {
    val start = Instant.parse("2026-07-18T08:00:00Z")

    assertFalse(isSlotDuration(start, start))
    assertFalse(isSlotDuration(start, start.minus(Duration.ofHours(2))))
    assertFalse(isSlotDuration(start, start.plus(Duration.ofHours(4))))
    assertFalse(isSlotDuration(start, start.plus(Duration.ofMinutes(90))))
  }

  @Test
  fun shiftSlot_withADurationThatIsNotASlot_isRefused() {
    val start = Instant.parse("2026-07-18T08:00:00Z")

    assertThrows(IllegalArgumentException::class.java) {
      ShiftSlot(startAt = start, endAt = start.plus(Duration.ofHours(10)))
    }
  }

  @Test
  fun shift_withADurationThatIsNotASlot_isRefused() {
    val start = Instant.parse("2026-07-18T08:00:00Z")

    assertThrows(IllegalArgumentException::class.java) {
      Shift(
          eventId = "event-1",
          startAt = start,
          endAt = start.plus(Duration.ofHours(10)),
          createdAt = start,
      )
    }
  }

  @Test
  fun shift_takesItsIdFromItsStart() {
    val slot = slotOf(DAY, 10, ZURICH)

    val shift =
        Shift(
            eventId = "event-1",
            startAt = slot.startAt,
            endAt = slot.endAt,
            createdAt = slot.startAt,
        )

    assertEquals(slot.shiftId, shift.id)
  }

  private companion object {
    val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")
    val DAY: LocalDate = LocalDate.of(2026, 7, 18)
  }
}
