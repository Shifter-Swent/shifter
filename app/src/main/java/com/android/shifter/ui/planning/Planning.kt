// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.planning

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.ui.components.days.BackToToday
import com.swent.shifter.ui.components.days.DayStrip
import com.swent.shifter.ui.components.days.DayWithoutMissions
import com.swent.shifter.ui.components.days.eventDays
import com.swent.shifter.ui.components.days.groupByDay
import com.swent.shifter.ui.components.days.rememberDaySelection
import com.swent.shifter.ui.components.time.formatDayOfWeek
import com.swent.shifter.ui.components.time.formatTime
import com.swent.shifter.ui.theme.shifter_mutedText
import com.swent.shifter.ui.theme.shifter_success
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A mission as the Planning shows it: one the signed-in user is personally assigned to.
 *
 * It holds only what the Planning displays, so the component stays free of the mission and check-in
 * models and can be shown with sample data.
 *
 * @property address where the mission takes place, or null for a mission with no fixed place.
 * @property checkedInAt when the user checked in to the mission, or null if they have not yet.
 * @property description what the mission is about, shown once its row is expanded.
 */
data class PlanningMission(
    val id: String,
    val title: String,
    val startAt: Instant,
    val endAt: Instant,
    val address: String? = null,
    val checkedInAt: Instant? = null,
    val description: String = "",
)

object PlanningTestTags {
  const val PLANNING = "planning"
  const val EMPTY = "planningEmpty"
  const val BACK_TO_TODAY = "planningBackToToday"
  const val EMPTY_DAY = "planningEmptyDay"

  fun mission(missionId: String) = "planningMission_$missionId"

  fun address(missionId: String) = "planningMissionAddress_$missionId"

  fun checkIn(missionId: String) = "planningMissionCheckIn_$missionId"

  fun description(missionId: String) = "planningMissionDescription_$missionId"
}

/**
 * The missions the signed-in user is assigned to, one day of the event at a time, in
 * [chronological] order, with whether they checked in.
 *
 * A strip offers every day of the event, and opens on [today] (the first day before the event, the
 * last one after it). An event of one day shows its missions without the strip.
 *
 * The rows are a plain column rather than a lazy list: the Planning is one section of a scrolling
 * page, and a user has a handful of missions per day.
 *
 * @param missions the missions to show, in any order.
 * @param eventPeriod when the event starts and ends, or null when unknown: the strip then runs from
 *   the first to the last day with missions.
 * @param today the event day it is now, see [com.swent.shifter.ui.components.days.eventDay].
 * @param zone the time zone the days and hours are counted in, replaceable in tests.
 * @param expandedMissionId the mission whose row shows its description, brought into view when it
 *   expands; none when null.
 * @param onMissionClick called with the id of a row tapped, to expand or collapse it; the rows
 *   cannot be tapped when null.
 * @param revealRequest a new value brings the expanded row into view again, even when it was
 *   already expanded, as the now card's "View details" does; on another day than the one shown, the
 *   Planning moves to the row's day first.
 */
@Composable
fun Planning(
    missions: List<PlanningMission>,
    eventPeriod: ClosedRange<Instant>?,
    today: LocalDate,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    expandedMissionId: String? = null,
    onMissionClick: ((String) -> Unit)? = null,
    revealRequest: Int = 0,
) {
  val byDay =
      remember(missions, zone) {
        groupByDay(missions.chronological(), { it.startAt }, zone).toMap()
      }
  val days = remember(byDay, eventPeriod, zone) { eventDays(eventPeriod, byDay.keys, zone) }
  val selection = rememberDaySelection(days, today)
  val selected = selection.selected
  val showsStrip = missions.isNotEmpty() && days.size > 1
  // The row asked by revealRequest while on another day: shown on its day, then brought into view.
  // Not saved: after a rotation, nothing is asked again.
  var revealOnShow by remember { mutableStateOf<String?>(null) }
  val handledRequest = remember { mutableIntStateOf(revealRequest) }
  LaunchedEffect(revealRequest) {
    if (revealRequest == handledRequest.intValue) return@LaunchedEffect
    handledRequest.intValue = revealRequest
    val day = byDay.entries.firstOrNull { (_, ms) -> ms.any { it.id == expandedMissionId } }?.key
    if (day != null && day != selected) {
      if (day == today) selection.backToToday() else selection.pick(day)
      revealOnShow = expandedMissionId
    }
  }
  Column(
      modifier = modifier.fillMaxWidth().testTag(PlanningTestTags.PLANNING),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Header(
        awayFromToday = showsStrip && selection.awayFromToday,
        onBackToToday = {
          selection.backToToday()
          revealOnShow = null
        },
    )
    if (missions.isEmpty() || selected == null) {
      Text(
          text = stringResource(R.string.planning_empty),
          style = MaterialTheme.typography.bodySmall,
          color = shifter_mutedText,
          modifier = Modifier.testTag(PlanningTestTags.EMPTY),
      )
      return@Column
    }
    if (showsStrip) {
      DayStrip(
          days = days,
          daysWithMissions = byDay.keys,
          selected = selected,
          today = today,
          onSelect = {
            selection.pick(it)
            revealOnShow = null
          },
      )
    }
    val shown = byDay[selected].orEmpty()
    if (shown.isEmpty()) {
      DayWithoutMissions(selected, Modifier.testTag(PlanningTestTags.EMPTY_DAY))
    }
    // Keyed by mission, so a row keeps its own state when the missions before it change.
    shown.forEach {
      key(it.id) {
        PlanningMissionRow(
            it,
            day = selected,
            zone = zone,
            expanded = it.id == expandedMissionId,
            onClick = onMissionClick,
            revealRequest = revealRequest,
            revealOnShow = it.id == revealOnShow,
        )
      }
    }
  }
}

