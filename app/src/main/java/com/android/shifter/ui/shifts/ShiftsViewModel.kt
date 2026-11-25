// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.shifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.shift.Shift
import com.swent.shifter.model.shift.ShiftRepository
import com.swent.shifter.model.shift.ShiftRepositoryException
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
import kotlinx.coroutines.flow.catch
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
 * @property period when the event runs, its end excluded, or null while unknown: the screen greys
 *   out the slots of [days] that `ShiftSlot.overlaps(start, endInclusive)` refuses, as do the
 *   rules.
 * @property days the days of the event, the columns of the grid, in order.
 * @property shifts the slots that are open, sorted by start, each with the volunteers who chose it.
 * @property openHoursByDay the hours the open slots add up to, by the day of [days] they are drawn
 *   under, a day with none open being absent. Counted from each slot's start and end, so a day the
 *   clocks change holds the 23 or 25 h it really does.
 * @property error the last load or save that failed, until the screen has shown it.
 * @property keptSlot the last slot that could not be closed, until the screen has shown it.
 */
data class ShiftsUiState(
    val isLoading: Boolean = true,
    val mode: ShiftsMode = ShiftsMode.VIEW,
    val period: ClosedRange<Instant>? = null,
    val days: List<LocalDate> = emptyList(),
    val shifts: List<Shift> = emptyList(),
    val openHoursByDay: Map<LocalDate, Long> = emptyMap(),
    val error: ShiftsError? = null,
    val keptSlot: KeptSlot? = null,
) {
  /** Whether the event is known to have no slot open: not while loading, nor after a failure. */
  val isEmpty: Boolean
    get() = !isLoading && error == null && shifts.isEmpty()

  /** The hours every open slot adds up to: the "26 h" of "26 h open over 3 days". */
  val openHours: Long
    get() = openHoursByDay.values.sum()
}

/**
 * Holds the 2 h slots of the event [eventId] for its organizer: the ones that are open, who chose
 * them, and the hours they add up to.
 *
 * It follows [ShiftRepository.observeShifts] rather than reading once, so a slot a volunteer picks
 * on their own phone shows here on its own, and a slot that cannot be closed because someone just
 * chose it already lists them, with nothing to reload.
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

  /** The slots whose open or close is running, so a second tap on one does nothing. */
  private val saving = mutableSetOf<String>()

  init {
    // Two reads: the grid appears as soon as the shifts arrive, and the event only widens its days
    // to those the organizer has not opened a slot on yet.
    viewModelScope.launch { loadPeriod() }
    viewModelScope.launch { observeShifts() }
  }

  /**
   * Opens the slot of [day] at [hour] when it is closed, closes it when it is open, saved at once.
   * Does nothing outside [ShiftsMode.EDIT], while that slot is already being saved, nor for a slot
   * the event does not run through, which the rules refuse.
   *
   * A slot volunteers chose is never closed: it stays and [ShiftsUiState.keptSlot] names it,
   * whether this ViewModel already knew them or the backend refuses the close because someone chose
   * it in between.
   */
  fun toggleSlot(day: LocalDate, hour: Int) {
    val state = _uiState.value
    if (state.mode != ShiftsMode.EDIT) return
    val slot = slotOf(day, hour, zone)
    // Before the slot is read: an invalid tap must not name volunteers it will not act on.
    val period = state.period ?: return
    if (!slot.overlaps(period.start, period.endInclusive)) return
    val open = shiftOf(slot.shiftId)
    if (open != null && open.volunteerIds.isNotEmpty()) {
      keep(open)
      return
    }
    if (!saving.add(slot.shiftId)) return
    viewModelScope.launch {
      try {
        if (open == null) shiftRepository.openSlot(eventId, slot)
        else shiftRepository.closeSlot(eventId, slot.shiftId)
      } catch (e: CancellationException) {
        throw e
      } catch (_: ShiftRepositoryException.PermissionDenied) {
        // Either a volunteer chose the slot just before the close, which the flow has already
        // brought in, or the signed-in user is not the organizer: the backend tells no difference.
        val chosen = shiftOf(slot.shiftId)?.takeIf { it.volunteerIds.isNotEmpty() }
        if (chosen != null) keep(chosen) else fail()
      } catch (_: Exception) {
        fail()
      } finally {
        saving -= slot.shiftId
      }
    }
  }

  /** Switches between reading the slots and editing them, e.g. "Open slots" and "Done". */
  fun setMode(mode: ShiftsMode) {
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
      _uiState.update { it.copy(period = period).withDaysAndHours() }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      // The days the open slots fall on still draw a grid; only the empty days around them go.
      _uiState.update { it.copy(error = ShiftsError.LOAD_FAILED) }
    }
  }

  private suspend fun observeShifts() {
    shiftRepository
        .observeShifts(eventId)
        .catch { e ->
          if (e is CancellationException) throw e
          _uiState.update { it.copy(isLoading = false, error = ShiftsError.LOAD_FAILED) }
        }
        .collect { shifts ->
          _uiState.update { it.copy(isLoading = false, shifts = shifts).withDaysAndHours() }
        }
  }

  /** The days of the grid and the hours each holds, both read from the slots of this state. */
  private fun ShiftsUiState.withDaysAndHours(): ShiftsUiState {
    // The day a slot counts under is the calendar day its start falls on, the one slotOf built
    // it from and the grid draws it in. An event day, which starts at 04:00, would put the
    // rows of 00:00 and 02:00 under the day before and disagree with their own column.
    val byDay = shifts.groupBy { it.startAt.atZone(zone).toLocalDate() }
    return copy(
        days = calendarDays(byDay.keys),
        // From each slot's start and end, never from the 2 h of the clock: on a night the clocks
        // change a slot lasts 1 h or 3 h.
        openHoursByDay =
            byDay.mapValues { (_, ofTheDay) ->
              ofTheDay.sumOf { Duration.between(it.startAt, it.endAt).toHours() }
            },
    )
  }

  /**
   * The columns of the grid: every calendar day from the first to the last that the event or an
   * open slot falls on. Calendar days, not the event days of `eventDays`, whose boundary is 04:00,
   * since the grid draws the rows of 00:00 and 02:00 in the column of their own date. The end is
   * when the event is over, so one ending at midnight sharp ends with the day before.
   */
  private fun ShiftsUiState.calendarDays(slotDays: Set<LocalDate>): List<LocalDate> {
    val ends = period?.let { listOf(it.start, it.endInclusive.minusNanos(1)) }.orEmpty()
    val dates = slotDays + ends.map { it.atZone(zone).toLocalDate() }
    val first = dates.minOrNull() ?: return emptyList()
    val last = dates.max()
    return generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.toList()
  }

  private fun shiftOf(shiftId: String): Shift? =
      _uiState.value.shifts.firstOrNull { it.id == shiftId }

  private fun keep(shift: Shift) {
    _uiState.update { it.copy(keptSlot = KeptSlot(shift.startAt, shift.volunteerIds.size)) }
  }

  private fun fail() {
    _uiState.update { it.copy(error = ShiftsError.SAVE_FAILED) }
  }

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
