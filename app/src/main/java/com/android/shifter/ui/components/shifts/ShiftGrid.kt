// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.shifts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.model.shift.SLOT_START_HOURS
import com.swent.shifter.model.shift.Shift
import com.swent.shifter.model.shift.ShiftSlot
import com.swent.shifter.model.shift.slotOf
import com.swent.shifter.model.time.formatDayOfWeek
import com.swent.shifter.model.time.formatFullDate
import com.swent.shifter.model.time.formatHour
import com.swent.shifter.model.time.formatTime
import com.swent.shifter.ui.theme.shifter_divider
import com.swent.shifter.ui.theme.shifter_mutedText
import com.swent.shifter.ui.theme.shifter_onShiftLocked
import com.swent.shifter.ui.theme.shifter_shiftClosed
import com.swent.shifter.ui.theme.shifter_shiftJoin
import com.swent.shifter.ui.theme.shifter_shiftOpen
import com.swent.shifter.ui.theme.shifter_shiftOpenable
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

object ShiftGridTestTags {
  const val GRID = "shiftGrid"
  const val PREVIOUS_DAY = "shiftGridPreviousDay"
  const val NEXT_DAY = "shiftGridNextDay"

  /**
   * The mark a cell draws. The same tag is used in every cell, so a test asks for the one under the
   * slot it means, on the unmerged tree: a cell is read out as a single node.
   */
  const val OPEN_ACTION = "shiftGridOpenAction"
  const val CLOSE_ACTION = "shiftGridCloseAction"
  const val LOCK = "shiftGridLock"
  const val PICK_CHECK = "shiftGridPickCheck"

  /** The column header of [day]: its name, its number and, for an organizer, its hours open. */
  fun day(day: LocalDate) = "shiftGridDay_$day"

  /**
   * The cell of the slot of [day] starting at [hour]. The design names these cells after the day of
   * the week ("SLOT_SAT_10"); the date is used instead, since a day's name comes back every week
   * and an event can run longer than that.
   */
  fun slot(day: LocalDate, hour: Int) = "shiftGridSlot_${day}_${formatHour(hour)}"
}

/** What the reader of the grid does with its slots. */
enum class ShiftGridMode {
  /** The organizer reads the slots they opened; nothing is tapped. */
  VIEW,
  /** The organizer taps a closed slot to open it, and an open one nobody chose to close it. */
  EDIT,
  /** A volunteer taps the open slots they can work, and taps one again to let it go. */
  PICK,
}

/** The days of the event that fit the width; beyond them the grid scrolls sideways. */
private const val DAYS_ON_SCREEN = 3

/** The hour column, the gap between two columns, and the height of a slot's cell. */
private val HOUR_WIDTH = 28.dp
private val COLUMN_GAP = 4.dp
private val ROW_HEIGHT = 48.dp

/** The chip of a cell where the slots stand apart, drawn inside the cell's 48 dp touch target. */
private val CHIP_HEIGHT = 44.dp

/** The corners of a block of slots: round at its ends, square or nearly so between two of them. */
private val VIEW_END_CORNER = 8.dp
private val CHIP_END_CORNER = 6.dp
private val CHIP_JOINED_CORNER = 3.dp

