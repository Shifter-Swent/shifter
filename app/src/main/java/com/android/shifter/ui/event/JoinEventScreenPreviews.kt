// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.team.Team
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant

// Previews of JoinEventScreen, kept apart from it: they are tooling the IDE renders and no test
// ever runs, so coverage is told to leave this file alone.

@Preview(name = "Step 1: join code")
@Composable
private fun JoinEventCodePreview() {
  ShifterTheme { JoinEventContent(JoinEventUiState(joinCode = "ABC234"), {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Step 2: event details and Apply")
@Composable
private fun JoinEventApplyPreview() {
  ShifterTheme {
    JoinEventContent(
        JoinEventUiState(event = PREVIEW_EVENT, teams = PREVIEW_TEAMS, selectedTeamId = "welcome"),
        {},
        {},
        {},
        {},
        {},
        {},
    )
  }
}

@Preview(name = "Step 2: request sent")
@Composable
private fun JoinEventAppliedPreview() {
  ShifterTheme {
    JoinEventContent(
        JoinEventUiState(
            event = PREVIEW_EVENT,
            teams = PREVIEW_TEAMS,
            selectedTeamId = "welcome",
            requestStatus = MembershipRequestStatus.PENDING,
        ),
        {},
        {},
        {},
        {},
        {},
        {},
    )
  }
}

private val PREVIEW_EVENT =
    Event(
        organizerId = "organizer",
        title = "Solidarity collection",
        description =
            "Support a community collection. Help welcome donors, receive donations and sort " +
                "collected items with the volunteer team.",
        type = EventType.OTHER,
        startAt = Instant.parse("2026-06-22T07:30:00Z"),
        endAt = Instant.parse("2026-06-22T15:00:00Z"),
        location = EventLocation("Associations House"),
        createdAt = Instant.EPOCH,
    )

private val PREVIEW_TEAMS =
    listOf(
        Team(
            id = "welcome",
            eventId = "event",
            name = "Welcome desk",
            description = "Greet donors and explain where each donation goes.",
            icon = "handshake",
            createdAt = Instant.EPOCH,
        ),
        Team(
            id = "sorting",
            eventId = "event",
            name = "Sorting",
            description = "Sort the collected items by category.",
            icon = "box",
            createdAt = Instant.EPOCH,
        ),
    )
