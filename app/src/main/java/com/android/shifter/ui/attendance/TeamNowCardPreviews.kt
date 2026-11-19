// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

// Previews of TeamNowCard, kept apart from it: they are tooling the IDE renders and no test ever
// runs, so coverage is told to leave this file alone.

/** The mission of the design starts at 14:00; the card is read ten minutes in, as it is drawn. */
private val START = Instant.parse("2027-06-20T14:00:00Z")
private val NOW = START + Duration.ofMinutes(10)

private fun mission(
    id: String,
    title: String,
    team: String,
    startAt: Instant = START,
    assignees: List<AttendanceVolunteer> = emptyList(),
) =
    AttendanceMission(
        id = id,
        title = title,
        teamId = team.lowercase(),
        teamName = team,
        volunteersNeeded = 3,
        startAt = startAt,
        endAt = startAt + Duration.ofMinutes(90),
        assignees = assignees,
        attendanceKnown = true,
    )

/**
 * The three volunteers already at work on both missions: each asks for three, so neither is short
 * of people, which is what lets the card say the team is all on duty.
 */
private val AT_WORK =
    listOf("Inès Roux", "Tom Keller", "Ava Blanc").mapIndexed { i, name ->
      AttendanceVolunteer("p${i + 1}", name, checkedInAt = NOW)
    }

private val SORTING = mission("m1", "Food sorting", "Logistics", assignees = AT_WORK)
private val WELCOME = mission("m2", "Welcome desk", "Front", assignees = AT_WORK)

private val CHLOE = AttendanceVolunteer("u1", "Chloé Dubois")
private val NOAH = AttendanceVolunteer("u2", "Noah Favre")

/** What comes after them, its check-in open: two of the three it needs are there already. */
private val BRIEFING =
    mission(
        "m3",
        "Briefing",
        "Front",
        startAt = NOW + Duration.ofMinutes(10),
        assignees = listOf(CHLOE.copy(checkedInAt = NOW), NOAH.copy(checkedInAt = NOW)),
    )

/** [this] with [volunteer] checked in, as the state reports them once the manager has. */
private fun AttendanceMission.with(volunteer: AttendanceVolunteer) =
    copy(assignees = assignees + volunteer.copy(checkedInAt = NOW))

/**
 * The card at the width it has on the Overview, on the screen's own background.
 *
 * [onCheckIn] has no default: a preview that forgets it would draw a card whose rows answer a tap
 * with nothing, which reads as the card losing the check-in rather than as a preview missing an
 * argument.
 */
@Composable
private fun CardFrame(state: TeamNowState, onCheckIn: (String, String) -> Unit) {
  ShifterTheme {
    Box(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp).width(358.dp)) {
      TeamNowCard(
          state = state,
          now = NOW,
          onCheckIn = onCheckIn,
          onViewOnMap = {},
          zone = ZoneOffset.UTC,
      )
    }
  }
}

/** The frame of the design: one volunteer missing for ten minutes, three others at work. */
@Preview(showBackground = true, backgroundColor = 0xFF151D27, widthDp = 390, heightDp = 520)
@Composable
private fun TeamNowCardMissingPreview() {
  CardFrame(
      state = TeamNowState.Missing(listOf(MissingAssignee(SORTING, CHLOE)), onDuty = 3),
      onCheckIn = { _, _ -> },
  )
}

/** The team at work: two missions running, and the next one a volunteer short. */
@Preview(showBackground = true, backgroundColor = 0xFF151D27, widthDp = 390, heightDp = 560)
@Composable
private fun TeamNowCardRunningPreview() {
  CardFrame(
      state = TeamNowState.Running(listOf(SORTING, WELCOME), onDuty = 3, next = listOf(BRIEFING)),
      onCheckIn = { _, _ -> },
  )
}

/**
 * The same card to play with in the IDE's interactive preview: tapping a row checks that volunteer
 * in, as the manager would.
 *
 * The state answers the way `AttendanceViewModel` does, so what the tap shows is what the app
 * shows: the volunteer stops being reported missing and is counted on duty at once, while the card
 * keeps their row a moment in green. Checking both in leaves nobody missing, so the team is at work
 * and the card moves to Running, carrying the green row into it.
 */
@Preview(showBackground = true, backgroundColor = 0xFF151D27, widthDp = 390, heightDp = 760)
@Composable
private fun TeamNowCardCheckInPreview() {
  var state by remember {
    mutableStateOf<TeamNowState>(
        TeamNowState.Missing(
            listOf(MissingAssignee(SORTING, CHLOE), MissingAssignee(WELCOME, NOAH)),
            onDuty = 3,
        )
    )
  }
  CardFrame(
      state = state,
      // Named, not a trailing lambda: what the card calls on a tap is spelled out here.
      onCheckIn = { missionId, userId ->
        (state as? TeamNowState.Missing)?.let { missing ->
          val left =
              missing.missing.filterNot {
                it.mission.id == missionId && it.volunteer.userId == userId
              }
          val onDuty = missing.onDuty + 1
          state =
              if (left.isEmpty()) {
                TeamNowState.Running(
                    listOf(SORTING.with(CHLOE), WELCOME.with(NOAH)),
                    onDuty,
                    next = listOf(BRIEFING),
                )
              } else {
                missing.copy(missing = left, onDuty = onDuty)
              }
        }
      },
  )
}
