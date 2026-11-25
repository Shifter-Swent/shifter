// Co-authored-by: Claude Opus 5 (1M context) <noreply@anthropic.com>
package com.swent.shifter.ui.shifts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.shift.Shift
import com.swent.shifter.model.shift.ShiftSlot
import com.swent.shifter.model.shift.slotOf
import com.swent.shifter.ui.components.shifts.ShiftGridTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The organizer's Shifts screen over [ShiftsContent], its stateless form: what each state draws and
 * what each tap reports. The grid itself is held to its own tests (#598), and what a tap does to
 * the shifts to [ShiftsViewModelTest] (#596).
 */
@RunWith(AndroidJUnit4::class)
class ShiftsScreenTest {

  @get:Rule val compose = createComposeRule()

  private val modes = mutableListOf<ShiftsMode>()
  private val taps = mutableListOf<ShiftSlot>()
  private var messagesShown = 0

  private fun show(state: ShiftsUiState, zone: ZoneId = ZONE) {
    compose.setContent {
      ShifterTheme {
        ShiftsContent(
            state = state,
            onBack = {},
            onSetMode = { modes += it },
            onToggleSlot = { taps += it },
            onMessageShown = { messagesShown++ },
            zone = zone,
        )
      }
    }
  }

  /** A state of an event running Saturday 08:00 to Sunday 22:00, with [shifts] open. */
  private fun loaded(
      shifts: List<Shift> = emptyList(),
      mode: ShiftsMode = ShiftsMode.VIEW,
      error: ShiftsError? = null,
      keptSlot: KeptSlot? = null,
      savingShiftIds: Set<String> = emptySet(),
  ) =
      ShiftsUiState(
          isLoading = false,
          mode = mode,
          period = slotOf(sat, 8, ZONE).startAt..slotOf(sun, 22, ZONE).startAt,
          days = listOf(sat, sun),
          shifts = shifts,
          savingShiftIds = savingShiftIds,
          error = error,
          keptSlot = keptSlot,
      )

  private fun shift(day: LocalDate, hour: Int, volunteers: Int = 0): Shift {
    val slot = slotOf(day, hour, ZONE)
    return Shift(
        eventId = "event-1",
        volunteerIds = List(volunteers) { "volunteer-$it" },
        startAt = slot.startAt,
        endAt = slot.endAt,
        createdAt = Instant.EPOCH,
    )
  }

  private fun cell(day: LocalDate, hour: Int) =
      compose.onNodeWithTag(ShiftGridTestTags.slot(day, hour))

  // ------------------------------------------------------------------ States

  @Test
  fun loading_showsTheSpinnerAndNeitherTheGridNorTheEmptyState() {
    show(ShiftsUiState())

    compose.onNodeWithTag(ShiftsScreenTestTags.LOADING).assertIsDisplayed()
    compose.onNodeWithTag(ShiftsScreenTestTags.EMPTY).assertDoesNotExist()
    compose.onNodeWithTag(ShiftGridTestTags.GRID).assertDoesNotExist()
  }

  @Test
  fun empty_offersOpenSlotsWhichGoesToEdit() {
    show(loaded())

    compose.onNodeWithTag(ShiftsScreenTestTags.EMPTY).assertIsDisplayed()
    compose.onNodeWithText("No shifts yet").assertIsDisplayed()
    compose.onNodeWithTag(ShiftGridTestTags.GRID).assertDoesNotExist()

    compose.onNodeWithTag(ShiftsScreenTestTags.EDIT).performClick()

    assertEquals(listOf(ShiftsMode.EDIT), modes)
  }

  @Test
  fun empty_inEdit_showsTheGridSoTheFirstSlotsCanBeOpened() {
    show(loaded(mode = ShiftsMode.EDIT))

    compose.onNodeWithTag(ShiftsScreenTestTags.EMPTY).assertDoesNotExist()
    compose.onNodeWithTag(ShiftGridTestTags.GRID).assertIsDisplayed()
    compose.onNodeWithTag(ShiftsScreenTestTags.DONE).assertIsDisplayed()
  }

  @Test
  fun view_showsTheHoursOpenAndTheGrid_andEditGoesToEdit() {
    show(loaded(shifts = listOf(shift(sat, 10), shift(sun, 10))))

    // 2 h + 2 h over the two days of the event.
    compose.onNodeWithText("4 h open over 2 days").assertIsDisplayed()
    compose.onNodeWithText("Volunteers pick these when they apply.").assertIsDisplayed()
    compose.onNodeWithTag(ShiftGridTestTags.GRID).assertIsDisplayed()

    compose.onNodeWithTag(ShiftsScreenTestTags.EDIT).performClick()

    assertEquals(listOf(ShiftsMode.EDIT), modes)
  }

  @Test
  fun edit_doneGoesBackToView() {
    show(loaded(shifts = listOf(shift(sat, 10)), mode = ShiftsMode.EDIT))

    compose.onNodeWithTag(ShiftsScreenTestTags.EDIT).assertDoesNotExist()
    compose.onNodeWithTag(ShiftsScreenTestTags.DONE).performClick()

    assertEquals(listOf(ShiftsMode.VIEW), modes)
  }

  // ---------------------------------------------------------- Opening, closing

  @Test
  fun edit_tappingAClosedSlot_reportsItsDayAndHour() {
    show(loaded(mode = ShiftsMode.EDIT))

    cell(sat, 10).performClick()

    assertEquals(listOf(slotOf(sat, 10, ZONE)), taps)
  }

  @Test
  fun edit_tappingAnOpenSlot_reportsItTooSoTheViewModelCloses() {
    show(loaded(shifts = listOf(shift(sat, 10)), mode = ShiftsMode.EDIT))

    cell(sat, 10).performClick()

    assertEquals(listOf(slotOf(sat, 10, ZONE)), taps)
  }

  @Test
  fun edit_tappingTheSlotOfAClockChange_reportsTheSlotTheCellDraws() {
    // That night 02:00 never comes: the cell of 02:00 runs 03:00 to 04:00, so an hour could no
    // longer name it and only the slot itself can be acted on.
    val night = LocalDate.parse("2026-03-29")
    show(
        ShiftsUiState(
            isLoading = false,
            mode = ShiftsMode.EDIT,
            period = slotOf(night, 0, ZONE).startAt..slotOf(night, 22, ZONE).startAt,
            days = listOf(night),
            shifts = emptyList(),
        )
    )

    cell(night, 2).performClick()

    assertEquals(listOf(slotOf(night, 2, ZONE)), taps)
  }

  @Test
  fun edit_aSlotOutsideTheEventOrBeingSaved_isNotTapped() {
    // The period and the slots being saved reach the grid: the event starts Saturday 08:00, so
    // 00-02 is outside it, and 10-12 is already being written.
    show(loaded(mode = ShiftsMode.EDIT, savingShiftIds = setOf(slotOf(sat, 10, ZONE).shiftId)))

    cell(sat, 0).performClick()
    cell(sat, 10).performClick()

    assertTrue("neither slot may be tapped, but reported $taps", taps.isEmpty())
  }

  // ------------------------------------------------------------- The messages

  @Test
  fun keptSlot_namesTheSlotAndItsVolunteers_thenIsShown() {
    val kept = KeptSlot(slotOf(sat, 10, ZONE).startAt, 3)
    var state by
        mutableStateOf(loaded(shifts = listOf(shift(sat, 10, volunteers = 3)), keptSlot = kept))
    compose.setContent {
      ShifterTheme {
        ShiftsContent(state, {}, { modes += it }, { taps += it }, { messagesShown++ }, ZONE)
      }
    }

    compose.onNodeWithTag(ShiftsScreenTestTags.LOCKED_MESSAGE).assertIsDisplayed()
    compose.onNodeWithText("Sat 10:00 stays").assertIsDisplayed()
    compose.onNodeWithText("3 volunteers chose it.").assertIsDisplayed()

    // Three quarters through its turn on screen, a change that says nothing new about the message
    // arrives: it must not take the message off and put it back, which would show it twice.
    compose.mainClock.autoAdvance = false
    compose.mainClock.advanceTimeBy(SHORT_SNACKBAR_MILLIS * 3 / 4)
    state = loaded(shifts = listOf(shift(sat, 10, volunteers = 3), shift(sat, 12)), keptSlot = kept)
    compose.mainClock.advanceTimeByFrame()

    // Once it has been read, the screen says so, so the ViewModel drops it and it never sticks.
    compose.mainClock.advanceTimeBy(SNACKBAR_MILLIS)
    assertEquals("the message is reported as shown once", 1, messagesShown)
  }

  @Test
  fun messages_whenASaveFailsWhileASlotIsKept_showBothBeforeEitherIsDropped() {
    val kept = KeptSlot(slotOf(sat, 10, ZONE).startAt, 3)
    var state by mutableStateOf(loaded(keptSlot = kept))
    compose.setContent {
      ShifterTheme {
        ShiftsContent(state, {}, { modes += it }, { taps += it }, { messagesShown++ }, ZONE)
      }
    }
    compose.onNodeWithTag(ShiftsScreenTestTags.LOCKED_MESSAGE).assertIsDisplayed()

    // A save fails while the kept slot is on screen: the ViewModel then holds both, and
    // messageShown() clears both, so neither may be reported before it has been shown.
    state = loaded(keptSlot = kept, error = ShiftsError.OFFLINE)
    compose.waitForIdle()
    assertEquals("a message was consumed before it showed", 0, messagesShown)

    compose.onNodeWithTag(ShiftsScreenTestTags.LOCKED_MESSAGE).assertIsDisplayed()

    // The kept slot is shown out, then the failure takes its place, still unreported.
    compose.waitUntil(SNACKBAR_MILLIS) {
      compose.onAllNodesWithText(OFFLINE).fetchSemanticsNodes().isNotEmpty()
    }
    assertEquals("the failure must wait its turn", 0, messagesShown)

    compose.waitUntil(SNACKBAR_MILLIS) { messagesShown == 1 }
  }

  @Test
  fun error_whenTheBackendCannotBeReached_saysSoOverTheGridItKeeps() {
    show(loaded(shifts = listOf(shift(sat, 10)), error = ShiftsError.OFFLINE))

    compose.onNodeWithTag(ShiftsScreenTestTags.MESSAGE).assertIsDisplayed()
    compose.onNodeWithText(OFFLINE).assertIsDisplayed()
    // The slots already read stay on screen behind the message.
    compose.onNodeWithTag(ShiftGridTestTags.GRID).assertIsDisplayed()
  }

  // -------------------------------------------------------- A follow that died

  @Test
  fun loadFailed_withNothingRead_saysSoRatherThanShowingAnEmptyEvent() {
    show(ShiftsUiState(isLoading = false, shiftsFailed = true, days = listOf(sat, sun)))

    compose.onNodeWithTag(ShiftsScreenTestTags.LOAD_FAILED).assertIsDisplayed()
    compose.onNodeWithTag(ShiftsScreenTestTags.EMPTY).assertDoesNotExist()
    compose.onNodeWithTag(ShiftGridTestTags.GRID).assertDoesNotExist()
    // Edit is not offered: the ViewModel refuses it, and no write would show.
    compose.onNodeWithTag(ShiftsScreenTestTags.EDIT).assertIsNotEnabled()
  }

  @Test
  fun loadFailed_overSlotsAlreadyRead_keepsTheGridAndRefusesEdit() {
    show(
        ShiftsUiState(
            isLoading = false,
            shiftsFailed = true,
            period = slotOf(sat, 8, ZONE).startAt..slotOf(sun, 22, ZONE).startAt,
            days = listOf(sat, sun),
            shifts = listOf(shift(sat, 10)),
        )
    )

    compose.onNodeWithTag(ShiftGridTestTags.GRID).assertIsDisplayed()
    compose.onNodeWithTag(ShiftsScreenTestTags.EDIT).assertIsNotEnabled()
  }

  @Test
  fun loadFailed_whileInEdit_stopsOfferingTheSlots_andDoneStillLeaves() {
    show(
        ShiftsUiState(
            isLoading = false,
            shiftsFailed = true,
            mode = ShiftsMode.EDIT,
            period = slotOf(sat, 8, ZONE).startAt..slotOf(sun, 22, ZONE).startAt,
            days = listOf(sat, sun),
            shifts = listOf(shift(sat, 10)),
        )
    )

    // The slots last read stay on screen, but none offers to be opened or closed any more.
    compose.onNodeWithTag(ShiftGridTestTags.GRID).assertIsDisplayed()
    cell(sat, 10).assertHasNoClickAction()
    cell(sat, 10).performClick()
    assertTrue("a dead follow must write nothing, but reported $taps", taps.isEmpty())

    // Done is still there, so the organizer is never stuck in Edit.
    compose.onNodeWithTag(ShiftsScreenTestTags.DONE).performClick()
    assertEquals(listOf(ShiftsMode.VIEW), modes)
  }

  @Test
  fun pendingWrite_doesNotPassForAnEventWithoutSlots() {
    // Offline, closing the last slot empties the grid at once while the delete is still pending:
    // leaving Edit then must not claim the event has no shift.
    show(loaded(savingShiftIds = setOf(slotOf(sat, 10, ZONE).shiftId)))

    compose.onNodeWithTag(ShiftsScreenTestTags.EMPTY).assertDoesNotExist()
    compose.onNodeWithTag(ShiftGridTestTags.GRID).assertIsDisplayed()
    // In View the slot being deleted has already left the grid, so the header says so instead.
    compose.onNodeWithTag(ShiftsScreenTestTags.SAVING).assertIsDisplayed()
  }

  @Test
  fun beforeTheEventIsKnown_editIsNotOffered() {
    // Without the period no slot can be told to run during the event, so the ViewModel refuses
    // Edit and the screen does not offer it.
    show(ShiftsUiState(isLoading = false, days = listOf(sat, sun), shifts = listOf(shift(sat, 10))))

    compose.onNodeWithTag(ShiftsScreenTestTags.EDIT).assertIsNotEnabled()
  }

  private companion object {
    /** Longer than a short snackbar, so the screen reports it as shown. */
    const val SNACKBAR_MILLIS = 10_000L

    /** What a short snackbar lasts: half of it is not long enough to report one as shown. */
    const val SHORT_SNACKBAR_MILLIS = 4_000L

    const val OFFLINE = "You are offline. Open a slot once you are back online."

    val ZONE: ZoneId = ZoneId.of("Europe/Zurich")
    val sat: LocalDate = LocalDate.parse("2026-06-20")
    val sun: LocalDate = LocalDate.parse("2026-06-21")
  }
}
