// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * The slots an organizer opens as shifts: a day holds 12 of them, each from an even hour to the
 * next one on the clock, 00 to 22.
 *
 * The hours are read in the zone the event's days already use (`eventDays`), which is the phone's:
 * a volunteer applying from another time zone sees the slots shifted, a known limit.
 *
 * A slot ends at the next even hour on the clock rather than 2 h after it starts, so the slots of a
 * day never overlap nor leave a gap, even on the nights the clocks change. On those nights the slot
 * of 02:00 lasts 1 h in spring, as 02:00 does not exist and starts at 03:00, and 3 h in autumn, as
 * 02:00 comes twice and the slot starts at the first one. A duration is therefore always read from a
 * slot's start and end, never from [SLOT_HOURS].
 *
 * The one zone whose clocks move by 30 minutes, `Australia/Lord_Howe`, is a known limit: on the
 * nights they change, a slot would last 1 h 30 or 2 h 30, which [ShiftSlot] refuses, so [slotOf]
 * throws.
 */

/** How long a slot lasts on the clock, in hours: 2 h of real time, except on a clock change. */
const val SLOT_HOURS = 2

/** The hours of the day a slot starts at: 00, 02, ..., 22. */
val SLOT_START_HOURS: List<Int> = (0 until 24 step SLOT_HOURS).toList()

/** The shortest and the longest a slot can last: on a night the clocks go forward, then back. */
private val SHORTEST_SLOT = Duration.ofHours(SLOT_HOURS - 1L)
private val LONGEST_SLOT = Duration.ofHours(SLOT_HOURS + 1L)
private val ONE_HOUR = Duration.ofHours(1)

/**
 * One slot of a day, opened or not: what an organizer opens as a [Shift]. Built by [slotOf], it
 * always lasts 1 to 3 whole hours ([isSlotDuration]): any other stretch is refused with
 * [IllegalArgumentException].
 */
data class ShiftSlot(val startAt: Instant, val endAt: Instant) {
  init {
    require(isSlotDuration(startAt, endAt)) {
      "A slot lasts 1 to 3 whole hours, not from $startAt to $endAt"
    }
  }

  /** The id of the shift this slot opens, see [shiftIdOf]. */
  val shiftId: String
    get() = shiftIdOf(startAt)
}

/**
 * The slot of [day] that starts at [hour] in [zone], running to the next even hour on the clock.
 *
 * Throws [IllegalArgumentException] when [hour] is not one of [SLOT_START_HOURS].
 */
fun slotOf(day: LocalDate, hour: Int, zone: ZoneId): ShiftSlot {
  require(hour in SLOT_START_HOURS) { "A slot starts on an even hour from 0 to 22, not $hour" }
  val midnight = day.atStartOfDay()
  return ShiftSlot(
      startAt = midnight.plusHours(hour.toLong()).atZone(zone).toInstant(),
      // The last slot of the day ends at midnight, at the start of the next day.
      endAt = midnight.plusHours(hour.toLong() + SLOT_HOURS).atZone(zone).toInstant(),
  )
}

/**
 * Whether this slot runs during an event from [eventStartAt] to [eventEndAt], even in part, so its
 * organizer can open it: the slots of the event's days that end before it starts or start after it
 * ends are greyed out. An event from 09:00 to 23:00 can thus open 08–10 and 22–00, but not 06–08.
 *
 * The security rules check the same (#593), so a slot built by [slotOf] that this accepts is never
 * refused for its time.
 */
fun ShiftSlot.overlaps(eventStartAt: Instant, eventEndAt: Instant): Boolean =
    endAt > eventStartAt && startAt < eventEndAt

/** The 12 slots of [day] in [zone], from 00 to 22, each ending where the next one starts. */
fun slotsOf(day: LocalDate, zone: ZoneId): List<ShiftSlot> = SLOT_START_HOURS.map {
  slotOf(day, it, zone)
}

/**
 * The id of the shift that starts at [startAt]: its epoch milliseconds. A slot has a single start,
 * so it can never be opened twice as two shifts.
 */
fun shiftIdOf(startAt: Instant): String = startAt.toEpochMilli().toString()

/**
 * Whether a shift from [startAt] to [endAt] can be a slot, whatever the zone: it lasts 1 to 3 whole
 * hours, as a slot does on a night the clocks change. Which hours it starts and ends at depends on
 * the zone, which only [slotOf] knows.
 */
fun isSlotDuration(startAt: Instant, endAt: Instant): Boolean {
  val duration = Duration.between(startAt, endAt)
  return duration in SHORTEST_SLOT..LONGEST_SLOT && duration.toNanos() % ONE_HOUR.toNanos() == 0L
}
