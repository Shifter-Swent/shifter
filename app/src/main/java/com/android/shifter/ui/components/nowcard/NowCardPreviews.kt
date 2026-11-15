// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.nowcard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionLocation
import com.swent.shifter.ui.checkin.NotCheckedIn
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

// Previews of NowCard, kept apart from it: they are tooling the IDE renders and no test ever runs,
// so coverage is told to leave this file alone.

private val START = Instant.parse("2026-11-14T14:00:00Z")

private fun sample(
    title: String = "Food sorting",
    start: Instant = START,
    place: String? = "Hall B",
    teamId: String? = "team",
) =
    Mission(
        id = title,
        eventId = "event",
        title = title,
        description = "",
        teamId = teamId,
        volunteersNeeded = 2,
        startAt = start,
        endAt = start.plus(Duration.ofMinutes(90)),
        location = place?.let { MissionLocation(it, GeoPosition(46.52, 6.57)) },
        createdAt = START,
    )

/** A state, the instant it is shown at, and what the card knows about the check-in. */
private class Frame(
    val state: NowState,
    val now: Instant,
    val checkedInAt: Instant? = null,
    val notCheckedIn: NotCheckedIn? = null,
)

/** The frames of the design, then a mission without a place and one of no team. */
private class NowCardFrames : PreviewParameterProvider<Frame> {
  private val next = sample("Welcome desk", START.plus(Duration.ofMinutes(105)), "Main gate")

  override val values =
      sequenceOf(
          Frame(NowState.Later(sample()), START.minus(Duration.ofHours(2))),
          Frame(NowState.Soon(sample()), START.minus(Duration.ofMinutes(20))),
          Frame(
              NowState.Late(sample(), null),
              START.plusSeconds(6 * 60),
              notCheckedIn = NotCheckedIn.OUTSIDE_ZONE,
          ),
          Frame(
              NowState.Ongoing(sample(), null),
              START.plus(Duration.ofMinutes(40)),
              checkedInAt = START.plusSeconds(2 * 60),
          ),
          Frame(
              NowState.Ongoing(sample(), next),
              START.plus(Duration.ofMinutes(75)),
              checkedInAt = START.plusSeconds(2 * 60),
          ),
          Frame(NowState.DoneForToday, START.plus(Duration.ofHours(3))),
          Frame(NowState.Late(sample(place = null), null), START.plusSeconds(6 * 60)),
          Frame(
              NowState.Ongoing(sample(teamId = null), null),
              START.plus(Duration.ofMinutes(40)),
          ),
      )
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27)
@Composable
private fun NowCardPreview(@PreviewParameter(NowCardFrames::class) frame: Frame) {
  ShifterTheme {
    Box(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp).width(358.dp)) {
      NowCard(
          state = frame.state,
          now = frame.now,
          checkedInAt = frame.checkedInAt,
          notCheckedIn = frame.notCheckedIn,
          onOpenMission = {},
          onContact = {},
          zone = ZoneOffset.UTC,
      )
    }
  }
}
