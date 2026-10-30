// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.planning

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.ui.theme.shifter_mutedText
import com.swent.shifter.ui.theme.shifter_success
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A mission as the Planning shows it: one the signed-in user is personally assigned to.
 *
 * It holds only what the Planning displays, so the component stays free of the mission and check-in
 * models and can be shown with sample data.
 *
 * @property address where the mission takes place, or null for a mission with no fixed place.
 * @property checkedInAt when the user checked in to the mission, or null if they have not yet.
 */
data class PlanningMission(
    val id: String,
    val title: String,
    val startAt: Instant,
    val endAt: Instant,
    val address: String? = null,
    val checkedInAt: Instant? = null,
)

object PlanningTestTags {
  const val PLANNING = "planning"
  const val EMPTY = "planningEmpty"

  fun mission(missionId: String) = "planningMission_$missionId"

  fun address(missionId: String) = "planningMissionAddress_$missionId"

  fun checkIn(missionId: String) = "planningMissionCheckIn_$missionId"
}

/**
 * The missions the signed-in user is assigned to, in [chronological] order, with whether they
 * checked in.
 *
 * The rows are a plain column rather than a lazy list: the Planning is one section of a scrolling
 * page, and a user has a handful of missions per event.
 *
 * @param missions the missions to show, in any order.
 * @param zone the time zone the hours are shown in, replaceable in tests.
 */
@Composable
fun Planning(
    missions: List<PlanningMission>,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
  Column(
      modifier = modifier.fillMaxWidth().testTag(PlanningTestTags.PLANNING),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
          text = stringResource(R.string.planning_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.weight(1f),
      )
      Text(
          text = stringResource(R.string.planning_subtitle),
          style = MaterialTheme.typography.labelSmall,
          color = shifter_mutedText,
      )
    }
    if (missions.isEmpty()) {
      Text(
          text = stringResource(R.string.planning_empty),
          style = MaterialTheme.typography.bodySmall,
          color = shifter_mutedText,
          modifier = Modifier.testTag(PlanningTestTags.EMPTY),
      )
    } else {
      val sorted = remember(missions) { missions.chronological() }
      sorted.forEach { PlanningMissionRow(it, zone) }
    }
  }
}

@Composable
private fun PlanningMissionRow(mission: PlanningMission, zone: ZoneId) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clip(MaterialTheme.shapes.medium)
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .padding(horizontal = 14.dp, vertical = 10.dp)
              .testTag(PlanningTestTags.mission(mission.id)),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    CheckInMarker(checkedIn = mission.checkedInAt != null)
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
          text =
              stringResource(
                  R.string.planning_hours,
                  mission.startAt.hours(zone),
                  mission.endAt.hours(zone),
              ),
          style = MaterialTheme.typography.labelSmall,
          color = shifter_mutedText,
      )
      Text(
          text = mission.title,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
      )
      Details(mission, zone)
    }
  }
}

/** The address and the check-in time, on one line; nothing at all when the mission has neither. */
@Composable
private fun Details(mission: PlanningMission, zone: ZoneId) {
  if (mission.address == null && mission.checkedInAt == null) return
  Row(verticalAlignment = Alignment.CenterVertically) {
    mission.address?.let {
      Text(
          text = it,
          style = MaterialTheme.typography.labelSmall,
          color = shifter_mutedText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          // Shrinks before the check-in time, which is the more useful of the two.
          modifier =
              Modifier.weight(1f, fill = false).testTag(PlanningTestTags.address(mission.id)),
      )
    }
    if (mission.address != null && mission.checkedInAt != null) {
      Text(
          text = stringResource(R.string.planning_separator),
          style = MaterialTheme.typography.labelSmall,
          color = shifter_mutedText,
      )
    }
    mission.checkedInAt?.let {
      Text(
          text = stringResource(R.string.planning_checked_in_at, it.hours(zone)),
          style = MaterialTheme.typography.labelSmall,
          color = shifter_success,
          maxLines = 1,
          modifier = Modifier.testTag(PlanningTestTags.checkIn(mission.id)),
      )
    }
  }
}

/** A green check once the user checked in, a grey dot until then. */
@Composable
private fun CheckInMarker(checkedIn: Boolean) {
  Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
    if (checkedIn) {
      Icon(
          imageVector = Icons.Outlined.CheckCircle,
          contentDescription = null,
          tint = shifter_success,
          modifier = Modifier.size(18.dp),
      )
    } else {
      Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(shifter_mutedText))
    }
  }
}

private fun Instant.hours(zone: ZoneId): String = HOURS.format(atZone(zone))

private val HOURS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * These missions earliest first. Missions starting together are ordered by the earliest end, then
 * by title, so the order stays the same when the list is updated.
 */
fun List<PlanningMission>.chronological(): List<PlanningMission> =
    sortedWith(compareBy({ it.startAt }, { it.endAt }, { it.title }))
