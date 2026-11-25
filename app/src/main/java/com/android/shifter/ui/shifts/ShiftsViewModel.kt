// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.shifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.shift.Shift
import com.swent.shifter.model.shift.ShiftRepository
import com.swent.shifter.model.shift.ShiftRepositoryException
import com.swent.shifter.model.shift.ShiftSlot
import com.swent.shifter.model.shift.overlaps
import com.swent.shifter.model.shift.slotOf
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the organizer is doing with the slots. */
enum class ShiftsMode {
  /** Reading the slots that are open. */
  VIEW,
  /** Opening and closing them, each tap saved at once. */
  EDIT,
}

/** Why the last load or save failed. The screen words it; the grid keeps what it had. */
enum class ShiftsError {
  /** The event or its shifts could not be read. */
  LOAD_FAILED,
  /** A slot could not be opened or closed. */
  SAVE_FAILED,
  /** The backend could not be reached: the device is offline. */
  OFFLINE,
}

/**
 * A slot that stays open because volunteers chose it, for the screen to word, e.g. "Sat 10:00 stays
 * · 3 volunteers chose it."
 */
data class KeptSlot(val startAt: Instant, val volunteers: Int)

/**
 * State of the organizer's Shifts screen.
 *
 * @property isLoading whether the first read of the shifts is still running.
 * @property shiftsFailed whether the shifts followed ended on a failure. Unlike [error], which the
 *   screen clears once shown, this stays: the grid keeps its last slots and writing stops.
 * @property period when the event runs, its end excluded, or null while unknown: the screen greys
 *   out the slots of [days] that `ShiftSlot.overlaps(start, endInclusive)` refuses, as do the
 *   rules.
 * @property days the days of the event, the columns of the grid, in order.
 * @property shifts the slots that are open, sorted by start, each with the volunteers who chose it.
 * @property savingShiftIds the slots whose open or close is in flight, for the screen to show them
 *   as pending; a further tap on one does nothing until it is answered.
 * @property error the last load or save that failed, until the screen has shown it.
 * @property keptSlot the last slot that could not be closed, until the screen has shown it.
 */
data class ShiftsUiState(
    val isLoading: Boolean = true,
    val shiftsFailed: Boolean = false,
    val mode: ShiftsMode = ShiftsMode.VIEW,
    val period: ClosedRange<Instant>? = null,
    val days: List<LocalDate> = emptyList(),
    val shifts: List<Shift> = emptyList(),
    val savingShiftIds: Set<String> = emptySet(),
    val error: ShiftsError? = null,
    val keptSlot: KeptSlot? = null,
) {
  /** Whether the event is known to have no slot open: only once a read has answered with none. */
  val isEmpty: Boolean
    get() = !isLoading && !shiftsFailed && shifts.isEmpty()

  /**
   * Whether the slots may be opened and closed: the shifts are in and still followed, and the event
   * says which slots it runs through. The screen offers "Open slots" on this.
   */
  val canEdit: Boolean
    get() = !isLoading && !shiftsFailed && period != null

  /**
   * The hours every open slot adds up to: the "26 h" of "26 h open over 3 days", from each slot's
   * own start and end, so one lasts the 1 h or 3 h it really does on a night the clocks change.
   */
  val openHours: Long
    get() = shifts.sumOf { Duration.between(it.startAt, it.endAt).toHours() }
}

/**
 * Holds the 2 h slots of the event [eventId] for its organizer: the ones that are open, who chose
 * them, and the hours they add up to.
 *
 * It follows [ShiftRepository.observeShifts] rather than reading once, so a slot a volunteer picks
 * on their own phone shows here on its own, and a slot that cannot be closed because someone just
 * chose it usually lists them already.
 *
 * The days are calendar days, the ones the grid draws its columns of, so an event running past
 * midnight gets a column for the night it ends on.
 *
 * @param zone the zone the slots are read in, the phone's, which the event's days already use.
 */
