// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [ShiftRepository] for unit tests.
 *
 * Setting [failure] makes every call throw it, and every flow of [observeShifts] end with it, to
 * exercise error handling.
 *
 * It does not record the shifts volunteers choose: applying with the fake
 * `MembershipRequestRepository` leaves [Shift.volunteerIds] alone, as that link is the backend's
 * (#594). A test that needs a chosen slot puts a shift with its volunteers, with [put].
 *
 * @param initialShifts the shifts the fake starts with, for a test that reads rather than opens.
 * @param now when a slot opened by [openSlot] is opened.
 */
class FakeShiftRepository(
    initialShifts: List<Shift> = emptyList(),
    private val now: () -> Instant = { Instant.EPOCH },
) : ShiftRepository {

  private val state = MutableStateFlow(initialShifts)

  /** Every shift the fake holds, whatever the order it was seeded or opened in. */
  val shifts: List<Shift>
    get() = state.value

  var failure: Exception? = null

  /**
   * Stores [shift] as is, replacing the one of the same slot, to set up a test or to stand for a
   * change made on another device, e.g. a volunteer choosing the slot: [observeShifts] emits it.
   */
  fun put(shift: Shift) {
    state.update { shifts -> shifts.filterNot { it.isSameSlotAs(shift) } + shift }
  }

  override suspend fun getShifts(eventId: String): List<Shift> {
    failure?.let { throw it }
    return shifts.of(eventId)
  }

  override fun observeShifts(eventId: String): Flow<List<Shift>> = flow {
    failure?.let { throw it }
    // A change to another event leaves this list as is: not emitted, like a Firestore listener
    // that only sees its own query.
    emitAll(state.map { it.of(eventId) }.distinctUntilChanged())
  }

  override suspend fun openSlot(eventId: String, slot: ShiftSlot): Shift {
    failure?.let { throw it }
    val existing = shifts.firstOrNull { it.eventId == eventId && it.id == slot.shiftId }
    if (existing != null) return existing
    val opened =
        Shift(eventId = eventId, startAt = slot.startAt, endAt = slot.endAt, createdAt = now())
    put(opened)
    return opened
  }

  /** Like the security rules, a slot volunteers chose is refused. */
  override suspend fun closeSlot(eventId: String, shiftId: String) {
    failure?.let { throw it }
    val shift = shifts.firstOrNull { it.eventId == eventId && it.id == shiftId } ?: return
    if (shift.volunteerIds.isNotEmpty()) throw ShiftRepositoryException.PermissionDenied()
    state.update { it - shift }
  }

  /** The shifts of [eventId], sorted by start, as the repository returns them. */
  private fun List<Shift>.of(eventId: String): List<Shift> {
    val ofTheEvent = filter { it.eventId == eventId }
    return ofTheEvent.sortedBy { it.startAt }
  }

  private fun Shift.isSameSlotAs(other: Shift) = eventId == other.eventId && id == other.id
}