/**
 * The week grid of an event's shifts: one column per day, one row per 2 h slot, the same grid for
 * the organizer who opens the slots and for the volunteer who picks the ones they can work.
 *
 * The grid is stateless: it draws [shifts] over [days] and reports the slot that was tapped, and
 * never opens, closes or picks anything itself. A caller that hands it other [shifts] or
 * [pickedShiftIds] sees the counts, the locks and the ticks follow at once.
 *
 * A shift is drawn in the cell of the slot it opened, found by its [Shift.id], which is the
 * [ShiftSlot.shiftId] of that day and hour. Every duration, the hours open under a day included, is
 * read from a shift's start and end rather than from the 2 h of the clock, so the nights the clocks
 * change count for what they really last.
 *
 * [ShiftGridMode.EDIT] shows the 12 slots of a day; [ShiftGridMode.VIEW] and [ShiftGridMode.PICK]
 * show only the rows from the first to the last open slot of the event, and no row when none is
 * open.
 *
 * @param days the days of the event, in order, as `eventDays` gives them.
 * @param shifts the open shifts of the event, with the volunteers who chose them. One whose slot is
 *   not a slot of [days] is simply not drawn.
 * @param mode what the reader does with the slots.
 * @param zone the zone the event's days are read in, the one `eventDays` already used.
 * @param pickedShiftIds in [ShiftGridMode.PICK], the ids of the shifts the volunteer has picked:
 *   the caller owns them, the grid only shows them.
 * @param onSlot the slot whose cell was tapped: in [ShiftGridMode.EDIT] one to open when it is
 *   closed and to close when it is open, in [ShiftGridMode.PICK] one to pick or to let go. Never
 *   called in [ShiftGridMode.VIEW], nor for a closed slot in [ShiftGridMode.PICK] or a slot
 *   volunteers already chose in [ShiftGridMode.EDIT].
 */
@Composable
fun ShiftGrid(
    days: List<LocalDate>,
    shifts: List<Shift>,
    mode: ShiftGridMode,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    pickedShiftIds: Set<String> = emptySet(),
    onSlot: (ShiftSlot) -> Unit = {},
) {
  val openShifts = remember(shifts) { shifts.associateBy { it.id } }
  val hours = remember(days, openShifts, mode, zone) { rowsOf(days, openShifts, mode, zone) }
  val shiftAt = { day: LocalDate, hour: Int -> openShifts[slotOf(day, hour, zone).shiftId] }
  // Blocks are drawn for an organizer, never for a volunteer picking one slot at a time.
  val joinsBlocks = mode != ShiftGridMode.PICK

  BoxWithConstraints(modifier = modifier.fillMaxWidth().testTag(ShiftGridTestTags.GRID)) {
    val columns = days.size.coerceIn(1, DAYS_ON_SCREEN)
    val columnWidth = (maxWidth - HOUR_WIDTH - COLUMN_GAP * columns) / columns
    val scroll = rememberScrollState()
    Column {
      // A volunteer picks slots; the hours opened are the organizer's own tally.
      val openHours =
          if (mode == ShiftGridMode.PICK) null else days.map { openHoursOf(it, openShifts, zone) }
      DaysHeader(days, openHours, columnWidth, scroll)
      hours.forEachIndexed { row, hour ->
        Row(horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP)) {
          Box(modifier = Modifier.width(HOUR_WIDTH).height(ROW_HEIGHT)) {
            Text(
                text = formatHour(hour),
                style = MaterialTheme.typography.labelSmall,
                color = shifter_mutedText,
            )
          }
          Row(
              modifier = Modifier.horizontalScroll(scroll),
              horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
          ) {
            days.forEach { day ->
              val slot = slotOf(day, hour, zone)
              val shift = openShifts[slot.shiftId]
              // Only two open cells join: a closed one, and the first and last row, close a block.
              val joins = joinsBlocks && shift != null
              SlotCell(
                  day = day,
                  hour = hour,
                  slot = slot,
                  shift = shift,
                  mode = mode,
                  picked = pickedShiftIds.contains(slot.shiftId),
                  joinedAbove = joins && row > 0 && shiftAt(day, hours[row - 1]) != null,
                  joinedBelow =
                      joins && row < hours.lastIndex && shiftAt(day, hours[row + 1]) != null,
                  width = columnWidth,
                  zone = zone,
                  onSlot = onSlot,
              )
            }
          }
        }
      }
    }
  }
}

/**
 * The hours of the rows to draw: the 12 slots of a day in [ShiftGridMode.EDIT], otherwise the
 * stretch from the first to the last hour a slot of the event is open at, and no row when none is.
 */
