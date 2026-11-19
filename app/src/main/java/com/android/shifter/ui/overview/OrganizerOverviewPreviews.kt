// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.overview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

// Previews of OrganizerOverviewContent, kept apart from the screen itself: they are tooling the IDE
// renders and no test ever runs, so coverage is told to leave this file alone.

@Preview
@Composable
private fun OrganizerOverviewContentPreview() {
  val start = Instant.parse("2027-06-19T08:00:00Z")
  ShifterTheme {
    OrganizerOverviewContent(
        state =
            OrganizerOverviewUiState(
                isLoading = false,
                teams = listOf(OverviewTeam("logistics", "Logistics"), OverviewTeam("bar", "Bar")),
                missions =
                    listOf(
                        OverviewMission("1", "Set up the collection", "logistics", true, start),
                        OverviewMission("2", "Prepare the counter", "bar", false, start),
                        OverviewMission(
                            "3",
                            "Welcome the volunteers",
                            null,
                            false,
                            start.plus(Duration.ofDays(1)),
                        ),
                    ),
                eventPeriod = start..start.plus(Duration.ofDays(2)),
            ),
        actions = OrganizerOverviewActions(),
        now = start,
        zone = ZoneOffset.UTC,
    )
  }
}
