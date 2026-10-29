// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.model.checkin.CheckInRules
import com.swent.shifter.ui.theme.shifter_mutedText
import com.swent.shifter.ui.theme.shifter_success
import com.swent.shifter.ui.theme.shifter_warning
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ManagerOverviewTestTags {
  const val SCREEN = "managerOverviewScreen"
  const val TITLE = "managerOverviewTitle"
  const val SUBTITLE = "managerOverviewSubtitle"
  const val ALL_TEAMS_CHIP = "managerOverviewTeamChip_All"
  const val MISSION_COUNT = "managerOverviewMissionCount"
  const val LOADING = "managerOverviewLoading"
  const val LOAD_ERROR = "managerOverviewLoadError"
  const val RETRY = "managerOverviewRetry"
  const val EMPTY = "managerOverviewEmpty"

  fun teamChip(teamId: String) = "managerOverviewTeamChip_$teamId"

  fun mission(missionId: String) = "managerOverviewMission_$missionId"

  /** The tappable part of a mission card: what expands and collapses it. */
  fun missionHeader(missionId: String) = "managerOverviewMissionHeader_$missionId"

  fun counter(missionId: String) = "managerOverviewCounter_$missionId"

  fun status(missionId: String) = "managerOverviewStatus_$missionId"

  fun present(missionId: String) = "managerOverviewPresent_$missionId"

  fun missing(missionId: String) = "managerOverviewMissing_$missionId"
}

/** What a card says about the attendance of its mission; the first case that holds wins. */
private enum class AttendanceStatus {
  /** The check-in has not opened, so nobody can be present yet, nor late. */
  NOT_OPEN,
  /** The check-ins of the mission could not be observed. */
  FAILED,
  /** The check-ins of the mission have not arrived yet. */
  WAITING,
  /** Fewer people are present than the mission needs, which the manager is warned about. */
  UNDERSTAFFED,
  /** Enough people are present: nothing to report. */
  STAFFED,
}

/**
 * The manager's overview of an event: the missions of the teams they manage, by start time, each
 * with who is present. Stateless but for which team is filtered on and which cards are expanded.
 *
 * @param now when the screen is read, which decides whose check-in has opened. It is an input
 *   rather than a clock, so the warnings are deterministic; nothing here ticks.
 * @param zone the time zone the times are shown in, the device's one in the app.
 * @param checkInOpensBefore how long before a mission starts its check-in opens.
 */
@Composable
fun ManagerOverviewContent(
    state: AttendanceUiState,
    eventTitle: String,
    now: Instant,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    checkInOpensBefore: Duration = CheckInRules.OPENS_BEFORE_START,
) {
  // The teams of the missions the user may watch: no query of its own, and none invented.
  val teams =
      remember(state.missions) {
        state.missions
            .mapNotNull { mission -> mission.teamId?.let { it to mission.teamName } }
            .distinctBy { it.first }
      }
  var pickedTeamId by remember { mutableStateOf<String?>(null) }
  // By id, so filtering a team out and back leaves the cards opened as they were.
  val expandedMissionIds = remember { mutableStateSetOf<String>() }
  // A team whose missions are gone, e.g. after a refresh, stops filtering them all away.
  val selectedTeamId = pickedTeamId?.takeIf { id -> teams.any { it.first == id } }
  val missions = state.missions.filter { selectedTeamId == null || it.teamId == selectedTeamId }
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(16.dp)
              .testTag(ManagerOverviewTestTags.SCREEN),
      verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Header(eventTitle)
    if (state.loadFailed) LoadError(onRetry)
    TeamFilters(teams, selectedTeamId) { pickedTeamId = it }
    MissionsHeader(missions.size)
    when {
      missions.isNotEmpty() ->
          missions.forEach { mission ->
            MissionCard(
                mission = mission,
                status = mission.status(now, checkInOpensBefore),
                expanded = mission.id in expandedMissionIds,
                zone = zone,
                // Each card on its own: opening one leaves the others as they are.
                onToggle = {
                  if (!expandedMissionIds.remove(mission.id)) expandedMissionIds += mission.id
                },
            )
          }
      // A reload keeps the cards already shown instead of replacing them with the spinner.
      state.isLoading ->
          CircularProgressIndicator(
              modifier = Modifier.size(20.dp).testTag(ManagerOverviewTestTags.LOADING),
              strokeWidth = 2.dp,
          )
      // The failure already says why there is nothing, so it is not called empty on top.
      !state.loadFailed ->
          Text(
              text =
                  stringResource(
                      if (selectedTeamId == null) R.string.attendance_no_missions
                      else R.string.overview_no_team_missions
                  ),
              style = MaterialTheme.typography.bodySmall,
              color = shifter_mutedText,
              modifier = Modifier.testTag(ManagerOverviewTestTags.EMPTY),
          )
    }
  }
}

