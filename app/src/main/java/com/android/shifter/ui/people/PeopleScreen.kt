// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.people

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.ui.components.UnderlinedTab
import com.swent.shifter.ui.components.UnderlinedTabRow
import com.swent.shifter.ui.membership.MembershipRequestUIState
import com.swent.shifter.ui.membership.MembershipRequestsScreen
import com.swent.shifter.ui.navigation.OrganizerTabs
import com.swent.shifter.ui.navigation.Tab
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.shifter_tabInactive

/** Tags of the People screen's own tabs, which switch between participants and requests. */
object PeopleScreenTestTags {
  const val PARTICIPANTS_TAB = "PeopleParticipantsTab"
  const val REQUESTS_TAB = "PeopleRequestsTab"
}

/**
 * The organizer's People tab: the event's participants by team, and the requests to join it.
 *
 * @param onReloadParticipants reloads the participants, on retry and whenever the organizer comes
 *   back to their tab, so a request accepted meanwhile shows its volunteer.
 * @param onAppointManager called with the id of a team and of the member the organizer confirmed as
 *   its manager, after tapping their card.
 * @param onRemoveManager called with the id of a team whose manager the organizer confirmed to
 *   remove, after tapping their card.
 */
@Composable
fun PeopleScreen(
    uiState: MembershipRequestUIState,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onRetry: () -> Unit,
    participants: ParticipantsUiState,
    onReloadParticipants: () -> Unit,
    onAppointManager: (teamId: String, userId: String) -> Unit,
    onRemoveManager: (teamId: String) -> Unit,
    onNavigate: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  var selectedTab by rememberSaveable { mutableIntStateOf(0) }
  // The card tapped, as ids so it survives a configuration change and a reload of the list.
  var selection by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
  Scaffold(
      modifier = modifier,
      containerColor = colors.background,
      topBar = { PeopleTopBar() },
      bottomBar = { OrganizerNavigationBar(onNavigate) },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      PeopleTabs(
          labels =
              listOf(
                  stringResource(R.string.people_tab_participants),
                  stringResource(R.string.people_tab_requests),
              ),
          selected = selectedTab,
          onSelect = {
            if (it == PARTICIPANTS_TAB && selectedTab != PARTICIPANTS_TAB) onReloadParticipants()
            selectedTab = it
          },
      )
      if (selectedTab == PARTICIPANTS_TAB) {
        ParticipantsList(
            participants,
            onReloadParticipants,
            { participant, team -> selection = participant.userId to team.id },
            Modifier.weight(1f),
        )
      } else {
        MembershipRequestsScreen(uiState, onAccept, onReject, onRetry, Modifier.weight(1f))
      }
    }
  }
  // Gone from the list since it was tapped, e.g. after a reload: there is nothing left to change.
  LaunchedEffect(selection, participants.groups) {
    val (userId, teamId) = selection ?: return@LaunchedEffect
    val group = participants.groups.firstOrNull { it.team?.id == teamId }
    if (group?.participants?.none { it.userId == userId } != false) selection = null
  }
  selection?.let { (userId, teamId) ->
    val group = participants.groups.firstOrNull { it.team?.id == teamId }
    val team = group?.team ?: return@let
    val participant = group.participants.firstOrNull { it.userId == userId } ?: return@let
    ManagerDialog(
        participant = participant,
        team = team,
        currentManager = group.participants.firstOrNull { it.isManager },
        onConfirm = {
          if (participant.isManager) onRemoveManager(teamId) else onAppointManager(teamId, userId)
          selection = null
        },
        onDismiss = { selection = null },
    )
  }
}

/** "People" title, laid out like the My Events header. */
@Composable
private fun PeopleTopBar() {
  Text(
      text = stringResource(R.string.people_title),
      color = MaterialTheme.colorScheme.onBackground,
      fontSize = 22.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
  )
}

/** Underlined tabs followed by the full-width divider, like the My Events tabs. */
@Composable
private fun PeopleTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
  val tags = listOf(PeopleScreenTestTags.PARTICIPANTS_TAB, PeopleScreenTestTags.REQUESTS_TAB)
  UnderlinedTabRow {
    labels.forEachIndexed { index, label ->
      UnderlinedTab(
          label = label,
          isSelected = index == selected,
          onClick = { onSelect(index) },
          width = 150.dp,
          modifier = Modifier.testTag(tags[index]),
      )
    }
  }
}

@Composable
private fun OrganizerNavigationBar(onNavigate: (Tab) -> Unit) {
  val colors = MaterialTheme.colorScheme
  NavigationBar(containerColor = colors.surfaceContainer) {
    OrganizerTabs.all.forEach { tab ->
      NavigationBarItem(
          selected = tab == OrganizerTabs.People,
          onClick = { onNavigate(tab) },
          icon = { Icon(tab.icon, contentDescription = null) },
          label = { Text(tab.label, fontSize = 11.sp) },
          colors =
              NavigationBarItemDefaults.colors(
                  selectedIconColor = colors.primary,
                  selectedTextColor = colors.primary,
                  indicatorColor = colors.primaryContainer,
                  unselectedIconColor = shifter_tabInactive,
                  unselectedTextColor = shifter_tabInactive,
              ),
          modifier = Modifier.testTag(tab.testTag),
      )
    }
  }
}

private const val PARTICIPANTS_TAB = 0

@Preview
@Composable
private fun PeopleScreenPreview() {
  ShifterTheme {
    PeopleScreen(
        uiState = MembershipRequestUIState(isLoading = false),
        onAccept = {},
        onReject = {},
        onRetry = {},
        participants = ParticipantsUiState(),
        onReloadParticipants = {},
        onAppointManager = { _, _ -> },
        onRemoveManager = {},
        onNavigate = {},
    )
  }
}
