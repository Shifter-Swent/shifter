// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.team

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.swent.shifter.model.team.Team
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant

// Previews of TeamSetupContent, kept apart from the screen itself: they are tooling the IDE
// renders and no test ever runs, so coverage is told to leave this file alone.

/** The step with [state], and a form under the teams only when [withForm]. */
@Composable
private fun PreviewHost(state: TeamSetupUiState, withForm: Boolean = true) {
  ShifterTheme {
    TeamSetupContent(
        state = state,
        onBack = {},
        onProceed = {},
        onRetry = {},
        onAddAnotherTeam = {},
        form = if (withForm) ({ PreviewForm() }) else null,
    )
  }
}

/**
 * The card of the form with a state of its own, so the interactive preview can type in it. It plays
 * the part [CreateTeamViewModel] holds in the screen, without being one.
 */
@Composable
private fun PreviewForm() {
  var state by remember { mutableStateOf(CreateTeamUiState()) }
  CreateTeamFormContent(
      state = state,
      actions =
          CreateTeamActions(
              onNameChange = { state = state.copy(name = it) },
              onIconChange = { state = state.copy(icon = it) },
              onDescriptionChange = { state = state.copy(description = it) },
          ),
      onSubmit = {},
      onCancel = {},
  )
}

private fun team(id: String, name: String, icon: TeamIcon, description: String) =
    Team(
        id = id,
        eventId = "event-1",
        name = name,
        description = description,
        icon = icon.id,
        createdAt = Instant.parse("2027-06-20T08:00:00Z"),
    )

private val SAVED_TEAMS =
    listOf(
        team("team-1", "Logistics", TeamIcon.TRUCK, "Set up the site and bring in the equipment."),
        team("team-2", "Bar", TeamIcon.WINE, "Serve the drinks and keep the counter stocked."),
    )

/** The first visit: no team yet, so the step opens on an empty form. */
@Preview(showBackground = true, backgroundColor = 0xFF151D27, heightDp = 916)
@Composable
private fun TeamSetupEmptyPreview() {
  PreviewHost(TeamSetupUiState(isLoading = false))
}

/** Two teams saved, with the form of the third open under them. */
@Preview(showBackground = true, backgroundColor = 0xFF151D27, heightDp = 916)
@Composable
private fun TeamSetupWithTeamsPreview() {
  PreviewHost(TeamSetupUiState(isLoading = false, teams = SAVED_TEAMS))
}

/** The teams of the event, with no form open: the step waits on one of the two actions. */
@Preview(showBackground = true, backgroundColor = 0xFF151D27, heightDp = 916)
@Composable
private fun TeamSetupNoFormPreview() {
  PreviewHost(TeamSetupUiState(isLoading = false, teams = SAVED_TEAMS), withForm = false)
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27, heightDp = 916)
@Composable
private fun TeamSetupLoadingPreview() {
  PreviewHost(TeamSetupUiState(), withForm = false)
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27, heightDp = 916)
@Composable
private fun TeamSetupLoadFailedPreview() {
  PreviewHost(TeamSetupUiState(isLoading = false, loadFailed = true), withForm = false)
}
