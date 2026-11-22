// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import java.time.Instant

/**
 * In-memory [ShiftRepository] for unit tests.
 *
 * Setting [failure] makes every call throw it, to exercise error handling.
 *
 * It does not record the shifts volunteers choose: applying with the fake
 * `MembershipRequestRepository` leaves [Shift.volunteerIds] alone, as that link is the backend's
 * (#594). A test that needs a chosen slot seeds a shift with its volunteers.
 *
 * @param initialShifts the shifts the fake starts with, for a test that reads rather than opens.
 * @param now when a slot opened by [openSlot] is opened.
 */
class FakeShiftRepository(
    initialShifts: List<Shift> = emptyList(),
    private val now: () -> Instant = { Instant.EPOCH },
) : ShiftRepository {

  /** Every shift the fake holds, whatever the order it was seeded or opened in. */
  val shifts: MutableList<Shift> = initialShifts.toMutableList()

  var failure: Exception? = null

  override suspend fun getShifts(eventId: String): List<Shift> {
    failure?.let { throw it }
    return shifts.filter { it.eventId == eventId }.sortedBy { it.startAt }
  }

  override suspend fun openSlot(eventId: String, slot: ShiftSlot): Shift {
    failure?.let { throw it }
    val existing = shifts.firstOrNull { it.eventId == eventId && it.id == slot.shiftId }
    if (existing != null) return existing
    val opened =
        Shift(eventId = eventId, startAt = slot.startAt, endAt = slot.endAt, createdAt = now())
    shifts += opened
    return opened
  }

  /** Like the security rules, a slot volunteers chose is refused. */
  override suspend fun closeSlot(eventId: String, shiftId: String) {
    failure?.let { throw it }
    val shift = shifts.firstOrNull { it.eventId == eventId && it.id == shiftId } ?: return
    if (shift.volunteerIds.isNotEmpty()) throw ShiftRepositoryException.PermissionDenied()
    shifts -= shift
  }
}
