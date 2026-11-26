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
 * Only shown for an event with shifts. While the volunteer picks, the grid starts at the day the
 * picking opened, so a running event only offers its days to come. Once the request is sent,
 * nothing is left to pick: only the line summing up its shifts is shown. On an event whose shifts
 * have all started, it says none is left instead of the grid.
 *
 * @param requestSent whether the volunteer's request is sent, so the grid and its help are hidden.
 * @param zone the zone the days and the hours are read in, the phone's.
 */
@Composable
internal fun ShiftSelector(
    event: Event,
    shiftPicking: ShiftPicking,
    onShiftPicked: (String) -> Unit,
    requestSent: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
) {
  val colors = MaterialTheme.colorScheme
  val shifts = shiftPicking.shifts
  val firstDay = shiftPicking.pickableFrom?.let { maxOf(it, event.startAt) } ?: event.startAt
  // The event's days from the first one offered, widened to the day of each shift so that none is
  // left out of the grid.
  val days =
      remember(event, shifts, firstDay, zone) {
        eventDays(
            firstDay..event.endAt,
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
    if (shiftPicking.noneLeft) {
      Text(
          text = stringResource(R.string.join_event_no_shift_left),
          color = colors.onSurfaceVariant,
          fontSize = 14.sp,
          modifier = Modifier.testTag(JoinEventScreenTestTags.NO_SHIFT_LEFT),
      )
      return@Column
    }
    if (!requestSent) {
      Text(
          text = stringResource(R.string.join_event_choose_shifts_help),
          color = colors.onSurfaceVariant,
          fontSize = 14.sp,
          modifier = Modifier.testTag(JoinEventScreenTestTags.SHIFTS_HELP),
      )
      ShiftGrid(
          days = days,
          shifts = shifts,
          mode = ShiftGridMode.PICK,
          zone = zone,
          pickedShiftIds = shiftPicking.pickedShiftIds,
          onSlot = { onShiftPicked(it.shiftId) },
      )
    }
    PickedSummary(shiftPicking, zone)
  }
}

/**
 * How many shifts are picked and when, back-to-back ones joined. Nothing while none is: the line
 * under Apply already asks for one.
 */
@Composable
private fun PickedSummary(shiftPicking: ShiftPicking, zone: ZoneId) {
  val picked = shiftPicking.picked
  if (picked.isEmpty()) return
  Text(
      text =
          pluralStringResource(
              R.plurals.join_event_shifts_picked,
              picked.size,
              picked.size,
              availabilityOf(picked).joinToString(", ") { it.formatShiftHours(zone) },
          ),
      color = MaterialTheme.colorScheme.primary,
      fontSize = 13.sp,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.testTag(JoinEventScreenTestTags.SHIFTS_PICKED),
  )
}
