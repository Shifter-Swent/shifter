// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.schedule

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers what [TimeSlot] accepts: only a slot that ends after it starts. */
class TimeSlotTest {

  private val start = Instant.parse("2026-11-21T14:00:00Z")

  @Test
  fun timeSlot_endingAfterItStarts_keepsItsBounds() {
    val end = start.plusSeconds(3600)

    val slot = TimeSlot(start, end)

    assertEquals(start, slot.start)
    assertEquals(end, slot.end)
  }

  @Test(expected = IllegalArgumentException::class)
  fun timeSlot_endingWhenItStarts_isRejected() {
    TimeSlot(start, start)
  }

  @Test(expected = IllegalArgumentException::class)
  fun timeSlot_endingBeforeItStarts_isRejected() {
    TimeSlot(start, start.minusSeconds(60))
  }
}
