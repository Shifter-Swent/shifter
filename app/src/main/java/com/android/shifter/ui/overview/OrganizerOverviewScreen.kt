// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.model.time.MinuteClock
import com.swent.shifter.ui.components.LoadErrorRow
import com.swent.shifter.ui.components.RoundedCard
import com.swent.shifter.ui.components.TeamFilterMenu
import com.swent.shifter.ui.components.days.DayHeader
import com.swent.shifter.ui.components.days.DayWithoutMissions
import com.swent.shifter.ui.components.days.EventDayPicker
import com.swent.shifter.ui.components.days.EventDayStrip
import com.swent.shifter.ui.components.days.rememberEventDayPicker
import com.swent.shifter.ui.components.time.rememberNow
import com.swent.shifter.ui.mission.AssignDialog
import com.swent.shifter.ui.theme.shifter_mutedText
import com.swent.shifter.ui.theme.shifter_success
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

object OrganizerOverviewScreenTestTags {
  const val SCREEN = "OrganizerOverviewScreen"
  const val ADD_MISSION_BUTTON = "OrganizerOverviewAddMissionButton"
  const val MANAGE_TEAMS_BUTTON = "OrganizerOverviewManageTeamsButton"
  const val SHIFTS_BUTTON = "OrganizerOverviewShiftsButton"
  const val PROGRESS_PERCENT = "OrganizerOverviewProgressPercent"
  const val PROGRESS_COUNT = "OrganizerOverviewProgressCount"
  const val ALL_TEAMS_CHIP = "OrganizerOverviewTeamChip_All"
  const val LOADING_INDICATOR = "OrganizerOverviewLoadingIndicator"
  const val LOAD_ERROR = "OrganizerOverviewLoadError"
  const val RETRY_BUTTON = "OrganizerOverviewRetryButton"
  const val EMPTY_MISSIONS = "OrganizerOverviewEmptyMissions"
  const val EMPTY_DAY = "OrganizerOverviewEmptyDay"
  const val SNACKBAR = "OrganizerOverviewSnackbar"

  fun teamChip(teamId: String) = "OrganizerOverviewTeamChip_$teamId"

  fun mission(missionId: String) = "OrganizerOverviewMission_$missionId"

  fun assignedCount(missionId: String) = "OrganizerOverviewAssignedCount_$missionId"

  fun completed(missionId: String) = "OrganizerOverviewCompleted_$missionId"

  fun assignButton(missionId: String) = "OrganizerOverviewAssignButton_$missionId"
}

/**
 * The organizer's management screen of an event: the progress of its missions, then its missions,
 * filtered by team and shown one day of the event at a time. Its join code is in the Sharing tab.
 *
 * @param clock moves today on, so the screen opens on the right day and passes 04:00 by itself.
 * @param onAddMission called to open the mission creation form.
 * @param onManageTeams called to open the teams of the event.
 * @param onEditMission called with the id of a mission to open its edit form.
 */
@Composable
fun OrganizerOverviewScreen(
    viewModel: OrganizerOverviewViewModel,
    clock: MinuteClock,
    onAddMission: () -> Unit,
    onManageTeams: () -> Unit = {},
    onManageShifts: () -> Unit = {},
    onEditMission: (String) -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsState()
  OrganizerOverviewContent(
      state = uiState,
      now = rememberNow(clock),
      actions =
          OrganizerOverviewActions(
              onAddMission = onAddMission,
              onManageTeams = onManageTeams,
              onManageShifts = onManageShifts,
              onEditMission = onEditMission,
              onSelectTeam = viewModel::selectTeam,
              onRetry = viewModel::refresh,
              onAssign = viewModel::openAssign,
              onToggleAssignee = viewModel::toggleAssignee,
              onRetryAssignees = viewModel::retryAssignees,
              onSaveAssignees = viewModel::saveAssignees,
              onDismissAssign = viewModel::dismissAssign,
              onDeletedMissionShown = viewModel::deletedMissionShown,
          ),
  )
}

/** Every interaction of the management screen, grouped so it takes a single parameter. */
data class OrganizerOverviewActions(
    val onAddMission: () -> Unit = {},
    val onManageTeams: () -> Unit = {},
    val onManageShifts: () -> Unit = {},
    val onEditMission: (String) -> Unit = {},
    val onSelectTeam: (String?) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onAssign: (String) -> Unit = {},
    val onToggleAssignee: (String) -> Unit = {},
    val onRetryAssignees: () -> Unit = {},
    val onSaveAssignees: () -> Unit = {},
    val onDismissAssign: () -> Unit = {},
    val onDeletedMissionShown: () -> Unit = {},
)

/**
 * Stateless form of [OrganizerOverviewScreen]: renders [state] and reports every interaction. Only
 * the day shown is kept here, across a configuration change.
 *
 * The days are picked from a strip, as in the volunteer's Planning and the manager's Overview: it
 * opens on today (the first day before the event, the last one after it), and an event of one day
 * has no strip.
 *
 * @param now when the screen is read, which decides which day is today.
 * @param zone the time zone the days are counted in, the device's one in the app.
 */
