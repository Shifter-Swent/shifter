// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.shifts

import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.shift.SLOT_START_HOURS
import com.swent.shifter.model.shift.Shift
import com.swent.shifter.model.shift.ShiftSlot
import com.swent.shifter.model.shift.slotOf
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShiftGridTest {

  @get:Rule val compose = createComposeRule()

  private val tapped = mutableListOf<ShiftSlot>()

  private fun show(
      mode: ShiftGridMode,
      days: List<LocalDate> = weekend,
      shifts: List<Shift> = emptyList(),
      picked: Set<String> = emptySet(),
  ) = showChanging(days, { mode }, { shifts }, { picked })

  /**
   * Shows the grid reading its inputs through lambdas, so a test can change one afterwards and
   * check that the grid follows without being shown again.
   */
  private fun showChanging(
      days: List<LocalDate> = weekend,
      mode: () -> ShiftGridMode = { ShiftGridMode.EDIT },
      shifts: () -> List<Shift> = { emptyList() },
      picked: () -> Set<String> = { emptySet() },
  ) {
    compose.setContent {
      ShifterTheme {
        ShiftGrid(
            days = days,
            shifts = shifts(),
            mode = mode(),
            zone = ZONE,
            modifier = Modifier.requiredWidth(GRID_WIDTH),
            pickedShiftIds = picked(),
            onSlot = { tapped += it },
        )
      }
    }
  }

  private fun cell(day: LocalDate, hour: Int) =
      compose.onNodeWithTag(ShiftGridTestTags.slot(day, hour))

  private fun header(day: LocalDate) = compose.onNodeWithTag(ShiftGridTestTags.day(day))

  /** The mark drawn inside the cell of [day] at [hour]: a cell is read out as one node. */
  private fun mark(tag: String, day: LocalDate, hour: Int) =
      compose.onNode(
          hasTestTag(tag) and hasAnyAncestor(hasTestTag(ShiftGridTestTags.slot(day, hour))),
          useUnmergedTree = true,
      )

  private fun shiftOf(day: LocalDate, hour: Int, volunteers: Int = 0): Shift {
    val slot = slotOf(day, hour, ZONE)
    return Shift(
        eventId = "event-1",
        volunteerIds = List(volunteers) { "volunteer-$it" },
        startAt = slot.startAt,
        endAt = slot.endAt,
        createdAt = Instant.EPOCH,
    )
  }

  // ---------------------------------------------------------------- Edit

  @Test
  fun edit_showsTheTwelveSlotsOfEveryDay() {
    show(ShiftGridMode.EDIT)

    SLOT_START_HOURS.forEach { hour -> cell(sat, hour).assertExists() }
    // The hour of every row is written beside it, even those no slot is open at.
    compose.onNodeWithText("00").assertExists()
    compose.onNodeWithText("22").assertExists()
  }

  @Test
  fun edit_aClosedSlot_offersToOpenIt() {
    show(ShiftGridMode.EDIT)

    mark(ShiftGridTestTags.OPEN_ACTION, sat, 10).assertExists()
    mark(ShiftGridTestTags.CLOSE_ACTION, sat, 10).assertDoesNotExist()
    cell(sat, 10).performClick()

    assertEquals(listOf(slotOf(sat, 10, ZONE)), tapped)
  }

  @Test
  fun edit_anOpenSlotNobodyChose_offersToCloseIt() {
    show(ShiftGridMode.EDIT, shifts = listOf(shiftOf(sat, 10)))

    mark(ShiftGridTestTags.CLOSE_ACTION, sat, 10).assertExists()
    mark(ShiftGridTestTags.OPEN_ACTION, sat, 10).assertDoesNotExist()
    cell(sat, 10).performClick()

    assertEquals(listOf(slotOf(sat, 10, ZONE)), tapped)
  }

  @Test
  fun edit_aSlotVolunteersChose_showsItsLockAndCannotBeClosed() {
    show(ShiftGridMode.EDIT, shifts = listOf(shiftOf(sat, 10, volunteers = 3)))

    mark(ShiftGridTestTags.LOCK, sat, 10).assertExists()
    mark(ShiftGridTestTags.CLOSE_ACTION, sat, 10).assertDoesNotExist()
    cell(sat, 10).assert(hasContentDescription(LOCKED_WITH_THREE, substring = true))
    cell(sat, 10).assertHasNoClickAction()
    cell(sat, 10).performClick()

    assertTrue("a locked slot must report nothing, but reported $tapped", tapped.isEmpty())
  }

  // ---------------------------------------------------------------- Pick

  @Test
  fun pick_anOpenSlot_reportsThePick() {
    show(ShiftGridMode.PICK, shifts = listOf(shiftOf(sat, 10)))

    cell(sat, 10).assertIsOff()
    cell(sat, 10).performClick()

    assertEquals(listOf(slotOf(sat, 10, ZONE)), tapped)
  }

  @Test
  fun pick_aPickedSlot_showsItsCheckAndCanBeLetGo() {
    val shift = shiftOf(sat, 10)
    show(ShiftGridMode.PICK, shifts = listOf(shift), picked = setOf(shift.id))

    cell(sat, 10).assertIsOn()
    mark(ShiftGridTestTags.PICK_CHECK, sat, 10).assertExists()
    cell(sat, 10).performClick()

    assertEquals(listOf(slotOf(sat, 10, ZONE)), tapped)
  }

  @Test
  fun pick_aClosedSlot_cannotBePicked() {
    show(ShiftGridMode.PICK, shifts = listOf(shiftOf(sat, 10)))

    cell(sun, 10).assertHasNoClickAction()
    mark(ShiftGridTestTags.PICK_CHECK, sun, 10).assertDoesNotExist()
    cell(sun, 10).performClick()

    assertTrue("a closed slot must report nothing, but reported $tapped", tapped.isEmpty())
  }

  @Test
  fun pick_showsOnlyTheRowsFromTheFirstToTheLastOpenSlot() {
    show(ShiftGridMode.PICK, shifts = listOf(shiftOf(sat, 10), shiftOf(sun, 14)))

    cell(sat, 8).assertDoesNotExist()
    cell(sat, 16).assertDoesNotExist()
    listOf(10, 12, 14).forEach { cell(sat, it).assertExists() }
  }

  // ---------------------------------------------------------------- View

  @Test
  fun view_stacksTheRowsWithNoGap_andReadsOutOpenAndClosedSlots() {
    show(
        ShiftGridMode.VIEW,
        shifts = listOf(shiftOf(sat, 10), shiftOf(sat, 12), shiftOf(sat, 16)),
    )

    // The rows carry no spacing, so a cell always touches the one below it, drawn as one block
    // with it or not. This pins the pitch of the grid, not the corners a block rounds.
    assertEquals(
        cell(sat, 10).getUnclippedBoundsInRoot().bottom,
        cell(sat, 12).getUnclippedBoundsInRoot().top,
    )
    // 16 stands apart from them: the slot before it is closed.
    cell(sat, 14).assert(hasContentDescription(CLOSED, substring = true))
    cell(sat, 16).assert(hasContentDescription(OPEN, substring = true))
  }

  @Test
  fun view_keepsTheVolunteersOfEachSlotOfABlock() {
    show(
        ShiftGridMode.VIEW,
        shifts = listOf(shiftOf(sat, 10, volunteers = 3), shiftOf(sat, 12, volunteers = 1)),
    )

    cell(sat, 10).assert(hasContentDescription(OPEN_WITH_THREE, substring = true))
    cell(sat, 12).assert(hasContentDescription("open, 1 volunteer", substring = true))
  }

  @Test
  fun view_showsTheHoursOpenUnderEachDay() {
    show(
        ShiftGridMode.VIEW,
        shifts = listOf(shiftOf(sat, 10), shiftOf(sat, 12), shiftOf(sat, 14), shiftOf(sun, 10)),
    )

    header(sat).assert(hasText("6 h open"))
    header(sun).assert(hasText("2 h open"))
    header(fri).assert(hasText("0 h open"))
  }

  @Test
  fun view_withoutAnOpenSlot_showsNoRow() {
    show(ShiftGridMode.VIEW)

    compose.onNodeWithTag(ShiftGridTestTags.GRID).assertExists()
    SLOT_START_HOURS.forEach { hour -> cell(sat, hour).assertDoesNotExist() }
    // The days are still there, so the organizer sees the event they are about to fill.
    header(sat).assertExists()
  }

  @Test
  fun view_neverReportsATap() {
    show(ShiftGridMode.VIEW, shifts = listOf(shiftOf(sat, 10)))

    cell(sat, 10).assertHasNoClickAction()
    cell(sat, 10).performClick()

    assertTrue("View reads the slots, it does not change them: $tapped", tapped.isEmpty())
  }

  // ---------------------------------------------------------------- Layout

  @Test
  fun layout_showsAColumnForEveryEventDay() {
    show(ShiftGridMode.EDIT, days = fiveDays)

    fiveDays.forEach { header(it).assertExists() }
  }

  @Test
  fun layout_threeDaysFitTheWidth() {
    show(ShiftGridMode.EDIT)

    weekend.forEach { header(it).assertIsDisplayed() }
    assertTrue(
        "the last day must end within the grid",
        cell(sun, 10).getUnclippedBoundsInRoot().right <= GRID_WIDTH,
    )
    compose.onNodeWithTag(ShiftGridTestTags.NEXT_DAY).assertDoesNotExist()
  }

  @Test
  fun layout_aFiveDayEvent_scrollsSideways() {
    show(ShiftGridMode.EDIT, days = fiveDays)

    header(tue).assertIsNotDisplayed()
    // The first day is in view, so only the way forward is offered.
    compose.onNodeWithTag(ShiftGridTestTags.PREVIOUS_DAY).assertIsNotEnabled()
    compose.onNodeWithTag(ShiftGridTestTags.NEXT_DAY).assertIsEnabled()
    compose.onNodeWithTag(ShiftGridTestTags.NEXT_DAY).performClick()
    compose.onNodeWithTag(ShiftGridTestTags.NEXT_DAY).performClick()

    header(tue).assertIsDisplayed()
    header(fri).assertIsNotDisplayed()
    // The last day is in view: the grid is at its end and only the way back is left.
    compose.onNodeWithTag(ShiftGridTestTags.NEXT_DAY).assertIsNotEnabled()
    compose.onNodeWithTag(ShiftGridTestTags.PREVIOUS_DAY).assertIsEnabled()

    compose.onNodeWithTag(ShiftGridTestTags.PREVIOUS_DAY).performClick()
    compose.onNodeWithTag(ShiftGridTestTags.PREVIOUS_DAY).performClick()

    header(fri).assertIsDisplayed()
    header(tue).assertIsNotDisplayed()
    compose.onNodeWithTag(ShiftGridTestTags.PREVIOUS_DAY).assertIsNotEnabled()
  }

  @Test
  fun layout_whileTheDaysScroll_theHoursStayAndTheColumnsKeepTheirName() {
    show(ShiftGridMode.EDIT, days = fiveDays)
    val hourBefore = compose.onNodeWithText("08").getUnclippedBoundsInRoot().left
    val headerBefore = header(sat).getUnclippedBoundsInRoot().left
    val cellBefore = cell(sat, 8).getUnclippedBoundsInRoot().left

    compose.onNodeWithTag(ShiftGridTestTags.NEXT_DAY).performClick()

    assertEquals(hourBefore, compose.onNodeWithText("08").getUnclippedBoundsInRoot().left)
    assertTrue(
        "the days must have moved under the hours",
        cell(sat, 8).getUnclippedBoundsInRoot().left < cellBefore,
    )
    // One scroll drives the day names and the cells, so a column never drifts from its name.
    assertEquals(
        headerBefore - header(sat).getUnclippedBoundsInRoot().left,
        cellBefore - cell(sat, 8).getUnclippedBoundsInRoot().left,
    )
  }

  @Test
  fun layout_everySlotCellIsAtLeast48dpHigh() {
    val shift = shiftOf(sat, 10, volunteers = 2)
    var mode by mutableStateOf(ShiftGridMode.EDIT)
    showChanging(mode = { mode }, shifts = { listOf(shift) }, picked = { setOf(shift.id) })

    // A locked cell, a picked one and an empty one are the tallest and the shortest it draws.
    ShiftGridMode.entries.forEach {
      mode = it
      cell(sat, 10).assertHeightIsAtLeast(48.dp)
      cell(sun, 10).assertHeightIsAtLeast(48.dp)
    }
  }

  // ------------------------------------------------- The state of the parent

  @Test
  fun shifts_whenASlotOpens_itsCellFollows() {
    var shifts by mutableStateOf(emptyList<Shift>())
    showChanging(shifts = { shifts })
    mark(ShiftGridTestTags.OPEN_ACTION, sat, 10).assertExists()

    shifts = listOf(shiftOf(sat, 10))

    mark(ShiftGridTestTags.CLOSE_ACTION, sat, 10).assertExists()
    cell(sat, 10).assert(hasContentDescription(OPEN, substring = true))
  }

  @Test
  fun shifts_whenASlotCloses_itsCellFollows() {
    var shifts by mutableStateOf(listOf(shiftOf(sat, 10)))
    showChanging(shifts = { shifts })
    mark(ShiftGridTestTags.CLOSE_ACTION, sat, 10).assertExists()

    shifts = emptyList()

    mark(ShiftGridTestTags.OPEN_ACTION, sat, 10).assertExists()
    cell(sat, 10).assert(hasContentDescription(CLOSED, substring = true))
  }

  @Test
  fun shifts_whenAVolunteerChoosesASlot_itLocksWithItsCount() {
    var shifts by mutableStateOf(listOf(shiftOf(sat, 10)))
    showChanging(shifts = { shifts })
    mark(ShiftGridTestTags.LOCK, sat, 10).assertDoesNotExist()

    shifts = listOf(shiftOf(sat, 10, volunteers = 3))

    mark(ShiftGridTestTags.LOCK, sat, 10).assertExists()
    cell(sat, 10).assert(hasContentDescription(LOCKED_WITH_THREE, substring = true))
    cell(sat, 10).assertHasNoClickAction()
  }

  @Test
  fun pickedShiftIds_whenTheyChange_theTicksFollow() {
    val shift = shiftOf(sat, 10)
    var picked by mutableStateOf(emptySet<String>())
    showChanging(
        mode = { ShiftGridMode.PICK },
        shifts = { listOf(shift) },
        picked = { picked },
    )
    cell(sat, 10).assertIsOff()

    picked = setOf(shift.id)

    cell(sat, 10).assertIsOn()
    mark(ShiftGridTestTags.PICK_CHECK, sat, 10).assertExists()
  }

  // ---------------------------------------------------------------- Edges

  @Test
  fun days_whenThereIsNone_theGridHoldsNoSlot() {
    show(ShiftGridMode.EDIT, days = emptyList())

    compose.onNodeWithTag(ShiftGridTestTags.GRID).assertExists()
    compose.onNodeWithText("00").assertDoesNotExist()
  }

  @Test
  fun days_whenTheEventLastsOne_itsColumnFillsTheWidth() {
    show(ShiftGridMode.EDIT, days = listOf(sat))

    val bounds = cell(sat, 10).getUnclippedBoundsInRoot()

    // The whole width but the hour column and the gap before the first day.
    assertEquals(GRID_WIDTH - 28.dp - 4.dp, bounds.right - bounds.left)
  }

  @Test
  fun view_onADayTheClocksGoForward_countsTheHoursItReallyHolds() {
    show(
        ShiftGridMode.VIEW,
        days = listOf(springForward),
        shifts = SLOT_START_HOURS.map { shiftOf(springForward, it) },
    )

    // 02:00 never comes that night, so the day holds 23 h rather than 24.
    header(springForward).assert(hasText("23 h open"))
  }

  @Test
  fun edit_onADayTheClocksGoForward_aSlotReadsTheHoursItReallyRuns() {
    show(ShiftGridMode.EDIT, days = listOf(springForward))

    // The slot of 02:00 starts at 03:00 and lasts one hour, which it says as it is.
    cell(springForward, 2).assert(hasContentDescription("Sunday 29 March 03:00 to 04:00, closed"))
  }

  private companion object {
    val ZONE: ZoneId = ZoneId.of("Europe/Zurich")

    /** The grid of the design: a 390 dp screen with its 16 dp margins. */
    val GRID_WIDTH = 358.dp

    val fri: LocalDate = LocalDate.parse("2026-06-19")
    val sat: LocalDate = LocalDate.parse("2026-06-20")
    val sun: LocalDate = LocalDate.parse("2026-06-21")
    val mon: LocalDate = LocalDate.parse("2026-06-22")
    val tue: LocalDate = LocalDate.parse("2026-06-23")

    /** The night the clocks go forward in [ZONE]: 02:00 never comes, so that slot lasts 1 h. */
    val springForward: LocalDate = LocalDate.parse("2026-03-29")

    val weekend = listOf(fri, sat, sun)
    val fiveDays = listOf(fri, sat, sun, mon, tue)

    const val OPEN = ", open"
    const val CLOSED = ", closed"
    const val OPEN_WITH_THREE = "open, 3 volunteers"
    const val LOCKED_WITH_THREE = "open, locked, 3 volunteers"
  }
}
