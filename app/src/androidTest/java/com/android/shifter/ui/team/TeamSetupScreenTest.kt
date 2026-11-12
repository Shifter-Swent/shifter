// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.team

import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.shifter.R
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.ui.navigation.NavigationTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The teams step over the real [TeamSetupViewModel] and [CreateTeamViewModel], on a fake
 * repository: what the organizer sees of the event's teams, and what adding one more does.
 *
 * The validation of a team belongs to [CreateTeamFormTest] and to the ViewModels' own tests; what
 * is checked here is the step around the form — its states, the teams it lists, and that each new
 * team starts from an empty form. The states that no interaction reaches are rendered through
 * [TeamSetupContent], which takes them as they are.
 */
@RunWith(AndroidJUnit4::class)
class TeamSetupScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  /**
   * The teams of the event, in memory: the fakes of the unit tests live in `src/test` and are out
   * of reach here. Ids are assigned in creation order, and [failure] makes every call throw.
   */
  private class FakeTeams(initialTeams: List<Team> = emptyList()) : TeamRepository {
    val teams: MutableList<Team> = initialTeams.toMutableList()
    var failure: Exception? = null
    var beforeUpdate: suspend () -> Unit = {}
    private var createdCount = 0

    override suspend fun createTeam(team: Team): Team {
      failure?.let { throw it }
      return team.copy(id = "team-${++createdCount}").also { teams += it }
    }

    override suspend fun getTeamsByEvent(eventId: String): List<Team> {
      failure?.let { throw it }
      return teams.filter { it.eventId == eventId }
    }

    override suspend fun getTeam(eventId: String, teamId: String): Team? = null

    override suspend fun getTeamsOfMember(eventId: String, userId: String): List<Team> = emptyList()

    override suspend fun getTeamsManagedBy(eventId: String, userId: String): List<Team> =
        emptyList()

    override suspend fun setManager(eventId: String, teamId: String, managerId: String?) = Unit

    override suspend fun updateTeam(
        eventId: String,
        teamId: String,
        name: String,
        description: String,
        icon: String,
    ) {
      beforeUpdate()
      failure?.let { throw it }
      val index = teams.indexOfFirst { it.id == teamId }
      teams[index] = teams[index].copy(name = name, description = description, icon = icon)
    }

    override suspend fun addMember(eventId: String, teamId: String, userId: String) = Unit

    override suspend fun removeMember(eventId: String, teamId: String, userId: String) = Unit

    override suspend fun deleteTeam(eventId: String, teamId: String) = Unit
  }

  private val eventId = "event-1"
  private val createdAt = Instant.parse("2027-06-20T08:00:00Z")

  private val context
    get() = InstrumentationRegistry.getInstrumentation().targetContext

  private fun team(id: String, name: String, icon: TeamIcon = TeamIcon.TRUCK) =
      Team(
          id = id,
          eventId = eventId,
          name = name,
          description = "What $name does",
          icon = icon.id,
          createdAt = createdAt,
      )

  /** What the card of a team says its icon is, as its selector describes it out loud. */
  private fun iconDescription(icon: TeamIcon) =
      context.getString(R.string.create_team_icon_selected, context.getString(icon.label))

  /** Opens the step over [teams], which the screen loads on its own. Returns what it reported. */
  private fun open(teams: FakeTeams): MutableList<String> {
    val events = mutableListOf<String>()
    composeTestRule.setContent {
      ShifterTheme {
        TeamSetupScreen(
            viewModel = remember { TeamSetupViewModel(eventId, teams) },
            onBack = { events += "back" },
            onProceed = { events += "proceed" },
        )
      }
    }
    return events
  }

  /** Renders [state] as it is, for a state no interaction can reach reliably. */
  private fun show(state: TeamSetupUiState, mode: TeamSetupMode = TeamSetupMode.CREATION) {
    composeTestRule.setContent {
      ShifterTheme {
        TeamSetupContent(
            state = state,
            mode = mode,
            onBack = {},
            onProceed = {},
            onRetry = {},
            onAddAnotherTeam = {},
            form = null,
        )
      }
    }
  }

  /** A node of the form that is open, told apart from the read-only cards of the saved teams. */
  private fun inActiveForm(tag: String) =
      composeTestRule.onNode(
          hasTestTag(tag) and hasAnyAncestor(hasTestTag(TeamSetupScreenTestTags.ACTIVE_FORM))
      )

  /** A node of the card of the team [teamId]. */
  private fun inSavedTeam(teamId: String, tag: String) =
      composeTestRule
          .onAllNodesWithTag(tag)
          .filterToOne(hasAnyAncestor(hasTestTag(TeamSetupScreenTestTags.savedTeam(teamId))))

  /** Fills the open form with a team called [name] and submits it. */
  private fun createTeam(name: String) {
    inActiveForm(CreateTeamFormTestTags.NAME_FIELD).performTextInput(name)
    inActiveForm(CreateTeamFormTestTags.ICON_SELECTOR).performClick()
    composeTestRule.onNodeWithTag(CreateTeamFormTestTags.iconOption(TeamIcon.TRUCK)).performClick()
    inActiveForm(CreateTeamFormTestTags.DESCRIPTION_FIELD).performTextInput("What $name does")
    inActiveForm(CreateTeamFormTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
  }

  @Test
  fun whileTheTeamsLoad_onlyTheIndicatorIsShown() {
    show(TeamSetupUiState())

    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.LOADING_INDICATOR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.SECTION_TITLE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.LOAD_ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.PROCEED_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun failedRefresh_keepsTheTeamsListed() {
    show(TeamSetupUiState(isLoading = false, loadFailed = true, teams = listOf(team("t1", "Logi"))))

    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.savedTeam("t1")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.LOAD_ERROR).assertExists()
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.PROCEED_BUTTON).assertIsEnabled()
  }

  @Test
  fun failedFirstLoad_offersNoTeamToBeAdded() {
    // Nothing is known about the event's teams, so adding one could repeat what it already has.
    show(TeamSetupUiState(isLoading = false, loadFailed = true))

    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.LOAD_ERROR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.ADD_ANOTHER_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun failedRefresh_stillLetsAnotherTeamBeAdded() {
    // The teams of an earlier load are still listed, so there is something to add to.
    show(TeamSetupUiState(isLoading = false, loadFailed = true, teams = listOf(team("t1", "Logi"))))

    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.ADD_ANOTHER_BUTTON).assertIsEnabled()
  }

  @Test
  fun recreatingTheScreen_afterAFinishedForm_doesNotBringItBack() {
    val teams = FakeTeams()
    val restoration = StateRestorationTester(composeTestRule)
    restoration.setContent {
      ShifterTheme {
        TeamSetupScreen(
            viewModel = remember { TeamSetupViewModel(eventId, teams) },
            onBack = {},
            onProceed = {},
        )
      }
    }
    createTeam("Logistics")
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.savedTeam("team-1")).assertIsDisplayed()

    restoration.emulateSavedInstanceStateRestore()

    // The team is still there, and the form that created it does not open itself again.
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.savedTeam("team-1")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.ACTIVE_FORM).assertDoesNotExist()
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.PROCEED_BUTTON).assertIsEnabled()
  }

  @Test
  fun withoutATeam_theStepOpensOnAnEmptyForm() {
    open(FakeTeams())

    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.ACTIVE_FORM).assertIsDisplayed()
    inActiveForm(CreateTeamFormTestTags.NAME_FIELD).assertTextEquals("")
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.LOADING_INDICATOR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.LOAD_ERROR).assertDoesNotExist()
  }

  @Test
  fun loaded_listsTheTeamsWithoutAFormOverThem() {
    // Icons that are not the teams' own names, so a card cannot pass by guessing one from them.
    open(
        FakeTeams(
            listOf(
                team("team-1", "Logistics", TeamIcon.CHEF_HAT),
                team("team-2", "Bar", TeamIcon.WINE),
            )
        )
    )

    inSavedTeam("team-1", CreateTeamFormTestTags.NAME_FIELD).assertTextEquals("Logistics")
    inSavedTeam("team-1", CreateTeamFormTestTags.DESCRIPTION_FIELD)
        .assertTextEquals("What Logistics does")
    inSavedTeam("team-1", CreateTeamFormTestTags.ICON_SELECTOR)
        .assertContentDescriptionEquals(iconDescription(TeamIcon.CHEF_HAT))

    inSavedTeam("team-2", CreateTeamFormTestTags.NAME_FIELD).assertTextEquals("Bar")
    inSavedTeam("team-2", CreateTeamFormTestTags.DESCRIPTION_FIELD)
        .assertTextEquals("What Bar does")
    inSavedTeam("team-2", CreateTeamFormTestTags.ICON_SELECTOR)
        .assertContentDescriptionEquals(iconDescription(TeamIcon.WINE))

    // Coming back to the step lists what the event has instead of asking for one more team.
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.ACTIVE_FORM).assertDoesNotExist()
  }

  @Test
  fun savedTeams_withoutAWayToEdit_areReadOnly() {
    show(TeamSetupUiState(isLoading = false, teams = listOf(team("team-1", "Logistics"))))

    // TeamSetupContent alone draws the saved teams read-only; the screen makes them editable.
    inSavedTeam("team-1", CreateTeamFormTestTags.NAME_FIELD).assertIsNotEnabled()
    inSavedTeam("team-1", CreateTeamFormTestTags.DESCRIPTION_FIELD).assertIsNotEnabled()
    inSavedTeam("team-1", CreateTeamFormTestTags.ICON_SELECTOR).assertIsNotEnabled()
  }

  @Test
  fun editingASavedTeam_savesAFieldOnceItLosesTheFocus() {
    val teams = FakeTeams(listOf(team("team-1", "Logistics")))
    open(teams)

    inSavedTeam("team-1", CreateTeamFormTestTags.NAME_FIELD).performTextReplacement("Supply")
    composeTestRule.waitForIdle()
    assertEquals("Logistics", teams.teams.single().name)
    // Moving on to the description is leaving the name.
    inSavedTeam("team-1", CreateTeamFormTestTags.DESCRIPTION_FIELD).performClick()

    composeTestRule.waitUntil(TIMEOUT_MILLIS) { teams.teams.single().name == "Supply" }
  }

  @Test
  fun editingASavedTeam_savesTheDescriptionOnceItLosesTheFocus() {
    val teams = FakeTeams(listOf(team("team-1", "Logistics")))
    open(teams)

    inSavedTeam("team-1", CreateTeamFormTestTags.DESCRIPTION_FIELD)
        .performTextReplacement("Bring the water")
    inSavedTeam("team-1", CreateTeamFormTestTags.NAME_FIELD).performClick()

    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      teams.teams.single().description == "Bring the water"
    }
  }

  @Test
  fun editingASavedTeam_savesTheIconAsSoonAsItIsPicked() {
    val teams = FakeTeams(listOf(team("team-1", "Logistics")))
    open(teams)

    inSavedTeam("team-1", CreateTeamFormTestTags.ICON_SELECTOR).performClick()
    composeTestRule.onNodeWithTag(CreateTeamFormTestTags.iconOption(TeamIcon.WINE)).performClick()

    composeTestRule.waitUntil(TIMEOUT_MILLIS) { teams.teams.single().icon == TeamIcon.WINE.id }
  }

  @Test
  fun editingASavedTeam_withAnEmptyName_showsTheErrorAndKeepsTheTeam() {
    val teams = FakeTeams(listOf(team("team-1", "Logistics")))
    open(teams)

    inSavedTeam("team-1", CreateTeamFormTestTags.NAME_FIELD).performTextReplacement("")
    inSavedTeam("team-1", CreateTeamFormTestTags.DESCRIPTION_FIELD).performClick()

    composeTestRule
        .onNodeWithText(context.getString(R.string.create_team_error_name_empty))
        .assertIsDisplayed()
    composeTestRule.waitForIdle()
    assertEquals("Logistics", teams.teams.single().name)
  }

  @Test
  fun editingASavedTeam_thatFailsToSave_showsASnackbar() {
    val teams = FakeTeams(listOf(team("team-1", "Logistics")))
    open(teams)
    composeTestRule.waitForIdle()
    teams.failure = IllegalStateException("denied")

    inSavedTeam("team-1", CreateTeamFormTestTags.NAME_FIELD).performTextReplacement("Supply")
    inSavedTeam("team-1", CreateTeamFormTestTags.DESCRIPTION_FIELD).performClick()

    val failure = context.getString(R.string.team_setup_save_failed)
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule.onAllNodesWithText(failure).fetchSemanticsNodes().isNotEmpty()
    }
    // Typing on leaves the report on screen.
    inSavedTeam("team-1", CreateTeamFormTestTags.DESCRIPTION_FIELD).performTextInput(" more")
    composeTestRule.onNodeWithText(failure).assertIsDisplayed()
  }

  @Test
  fun leavingTheScreen_savesWhatTheFocusedFieldHolds() {
    val teams = FakeTeams(listOf(team("team-1", "Logistics")))
    var shown by mutableStateOf(true)
    composeTestRule.setContent {
      ShifterTheme {
        if (shown) {
          TeamSetupScreen(
              viewModel = remember { TeamSetupViewModel(eventId, teams) },
              onBack = {},
              onProceed = {},
          )
        }
      }
    }

    inSavedTeam("team-1", CreateTeamFormTestTags.NAME_FIELD).performTextReplacement("Supply")
    // The field still has the focus, which it loses as the screen goes.
    shown = false

    composeTestRule.waitUntil(TIMEOUT_MILLIS) { teams.teams.single().name == "Supply" }
  }

  @Test
  fun pressBack_withFocusedEdit_popsTheEntryAndSavesTheTeam() {
    checkNavigationWithDelayedSave { pressBack() }
  }

  @Test
  fun headerBack_withFocusedEdit_popsTheEntryAndSavesTheTeam() {
    checkNavigationWithDelayedSave {
      composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    }
  }

  @Test
  fun proceed_withFocusedEdit_popsTheEntryAndSavesTheTeam() {
    checkNavigationWithDelayedSave {
      composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.PROCEED_BUTTON).performClick()
    }
  }

  private fun checkNavigationWithDelayedSave(leave: () -> Unit) {
    val teams = FakeTeams(listOf(team("team-1", "Logistics")))
    lateinit var navController: NavHostController
    lateinit var setup: TeamSetupViewModel
    val release = CompletableDeferred<Unit>()
    var updateStarted = false
    teams.beforeUpdate = {
      updateStarted = true
      release.await()
    }
    composeTestRule.setContent {
      ShifterTheme {
        navController = rememberNavController()
        NavHost(navController, startDestination = "overview") {
          composable("overview") {
            Button(onClick = { navController.navigate("teams") }) { Text("Manage teams") }
          }
          composable("teams") {
            setup = viewModel(factory = TeamSetupViewModel.factory(eventId, teams))
            TeamSetupScreen(
                viewModel = setup,
                onBack = { navController.popBackStack() },
                onProceed = { navController.popBackStack() },
                mode = TeamSetupMode.MANAGEMENT,
            )
          }
        }
      }
    }
    try {
      composeTestRule.onNodeWithText("Manage teams").performClick()
      inSavedTeam("team-1", CreateTeamFormTestTags.NAME_FIELD).performTextReplacement("Supply")
      // Hide the keyboard without moving focus, so Back leaves the destination.
      composeTestRule.runOnIdle {
        composeTestRule.activity
            .getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            .hideSoftInputFromWindow(composeTestRule.activity.window.decorView.windowToken, 0)
      }
      composeTestRule.waitForIdle()
      leave()
      composeTestRule.onNodeWithText("Manage teams").assertIsDisplayed()
      composeTestRule.waitUntil(TIMEOUT_MILLIS) {
        updateStarted && setup.viewModelScope.coroutineContext[Job]?.isCancelled == true
      }
      composeTestRule.runOnIdle {
        assertEquals("overview", navController.currentDestination?.route)
        assertNull(navController.previousBackStackEntry)
        assertEquals("Logistics", teams.teams.single().name)
      }
    } finally {
      release.complete(Unit)
    }
    composeTestRule.waitUntil(TIMEOUT_MILLIS) { teams.teams.single().name == "Supply" }
  }

  @Test
  fun editingASavedTeam_withInvalidName_savesTheDescriptionAndIcon() {
    val teams = FakeTeams(listOf(team("team-1", "Logistics")))
    open(teams)
    inSavedTeam("team-1", CreateTeamFormTestTags.NAME_FIELD).performTextReplacement("")
    inSavedTeam("team-1", CreateTeamFormTestTags.DESCRIPTION_FIELD)
        .performTextReplacement("Bring the water")
    inSavedTeam("team-1", CreateTeamFormTestTags.ICON_SELECTOR).performClick()
    composeTestRule.onNodeWithTag(CreateTeamFormTestTags.iconOption(TeamIcon.WINE)).performClick()

    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      teams.teams.single().description == "Bring the water" &&
          teams.teams.single().icon == TeamIcon.WINE.id
    }
    assertEquals("Logistics", teams.teams.single().name)
    composeTestRule
        .onNodeWithText(context.getString(R.string.create_team_error_name_empty))
        .assertIsDisplayed()
  }

  @Test
  fun failedLoad_isReportedAndRetryListsTheTeams() {
    val teams = FakeTeams(listOf(team("team-1", "Logistics")))
    teams.failure = IllegalStateException("offline")

    open(teams)

    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.LOAD_ERROR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.ACTIVE_FORM).assertDoesNotExist()
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.PROCEED_BUTTON).assertIsNotEnabled()

    teams.failure = null
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.RETRY_BUTTON).performClick()

    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.LOAD_ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.savedTeam("team-1")).assertIsDisplayed()
  }

  @Test
  fun creatingATeam_listsItAndDismissesItsForm() {
    val teams = FakeTeams()
    open(teams)

    createTeam("Logistics")

    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.savedTeam("team-1")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.ACTIVE_FORM).assertDoesNotExist()
    assertEquals(listOf("Logistics"), teams.teams.map { it.name })
  }

  @Test
  fun creatingTeams_startsEachFromAFreshForm() {
    val teams = FakeTeams()
    open(teams)

    createTeam("Logistics")
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.ADD_ANOTHER_BUTTON).performClick()

    // The form of the second team starts empty: the ViewModel of the first is never reopened.
    inActiveForm(CreateTeamFormTestTags.NAME_FIELD).assertTextEquals("")
    inActiveForm(CreateTeamFormTestTags.DESCRIPTION_FIELD).assertTextEquals("")

    createTeam("Bar")

    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.savedTeam("team-1")).assertExists()
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.savedTeam("team-2")).assertExists()
    assertEquals(listOf("Logistics", "Bar"), teams.teams.map { it.name })
  }

  @Test
  fun cancellingTheForm_dropsItWithoutTouchingTheSavedTeams() {
    val teams = FakeTeams()
    open(teams)
    createTeam("Logistics")
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.ADD_ANOTHER_BUTTON).performClick()
    inActiveForm(CreateTeamFormTestTags.NAME_FIELD).performTextInput("Bar")

    inActiveForm(CreateTeamFormTestTags.CANCEL_BUTTON).performScrollTo().performClick()

    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.ACTIVE_FORM).assertDoesNotExist()
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.savedTeam("team-1")).assertIsDisplayed()
    assertEquals(listOf("Logistics"), teams.teams.map { it.name })
  }

  @Test
  fun addAnotherTeam_onAnEventWithTeams_opensAForm() {
    open(FakeTeams(listOf(team("team-1", "Logistics"))))
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.ACTIVE_FORM).assertDoesNotExist()

    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.ADD_ANOTHER_BUTTON).performClick()

    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.ACTIVE_FORM).assertIsDisplayed()
    inActiveForm(CreateTeamFormTestTags.NAME_FIELD).assertTextEquals("")
  }

  @Test
  fun proceeding_waitsForATeamToExist() {
    val events = open(FakeTeams())

    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.PROCEED_BUTTON).assertIsNotEnabled()
    createTeam("Logistics")

    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.PROCEED_BUTTON).assertIsEnabled()
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.PROCEED_BUTTON).performClick()

    assertEquals(listOf("proceed"), events)
  }

  @Test
  fun proceeding_withATeamAlready_isPossibleAtOnce() {
    val events = open(FakeTeams(listOf(team("team-1", "Logistics"))))

    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.PROCEED_BUTTON).performClick()

    assertEquals(listOf("proceed"), events)
  }

  @Test
  fun header_leavesTheStep() {
    val events = open(FakeTeams())

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()

    assertEquals(listOf("back"), events)
  }

  @Test
  fun creationMode_asksToCreateTheEventAndWaitsForATeam() {
    show(TeamSetupUiState(isLoading = false))

    composeTestRule
        .onNodeWithTag(TeamSetupScreenTestTags.PROCEED_BUTTON)
        .assertTextEquals(context.getString(R.string.team_setup_proceed))
        .assertIsNotEnabled()
    composeTestRule.onNodeWithText(context.getString(R.string.create_event_title)).assertExists()
  }

  @Test
  fun managementMode_isDoneAndNeedsNoTeamToLeave() {
    show(TeamSetupUiState(isLoading = false), TeamSetupMode.MANAGEMENT)

    // The event exists already, so leaving its teams is never blocked.
    composeTestRule
        .onNodeWithTag(TeamSetupScreenTestTags.PROCEED_BUTTON)
        .assertTextEquals(context.getString(R.string.team_setup_done))
        .assertIsEnabled()
    composeTestRule
        .onNodeWithText(context.getString(R.string.team_setup_manage_title))
        .assertExists()
    composeTestRule
        .onNodeWithText(context.getString(R.string.create_event_title))
        .assertDoesNotExist()
  }

  @Test
  fun opening_saysWhatTheStepIsAbout() {
    open(FakeTeams())

    composeTestRule
        .onNodeWithTag(TeamSetupScreenTestTags.SECTION_TITLE)
        .assertTextEquals(context.getString(R.string.team_setup_section_title))
    composeTestRule
        .onNodeWithTag(TeamSetupScreenTestTags.SUBTITLE)
        .assertTextEquals(context.getString(R.string.team_setup_subtitle))
  }

  private companion object {
    /** Long enough for a slow CI emulator, where the default second is tight. */
    const val TIMEOUT_MILLIS = 5_000L
  }
}