@Composable
fun OrganizerOverviewContent(
    state: OrganizerOverviewUiState,
    actions: OrganizerOverviewActions,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val deleted = stringResource(R.string.overview_assign_deleted)
  LaunchedEffect(state.assignedMissionDeleted) {
    if (state.assignedMissionDeleted) {
      // In finally, so leaving the screen while it shows does not show it again on return; not
      // before it, as clearing the key would restart this effect and cancel the snackbar.
      try {
        snackbarHostState.showSnackbar(deleted)
      } finally {
        actions.onDeletedMissionShown()
      }
    }
  }
  Box(modifier = Modifier.fillMaxSize()) {
    OverviewColumn(state, actions, now, zone)
    SnackbarHost(
        snackbarHostState,
        modifier =
            Modifier.align(Alignment.BottomCenter)
                .testTag(OrganizerOverviewScreenTestTags.SNACKBAR),
    )
  }
  state.assigning?.let {
    AssignDialog(
        assigning = it,
        onToggle = actions.onToggleAssignee,
        onRetry = actions.onRetryAssignees,
        onSave = actions.onSaveAssignees,
        onDismiss = actions.onDismissAssign,
    )
  }
}

@Composable
private fun OverviewColumn(
    state: OrganizerOverviewUiState,
    actions: OrganizerOverviewActions,
    now: Instant,
    zone: ZoneId,
) {
  // From all the missions, not the filtered ones: the strip stays the same when a team is picked.
  val dayPicker =
      rememberEventDayPicker(state.eventPeriod, state.missions.map { it.startAt }, now, zone)
  Column(
      modifier =
          Modifier.fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(vertical = 12.dp)
              .testTag(OrganizerOverviewScreenTestTags.SCREEN),
      verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    AddMissionButton(actions.onAddMission)
    // Side by side, as the design has them, each taking half the row and the height of the taller
    // of the two, so a label wrapping at a large font scale grows the pair instead of being cut.
    Row(
        modifier = Modifier.height(IntrinsicSize.Min).heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      ManageTeamsButton(actions.onManageTeams, Modifier.weight(1f).fillMaxHeight())
      ShiftsButton(actions.onManageShifts, Modifier.weight(1f).fillMaxHeight())
    }
    ProgressCard(state)
    if (state.loadFailed) {
      LoadErrorRow(
          message = stringResource(R.string.overview_load_failed),
          retryLabel = stringResource(R.string.overview_retry),
          onRetry = actions.onRetry,
          messageTag = OrganizerOverviewScreenTestTags.LOAD_ERROR,
          retryTag = OrganizerOverviewScreenTestTags.RETRY_BUTTON,
      )
    }
    // No "Back to today" next to the filter: the strip's "Today" brings today back.
    DayHeader(
        title = missionsTitle(state, dayPicker),
        filter = {
          TeamFilterMenu(
              teams = state.teams.map { it.id to it.name },
              selectedTeamId = state.selectedTeamId,
              onSelectTeam = actions.onSelectTeam,
              allTestTag = OrganizerOverviewScreenTestTags.ALL_TEAMS_CHIP,
              teamTestTag = OrganizerOverviewScreenTestTags::teamChip,
          )
        },
        // The days of the team picked get a dot.
        strip =
            if (dayPicker.showsStrip) {
              {
                EventDayStrip(dayPicker, dayPicker.daysOf(state.visibleMissions.map { it.startAt }))
              }
            } else null,
    )
    Missions(state, dayPicker, actions.onAssign, actions.onEditMission)
  }
}

@Composable
private fun AddMissionButton(onClick: () -> Unit) {
  Button(
      onClick = onClick,
      shape = RoundedCornerShape(14.dp),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
      modifier =
          Modifier.fillMaxWidth()
              .height(48.dp)
              .testTag(OrganizerOverviewScreenTestTags.ADD_MISSION_BUTTON),
  ) {
    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
    Text(
        text = stringResource(R.string.overview_add_mission),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp).weight(1f),
    )
  }
}

@Composable
private fun ManageTeamsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  OutlinedButton(
      onClick = onClick,
      shape = RoundedCornerShape(14.dp),
      modifier = modifier.testTag(OrganizerOverviewScreenTestTags.MANAGE_TEAMS_BUTTON),
  ) {
    Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(20.dp))
    Text(
        text = stringResource(R.string.overview_manage_teams),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp).weight(1f),
    )
  }
}

/** Opens the week grid where the organizer opens and closes the event's 2 h slots (#597). */
@Composable
private fun ShiftsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  OutlinedButton(
      onClick = onClick,
      shape = RoundedCornerShape(14.dp),
      modifier = modifier.testTag(OrganizerOverviewScreenTestTags.SHIFTS_BUTTON),
  ) {
    Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(20.dp))
    Text(
        text = stringResource(R.string.overview_shifts),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp).weight(1f),
    )
  }
}

