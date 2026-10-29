// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.attendance

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

// Previews of ManagerOverviewContent, kept apart from the screen itself: they are tooling the IDE
// renders and no test ever runs, so coverage is told to leave this file alone.

private val START = Instant.parse("2027-06-20T09:00:00Z")

private fun volunteer(id: String, name: String, arrivedAfter: Duration? = null) =
    AttendanceVolunteer(id, name, arrivedAfter?.let { START + it })

/** Two of the three needed are there; the third is late, the check-in having opened. */
private val SET_UP =
    AttendanceMission(
        id = "m1",
        title = "Set up equipment",
        teamId = "logistics",
        teamName = "Logistics",
        volunteersNeeded = 3,
        startAt = START,
        endAt = START + Duration.ofHours(2),
        assignees =
            listOf(
                volunteer("u1", "Alice Martin", Duration.ofMinutes(-8)),
                volunteer("u2", "Thomas Petit", Duration.ofMinutes(3)),
                volunteer("u3", "Emma Dubois"),
            ),
        attendanceKnown = true,
    )

/** Of another team, and hours away: its check-in has not opened, so it warns about nothing. */
private val DRINKS =
    AttendanceMission(
        id = "m2",
        title = "Serve drinks",
        teamId = "bar",
        teamName = "Bar",
        volunteersNeeded = 2,
        startAt = START + Duration.ofHours(4),
        endAt = START + Duration.ofHours(6),
        assignees = listOf(volunteer("u4", "Léa Rousseau")),
        attendanceKnown = true,
    )

/** The missions of [state] five minutes into the first one, in a fixed zone. */
@Composable
private fun PreviewHost(state: AttendanceUiState) {
  ShifterTheme {
    ManagerOverviewContent(
        state = state,
        eventTitle = "City Marathon",
        now = START + Duration.ofMinutes(5),
        onRetry = {},
        zone = ZoneId.of("UTC"),
    )
  }
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27, heightDp = 916)
@Composable
private fun ManagerOverviewContentPreview() {
  PreviewHost(AttendanceUiState(isLoading = false, missions = listOf(SET_UP, DRINKS)))
}

/** The check-ins of the first mission are gone, which leaves the second one untouched. */
@Preview(showBackground = true, backgroundColor = 0xFF151D27, heightDp = 916)
@Composable
private fun ManagerOverviewUnknownPreview() {
  PreviewHost(
      AttendanceUiState(
          isLoading = false,
          missions = listOf(SET_UP.copy(attendanceKnown = false, attendanceFailed = true), DRINKS),
      )
  )
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27, heightDp = 916)
@Composable
private fun ManagerOverviewLoadFailedPreview() {
  PreviewHost(AttendanceUiState(isLoading = false, loadFailed = true))
}