/** The title, and "Back to today" in place of the subtitle while another day is shown. */
@Composable
private fun Header(awayFromToday: Boolean, onBackToToday: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = stringResource(R.string.planning_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.weight(1f),
    )
    if (awayFromToday) {
      BackToToday(onBackToToday, Modifier.testTag(PlanningTestTags.BACK_TO_TODAY))
    } else {
      Text(
          text = stringResource(R.string.planning_subtitle),
          style = MaterialTheme.typography.labelSmall,
          color = shifter_mutedText,
      )
    }
  }
}

/**
 * One mission of [day]. A mission starting after midnight, which still belongs to the evening
 * before, names its calendar day: "Sun 02:00–04:00".
 */
@Composable
private fun PlanningMissionRow(
    mission: PlanningMission,
    day: LocalDate,
    zone: ZoneId,
    expanded: Boolean,
    onClick: ((String) -> Unit)?,
    revealRequest: Int,
    revealOnShow: Boolean,
) {
  val bringIntoView = remember { BringIntoViewRequester() }
  // Shown whole once expanded, or asked again while expanded. A row already expanded when it is
  // first shown, as when the page comes back, leaves the page where it was, unless it is shown
  // because it was asked for ([revealOnShow]).
  val wasExpanded = remember { mutableStateOf(expanded && !revealOnShow) }
  val lastRequest = remember { mutableIntStateOf(revealRequest) }
  LaunchedEffect(expanded, revealRequest) {
    val asked = !wasExpanded.value || revealRequest != lastRequest.intValue
    wasExpanded.value = expanded
    lastRequest.intValue = revealRequest
    if (expanded && asked) bringIntoView.bringIntoView()
  }
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .bringIntoViewRequester(bringIntoView)
              .clip(MaterialTheme.shapes.medium)
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .then(
                  if (onClick == null) Modifier
                  else {
                    val state =
                        stringResource(
                            if (expanded) R.string.planning_expanded
                            else R.string.planning_collapsed
                        )
                    Modifier.clickable(
                            role = Role.Button,
                            onClickLabel =
                                stringResource(
                                    if (expanded) R.string.planning_hide_details
                                    else R.string.planning_show_details
                                ),
                        ) {
                          onClick(mission.id)
                        }
                        .semantics { stateDescription = state }
                  }
              )
              .padding(horizontal = 14.dp, vertical = 10.dp)
              .testTag(PlanningTestTags.mission(mission.id))
  ) {
    // The marker stays centred on the header, wherever the description below takes the row.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      CheckInMarker(checkedIn = mission.checkedInAt != null)
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val startDay = mission.startAt.atZone(zone).toLocalDate()
        Text(
            text =
                if (startDay == day) {
                  stringResource(
                      R.string.planning_hours,
                      mission.startAt.formatTime(zone),
                      mission.endAt.formatTime(zone),
                  )
                } else {
                  stringResource(
                      R.string.planning_hours_on_day,
                      startDay.formatDayOfWeek(),
                      mission.startAt.formatTime(zone),
                      mission.endAt.formatTime(zone),
                  )
                },
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
    if (expanded) {
      Text(
          text = mission.description.ifBlank { stringResource(R.string.planning_no_description) },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          // Lined up with the header's text, past the marker (18 dp) and the gap (12 dp).
          modifier =
              Modifier.padding(start = 30.dp, top = 6.dp)
                  .testTag(PlanningTestTags.description(mission.id)),
      )
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
          text = stringResource(R.string.planning_checked_in_at, it.formatTime(zone)),
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

/**
 * These missions earliest first. Missions starting together are ordered by the earliest end, then
 * by title, so the order stays the same when the list is updated.
 */
fun List<PlanningMission>.chronological(): List<PlanningMission> =
    sortedWith(compareBy({ it.startAt }, { it.endAt }, { it.title }))
