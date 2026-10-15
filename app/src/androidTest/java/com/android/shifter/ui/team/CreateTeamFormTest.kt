// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.team

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.shifter.R
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The inline form over the real [CreateTeamViewModel], on a fake repository: what the organizer
 * does, what reaches the backend and what comes back out through the callbacks.
 *
 * The validation rules themselves belong to the ViewModel and are covered by its own tests; what a
 * few representative cases check here is that the form asks for them, shows their answer next to
 * the right field, and never creates a team twice.
 */
@RunWith(AndroidJUnit4::class)
class CreateTeamFormTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val clock = Clock.fixed(Instant.parse("2027-06-01T10:00:00Z"), ZoneId.of("UTC"))

  private val context
    get() = InstrumentationRegistry.getInstrumentation().targetContext

  /**
   * Records the teams it is asked to create. [gate] holds a creation in progress for as long as a
   * test needs it, and [failure] makes the next one fail; neither needs a clock or a sleep.
   */
  private class RecordingTeamRepository : TeamRepository {
    val created = mutableListOf<Team>()
    var attempts = 0
    var gate: CompletableDeferred<Unit>? = null
    var failure: Exception? = null

    override suspend fun createTeam(team: Team): Team {
      attempts++
      gate?.await()
      failure?.let { throw it }
      return team.copy(id = "team-${created.size + 1}").also { created += it }
    }

    override suspend fun getTeam(eventId: String, teamId: String): Team? = null

    override suspend fun getTeamsByEvent(eventId: String): List<Team> = emptyList()

    override suspend fun getTeamsOfMember(eventId: String, userId: String): List<Team> = emptyList()

    override suspend fun getTeamsManagedBy(eventId: String, userId: String): List<Team> =
        emptyList()

    override suspend fun setManager(eventId: String, teamId: String, managerId: String?) = Unit

    override suspend fun addMember(eventId: String, teamId: String, userId: String) = Unit

    override suspend fun removeMember(eventId: String, teamId: String, userId: String) = Unit
  }

  private fun viewModelOn(repository: TeamRepository) =
      CreateTeamViewModel(eventId = EVENT_ID, teamRepository = repository, clock = clock)

  private fun setForm(
      repository: TeamRepository,
      onTeamCreated: (Team) -> Unit = {},
      onCancel: () -> Unit = {},
  ) {
    val viewModel = viewModelOn(repository)
    composeTestRule.setContent {
      ShifterTheme { CreateTeamForm(viewModel, onTeamCreated = onTeamCreated, onCancel = onCancel) }
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  private fun errorNode(field: TeamFormField) =
      composeTestRule.onNodeWithTag(CreateTeamFormTestTags.error(field), useUnmergedTree = true)

  private fun pickIcon() {
    node(CreateTeamFormTestTags.ICON_SELECTOR).performClick()
    node(CreateTeamFormTestTags.iconOption(TeamIcon.TRUCK)).performClick()
  }

  private fun fillValidForm() {
    node(CreateTeamFormTestTags.NAME_FIELD).performTextInput(NAME)
    node(CreateTeamFormTestTags.DESCRIPTION_FIELD).performTextInput(DESCRIPTION)
    pickIcon()
  }

  private fun submit() = node(CreateTeamFormTestTags.SUBMIT_BUTTON).performClick()

  /** The indicator lives inside the merged submit button, so its tag needs the unmerged tree. */
  private fun awaitTag(tag: String, useUnmergedTree: Boolean = false) = composeTestRule.waitUntil {
    composeTestRule.onAllNodesWithTag(tag, useUnmergedTree).fetchSemanticsNodes().isNotEmpty()
  }

  // ---------------------------------------------------------------- initial state

  @Test
  fun startsEmptyWithBothActionsAndNoError() {
    setForm(RecordingTeamRepository())

    node(CreateTeamFormTestTags.NAME_FIELD).assertTextEquals("")
    node(CreateTeamFormTestTags.DESCRIPTION_FIELD).assertTextEquals("")
    node(CreateTeamFormTestTags.SUBMIT_BUTTON).assertIsDisplayed().assertIsEnabled()
    node(CreateTeamFormTestTags.CANCEL_BUTTON).assertIsDisplayed().assertIsEnabled()
    TeamFormField.entries.forEach { errorNode(it).assertDoesNotExist() }
    node(CreateTeamFormTestTags.SAVE_ERROR).assertDoesNotExist()
  }

  // ---------------------------------------------------------------- validation

  @Test
  fun submittingEmptyForm_showsEveryErrorAndCreatesNothing() {
    val repository = RecordingTeamRepository()
    setForm(repository)

    submit()

    TeamFormField.entries.forEach { errorNode(it).assertIsDisplayed() }
    assertEquals(0, repository.attempts)
  }

  @Test
  fun fixingOneFieldAfterSubmit_clearsOnlyItsError() {
    setForm(RecordingTeamRepository())
    submit()

    node(CreateTeamFormTestTags.NAME_FIELD).performTextInput(NAME)

    errorNode(TeamFormField.NAME).assertDoesNotExist()
    errorNode(TeamFormField.ICON).assertExists()
    errorNode(TeamFormField.DESCRIPTION).assertExists()
  }

  /** The one length rule shown end to end: its message carries the limit the ViewModel holds. */
  @Test
  fun tooLongName_showsItsMessageAndCreatesNothing() {
    val repository = RecordingTeamRepository()
    setForm(repository)
    fillValidForm()
    node(CreateTeamFormTestTags.NAME_FIELD)
        .performTextReplacement("a".repeat(CreateTeamViewModel.NAME_MAX_LENGTH + 1))

    submit()

    errorNode(TeamFormField.NAME)
        .assertTextEquals(
            context.resources.getQuantityString(
                R.plurals.create_team_error_too_long,
                CreateTeamViewModel.NAME_MAX_LENGTH,
                CreateTeamViewModel.NAME_MAX_LENGTH,
            )
        )
    assertEquals(0, repository.attempts)
  }

  /** The icon is the only field that is not typed, so its absence gets its own case. */
  @Test
  fun withoutAnIcon_nothingIsCreated() {
    val repository = RecordingTeamRepository()
    setForm(repository)
    node(CreateTeamFormTestTags.NAME_FIELD).performTextInput(NAME)
    node(CreateTeamFormTestTags.DESCRIPTION_FIELD).performTextInput(DESCRIPTION)

    submit()

    errorNode(TeamFormField.ICON)
        .assertTextEquals(context.getString(R.string.create_team_error_icon_missing))
    assertEquals(0, repository.attempts)
  }

  // ---------------------------------------------------------------- creation

  @Test
  fun validForm_createsTheTeamTheOrganizerFilledIn() {
    val repository = RecordingTeamRepository()
    var reported: Team? = null
    setForm(repository, onTeamCreated = { reported = it })

    fillValidForm()
    submit()

    composeTestRule.waitUntil { reported != null }
    val created = repository.created.single()
    assertEquals(NAME, created.name)
    assertEquals(DESCRIPTION, created.description)
    assertEquals(TeamIcon.TRUCK.id, created.icon)
    assertEquals(EVENT_ID, created.eventId)
    assertEquals(created, reported)
  }

  @Test
  fun afterCreation_theFormIsFrozenAndReportsTheTeamOnce() {
    val repository = RecordingTeamRepository()
    var reports = 0
    var cancelled = 0
    setForm(repository, onTeamCreated = { reports++ }, onCancel = { cancelled++ })

    fillValidForm()
    submit()
    composeTestRule.waitUntil { reports > 0 }

    // The team exists: the form waits to be dismissed, and takes nothing more from the organizer.
    node(CreateTeamFormTestTags.NAME_FIELD).assertIsNotEnabled()
    node(CreateTeamFormTestTags.DESCRIPTION_FIELD).assertIsNotEnabled()
    node(CreateTeamFormTestTags.ICON_SELECTOR).assertIsNotEnabled()
    node(CreateTeamFormTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
    node(CreateTeamFormTestTags.CANCEL_BUTTON).assertIsNotEnabled()

    // Neither action goes through any more, and the team is reported once; that it stays reported
    // once through a recomposition is [theCreatedTeam_isNotReportedAgainAfterRecreation].
    submit()
    node(CreateTeamFormTestTags.CANCEL_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, reports)
    assertEquals(1, repository.attempts)
    assertEquals(0, cancelled)
  }

  @Test
  fun theCreatedTeam_isNotReportedAgainAfterRecreation() {
    var reports = 0
    val viewModel = viewModelOn(RecordingTeamRepository())
    val restorationTester = StateRestorationTester(composeTestRule)
    restorationTester.setContent {
      ShifterTheme { CreateTeamForm(viewModel, onTeamCreated = { reports++ }, onCancel = {}) }
    }

    fillValidForm()
    submit()
    composeTestRule.waitUntil { reports > 0 }
    restorationTester.emulateSavedInstanceStateRestore()
    composeTestRule.waitForIdle()

    assertEquals(1, reports)
  }

  // ---------------------------------------------------------------- saving

  @Test
  fun whileSaving_theFormIsFrozen() {
    val repository = RecordingTeamRepository().apply { gate = CompletableDeferred() }
    var cancelled = 0
    setForm(repository, onCancel = { cancelled++ })

    fillValidForm()
    submit()
    awaitTag(CreateTeamFormTestTags.SAVING_INDICATOR, useUnmergedTree = true)

    composeTestRule
        .onNodeWithTag(CreateTeamFormTestTags.SAVING_INDICATOR, useUnmergedTree = true)
        .assertIsDisplayed()
    node(CreateTeamFormTestTags.NAME_FIELD).assertIsNotEnabled()
    node(CreateTeamFormTestTags.DESCRIPTION_FIELD).assertIsNotEnabled()
    node(CreateTeamFormTestTags.ICON_SELECTOR).assertIsNotEnabled()
    node(CreateTeamFormTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
    node(CreateTeamFormTestTags.CANCEL_BUTTON).assertIsNotEnabled()

    // Neither action goes through while the first one is still in flight.
    submit()
    node(CreateTeamFormTestTags.CANCEL_BUTTON).performClick()
    assertEquals(1, repository.attempts)
    assertEquals(0, cancelled)

    repository.gate?.complete(Unit)
  }

  // ---------------------------------------------------------------- failure and retry

  @Test
  fun aFailedSave_isShownAndKeepsWhatWasTyped() {
    val repository = RecordingTeamRepository().apply { failure = IllegalStateException("offline") }
    var reported: Team? = null
    setForm(repository, onTeamCreated = { reported = it })

    fillValidForm()
    submit()

    awaitTag(CreateTeamFormTestTags.SAVE_ERROR)
    node(CreateTeamFormTestTags.SAVE_ERROR)
        .assertTextEquals(context.getString(R.string.create_team_error_save_failed))
    node(CreateTeamFormTestTags.NAME_FIELD).assertTextContains(NAME)
    node(CreateTeamFormTestTags.DESCRIPTION_FIELD).assertTextContains(DESCRIPTION)
    node(CreateTeamFormTestTags.SUBMIT_BUTTON).assertIsEnabled()
    assertNull(reported)
  }

  @Test
  fun retryingAfterAFailure_createsTheTeam() {
    val repository = RecordingTeamRepository().apply { failure = IllegalStateException("offline") }
    var reported: Team? = null
    setForm(repository, onTeamCreated = { reported = it })

    fillValidForm()
    submit()
    awaitTag(CreateTeamFormTestTags.SAVE_ERROR)

    repository.failure = null
    submit()

    composeTestRule.waitUntil { reported != null }
    assertEquals(NAME, repository.created.single().name)
    assertEquals(2, repository.attempts)
  }

  // ---------------------------------------------------------------- cancellation

  @Test
  fun cancelling_reportsItAndCreatesNothing() {
    val repository = RecordingTeamRepository()
    var cancelled = 0
    setForm(repository, onCancel = { cancelled++ })

    fillValidForm()
    node(CreateTeamFormTestTags.CANCEL_BUTTON).performClick()

    assertEquals(1, cancelled)
    assertEquals(0, repository.attempts)
    assertTrue(repository.created.isEmpty())
  }

  private companion object {
    const val EVENT_ID = "event-1"
    const val NAME = "Logistics"
    const val DESCRIPTION = "Set up the site and bring in the equipment."
  }
}