private fun rowsOf(
    days: List<LocalDate>,
    openShifts: Map<String, Shift>,
    mode: ShiftGridMode,
    zone: ZoneId,
): List<Int> {
  if (days.isEmpty()) return emptyList()
  if (mode == ShiftGridMode.EDIT) return SLOT_START_HOURS
  val open = SLOT_START_HOURS.filter { hour ->
    days.any { openShifts.containsKey(slotOf(it, hour, zone).shiftId) }
  }
  if (open.isEmpty()) return emptyList()
  return SLOT_START_HOURS.filter { it in open.first()..open.last() }
}

/**
 * The whole hours [day] has open, read from each shift's start and end: on a day the clocks change
 * a slot lasts 1 h or 3 h, so a day holds 23 or 25 h rather than 24.
 */
private fun openHoursOf(day: LocalDate, openShifts: Map<String, Shift>, zone: ZoneId): Long =
    SLOT_START_HOURS.sumOf { hour ->
      openShifts[slotOf(day, hour, zone).shiftId]?.let {
        Duration.between(it.startAt, it.endAt).toHours()
      } ?: 0L
    }

/**
 * The days above the grid, scrolling with its cells, with the arrows that bring the next and the
 * previous one into view when the event is longer than [DAYS_ON_SCREEN].
 *
 * @param openHours the hours open under each of [days], or null for a reader not offered them.
 */
@Composable
private fun DaysHeader(
    days: List<LocalDate>,
    openHours: List<Long>?,
    columnWidth: Dp,
    scroll: ScrollState,
) {
  val scope = rememberCoroutineScope()
  val step = with(LocalDensity.current) { (columnWidth + COLUMN_GAP).roundToPx() }
  Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP)) {
      Spacer(modifier = Modifier.width(HOUR_WIDTH))
      Row(
          modifier = Modifier.horizontalScroll(scroll),
          horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
      ) {
        days.forEachIndexed { i, day -> DayColumnHeader(day, openHours?.get(i), columnWidth) }
      }
    }
    if (days.size > DAYS_ON_SCREEN) {
      ScrollArrow(
          icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
          description = stringResource(R.string.shift_grid_previous_day),
          enabled = scroll.canScrollBackward,
          modifier = Modifier.align(Alignment.CenterStart).testTag(ShiftGridTestTags.PREVIOUS_DAY),
          onClick = { scope.launch { scroll.animateScrollTo(scroll.value - step) } },
      )
      ScrollArrow(
          icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          description = stringResource(R.string.shift_grid_next_day),
          enabled = scroll.canScrollForward,
          modifier = Modifier.align(Alignment.CenterEnd).testTag(ShiftGridTestTags.NEXT_DAY),
          onClick = { scope.launch { scroll.animateScrollTo(scroll.value + step) } },
      )
    }
  }
}

@Composable
private fun ScrollArrow(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
  IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp),
    )
  }
}

