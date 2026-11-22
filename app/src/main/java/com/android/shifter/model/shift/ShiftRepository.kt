// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

/**
 * Access to the shifts of the events: the slots an organizer opened for volunteers to choose.
 * ViewModels depend on this interface, never on an implementation.
 *
 * A shift is never edited, only opened and closed. Its [Shift.volunteerIds] change through the
 * volunteers' requests (`MembershipRequestRepository`), never through this repository.
 *
 * Every method throws a [ShiftRepositoryException] when the operation fails.
 */
interface ShiftRepository {

  /** Returns the shifts of [eventId] sorted by start, or an empty list when none is open. */
  suspend fun getShifts(eventId: String): List<Shift>

  /**
   * Opens [slot] in [eventId] and returns its shift, with no volunteers and opened now. The slot is
   * built by [slotOf], since only the zone tells where it ends.
   *
   * A slot already open is returned unchanged: a second tap or a retry never creates a second shift
   * nor takes its volunteers off. Only the organizer can open a slot: security rules deny anyone
   * else with [ShiftRepositoryException.PermissionDenied].
   */
  suspend fun openSlot(eventId: String, slot: ShiftSlot): Shift

  /**
   * Closes the slot of [shiftId] in [eventId] by deleting its shift. A slot already closed does
   * nothing, so a retry after a lost answer succeeds.
   *
   * A slot that volunteers chose stays open: it is refused with
   * [ShiftRepositoryException.PermissionDenied], as the security rules answer, so the caller
   * reloads the shifts to show who chose it. Only the organizer can close a slot.
   */
  suspend fun closeSlot(eventId: String, shiftId: String)
}
