// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.shift.availabilityOf
import com.swent.shifter.model.shift.formatShiftHours
import com.swent.shifter.ui.components.days.eventDays
import com.swent.shifter.ui.components.shifts.ShiftGrid
import com.swent.shifter.ui.components.shifts.ShiftGridMode
import java.time.ZoneId

/**
 * The shifts of the event, of which the volunteer picks every one they can work, in the week grid
 * the organizer opened them in, with a line summing up the picks: "3 shifts picked: Fri 18–20, Sat
 * 10–14".
 *
 * Only shown for an event with shifts. Once the request is sent, the grid shows the shifts it holds
 * and the ViewModel ignores the taps, as the team stays fixed.
 *
 * @param zone the zone the days and the hours are read in, the phone's.
 */
@Composable
internal fun ShiftSelector(
    event: Event,
    shiftPicking: ShiftPicking,
    onShiftPicked: (String) -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
  val colors = MaterialTheme.colorScheme
  val shifts = shiftPicking.shifts
  // The event's days, widened to the day of each shift so that none is left out of the grid.
  val days =
      remember(event, shifts, zone) {
        eventDays(
            event.startAt..event.endAt,
            shifts.map { it.startAt.atZone(zone).toLocalDate() },
            zone,
        )
      }
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
        text = stringResource(R.string.join_event_choose_shifts),
        color = colors.onBackground,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.join_event_choose_shifts_help),
        color = colors.onSurfaceVariant,
        fontSize = 14.sp,
    )
    ShiftGrid(
        days = days,
        shifts = shifts,
        mode = ShiftGridMode.PICK,
        zone = zone,
        pickedShiftIds = shiftPicking.pickedShiftIds,
        onSlot = { onShiftPicked(it.shiftId) },
    )
    PickedSummary(shiftPicking, zone)
  }
}

/** How many shifts are picked and when, back-to-back ones joined, or that none is yet. */
@Composable
private fun PickedSummary(shiftPicking: ShiftPicking, zone: ZoneId) {
  val picked = shiftPicking.picked
  val colors = MaterialTheme.colorScheme
  val text =
      if (picked.isEmpty()) stringResource(R.string.join_event_no_shift_picked)
      else
          pluralStringResource(
              R.plurals.join_event_shifts_picked,
              picked.size,
              picked.size,
              availabilityOf(picked).joinToString(", ") { it.formatShiftHours(zone) },
          )
  Text(
      text = text,
      color = if (picked.isEmpty()) colors.onBackground else colors.primary,
      fontSize = 13.sp,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.testTag(JoinEventScreenTestTags.SHIFTS_PICKED),
  )
}
