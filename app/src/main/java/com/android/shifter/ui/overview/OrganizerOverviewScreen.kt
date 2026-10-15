// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.shifter_mutedText
import kotlin.math.roundToInt

object OrganizerOverviewScreenTestTags {
  const val SCREEN = "OrganizerOverviewScreen"
  const val ADD_MISSION_BUTTON = "OrganizerOverviewAddMissionButton"
  const val PROGRESS_PERCENT = "OrganizerOverviewProgressPercent"
  const val PROGRESS_COUNT = "OrganizerOverviewProgressCount"
  const val ALL_TEAMS_CHIP = "OrganizerOverviewTeamChip_All"
  const val MISSIONS_HEADER = "OrganizerOverviewMissionsHeader"
  const val LOADING_INDICATOR = "OrganizerOverviewLoadingIndicator"
  const val LOAD_ERROR = "OrganizerOverviewLoadError"
  const val RETRY_BUTTON = "OrganizerOverviewRetryButton"
  const val EMPTY_MISSIONS = "OrganizerOverviewEmptyMissions"
  const val NOTIFICATION_BUTTON = "OrganizerOverviewNotificationButton"

  fun teamChip(teamId: String) = "OrganizerOverviewTeamChip_$teamId"

  fun mission(missionId: String) = "OrganizerOverviewMission_$missionId"
}

/**
 * The organizer's management screen of an event: the progress of its missions, then its missions,
 * filtered by team.
 *
 * @param onAddMission called to open the mission creation form.
 */
@Composable
fun OrganizerOverviewScreen(viewModel: OrganizerOverviewViewModel, onAddMission: () -> Unit) {
  val uiState by viewModel.uiState.collectAsState()
  OrganizerOverviewContent(
      state = uiState,
      actions =
          OrganizerOverviewActions(
              onAddMission = onAddMission,
              onSelectTeam = viewModel::selectTeam,
              onRetry = viewModel::refresh,
          ),
  )
}

/** Every interaction of the management screen, grouped so it takes a single parameter. */
data class OrganizerOverviewActions(
    val onAddMission: () -> Unit = {},
    val onSelectTeam: (String?) -> Unit = {},
    val onRetry: () -> Unit = {},
    // TODO: open the notification composer once notifications exist.
    val onSendNotification: () -> Unit = {},
)

/** Stateless form of [OrganizerOverviewScreen]: renders [state] and reports every interaction. */
@Composable
fun OrganizerOverviewContent(state: OrganizerOverviewUiState, actions: OrganizerOverviewActions) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(vertical = 12.dp)
              .testTag(OrganizerOverviewScreenTestTags.SCREEN),
      verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    AddMissionButton(actions.onAddMission)
    ProgressCard(state)
    if (state.loadFailed) LoadError(actions.onRetry)
    TeamFilters(state.teams, state.selectedTeamId, actions.onSelectTeam)
    Missions(state)
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

/** "All" followed by the teams of the event; exactly one is selected. */
@Composable
private fun TeamFilters(
    teams: List<OverviewTeam>,
    selectedId: String?,
    onSelectTeam: (String?) -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    TeamChip(
        label = stringResource(R.string.overview_filter_all),
        selected = selectedId == null,
        onClick = { onSelectTeam(null) },
        testTag = OrganizerOverviewScreenTestTags.ALL_TEAMS_CHIP,
    )
    teams.forEach { team ->
      TeamChip(
          label = team.name,
          selected = team.id == selectedId,
          onClick = { onSelectTeam(team.id) },
          testTag = OrganizerOverviewScreenTestTags.teamChip(team.id),
      )
    }
  }
}

@Composable
private fun TeamChip(label: String, selected: Boolean, onClick: () -> Unit, testTag: String) {
  val colorScheme = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(20.dp)
  Box(
      modifier =
          Modifier.clip(shape)
              .background(
                  if (selected) colorScheme.primaryContainer else colorScheme.surfaceVariant
              )
              .border(1.dp, if (selected) colorScheme.primary else colorScheme.outline, shape)
              .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
              .padding(horizontal = 12.dp, vertical = 7.dp)
              .testTag(testTag)
  ) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
    )
  }
}

/** The missions of the selected team, under a header naming it and counting them. */
@Composable
private fun Missions(state: OrganizerOverviewUiState) {
  val missions = state.visibleMissions
  val selectedTeam = state.teams.firstOrNull { it.id == state.selectedTeamId }
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        modifier = Modifier.testTag(OrganizerOverviewScreenTestTags.MISSIONS_HEADER),
    )
    when {
      missions.isNotEmpty() -> missions.forEach { MissionRow(it, state.teamName(it.teamId)) }
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
private fun MissionRow(mission: OverviewMission, teamName: String?) {
  // TODO: show whether the mission is to do, in progress or completed once missions have a status.
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .clip(MaterialTheme.shapes.medium)
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .padding(horizontal = 14.dp, vertical = 10.dp)
              .testTag(OrganizerOverviewScreenTestTags.mission(mission.id)),
      verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
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
  }
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

@Preview
@Composable
private fun OrganizerOverviewContentPreview() {
  ShifterTheme {
    OrganizerOverviewContent(
        state =
            OrganizerOverviewUiState(
                isLoading = false,
                teams = listOf(OverviewTeam("logistics", "Logistics"), OverviewTeam("bar", "Bar")),
                missions =
                    listOf(
                        OverviewMission("1", "Set up the collection", "logistics", false),
                        OverviewMission("2", "Prepare the counter", "bar", false),
                        OverviewMission("3", "Welcome the volunteers", null, false),
                    ),
            ),
        actions = OrganizerOverviewActions(),
    )
  }
}
