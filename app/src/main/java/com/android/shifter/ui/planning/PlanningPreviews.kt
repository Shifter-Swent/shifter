// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.planning

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import java.time.ZoneOffset

// Previews of Planning, kept apart from the component itself: they are tooling the IDE renders and
// no test ever runs, so coverage is told to leave this file alone.

@Composable
private fun PreviewHost(missions: List<PlanningMission>) {
  ShifterTheme {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
      Planning(missions = missions, zone = ZoneOffset.UTC)
    }
  }
}

private fun at(time: String): Instant = Instant.parse("2025-06-14T$time:00Z")

/** Given out of order, as the Planning sorts them itself. */
private val MISSIONS =
    listOf(
        PlanningMission(
            id = "water",
            title = "Hand out water to runners",
            startAt = at("08:30"),
            endAt = at("11:00"),
            address = "Hydration station A",
        ),
        PlanningMission(
            id = "briefing",
            title = "Briefing & station setup",
            startAt = at("08:00"),
            endAt = at("08:30"),
            address = "Volunteer tent",
            checkedInAt = at("07:58"),
        ),
        PlanningMission(
            id = "break",
            title = "Break & team handover",
            startAt = at("11:00"),
            endAt = at("11:30"),
        ),
        PlanningMission(
            id = "finish",
            title = "Support the finish area",
            startAt = at("11:30"),
            endAt = at("14:00"),
            address = "Finish zone · Place Bellecour",
        ),
    )

@Preview(showBackground = true, backgroundColor = 0xFF151D27)
@Composable
private fun PlanningPreview() {
  PreviewHost(MISSIONS)
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27)
@Composable
private fun PlanningEmptyPreview() {
  PreviewHost(emptyList())
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27, widthDp = 320)
@Composable
private fun PlanningLongTextPreview() {
  PreviewHost(
      listOf(
          PlanningMission(
              id = "long",
              title = "Guide the runners through the detour around the closed bridge and back",
              startAt = at("09:00"),
              endAt = at("12:00"),
              address = "Corner of Quai Claude Bernard and Rue de l'Université",
              checkedInAt = at("08:57"),
          )
      )
  )
}
