// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.shifts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.shifter.model.shift.Shift
import com.swent.shifter.model.shift.slotOf
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Previews of ShiftGrid, kept apart from the component itself: they are tooling the IDE renders and
// no test ever runs, so coverage is told to leave this file alone.

private val ZONE: ZoneId = ZoneId.of("Europe/Zurich")

/** The festival of the Figma frames: Friday 20 to Monday 23 June, three of its days on screen. */
private val FRIDAY: LocalDate = LocalDate.parse("2025-06-20")
private val EVENT: List<LocalDate> = (0L until 4L).map { FRIDAY.plusDays(it) }

private fun shift(day: Long, hour: Int, volunteers: Int = 0): Shift {
  val slot = slotOf(FRIDAY.plusDays(day), hour, ZONE)
  return Shift(
      eventId = "summer-festival",
      volunteerIds = List(volunteers) { "volunteer-$it" },
      startAt = slot.startAt,
      endAt = slot.endAt,
      createdAt = Instant.parse("2025-06-01T09:00:00Z"),
  )
}

/** The 30 h over 4 days of the Figma frames: 6 h on Friday, 12 h on Saturday, 8 h on Sunday. */
private val SHIFTS: List<Shift> =
    listOf(
        shift(0, 18),
        shift(0, 20, volunteers = 2),
        shift(0, 22),
        shift(1, 8),
        shift(1, 10, volunteers = 3),
        shift(1, 12),
        shift(1, 18, volunteers = 1),
        shift(1, 20),
        shift(1, 22),
        shift(2, 10),
        shift(2, 12, volunteers = 1),
        shift(2, 14),
        shift(2, 16),
        shift(3, 10),
    )

@Composable
private fun PreviewHost(
    mode: ShiftGridMode,
    shifts: List<Shift> = SHIFTS,
    picked: Set<String> = emptySet(),
) {
  ShifterTheme {
    // The width of a screen's content on a phone: three days fit it, the fourth scrolls into view.
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
      ShiftGrid(
          days = EVENT,
          shifts = shifts,
          mode = mode,
          zone = ZONE,
          modifier = Modifier.width(358.dp),
          pickedShiftIds = picked,
      )
    }
  }
}

@Preview(name = "View: the slots the organizer opened", heightDp = 560)
@Composable
private fun ViewPreview() = PreviewHost(ShiftGridMode.VIEW)

@Preview(name = "Edit: opening and closing the slots", heightDp = 740)
@Composable
private fun EditPreview() = PreviewHost(ShiftGridMode.EDIT)

@Preview(name = "Pick: the slots a volunteer can work", heightDp = 560)
@Composable
private fun PickPreview() =
    PreviewHost(
        mode = ShiftGridMode.PICK,
        picked = setOf(shift(0, 18).id, shift(1, 10).id, shift(1, 12).id),
    )

@Preview(name = "View: an event with no slot open yet", heightDp = 160)
@Composable
private fun EmptyViewPreview() = PreviewHost(ShiftGridMode.VIEW, shifts = emptyList())
