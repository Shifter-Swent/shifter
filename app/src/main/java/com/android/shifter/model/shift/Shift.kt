// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import java.time.Instant

/**
 * A slot of an event that its organizer opened, for volunteers to choose when they apply, stored
 * under the event it belongs to. It has no name and no number of spots: it is only its time.
 *
 * The model stays free of Firebase types so it can be used by ViewModels and tested without a
 * backend.
 *
 * @property eventId id of the event the shift belongs to. The event document is the parent of the
 *   shift, so the path carries this id and it is never stored as a field; it is read back from the
 *   path because a volunteer whose schedule spans several events has to know which event each of
 *   their shifts belongs to.
 * @property volunteerIds ids of the `/users` documents of the volunteers who chose this shift, by a
 *   pending or accepted request, empty while nobody did.
 * @property startAt when the slot starts, on an even hour (see [slotOf]).
 * @property endAt when the slot ends, at the next even hour on the clock: 2 h after it starts, but
 *   1 h or 3 h on the nights the clocks change. A duration is read from [startAt] and [endAt],
 *   never from [SLOT_HOURS].
 * @property createdAt when the organizer opened the slot.
 */
data class Shift(
    val eventId: String,
    val volunteerIds: List<String> = emptyList(),
    val startAt: Instant,
    val endAt: Instant,
    val createdAt: Instant,
) {
  init {
    require(isSlotDuration(startAt, endAt)) {
      "A shift lasts 1 to 3 whole hours, not from $startAt to $endAt"
    }
  }

  /** The backend document id, taken from [startAt] (see [shiftIdOf]), so a slot is opened once. */
  val id: String
    get() = shiftIdOf(startAt)
}