@Composable
private fun DayColumnHeader(day: LocalDate, openHours: Long?, width: Dp) {
  Column(
      modifier =
          Modifier.width(width)
              .padding(start = 12.dp, bottom = 2.dp)
              .semantics(mergeDescendants = true) {}
              .testTag(ShiftGridTestTags.day(day))
  ) {
    Text(
        text = day.formatDayOfWeek(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
    Text(
        text = day.dayOfMonth.toString(),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
    )
    if (openHours != null) {
      Text(
          text = stringResource(R.string.shift_grid_hours_open, openHours),
          style = MaterialTheme.typography.labelSmall,
          color = shifter_mutedText,
          maxLines = 1,
      )
    }
  }
}

/**
 * One slot of the grid, 48 dp tall whatever it draws, so every cell can be tapped and read out.
 *
 * Only a cell the mode acts on carries an action: a volunteer never taps a closed slot, and an
 * organizer never closes one volunteers chose, which its lock shows.
 */
@Composable
private fun SlotCell(
    day: LocalDate,
    hour: Int,
    slot: ShiftSlot,
    shift: Shift?,
    mode: ShiftGridMode,
    picked: Boolean,
    joinedAbove: Boolean,
    joinedBelow: Boolean,
    width: Dp,
    zone: ZoneId,
    onSlot: (ShiftSlot) -> Unit,
) {
  val volunteers = shift?.volunteerIds?.size ?: 0
  val locked = mode == ShiftGridMode.EDIT && volunteers > 0
  val description = slotDescription(day, slot, shift, mode, picked, zone)
  val action =
      when {
        mode == ShiftGridMode.EDIT && !locked ->
            Modifier.clickable(role = Role.Button) { onSlot(slot) }
        mode == ShiftGridMode.PICK && shift != null ->
            Modifier.toggleable(value = picked, role = Role.Checkbox) { onSlot(slot) }
        else -> Modifier
      }
  Box(
      modifier =
          Modifier.width(width)
              .height(ROW_HEIGHT)
              .then(action)
              .semantics(mergeDescendants = true) { contentDescription = description }
              .testTag(ShiftGridTestTags.slot(day, hour)),
      contentAlignment = Alignment.Center,
  ) {
    when (mode) {
      ShiftGridMode.VIEW -> ViewChip(shift != null, volunteers, joinedAbove, joinedBelow)
      ShiftGridMode.EDIT -> EditChip(shift != null, locked, volunteers, joinedAbove, joinedBelow)
      ShiftGridMode.PICK -> PickChip(shift != null, picked)
    }
  }
}

/** An open slot as a block, back to back with the ones above and below it, or the bare grid. */
@Composable
private fun ViewChip(open: Boolean, volunteers: Int, joinedAbove: Boolean, joinedBelow: Boolean) {
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .height(ROW_HEIGHT)
              .slotChip(
                  blockShape(joinedAbove, joinedBelow, VIEW_END_CORNER, 0.dp),
                  if (open) shifter_shiftOpen else shifter_shiftClosed,
              ),
      contentAlignment = Alignment.CenterStart,
  ) {
    // The line of the grid above a cell, in the block's own tone where the block carries on. The
    // top of a block has none: its round corner is what shows where it starts.
    if (!open || joinedAbove) {
      Box(
          modifier =
              Modifier.fillMaxWidth()
                  .height(1.dp)
                  .background(if (open) shifter_shiftJoin else shifter_divider)
                  .align(Alignment.TopStart)
      )
    }
    if (open) {
      Box(
          modifier =
              Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary)
      )
      if (volunteers > 0) {
        VolunteerCount(
            count = volunteers,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(start = 12.dp),
        )
      }
    }
  }
}

/** A slot to open ("+"), to close ("×"), or locked by the volunteers who chose it. */
@Composable
private fun EditChip(
    open: Boolean,
    locked: Boolean,
    volunteers: Int,
    joinedAbove: Boolean,
    joinedBelow: Boolean,
) {
  val colors = MaterialTheme.colorScheme
  val shape =
      if (open) blockShape(joinedAbove, joinedBelow, CHIP_END_CORNER, CHIP_JOINED_CORNER)
      else RoundedCornerShape(CHIP_END_CORNER)
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .height(CHIP_HEIGHT)
              .slotChip(
                  shape = shape,
                  fill =
                      when {
                        locked -> colors.primaryContainer
                        open -> shifter_shiftOpen
                        else -> shifter_shiftOpenable
                      },
                  // Outlined while the organizer can still close it; a locked slot is not.
                  border = if (open && !locked) BorderStroke(1.5.dp, colors.primary) else null,
              ),
      contentAlignment = Alignment.Center,
  ) {
    when {
      locked ->
          Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = shifter_onShiftLocked,
                modifier = Modifier.size(12.dp).testTag(ShiftGridTestTags.LOCK),
            )
            VolunteerCount(count = volunteers, color = shifter_onShiftLocked)
          }
      open ->
          Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = null,
              tint = colors.onPrimaryContainer,
              modifier = Modifier.size(16.dp).testTag(ShiftGridTestTags.CLOSE_ACTION),
          )
      else ->
          Icon(
              imageVector = Icons.Filled.Add,
              contentDescription = null,
              tint = colors.onSurfaceVariant,
              modifier = Modifier.size(16.dp).testTag(ShiftGridTestTags.OPEN_ACTION),
          )
    }
  }
}

