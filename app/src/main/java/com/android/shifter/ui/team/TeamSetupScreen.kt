// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.team

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.shifter.R
import com.swent.shifter.model.team.Team
import com.swent.shifter.ui.components.FormHeader
import com.swent.shifter.ui.components.FormPrimaryButton
import com.swent.shifter.ui.theme.shifter_mutedText

/** Tags of the team setup step: its states, the teams it lists and the two actions under them. */
object TeamSetupScreenTestTags {
  const val BACK_BUTTON = "TeamSetupBackButton"
  const val LOADING_INDICATOR = "TeamSetupLoadingIndicator"
  const val LOAD_ERROR = "TeamSetupLoadError"
  const val RETRY_BUTTON = "TeamSetupRetryButton"
  const val SECTION_TITLE = "TeamSetupSectionTitle"
  const val SUBTITLE = "TeamSetupSubtitle"
  const val ACTIVE_FORM = "TeamSetupActiveForm"
  const val ADD_ANOTHER_BUTTON = "TeamSetupAddAnotherButton"
  const val PROCEED_BUTTON = "TeamSetupProceedButton"

  fun savedTeam(teamId: String) = "TeamSetupSavedTeam_$teamId"
}

/**
 * What the step is used for, which changes its labels and what moving on waits for.
 *
 * @property requiresTeam whether moving on waits for a team: creating the event does, since its
 *   volunteers pick one when they apply; leaving management again does not.
 */
enum class TeamSetupMode(
    @get:StringRes val backLabel: Int,
    @get:StringRes val title: Int,
    @get:StringRes val proceedLabel: Int,
    val requiresTeam: Boolean,
) {
  /** Part of the event creation flow, between the form and the join code. */
  CREATION(
      R.string.create_event_back,
      R.string.create_event_title,
      R.string.team_setup_proceed,
      requiresTeam = true,
  ),
  /** Reopened later from the event's Overview, the event long since created. */
  MANAGEMENT(
      R.string.team_setup_manage_back,
      R.string.team_setup_manage_title,
      R.string.team_setup_done,
      requiresTeam = false,
  ),
}

/**
 * The teams step of the organizer's event creation flow: the teams of the event, one form to add
 * the next, and the way on to its join code.
 *
 * The event is already persisted by the screen before this one, and each team is persisted on its
 * own as it is created.
 *
 * @param onBack called from the header, to leave the flow.
 * @param onProceed called once the organizer is done adding teams, which in
 *   [TeamSetupMode.CREATION] needs at least one.
 */
@Composable
fun TeamSetupScreen(
    viewModel: TeamSetupViewModel,
    onBack: () -> Unit,
    onProceed: () -> Unit,
    mode: TeamSetupMode = TeamSetupMode.CREATION,
) {
  val state by viewModel.uiState.collectAsState()
  val newTeamFactory = remember(viewModel) { viewModel.newTeamFactory() }

  // Only one team is created at a time, and each gets a fresh ViewModel: a key that moves on after
  // every creation, since a CreateTeamViewModel refuses to create a second team.
  var formKey by rememberSaveable { mutableIntStateOf(0) }
  // Null until the first load answers, which decides whether the step opens on a form: an event
  // without teams does, one the organizer comes back to lists what it has instead.
  var formShown by rememberSaveable { mutableStateOf<Boolean?>(null) }

  TeamSetupContent(
      state = state,
      mode = mode,
      onBack = onBack,
      onProceed = onProceed,
      onRetry = viewModel::refresh,
      onAddAnotherTeam = { formShown = true },
      form =
          if (formShown ?: state.isEmpty) {
            {
              NewTeamForm(
                  factory = newTeamFactory,
                  formKey = formKey,
                  onTeamCreated = { team ->
                    viewModel.onTeamCreated(team)
                    formKey++
                    formShown = false
                  },
                  onCancel = { formShown = false },
              )
            }
          } else {
            null
          },
  )
}

/**
 * Stateless content of [TeamSetupScreen]: renders [state] and reports every interaction.
 *
 * @param form the creation form to show under the teams, or null while none is open. A slot, so
 *   this stays free of the [CreateTeamViewModel] one needs.
 */
