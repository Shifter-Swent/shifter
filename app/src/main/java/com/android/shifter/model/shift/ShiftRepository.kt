// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import kotlinx.coroutines.flow.Flow

/**
 * Access to the shifts of the events: the slots an organizer opened for volunteers to choose.
 * ViewModels depend on this interface, never on an implementation.
 *
 * A shift is never edited, only opened and closed. Its [Shift.volunteerIds] change through the
 * volunteers' requests (`MembershipRequestRepository`), never through this repository.
 *
 * Every method throws a [ShiftRepositoryException] when the operation fails, and the flow of
 * [observeShifts] ends with one.
 */
interface ShiftRepository {

  /**
   * Returns the shifts of [eventId] sorted by start, or an empty list when none is open: a single
   * read, for a screen that does not need to follow the changes, e.g. when a volunteer applies.
   */
  suspend fun getShifts(eventId: String): List<Shift>

  /**
   * Emits the shifts of [eventId] sorted by start, then again each time one is opened or closed, or
   * a volunteer chooses or leaves one, on this device or another, so the organizer's grid stays up
   * to date without reloading. Emits an empty list when none is open.
   */
  fun observeShifts(eventId: String): Flow<List<Shift>>

  /**
   * Opens [slot] in [eventId] and returns its shift, with no volunteers and opened now. The slot is
   * built by [slotOf], since only the zone tells where it ends.
   *
   * A slot already open is returned unchanged: a second tap or a retry never creates a second shift
   * nor takes its volunteers off. Returning it only reads it, which any signed-in user may do. Only
   * creating it needs the organizer: security rules deny anyone else opening a slot that is not
   * open yet with [ShiftRepositoryException.PermissionDenied]. A slot closed on another device
   * while it opens stays closed: this then fails with [ShiftRepositoryException.Unknown].
   */
  suspend fun openSlot(eventId: String, slot: ShiftSlot): Shift

  /**
   * Closes the slot of [shiftId] in [eventId] by deleting its shift. A slot already closed does
   * nothing, so a retry after a lost answer succeeds.
   *
   * A slot that volunteers chose stays open: it is refused with
   * [ShiftRepositoryException.PermissionDenied], as the security rules answer. A screen following
   * [observeShifts] then already shows who chose it; one that read [getShifts] reloads. Only the
   * organizer can close a slot.
   */
  suspend fun closeSlot(eventId: String, shiftId: String)
}
