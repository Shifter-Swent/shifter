// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.people

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.ui.components.ShifterNavigationBar
import com.swent.shifter.ui.components.ShifterTopBar
import com.swent.shifter.ui.components.UnderlinedTab
import com.swent.shifter.ui.components.UnderlinedTabRow
import com.swent.shifter.ui.membership.MembershipRequestUIState
import com.swent.shifter.ui.membership.MembershipRequestsScreen
import com.swent.shifter.ui.navigation.OrganizerTabs
import com.swent.shifter.ui.navigation.Tab
import com.swent.shifter.ui.theme.ShifterTheme

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
 * @param eventTitle the event's name, shown in the top bar.
 * @param onBack called to leave the event, from the back button of the top bar.
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
    eventTitle: String,
    onBack: () -> Unit,
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
      topBar = { ShifterTopBar(eventTitle, onBack) },
      bottomBar = { ShifterNavigationBar(OrganizerTabs.all, OrganizerTabs.People, onNavigate) },
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
        eventTitle = "City Marathon",
        onBack = {},
        onNavigate = {},
    )
  }
}
