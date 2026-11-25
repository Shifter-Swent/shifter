// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.shifts

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FakeEventRepository
import com.swent.shifter.model.shift.FakeShiftRepository
import com.swent.shifter.model.shift.Shift
import com.swent.shifter.model.shift.ShiftRepository
import com.swent.shifter.model.shift.ShiftRepositoryException
import com.swent.shifter.model.shift.ShiftSlot
import com.swent.shifter.model.shift.slotOf
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShiftsViewModelTest {

  private val dispatcher = StandardTestDispatcher()

  private lateinit var shifts: FakeShiftRepository
  private lateinit var events: FakeEventRepository
  private lateinit var viewModel: ShiftsViewModel

  private val state
    get() = viewModel.uiState.value

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    shifts = FakeShiftRepository()
    events = FakeEventRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** Starts the ViewModel over [seeded] and [event], and lets its first reads finish. */
  private fun TestScope.start(
      seeded: List<Shift> = emptyList(),
      event: Event? = eventFrom(fri, sun),
      repository: ShiftRepository = shifts,
  ) {
    seeded.forEach(shifts::put)
    event?.let { events.events += it }
    viewModel = ShiftsViewModel(EVENT_ID, repository, events, ZONE)
    advanceUntilIdle()
  }

  /**
   * The days a ViewModel over [event] draws, with [seeded] open. Seeded slots stay for the next
   * call, so a test seeds on its last one.
   */
  private fun TestScope.daysOf(event: Event, seeded: List<Shift> = emptyList()): List<LocalDate> {
    events.events.clear()
    start(seeded = seeded, event = event)
    return state.days.also { viewModel.viewModelScope.cancel() }
  }

  private fun TestScope.edit() {
    viewModel.setMode(ShiftsMode.EDIT)
    advanceUntilIdle()
  }

  /** The shift of the slot of [day] at [hour], chosen by [volunteers] of them. */
  private fun shift(day: LocalDate, hour: Int, volunteers: Int = 0): Shift {
    val slot = slotOf(day, hour, ZONE)
    return Shift(
        eventId = EVENT_ID,
        volunteerIds = List(volunteers) { "volunteer-$it" },
        startAt = slot.startAt,
        endAt = slot.endAt,
        createdAt = Instant.EPOCH,
    )
  }

  /**
   * An event running from [first] at [firstHour] to [last] at [lastHour], on the clock of [ZONE].
   */
  private fun eventFrom(
      first: LocalDate,
      last: LocalDate,
      firstHour: Int = 8,
      lastHour: Int = 22,
  ) =
      Event(
          id = EVENT_ID,
          organizerId = "organizer-1",
          title = "Summer Festival",
          description = "Three days by the lake",
          type = EventType.MUSIC,
          startAt = slotOf(first, firstHour, ZONE).startAt,
          endAt = slotOf(last, lastHour, ZONE).startAt,
          location = EventLocation(address = "Lakeside Park"),
          createdAt = Instant.EPOCH,
      )

  // ------------------------------------------------------------------ Loading

  @Test
  fun load_beforeTheShiftsArrive_isLoadingAndNotYetEmpty() = runTest {
    viewModel = ShiftsViewModel(EVENT_ID, shifts, events, ZONE)

    assertTrue(state.isLoading)
    assertFalse("the grid must not flash its empty state while loading", state.isEmpty)
  }

  @Test
  fun load_whenTheEventIsNotFound_keepsTheDaysOfTheOpenSlots() = runTest {
    start(seeded = listOf(shift(sat, 10)), event = null)

    // An event that is gone is not a failure to report: its open slots still draw their own days.
    assertEquals(listOf(shift(sat, 10)), state.shifts)
    assertEquals(listOf(sat), state.days)
    assertNull(state.error)
    assertFalse(state.isLoading)
  }

  @Test
  fun load_withoutASlotOpen_showsTheDaysAndTheEmptyState() = runTest {
    start()

    assertFalse(state.isLoading)
    assertEquals(listOf(fri, sat, sun), state.days)
    assertEquals(emptyList<Shift>(), state.shifts)
    assertTrue(state.isEmpty)
    assertEquals(0L, state.openHours)
    assertEquals(ShiftsMode.VIEW, state.mode)
    assertEquals(slotOf(fri, 8, ZONE).startAt..slotOf(sun, 22, ZONE).startAt, state.period)
  }

  @Test
  fun load_withSlotsOpen_showsThemWithTheirVolunteers() = runTest {
    start(seeded = listOf(shift(sat, 10, volunteers = 3), shift(sat, 12)))

    assertFalse(state.isEmpty)
    assertEquals(listOf(shift(sat, 10, volunteers = 3), shift(sat, 12)), state.shifts)
    assertEquals(3, state.shifts.first().volunteerIds.size)
    assertEquals(listOf(fri, sat, sun), state.days)
  }

  @Test
  fun observeShifts_whenAVolunteerChoosesASlot_theStateFollowsWithNoReload() = runTest {
    start(seeded = listOf(shift(sat, 10)))
    assertEquals(0, state.shifts.single().volunteerIds.size)

    shifts.put(shift(sat, 10, volunteers = 2))
    advanceUntilIdle()

    assertEquals(2, state.shifts.single().volunteerIds.size)
  }

  // --------------------------------------------------------------------- Days

  @Test
  fun days_ofAnEventRunningPastMidnight_areItsCalendarDays() = runTest {
    // Event days start at 04:00, calendar days do not: an event from Sat 18:00 to Sun 02:00 has
    // both ends on Saturday's event day, so its Sunday 00-02 slot would get no column to be
    // opened in, and one starting Sun 02:00 would get a Saturday of twelve slots outside it.
    assertEquals("ends after midnight", listOf(sat, sun), daysOf(eventFrom(sat, sun, 18, 2)))
    assertEquals("starts after midnight", listOf(sun), daysOf(eventFrom(sun, sun, 2, 22)))
    // The end is when the event is over, so nothing of it runs on the Sunday.
    assertEquals("ends at midnight", listOf(sat), daysOf(eventFrom(sat, sun, 18, 0)))
    // A slot open after midnight makes its own date a column, even past the event's last day.
    assertEquals(
        "a slot after midnight",
        listOf(sat, sun),
        daysOf(eventFrom(sat, sat, 18, 22), seeded = listOf(shift(sun, 0))),
    )
  }

  // ------------------------------------------------------------------- Modes

  @Test
  fun setMode_switchesTheMode_andOnlyEditActsOnATap() = runTest {
    start()

    // In View a tap changes nothing, so the grid can be read without opening a slot by mistake.
    viewModel.toggleSlot(sat, 10)
    advanceUntilIdle()
    assertEquals(emptyList<Shift>(), state.shifts)

    edit()
    assertEquals(ShiftsMode.EDIT, state.mode)
    viewModel.toggleSlot(sat, 10)
    advanceUntilIdle()
    assertEquals(listOf(shift(sat, 10)), state.shifts)

    viewModel.setMode(ShiftsMode.VIEW)
    assertEquals(ShiftsMode.VIEW, state.mode)
  }

  // ----------------------------------------------------------- Opening, closing

  @Test
  fun toggleSlot_onAClosedSlot_opensItAndShowsItAtOnce() = runTest {
    start()
    edit()

    viewModel.toggleSlot(sat, 10)
    advanceUntilIdle()

    assertEquals(listOf(shift(sat, 10)), shifts.shifts)
    assertEquals(listOf(shift(sat, 10)), state.shifts)
    assertEquals(2L, state.openHours)
  }

  @Test
  fun toggleSlot_onAnOpenSlotNobodyChose_closesIt() = runTest {
    start(seeded = listOf(shift(sat, 10)))
    edit()

    viewModel.toggleSlot(sat, 10)
    advanceUntilIdle()

    assertEquals(emptyList<Shift>(), shifts.shifts)
    assertEquals(emptyList<Shift>(), state.shifts)
  }

  @Test
  fun toggleSlot_onASlotVolunteersChose_keepsItAndSaysWhoKeptIt() = runTest {
    start(seeded = listOf(shift(sat, 10, volunteers = 3)))
    edit()

    viewModel.toggleSlot(sat, 10)
    advanceUntilIdle()

    assertEquals(listOf(shift(sat, 10, volunteers = 3)), state.shifts)
    assertEquals(KeptSlot(slotOf(sat, 10, ZONE).startAt, 3), state.keptSlot)
    assertNull(state.error)
  }

  @Test
  fun toggleSlot_whenAVolunteerChoosesItFirst_keepsItAndSaysWhoKeptIt() = runTest {
    start(seeded = listOf(shift(sat, 10)))
    edit()

    // The volunteer's choice is on its way: the flow has not delivered it when the organizer taps,
    // so the close is sent and the backend is the one to refuse it.
    shifts.put(shift(sat, 10, volunteers = 1))
    viewModel.toggleSlot(sat, 10)
    advanceUntilIdle()

    assertEquals(listOf(shift(sat, 10, volunteers = 1)), state.shifts)
    assertEquals(KeptSlot(slotOf(sat, 10, ZONE).startAt, 1), state.keptSlot)
    assertNull(state.error)
  }

  @Test
  fun messageShown_dropsTheKeptSlotAndTheError() = runTest {
    start(seeded = listOf(shift(sat, 10, volunteers = 1)))
    edit()
    viewModel.toggleSlot(sat, 10)
    advanceUntilIdle()

    viewModel.messageShown()

    assertNull(state.keptSlot)
    assertNull(state.error)
  }

  @Test
  fun toggleSlot_tappedTwiceBeforeItIsSaved_opensTheSlotOnce() = runTest {
    val counting = CountingShifts(shifts)
    start(repository = counting)
    edit()

    viewModel.toggleSlot(sat, 10)
    viewModel.toggleSlot(sat, 10)
    advanceUntilIdle()

    assertEquals(1, counting.opens)
    assertEquals(listOf(shift(sat, 10)), state.shifts)
  }

  // --------------------------------------------------- Slots outside the event

  @Test
  fun toggleSlot_atTheEdgesOfTheEvent_opensOnlyTheSlotsItRunsThrough() = runTest {
    // Friday 08:00 to Sunday 22:00: 00-02 is well before it, 06-08 ends as it starts and Sunday's
    // 22-00 starts as it ends, so the rules refuse all three (#593) and the taps are dropped
    // before any call rather than coming back as a failed save. 08-10 and 20-22 do run in it.
    start()
    edit()

    listOf(fri to 0, fri to 6, fri to 8, sun to 20, sun to 22).forEach { (day, hour) ->
      viewModel.toggleSlot(day, hour)
    }
    advanceUntilIdle()

    assertEquals(listOf(shift(fri, 8), shift(sun, 20)), shifts.shifts.sortedBy { it.startAt })
    assertNull(state.error)
  }

  @Test
  fun toggleSlot_onASlotTheEventOnlyRunsPartOf_opensIt() = runTest {
    // The event stops at 01:00, halfway through the 00-02 slot, which it still runs through.
    val event = eventFrom(sat, sun, firstHour = 18, lastHour = 0)
    start(event = event.copy(endAt = event.endAt.plus(Duration.ofHours(1))))
    edit()

    viewModel.toggleSlot(sun, 0)
    viewModel.toggleSlot(sun, 2)
    advanceUntilIdle()

    assertEquals(listOf(shift(sun, 0)), shifts.shifts)
  }

  @Test
  fun toggleSlot_beforeTheEventIsKnown_writesNothing() = runTest {
    // Without the event, a slot cannot be told to run during it, so nothing is written.
    start(seeded = listOf(shift(sat, 10)), event = null)
    edit()

    viewModel.toggleSlot(sat, 12)
    advanceUntilIdle()

    assertEquals(listOf(shift(sat, 10)), shifts.shifts)
    assertNull(state.error)
  }

  // ------------------------------------------------------------- Hours open

  @Test
  fun openHours_perDayAndInAll_followTheSlotsThatAreOpen() = runTest {
    start(
        seeded =
            listOf(shift(sat, 10), shift(sat, 12), shift(sat, 18), shift(sun, 10, volunteers = 1))
    )

    assertEquals(mapOf(sat to 6L, sun to 2L), state.openHoursByDay)
    assertEquals(8L, state.openHours)
  }

  @Test
  fun openHours_onTheNightTheClocksGoForward_countTheHourItReallyLasts() = runTest {
    // 02:00 never comes that night, so the slot of 02:00 starts at 03:00 and lasts one hour. It is
    // still drawn in the column of the 29th, where its hour counts.
    val night = LocalDate.parse("2026-03-29")
    start(seeded = listOf(shift(night, 2)), event = eventFrom(night.minusDays(1), night))

    assertEquals(1L, state.openHours)
    assertEquals(mapOf(night to 1L), state.openHoursByDay)
  }

  @Test
  fun openHours_onTheNightTheClocksGoBack_countTheThreeHoursItReallyLasts() = runTest {
    // 02:00 comes twice that night, so the slot of 02:00 lasts three hours.
    val night = LocalDate.parse("2026-10-25")
    start(seeded = listOf(shift(night, 2)), event = eventFrom(night.minusDays(1), night))

    assertEquals(3L, state.openHours)
    assertEquals(mapOf(night to 3L), state.openHoursByDay)
  }

  // -------------------------------------------------------------- Failures

  @Test
  fun load_whenTheEventCannotBeRead_keepsTheSlotsAndReportsIt() = runTest {
    events.failure = RuntimeException("offline")
    start(seeded = listOf(shift(sat, 10)))

    // The days the open slots fall on still draw a grid; only the empty days around them go.
    assertEquals(listOf(shift(sat, 10)), state.shifts)
    assertEquals(listOf(sat), state.days)
    assertEquals(ShiftsError.LOAD_FAILED, state.error)
  }

  @Test
  fun load_whenTheShiftsCannotBeRead_reportsItAndStopsLoading() = runTest {
    shifts.failure = ShiftRepositoryException.Unavailable()
    start()

    assertFalse(state.isLoading)
    assertEquals(ShiftsError.LOAD_FAILED, state.error)
    assertFalse("a failed load is not an event without slots", state.isEmpty)
    assertEquals(listOf(fri, sat, sun), state.days)
  }

  @Test
  fun toggleSlot_whenTheSaveFails_keepsTheGridAndReportsIt() = runTest {
    start(seeded = listOf(shift(sat, 10)))
    edit()
    shifts.failure = ShiftRepositoryException.Unavailable()

    viewModel.toggleSlot(sat, 12)
    advanceUntilIdle()

    assertEquals(listOf(shift(sat, 10)), state.shifts)
    assertEquals(ShiftsError.SAVE_FAILED, state.error)
    assertNull(state.keptSlot)
  }

  @Test
  fun toggleSlot_whenTheCloseIsDeniedWithNobodyOnTheSlot_reportsTheFailure() = runTest {
    start(seeded = listOf(shift(sat, 10)))
    edit()
    // Denied for another reason than the volunteers, e.g. the user is not the organizer.
    shifts.failure = ShiftRepositoryException.PermissionDenied()

    viewModel.toggleSlot(sat, 10)
    advanceUntilIdle()

    assertEquals(ShiftsError.SAVE_FAILED, state.error)
    assertNull(state.keptSlot)
  }

  // ----------------------------------------------------------- Cancellation

  @Test
  fun load_whenTheEventReadIsCancelled_reportsNoFailure() = runTest {
    events.failure = CancellationException("the screen went away")
    start(seeded = listOf(shift(sat, 10)))

    assertNull(state.error)
    assertEquals(listOf(shift(sat, 10)), state.shifts)
  }

  @Test
  fun observeShifts_whenTheFlowIsCancelled_reportsNoFailure() = runTest {
    shifts.failure = CancellationException("the screen went away")
    start()

    assertNull(state.error)
    assertTrue("a cancelled read leaves the state as it was", state.isLoading)
  }

  @Test
  fun toggleSlot_whenTheSaveIsCancelled_reportsNoFailure() = runTest {
    start(seeded = listOf(shift(sat, 10)))
    edit()
    shifts.failure = CancellationException("the screen went away")

    viewModel.toggleSlot(sat, 12)
    advanceUntilIdle()

    assertNull(state.error)
    assertEquals(listOf(shift(sat, 10)), state.shifts)
  }

  @Test
  fun clearing_stopsFollowingTheShifts() = runTest {
    start(seeded = listOf(shift(sat, 10)))

    viewModel.viewModelScope.cancel()
    shifts.put(shift(sat, 10, volunteers = 4))
    advanceUntilIdle()

    assertEquals(0, state.shifts.single().volunteerIds.size)
  }

  // ------------------------------------------------------------------ Factory

  @Test
  fun factory_buildsAViewModelOverTheSameEventAndRepositories() = runTest {
    shifts.put(shift(sat, 10))
    events.events += eventFrom(fri, sun)

    val factory = ShiftsViewModel.factory(EVENT_ID, shifts, events)
    val built = ViewModelProvider(ViewModelStore(), factory)[ShiftsViewModel::class.java]
    advanceUntilIdle()

    // The zone is the phone's here, so only what does not depend on it is asserted.
    assertEquals(listOf(shift(sat, 10)), built.uiState.value.shifts)
    assertNull(built.uiState.value.error)
  }

  /** Counts the slots opened, to check a second tap on one does not send a second write. */
  private class CountingShifts(private val delegate: ShiftRepository) :
      ShiftRepository by delegate {
    var opens = 0

    override suspend fun openSlot(eventId: String, slot: ShiftSlot): Shift {
      opens++
      return delegate.openSlot(eventId, slot)
    }
  }

  private companion object {
    const val EVENT_ID = "event-1"

    val ZONE: ZoneId = ZoneId.of("Europe/Zurich")

    val fri: LocalDate = LocalDate.parse("2026-06-19")
    val sat: LocalDate = LocalDate.parse("2026-06-20")
    val sun: LocalDate = LocalDate.parse("2026-06-21")
  }
}
