// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.swent.shifter.ui.mission.AssignState
import com.swent.shifter.ui.mission.AssigneeOption
import com.swent.shifter.ui.mission.AssigneeSelection
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// Previews of the proposed schedule, kept apart from the screen itself: they are tooling the IDE
// renders and no test ever runs, so coverage is told to leave this file alone.

private val ZONE: ZoneId = ZoneId.of("Europe/Zurich")
private val FRIDAY: LocalDate = LocalDate.parse("2026-11-20")

/** [time] on the [day]-th day of the festival of the Figma frames, Friday 20 to Sunday 22. */
private fun at(day: Long, time: String): Instant =
    FRIDAY.plusDays(day).atTime(LocalTime.parse(time)).atZone(ZONE).toInstant()

private val FESTIVAL = at(0, "08:00")..at(2, "23:00")

/** The missions of Saturday in the Figma frame, and one on Sunday with someone already on it. */
private val PROPOSAL =
    AutoScheduleUiState(
        isLoading = false,
        eventPeriod = FESTIVAL,
        missions =
            listOf(
                ProposedMission(
                    id = "stands",
                    title = "Set up the stands",
                    startAt = at(1, "08:00"),
                    endAt = at(1, "11:00"),
                    isGeneral = false,
                    teamName = "Logistics",
                    volunteersNeeded = 4,
                    assigned = listOf("Léa Petit"),
                    proposed = listOf("Maya Bernard", "Sam Keller"),
                    missing = 1,
                ),
                ProposedMission(
                    id = "bar",
                    title = "Serve at the bar",
                    startAt = at(1, "12:00"),
                    endAt = at(1, "16:00"),
                    isGeneral = false,
                    teamName = "Bar",
                    volunteersNeeded = 3,
                    proposed = listOf("Alex Laurent", "Sam Dupont", "Inès Moreau"),
                    missing = 0,
                ),
                ProposedMission(
                    id = "night",
                    title = "Bar · night service",
                    startAt = at(1, "20:00"),
                    endAt = at(1, "23:00"),
                    isGeneral = false,
                    teamName = "Bar",
                    volunteersNeeded = 2,
                    proposed = listOf("Claire Dubois", "Bob Rossi"),
                    missing = 0,
                ),
                ProposedMission(
                    id = "park",
                    title = "Clean the park",
                    startAt = at(2, "09:00"),
                    endAt = at(2, "12:00"),
                    isGeneral = false,
                    teamName = "Logistics",
                    volunteersNeeded = 3,
                    assigned = listOf("Léa Petit"),
                    proposed = listOf("Maya Bernard", "Sam Keller"),
                    missing = 0,
                ),
            ),
    )

@Composable
private fun PreviewHost(state: AutoScheduleUiState) {
  ShifterTheme {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
      AutoScheduleContent(state, AutoScheduleActions(), now = at(1, "10:00"), zone = ZONE)
    }
  }
}

@Preview(name = "Saturday", widthDp = 390, heightDp = 988)
@Composable
private fun SaturdayPreview() = PreviewHost(PROPOSAL)

/**
 * The Figma frame "fill by hand": the assign dialog over Saturday, for the stands. Léa, already on
 * them, is locked; the limit is her and the one spot left, so the two people proposed keep theirs.
 */
@Preview(name = "Fill by hand", widthDp = 390, heightDp = 988)
@Composable
private fun FillByHandPreview() =
    PreviewHost(
        PROPOSAL.copy(
            assigning =
                AssignState(
                    missionId = "stands",
                    title = "Set up the stands",
                    limit = 2,
                    selection =
                        AssigneeSelection(
                            candidates =
                                listOf(
                                    AssigneeOption("lea", "Léa Petit"),
                                    AssigneeOption("noah", "Noah Martin"),
                                    AssigneeOption("zoe", "Zoé Blanc"),
                                ),
                            selectedIds = listOf("lea", "noah"),
                            lockedIds = setOf("lea"),
                        ),
                )
        )
    )

@Preview(name = "Nothing to propose", widthDp = 390, heightDp = 844)
@Composable
private fun NothingToProposePreview() =
    PreviewHost(AutoScheduleUiState(isLoading = false, eventPeriod = FESTIVAL))

@Preview(name = "Saving failed", widthDp = 390, heightDp = 988)
@Composable
private fun SaveFailedPreview() = PreviewHost(PROPOSAL.copy(saveError = ScheduleSaveError.RETRY))
