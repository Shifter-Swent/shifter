// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

/**
 * Read and write access to the shifts of an event, independent of any backend.
 *
 * ViewModels depend on this interface and never on an implementation, so the Firestore repository
 * can be replaced by a fake in unit tests.
 *
 * Every method takes the id of the event the shift belongs to: a [Shift] is stored below its parent
 * event and does not carry an event id of its own, so the caller has to name the event it is
 * working in. [Shift.teamId] points at a team of that same event.
 *
 * Who works a shift is deliberately out of reach here: assigning volunteers is a separate concept,
 * and so is the number of spots a shift still has free.
 */
interface ShiftRepository {

  /** Persists [shift] under the event [eventId] and returns it with the id the backend assigns. */
  suspend fun createShift(eventId: String, shift: Shift): Shift

  /** Returns every shift of [eventId], or an empty list when it has none. */
  suspend fun getShifts(eventId: String): List<Shift>

  /**
   * Returns every shift of [eventId] that the team [teamId] has to staff, or an empty list when
   * there is none.
   *
   * A query of its own rather than filtering [getShifts]: an event is staffed over as many shifts
   * as it has teams times time slots, and a manager looking at one team must not download them all.
   */
  suspend fun getShiftsByTeam(eventId: String, teamId: String): List<Shift>
}
