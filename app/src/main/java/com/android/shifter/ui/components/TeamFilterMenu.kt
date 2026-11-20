// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swent.shifter.R

object TeamFilterMenuTestTags {
  /** The button that opens the menu, showing the team picked. */
  const val MENU = "teamFilterMenu"
}

/**
 * Picks the team whose missions are listed: a button showing "All teams" or the team picked, which
 * opens the list of "All teams" then each team.
 *
 * The entries carry the tags the screen gives them, and say which one is picked, so a test or a
 * screen reader can tell.
 *
 * @param teams each team's id and name, in the order to offer them.
 * @param selectedTeamId the team picked, or null for all of them.
 * @param onSelectTeam called with the team picked, or null for all of them.
 * @param allTestTag the tag of the "All teams" entry.
 * @param teamTestTag the tag of a team's entry, from its id.
 */
@Composable
fun TeamFilterMenu(
    teams: List<Pair<String, String>>,
    selectedTeamId: String?,
    onSelectTeam: (String?) -> Unit,
    allTestTag: String,
    teamTestTag: (String) -> String,
    modifier: Modifier = Modifier,
) {
  // Saved, so a rotation keeps the menu open on what was being picked.
  var expanded by rememberSaveable { mutableStateOf(false) }
  val all = stringResource(R.string.team_filter_all)
  val colors = MaterialTheme.colorScheme
  Box(modifier = modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            // Tappable over 48 dp, more than the pill shows.
            Modifier.minimumInteractiveComponentSize()
                .widthIn(max = MAX_WIDTH)
                .clip(CircleShape)
                .background(colors.surfaceContainer)
                .clickable(
                    role = Role.DropdownList,
                    onClickLabel = stringResource(R.string.team_filter_choose),
                ) {
                  expanded = true
                }
                .padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp)
                .testTag(TeamFilterMenuTestTags.MENU),
    ) {
      Text(
          text = teams.firstOrNull { it.first == selectedTeamId }?.second ?: all,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Medium,
          color = colors.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
      )
      Icon(
          imageVector = Icons.Filled.KeyboardArrowDown,
          contentDescription = null,
          tint = colors.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
      )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        shape = MaterialTheme.shapes.medium,
        containerColor = colors.surfaceVariant,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
      (listOf(null to all) + teams).forEach { (teamId, name) ->
        val chosen = teamId == selectedTeamId
        DropdownMenuItem(
            text = { Text(text = name, style = MaterialTheme.typography.bodyMedium) },
            onClick = {
              onSelectTeam(teamId)
              expanded = false
            },
            colors =
                MenuDefaults.itemColors(
                    textColor = if (chosen) colors.primary else colors.onSurface
                ),
            // The colour is the only visual sign of the team picked, so it is said out loud too.
            modifier =
                Modifier.testTag(teamId?.let(teamTestTag) ?: allTestTag).semantics {
                  selected = chosen
                },
        )
      }
    }
  }
}

/** Past it, a long team name is cut, so the title next to the menu keeps its room. */
private val MAX_WIDTH = 180.dp