class ShiftsViewModel(
    private val eventId: String,
    private val shiftRepository: ShiftRepository,
    private val eventRepository: EventRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(ShiftsUiState())
  val uiState: StateFlow<ShiftsUiState> = _uiState.asStateFlow()

  init {
    // Two reads: the grid appears as soon as the shifts arrive, and the event only widens its days
    // to those the organizer has not opened a slot on yet.
    viewModelScope.launch { loadPeriod() }
    viewModelScope.launch { observeShifts() }
  }

  /**
   * Opens the slot of [day] at [hour] when it is closed, closes it when it is open, saved at once.
   * Does nothing outside [ShiftsMode.EDIT], while that slot is already being saved, while the slots
   * cannot be edited ([ShiftsUiState.canEdit]), nor for a slot the event does not run through,
   * which the rules refuse.
   *
   * A slot volunteers chose is never closed: it stays and [ShiftsUiState.keptSlot] names it,
   * whether this ViewModel already knew them or the backend refuses the close because someone chose
   * it in between.
   */
  fun toggleSlot(day: LocalDate, hour: Int) = toggleSlot(slotOf(day, hour, zone))

  /**
   * The same, for the slot itself: the grid draws a cell from [slotOf] and reports it back, so the
   * slot written is the one tapped even on a night the clocks change, where the cell of 02:00
   * starts at 03:00 and no hour could name it again.
   */
  fun toggleSlot(slot: ShiftSlot) {
    val state = _uiState.value
    if (state.mode != ShiftsMode.EDIT) return
    if (slot.shiftId in state.savingShiftIds) return
    // Before the slot is read: a grid still loading, or left behind by a follow that ended, would
    // name volunteers who may no longer be on it.
    val period = state.period ?: return
    if (!state.canEdit || !slot.overlaps(period.start, period.endInclusive)) return
    val open = shiftOf(slot.shiftId)
    if (open != null && open.volunteerIds.isNotEmpty()) {
      keep(open)
      return
    }
    _uiState.update { it.copy(savingShiftIds = it.savingShiftIds + slot.shiftId) }
    viewModelScope.launch { saveSlot(slot, open) }
  }

  /**
   * Opens [slot], or closes it when [open] is the shift it already holds, and takes it out of
   * [ShiftsUiState.savingShiftIds] once the backend has answered, failed or been cancelled.
   */
  private suspend fun saveSlot(slot: ShiftSlot, open: Shift?) {
    try {
      if (open == null) shiftRepository.openSlot(eventId, slot)
      else shiftRepository.closeSlot(eventId, slot.shiftId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: ShiftRepositoryException.PermissionDenied) {
      // A refused open is the user not being the organizer; only a close has volunteers to find.
      if (open == null) fail(e) else keepOrFail(slot, e)
    } catch (e: Exception) {
      fail(e)
    } finally {
      _uiState.update { it.copy(savingShiftIds = it.savingShiftIds - slot.shiftId) }
    }
  }

  /**
   * Switches between reading the slots and editing them, e.g. "Open slots" and "Done". Edit is
   * refused while [ShiftsUiState.canEdit] is false.
   */
  fun setMode(mode: ShiftsMode) {
    if (mode == ShiftsMode.EDIT && !_uiState.value.canEdit) return
    _uiState.update { it.copy(mode = mode) }
  }

  /** Drops the error and the kept slot, once the screen has shown them. */
  fun messageShown() {
    _uiState.update { it.copy(error = null, keptSlot = null) }
  }

  /** Reads when the event runs, then widens the days to it. Its failure leaves the grid alone. */
  private suspend fun loadPeriod() {
    try {
      val period = eventRepository.getEvent(eventId)?.let { it.startAt..it.endAt }
      _uiState.update { it.copy(period = period).withDays() }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      // The days the open slots fall on still draw a grid; only the empty days around them go.
      _uiState.update { it.copy(error = errorOf(e, ShiftsError.LOAD_FAILED)) }
    }
  }

  /**
   * A try/catch around the collection, as the other ViewModels do it, so a flow failing before its
   * first emission and one failing after are caught alike.
   */
  private suspend fun observeShifts() {
    try {
      shiftRepository.observeShifts(eventId).collect { shifts ->
        _uiState.update { it.copy(isLoading = false, shifts = shifts).withDays() }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      // The grid keeps the shifts it last had; only writing stops, as nothing would follow one.
      _uiState.update {
        it.copy(isLoading = false, shiftsFailed = true, error = errorOf(e, ShiftsError.LOAD_FAILED))
      }
    }
  }

  /**
   * The columns of the grid: every calendar day from the first to the last that the event or an
   * open slot falls on. Calendar days, not the event days of `eventDays`, whose boundary is 04:00,
   * since the grid draws the rows of 00:00 and 02:00 in the column of their own date. The end is
   * when the event is over, so one ending at midnight sharp ends with the day before.
   */
  private fun ShiftsUiState.withDays(): ShiftsUiState {
    val ends = period?.let { listOf(it.start, it.endInclusive.minusNanos(1)) }.orEmpty()
    val dates = (shifts.map { it.startAt } + ends).map { it.atZone(zone).toLocalDate() }
    val first = dates.minOrNull() ?: return copy(days = emptyList())
    val last = dates.max()
    return copy(days = generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.toList())
  }

  /**
   * The shifts followed usually name the volunteers a refused close kept the slot for. They do not
   * when Firestore hides the shift as the delete is sent and the refusal comes back before the
   * listener puts it again, so the slot is gone from the grid here. One read is then asked, and
   * only asked: the listener stays the one to say what the grid holds. [ShiftRepository.getShifts]
   * takes its backend's default source, so a read that answers from a cache that has not caught up
   * only means the save is reported as failed.
   */
  private suspend fun keepOrFail(slot: ShiftSlot, denied: Exception) {
    val chosen =
        shiftOf(slot.shiftId)?.takeIf { it.volunteerIds.isNotEmpty() }
            ?: try {
              shiftRepository.getShifts(eventId).firstOrNull {
                it.id == slot.shiftId && it.volunteerIds.isNotEmpty()
              }
            } catch (e: CancellationException) {
              throw e
            } catch (_: Exception) {
              null
            }
    if (chosen != null) keep(chosen) else fail(denied)
  }

  private fun shiftOf(shiftId: String): Shift? =
      _uiState.value.shifts.firstOrNull { it.id == shiftId }

  private fun keep(shift: Shift) {
    _uiState.update { it.copy(keptSlot = KeptSlot(shift.startAt, shift.volunteerIds.size)) }
  }

  private fun fail(e: Throwable) {
    _uiState.update { it.copy(error = errorOf(e, ShiftsError.SAVE_FAILED)) }
  }

  /** [ShiftsError.OFFLINE] when the backend could not be reached, [otherwise] for anything else. */
  private fun errorOf(e: Throwable, otherwise: ShiftsError): ShiftsError =
      if (e is ShiftRepositoryException.Unavailable || e is EventRepositoryException.Unavailable)
          ShiftsError.OFFLINE
      else otherwise

  companion object {
    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(
        eventId: String,
        shiftRepository: ShiftRepository,
        eventRepository: EventRepository,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { ShiftsViewModel(eventId, shiftRepository, eventRepository, zone) }
    }
  }
}