/** An open slot the volunteer can pick, filled with a tick once they have. */
@Composable
private fun PickChip(open: Boolean, picked: Boolean) {
  val colors = MaterialTheme.colorScheme
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .height(CHIP_HEIGHT)
              .slotChip(
                  shape = RoundedCornerShape(CHIP_END_CORNER),
                  fill =
                      when {
                        !open -> shifter_shiftClosed
                        picked -> colors.primary
                        else -> shifter_shiftOpen
                      },
                  // An open slot waiting to be picked is outlined; picked, it is filled instead.
                  border =
                      if (open && !picked) BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f))
                      else null,
              ),
      contentAlignment = Alignment.Center,
  ) {
    if (open && picked) {
      Icon(
          imageVector = Icons.Filled.Check,
          contentDescription = null,
          tint = colors.onPrimary,
          modifier = Modifier.size(14.dp).testTag(ShiftGridTestTags.PICK_CHECK),
      )
    }
  }
}

@Composable
private fun VolunteerCount(count: Int, color: Color, modifier: Modifier = Modifier) {
  Row(
      modifier = modifier,
      horizontalArrangement = Arrangement.spacedBy(3.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        imageVector = Icons.Filled.Person,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(11.dp),
    )
    Text(
        text = count.toString(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
    )
  }
}

/** Read out for a cell, e.g. "Saturday 21 June 10:00 to 12:00, open, 3 volunteers". */
@Composable
private fun slotDescription(
    day: LocalDate,
    slot: ShiftSlot,
    shift: Shift?,
    mode: ShiftGridMode,
    picked: Boolean,
    zone: ZoneId,
): String {
  val volunteers = shift?.volunteerIds?.size ?: 0
  val state =
      when {
        shift == null -> stringResource(R.string.shift_grid_slot_closed)
        mode == ShiftGridMode.PICK && picked -> stringResource(R.string.shift_grid_slot_picked)
        mode == ShiftGridMode.EDIT && volunteers > 0 ->
            stringResource(R.string.shift_grid_slot_open_locked)
        else -> stringResource(R.string.shift_grid_slot_open)
      }
  val said =
      if (volunteers == 0) state
      else
          stringResource(
              R.string.shift_grid_slot_and_volunteers,
              state,
              pluralStringResource(R.plurals.shift_grid_volunteers, volunteers, volunteers),
          )
  return stringResource(
      R.string.shift_grid_slot_description,
      day.formatFullDate(),
      slot.startAt.formatTime(zone),
      slot.endAt.formatTime(zone),
      said,
  )
}

/** A chip's tone and, where the reader can act on its slot, the outline that follows its shape. */
private fun Modifier.slotChip(shape: Shape, fill: Color, border: BorderStroke? = null): Modifier =
    clip(shape)
        .background(fill)
        .then(if (border == null) Modifier else Modifier.border(border, shape))

/** Round at the ends of a block of open slots, square or nearly so between two of them. */
private fun blockShape(joinedAbove: Boolean, joinedBelow: Boolean, end: Dp, joined: Dp) =
    RoundedCornerShape(
        topStart = if (joinedAbove) joined else end,
        topEnd = if (joinedAbove) joined else end,
        bottomStart = if (joinedBelow) joined else end,
        bottomEnd = if (joinedBelow) joined else end,
    )