/**
 * The status of [this] at [now]: the check-in window is read before the attendance, so a mission
 * that nobody can have checked in to yet is never reported short of volunteers.
 */
private fun AttendanceMission.status(now: Instant, opensBefore: Duration): AttendanceStatus =
    when {
      now < startAt - opensBefore -> AttendanceStatus.NOT_OPEN
      attendanceFailed -> AttendanceStatus.FAILED
      !attendanceKnown -> AttendanceStatus.WAITING
      isUnderstaffed -> AttendanceStatus.UNDERSTAFFED
      else -> AttendanceStatus.STAFFED
    }

/** The event being watched, named as the manager's view of it; the tabs are the navigation's. */
@Composable
private fun Header(eventTitle: String) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
        text = eventTitle,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.testTag(ManagerOverviewTestTags.TITLE),
    )
    Text(
        text = stringResource(R.string.attendance_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = shifter_mutedText,
        modifier = Modifier.testTag(ManagerOverviewTestTags.SUBTITLE),
    )
  }
}

@Composable
private fun LoadError(onRetry: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = stringResource(R.string.overview_load_failed),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.weight(1f).testTag(ManagerOverviewTestTags.LOAD_ERROR),
    )
    TextButton(onClick = onRetry, modifier = Modifier.testTag(ManagerOverviewTestTags.RETRY)) {
      Text(stringResource(R.string.overview_retry))
    }
  }
}

/** "All" followed by the teams of the listed missions, by id; exactly one is selected. */
@Composable
private fun TeamFilters(
    teams: List<Pair<String, String?>>,
    selectedTeamId: String?,
    onSelectTeam: (String?) -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    TeamChip(
        label = stringResource(R.string.overview_filter_all),
        selected = selectedTeamId == null,
        onClick = { onSelectTeam(null) },
        testTag = ManagerOverviewTestTags.ALL_TEAMS_CHIP,
    )
    teams.forEach { (teamId, teamName) ->
      TeamChip(
          // A team that no longer exists keeps its missions filterable, under no name of its own.
          label = teamName ?: stringResource(R.string.overview_team_unknown),
          selected = teamId == selectedTeamId,
          onClick = { onSelectTeam(teamId) },
          testTag = ManagerOverviewTestTags.teamChip(teamId),
      )
    }
  }
}

@Composable
private fun TeamChip(label: String, selected: Boolean, onClick: () -> Unit, testTag: String) {
  val colors = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(20.dp)
  Box(
      modifier =
          Modifier.clip(shape)
              .background(if (selected) colors.primaryContainer else colors.surfaceVariant)
              .border(1.dp, if (selected) colors.primary else colors.outline, shape)
              .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
              .padding(horizontal = 12.dp, vertical = 7.dp)
              .testTag(testTag)
  ) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) colors.primary else colors.onSurfaceVariant,
    )
  }
}

@Composable
private fun MissionsHeader(count: Int) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = stringResource(R.string.attendance_missions_title),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.weight(1f),
    )
    Text(
        text = pluralStringResource(R.plurals.attendance_mission_count, count, count),
        style = MaterialTheme.typography.labelSmall,
        color = shifter_mutedText,
        modifier = Modifier.testTag(ManagerOverviewTestTags.MISSION_COUNT),
    )
  }
}

/**
 * One mission and its live attendance. Only the header is tappable, so the counter and the lists
 * keep their own accessibility nodes instead of being merged into the card.
 */