@Composable
fun TeamSetupContent(
    state: TeamSetupUiState,
    mode: TeamSetupMode = TeamSetupMode.CREATION,
    onBack: () -> Unit,
    onProceed: () -> Unit,
    onRetry: () -> Unit,
    onAddAnotherTeam: () -> Unit,
    form: (@Composable () -> Unit)?,
) {
  // The first load failed and left nothing to show: the error takes the place of the teams.
  val teamsUnknown = state.loadFailed && state.teams.isEmpty()
  Scaffold(
      topBar = {
        FormHeader(
            backLabel = stringResource(mode.backLabel),
            title = stringResource(mode.title),
            backButtonTestTag = TeamSetupScreenTestTags.BACK_BUTTON,
            onBack = onBack,
        )
      },
      bottomBar = {
        BottomActions(
            mode = mode,
            canProceed = state.canProceed,
            // A first load that failed leaves the teams unknown, and a team added over them could
            // repeat one the event already has.
            canAddTeam = !teamsUnknown,
            onAddAnotherTeam = onAddAnotherTeam,
            onProceed = onProceed,
        )
      },
  ) { padding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 10.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Nothing is known about the teams until the first load answers, so neither the teams nor
      // the form are drawn before it does.
      when {
        state.isLoading && state.teams.isEmpty() ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(Modifier.testTag(TeamSetupScreenTestTags.LOADING_INDICATOR))
            }
        teamsUnknown -> LoadError(onRetry)
        else -> {
          SectionHeading()
          state.teams.forEach { team -> key(team.id) { SavedTeamCard(team) } }
          form?.let { Box(Modifier.testTag(TeamSetupScreenTestTags.ACTIVE_FORM)) { it() } }
          // A refresh that failed over teams an earlier load brought in: they stay listed above.
          if (state.loadFailed) LoadError(onRetry)
        }
      }
    }
  }
}

/** The heading of the step, over what it is for. */
@Composable
private fun SectionHeading() {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
        text = stringResource(R.string.team_setup_section_title),
        // The 18sp semibold of the designs, between the theme's 16sp and 22sp titles.
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.testTag(TeamSetupScreenTestTags.SECTION_TITLE),
    )
    Text(
        text = stringResource(R.string.team_setup_subtitle),
        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
        color = shifter_mutedText,
        modifier = Modifier.testTag(TeamSetupScreenTestTags.SUBTITLE),
    )
  }
}

/**
 * A team the event already has, in the very card the form draws, which [CreateTeamUiState] leaves
 * read-only once a team has been created. Editing one is #226's business, not this step's.
 */
@Composable
private fun SavedTeamCard(team: Team) {
  Box(modifier = Modifier.testTag(TeamSetupScreenTestTags.savedTeam(team.id))) {
    CreateTeamContent(
        state =
            CreateTeamUiState(
                name = team.name,
                icon = team.icon,
                description = team.description,
                createdTeam = team,
            ),
        actions = CreateTeamActions(),
    )
  }
}

/**
 * The form of the next team, over a [CreateTeamViewModel] of its own: [formKey] is what makes it a
 * fresh one, so a completed form is never reopened.
 */
@Composable
private fun NewTeamForm(
    factory: ViewModelProvider.Factory,
    formKey: Int,
    onTeamCreated: (Team) -> Unit,
    onCancel: () -> Unit,
) {
  val formViewModel: CreateTeamViewModel =
      viewModel(key = "$NEW_TEAM_FORM_KEY$formKey", factory = factory)
  CreateTeamForm(
      viewModel = formViewModel,
      onTeamCreated = onTeamCreated,
      onCancel = onCancel,
  )
}

/** Why the teams could not be loaded, and the way to ask for them again. */
@Composable
private fun LoadError(onRetry: () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
        text = stringResource(R.string.team_setup_load_error),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag(TeamSetupScreenTestTags.LOAD_ERROR),
    )
    OutlinedButton(
        onClick = onRetry,
        shape = MaterialTheme.shapes.medium,
        modifier =
            Modifier.fillMaxWidth()
                .height(ACTION_HEIGHT)
                .testTag(TeamSetupScreenTestTags.RETRY_BUTTON),
    ) {
      Text(
          text = stringResource(R.string.team_setup_retry),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

/**
 * The two actions of the step, which stay put while the teams above them scroll. Proceeding waits
 * for a team to exist, which [TeamSetupUiState.canProceed] answers.
 */
@Composable
private fun BottomActions(
    mode: TeamSetupMode,
    canProceed: Boolean,
    canAddTeam: Boolean,
    onAddAnotherTeam: () -> Unit,
    onProceed: () -> Unit,
) {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .background(MaterialTheme.colorScheme.background)
              // The keyboard first, then the navigation bar out from under what is left of it.
              .imePadding()
              .navigationBarsPadding()
              .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    OutlinedButton(
        onClick = onAddAnotherTeam,
        enabled = canAddTeam,
        shape = MaterialTheme.shapes.medium,
        modifier =
            Modifier.fillMaxWidth()
                .height(ACTION_HEIGHT)
                .testTag(TeamSetupScreenTestTags.ADD_ANOTHER_BUTTON),
    ) {
      Text(
          text = stringResource(R.string.team_setup_add_another),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
      )
    }
    FormPrimaryButton(
        text = stringResource(mode.proceedLabel),
        onClick = onProceed,
        testTag = TeamSetupScreenTestTags.PROCEED_BUTTON,
        enabled = canProceed || !mode.requiresTeam,
    )
  }
}

/** Height of every action of the step, which [FormPrimaryButton] gives itself too. */
private val ACTION_HEIGHT = 48.dp

/** Prefix of the key each creation form keeps its own ViewModel under. */
private const val NEW_TEAM_FORM_KEY = "TeamSetupNewTeam-"
