// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.swent.shifter.ui.components.TeamFilterMenu
import com.swent.shifter.ui.components.days.BackToToday
import com.swent.shifter.ui.components.days.DayWithoutMissions
import com.swent.shifter.ui.components.days.EventDayPicker
import com.swent.shifter.ui.components.days.EventDayStrip
import com.swent.shifter.ui.components.days.rememberEventDayPicker
import com.swent.shifter.ui.components.time.rememberNow
import com.swent.shifter.ui.mission.AssigneePicker
import com.swent.shifter.ui.theme.shifter_mutedText
import com.swent.shifter.ui.theme.shifter_success
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

object OrganizerOverviewScreenTestTags {
  const val SCREEN = "OrganizerOverviewScreen"
  const val ADD_MISSION_BUTTON = "OrganizerOverviewAddMissionButton"
  const val MANAGE_TEAMS_BUTTON = "OrganizerOverviewManageTeamsButton"
  const val PROGRESS_PERCENT = "OrganizerOverviewProgressPercent"
  const val PROGRESS_COUNT = "OrganizerOverviewProgressCount"
  const val ALL_TEAMS_CHIP = "OrganizerOverviewTeamChip_All"
  const val MISSIONS_HEADER = "OrganizerOverviewMissionsHeader"
  const val LOADING_INDICATOR = "OrganizerOverviewLoadingIndicator"
  const val LOAD_ERROR = "OrganizerOverviewLoadError"
  const val RETRY_BUTTON = "OrganizerOverviewRetryButton"
  const val EMPTY_MISSIONS = "OrganizerOverviewEmptyMissions"
  const val EMPTY_DAY = "OrganizerOverviewEmptyDay"
  const val BACK_TO_TODAY = "OrganizerOverviewBackToToday"
  const val NOTIFICATION_BUTTON = "OrganizerOverviewNotificationButton"
  const val ASSIGN_DIALOG = "OrganizerOverviewAssignDialog"
  const val ASSIGN_SAVE = "OrganizerOverviewAssignSave"
  const val ASSIGN_CANCEL = "OrganizerOverviewAssignCancel"
  const val ASSIGN_ERROR = "OrganizerOverviewAssignError"

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
              onEditMission = onEditMission,
              onSelectTeam = viewModel::selectTeam,
              onRetry = viewModel::refresh,
              onAssign = viewModel::openAssign,
              onToggleAssignee = viewModel::toggleAssignee,
              onRetryAssignees = viewModel::retryAssignees,
              onSaveAssignees = viewModel::saveAssignees,
              onDismissAssign = viewModel::dismissAssign,
          ),
  )
}

/** Every interaction of the management screen, grouped so it takes a single parameter. */
data class OrganizerOverviewActions(
    val onAddMission: () -> Unit = {},
    val onManageTeams: () -> Unit = {},
    val onEditMission: (String) -> Unit = {},
    val onSelectTeam: (String?) -> Unit = {},
    val onRetry: () -> Unit = {},
    // TODO: open the notification composer once notifications exist.
    val onSendNotification: () -> Unit = {},
    val onAssign: (String) -> Unit = {},
    val onToggleAssignee: (String) -> Unit = {},
    val onRetryAssignees: () -> Unit = {},
    val onSaveAssignees: () -> Unit = {},
    val onDismissAssign: () -> Unit = {},
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
  OverviewColumn(state, actions, now, zone)
  state.assigning?.let { AssignDialog(it, actions) }
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
    ManageTeamsButton(actions.onManageTeams)
    ProgressCard(state)
    if (state.loadFailed) LoadError(actions.onRetry)
    TeamFilterMenu(
        teams = state.teams.map { it.id to it.name },
        selectedTeamId = state.selectedTeamId,
        onSelectTeam = actions.onSelectTeam,
        allTestTag = OrganizerOverviewScreenTestTags.ALL_TEAMS_CHIP,
        teamTestTag = OrganizerOverviewScreenTestTags::teamChip,
    )
    if (dayPicker.showsStrip) {
      // The days of the team picked get a dot.
      EventDayStrip(dayPicker, dayPicker.daysOf(state.visibleMissions.map { it.startAt }))
    }
    Missions(state, dayPicker, actions.onAssign, actions.onEditMission)
    NotificationCard(actions.onSendNotification)
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
private fun ManageTeamsButton(onClick: () -> Unit) {
  OutlinedButton(
      onClick = onClick,
      shape = RoundedCornerShape(14.dp),
      modifier =
          Modifier.fillMaxWidth()
              .height(48.dp)
              .testTag(OrganizerOverviewScreenTestTags.MANAGE_TEAMS_BUTTON),
  ) {
    Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(20.dp))
    Text(
        text = stringResource(R.string.overview_manage_teams),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp).weight(1f),
    )
  }
}