@Composable
private fun MissionCard(
    mission: AttendanceMission,
    status: AttendanceStatus,
    expanded: Boolean,
    zone: ZoneId,
    onToggle: () -> Unit,
) {
  val shape = RoundedCornerShape(18.dp)
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .clip(shape)
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .border(1.dp, MaterialTheme.colorScheme.outline, shape)
              .padding(16.dp)
              .testTag(ManagerOverviewTestTags.mission(mission.id)),
      verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(
                    onClickLabel =
                        stringResource(
                            if (expanded) R.string.attendance_collapse
                            else R.string.attendance_expand
                        ),
                    onClick = onToggle,
                )
                .testTag(ManagerOverviewTestTags.missionHeader(mission.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = mission.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text =
                stringResource(
                    R.string.attendance_mission_subtitle,
                    when {
                      mission.teamId == null -> stringResource(R.string.overview_team_general)
                      else -> mission.teamName ?: stringResource(R.string.overview_team_unknown)
                    },
                    "${TIME.format(mission.startAt.atZone(zone))}–${TIME.format(mission.endAt.atZone(zone))}",
                ),
            style = MaterialTheme.typography.labelSmall,
            color = shifter_mutedText,
        )
      }
      Icon(
          imageVector =
              if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
          contentDescription = null,
          tint = shifter_mutedText,
      )
    }

    // The label sits directly above the count.
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
          text = stringResource(R.string.attendance_live_check_in),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = shifter_mutedText,
          letterSpacing = 1.sp,
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text =
                stringResource(
                    R.string.attendance_counter,
                    // An unknown attendance is not nobody present, so it is not counted as 0.
                    if (mission.attendanceKnown) mission.presentCount.toString()
                    else stringResource(R.string.attendance_count_unknown),
                    mission.volunteersNeeded,
                ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).testTag(ManagerOverviewTestTags.counter(mission.id)),
        )
        StatusLabel(status, ManagerOverviewTestTags.status(mission.id))
      }
    }

    // Who is there is only told once the check-ins are known and could have happened at all.
    if (expanded && mission.attendanceKnown && status != AttendanceStatus.NOT_OPEN) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      People(
          label = stringResource(R.string.attendance_present, mission.presentCount),
          people = mission.presentAssignees,
          icon = Icons.Filled.Check,
          tint = shifter_success,
          zone = zone,
          testTag = ManagerOverviewTestTags.present(mission.id),
      )
      if (mission.missingAssignees.isNotEmpty()) {
        People(
            label = stringResource(R.string.attendance_missing, mission.missingAssignees.size),
            people = mission.missingAssignees,
            icon = Icons.Filled.RadioButtonUnchecked,
            tint = shifter_warning,
            zone = zone,
            testTag = ManagerOverviewTestTags.missing(mission.id),
        )
      }
    }
  }
}

/** Nothing at all when there is nothing to report, so a staffed mission stays quiet. */
@Composable
private fun StatusLabel(status: AttendanceStatus, testTag: String) {
  val label: Int
  val color: Color
  var icon: ImageVector? = null
  when (status) {
    AttendanceStatus.NOT_OPEN -> {
      label = R.string.attendance_check_in_not_open
      color = shifter_mutedText
    }
    AttendanceStatus.FAILED -> {
      label = R.string.attendance_unavailable
      color = MaterialTheme.colorScheme.error
    }
    AttendanceStatus.WAITING -> {
      label = R.string.attendance_waiting
      color = shifter_mutedText
    }
    AttendanceStatus.UNDERSTAFFED -> {
      label = R.string.attendance_understaffed
      color = shifter_warning
      icon = Icons.Filled.Warning
    }
    AttendanceStatus.STAFFED -> return
  }
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    icon?.let { Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(14.dp)) }
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.testTag(testTag),
    )
  }
}

/** The people of one section of an expanded card, with their arrival time when they have one. */
@Composable
private fun People(
    label: String,
    people: List<AttendanceVolunteer>,
    icon: ImageVector,
    tint: Color,
    zone: ZoneId,
    testTag: String,
) {
  Column(modifier = Modifier.testTag(testTag), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = shifter_mutedText,
    )
    people.forEach { volunteer ->
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            // An id is not a name, so someone whose profile could not be read stays unnamed.
            text =
                volunteer.displayName?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.attendance_volunteer_unknown),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        volunteer.checkedInAt?.let {
          Text(
              text = TIME.format(it.atZone(zone)),
              style = MaterialTheme.typography.labelSmall,
              color = shifter_mutedText,
          )
        }
      }
    }
  }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
