// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.model.team.Team

/**
 * The teams of the event, of which the volunteer picks the single one they apply to. Only names and
 * descriptions are shown, never who is already in a team.
 *
 * @param enabled false once the request is sent or while it is being sent, which leaves the pick
 *   shown but fixed.
 */
@Composable
internal fun TeamSelector(
    teams: List<Team>,
    selectedTeamId: String?,
    enabled: Boolean,
    onTeamSelected: (String) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
        text = stringResource(R.string.join_event_choose_team),
        color = colors.onBackground,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
    )
    if (teams.isEmpty()) {
      Text(
          text = stringResource(R.string.join_event_no_teams),
          color = colors.onSurfaceVariant,
          fontSize = 15.sp,
          modifier = Modifier.testTag(JoinEventScreenTestTags.NO_TEAMS),
      )
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.selectableGroup(),
    ) {
      teams.forEach { team ->
        TeamOption(team, team.id == selectedTeamId, enabled) { onTeamSelected(team.id) }
      }
    }
  }
}

@Composable
private fun TeamOption(team: Team, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(16.dp)
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier =
          Modifier.fillMaxWidth()
              .clip(shape)
              .background(colors.surfaceVariant)
              .border(1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
              .selectable(
                  selected = selected,
                  enabled = enabled,
                  role = Role.RadioButton,
                  onClick = onClick,
              )
              .padding(horizontal = 12.dp, vertical = 12.dp)
              .testTag(JoinEventScreenTestTags.teamOption(team.id)),
  ) {
    RadioButton(selected = selected, onClick = null, enabled = enabled)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
      Text(
          text = team.name,
          color = colors.onSurface,
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
      )
      if (team.description.isNotBlank()) {
        Text(text = team.description, color = colors.onSurfaceVariant, fontSize = 14.sp)
      }
    }
  }
}