/** The share of the event's missions that are completed, whatever team is selected. */
@Composable
private fun ProgressCard(state: OrganizerOverviewUiState) {
  OverviewCard {
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

@Composable
private fun LoadError(onRetry: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = stringResource(R.string.overview_load_failed),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.weight(1f).testTag(OrganizerOverviewScreenTestTags.LOAD_ERROR),
    )
    TextButton(
        onClick = onRetry,
        modifier = Modifier.testTag(OrganizerOverviewScreenTestTags.RETRY_BUTTON),
    ) {
      Text(stringResource(R.string.overview_retry))
    }
  }
}

/**
 * The missions of the selected team on the day [dayPicker] shows, under a header naming the team
 * and counting them, with "Back to today" while another day is shown.
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
  val onBackToToday = dayPicker.onBackToToday
  val selectedTeam = state.teams.firstOrNull { it.id == state.selectedTeamId }
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
          text =
              if (selectedTeam == null) {
                stringResource(R.string.overview_missions_all, missions.size)
              } else {
                stringResource(R.string.overview_missions_team, selectedTeam.name, missions.size)
              },
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.weight(1f).testTag(OrganizerOverviewScreenTestTags.MISSIONS_HEADER),
      )
      if (onBackToToday != null) {
        BackToToday(onBackToToday, Modifier.testTag(OrganizerOverviewScreenTestTags.BACK_TO_TODAY))
      }
    }
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
                      if (selectedTeam == null) R.string.overview_no_missions
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

/** Edits who is assigned to the mission of [assigning]; nothing is stored until Save. */
@Composable
private fun AssignDialog(assigning: AssignState, actions: OrganizerOverviewActions) {
  AlertDialog(
      // A save cannot be taken back: the dialog stays open until it ends.
      onDismissRequest = { if (!assigning.isSaving) actions.onDismissAssign() },
      modifier = Modifier.testTag(OrganizerOverviewScreenTestTags.ASSIGN_DIALOG),
      title = { Text(assigning.title) },
      text = {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          AssigneePicker(
              selection = assigning.selection,
              limit = assigning.limit,
              enabled = !assigning.isSaving,
              onToggle = actions.onToggleAssignee,
              onRetry = actions.onRetryAssignees,
          )
          if (assigning.saveFailed) {
            Text(
                text = stringResource(R.string.overview_assign_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(OrganizerOverviewScreenTestTags.ASSIGN_ERROR),
            )
          }
        }
      },
      confirmButton = {
        TextButton(
            onClick = actions.onSaveAssignees,
            // Saving before the candidates are loaded could not show who is picked.
            enabled =
                !assigning.isSaving &&
                    !assigning.selection.isLoading &&
                    !assigning.selection.loadFailed,
            modifier = Modifier.testTag(OrganizerOverviewScreenTestTags.ASSIGN_SAVE),
        ) {
          Text(stringResource(R.string.overview_assign_save))
        }
      },
      dismissButton = {
        TextButton(
            onClick = actions.onDismissAssign,
            enabled = !assigning.isSaving,
            modifier = Modifier.testTag(OrganizerOverviewScreenTestTags.ASSIGN_CANCEL),
        ) {
          Text(stringResource(R.string.overview_assign_cancel))
        }
      },
  )
}

/** Sends a notification to every team of the event. */
@Composable
private fun NotificationCard(onSendNotification: () -> Unit) {
  OverviewCard {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
          text = stringResource(R.string.overview_notification_title),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
          text = stringResource(R.string.overview_notification_scope),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      TextButton(
          onClick = onSendNotification,
          modifier = Modifier.testTag(OrganizerOverviewScreenTestTags.NOTIFICATION_BUTTON),
      ) {
        Text(
            text = stringResource(R.string.overview_notification_action),
            fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}

/** A rounded, outlined block of the screen. */
@Composable
private fun OverviewCard(content: @Composable () -> Unit) {
  val shape = RoundedCornerShape(18.dp)
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .clip(shape)
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .border(1.dp, MaterialTheme.colorScheme.outline, shape)
              .padding(16.dp)
  ) {
    content()
  }
}