/** The share of the event's missions that are completed, whatever team is selected. */
@Composable
private fun ProgressCard(state: OrganizerOverviewUiState) {
  RoundedCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.size(80.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline,
            strokeWidth = 8.dp,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
        Text(
            text =
                stringResource(
                    R.string.overview_progress_percent,
                    (state.progress * 100).roundToInt(),
                ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(OrganizerOverviewScreenTestTags.PROGRESS_PERCENT),
        )
      }
      Column(
          modifier = Modifier.padding(start = 18.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
            text = stringResource(R.string.overview_progress_title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text =
                stringResource(
                    R.string.overview_progress_count,
                    state.completedCount,
                    state.missions.size,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(OrganizerOverviewScreenTestTags.PROGRESS_COUNT),
        )
        Text(
            text = stringResource(R.string.overview_progress_scope),
            style = MaterialTheme.typography.labelSmall,
            color = shifter_mutedText,
        )
      }
    }
  }
}

/**
 * "Missions (4)": the missions of the team picked on the day shown. The team is left to the menu
 * next to it, which already names it.
 */
@Composable
private fun missionsTitle(state: OrganizerOverviewUiState, dayPicker: EventDayPicker): String {
  val count = state.visibleMissions.count { dayPicker.isShown(it.startAt) }
  return stringResource(R.string.overview_missions_count, count)
}

/**
 * The missions of the selected team on the day [dayPicker] shows, under the [DayHeader] that counts
 * them; or why there are none.
 */
@Composable
private fun Missions(
    state: OrganizerOverviewUiState,
    dayPicker: EventDayPicker,
    onAssign: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
  val teamMissions = state.visibleMissions
  val missions = teamMissions.filter { dayPicker.isShown(it.startAt) }
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    when {
      missions.isNotEmpty() ->
          missions.forEach { mission ->
            MissionRow(
                mission = mission,
                teamName = state.teamName(mission.teamId),
                onAssign = { onAssign(mission.id) },
                onEdit = { onEdit(mission.id) },
            )
          }
      // Other days have missions of this team: only this one has none.
      teamMissions.isNotEmpty() ->
          DayWithoutMissions(
              dayPicker.shown,
              Modifier.testTag(OrganizerOverviewScreenTestTags.EMPTY_DAY),
          )
      state.isLoading ->
          CircularProgressIndicator(
              modifier =
                  Modifier.size(20.dp).testTag(OrganizerOverviewScreenTestTags.LOADING_INDICATOR),
              strokeWidth = 2.dp,
          )
      !state.loadFailed ->
          Text(
              text =
                  stringResource(
                      if (state.selectedTeamId == null) R.string.overview_no_missions
                      else R.string.overview_no_team_missions
                  ),
              style = MaterialTheme.typography.bodySmall,
              color = shifter_mutedText,
              modifier = Modifier.testTag(OrganizerOverviewScreenTestTags.EMPTY_MISSIONS),
          )
    }
  }
}

@Composable
private fun MissionRow(
    mission: OverviewMission,
    teamName: String?,
    onAssign: () -> Unit,
    onEdit: () -> Unit,
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clip(MaterialTheme.shapes.medium)
              .background(MaterialTheme.colorScheme.surfaceVariant)
              // A completed mission can no longer be changed, so it does not open the form.
              .clickable(
                  enabled = !mission.isCompleted,
                  onClickLabel = stringResource(R.string.overview_edit_mission),
                  role = Role.Button,
                  onClick = onEdit,
              )
              .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
              .testTag(OrganizerOverviewScreenTestTags.mission(mission.id)),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
          text = mission.title,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
          text =
              when {
                mission.teamId == null -> stringResource(R.string.overview_team_general)
                // Its team was not found, e.g. deleted: not shown as General, which means no team.
                else -> teamName ?: stringResource(R.string.overview_team_unknown)
              },
          style = MaterialTheme.typography.labelSmall,
          color = shifter_mutedText,
      )
      Text(
          text =
              stringResource(
                  R.string.assignee_picker_count,
                  mission.assigneeIds.size,
                  mission.volunteersNeeded,
              ),
          style = MaterialTheme.typography.labelSmall,
          color = shifter_mutedText,
          modifier = Modifier.testTag(OrganizerOverviewScreenTestTags.assignedCount(mission.id)),
      )
      if (mission.isCompleted) {
        Text(
            text = stringResource(R.string.overview_mission_completed),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = shifter_success,
            modifier = Modifier.testTag(OrganizerOverviewScreenTestTags.completed(mission.id)),
        )
      }
    }
    TextButton(
        onClick = onAssign,
        modifier = Modifier.testTag(OrganizerOverviewScreenTestTags.assignButton(mission.id)),
    ) {
      Text(stringResource(R.string.overview_assign), fontWeight = FontWeight.SemiBold)
    }
  }
}
