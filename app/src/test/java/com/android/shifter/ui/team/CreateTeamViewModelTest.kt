// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.team

import androidx.lifecycle.viewmodel.CreationExtras
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateTeamViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private val zone = ZoneId.of("Europe/Zurich")

  /** 1 June 2027, 12:00 in Zurich. */
  private val now = Instant.parse("2027-06-01T10:00:00Z")
  private val clock = Clock.fixed(now, zone)

  private val eventId = "event-1"

  private lateinit var teams: FakeTeamRepository
  private lateinit var viewModel: CreateTeamViewModel

  private val state
    get() = viewModel.uiState.value

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    teams = FakeTeamRepository()
    viewModel = CreateTeamViewModel(eventId, teams, clock)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** Fills the three fields of the form with values that pass every rule. */
  private fun fillValidForm() {
    viewModel.onNameChange("Logistics")
    viewModel.onIconChange("box")
    viewModel.onDescriptionChange("Move the equipment around the site")
  }

  /** Submits and lets the write complete. */
  private fun TestScope.submit() {
    viewModel.createTeam()
    advanceUntilIdle()
  }

  /** A name of exactly [length] characters. */
  private fun name(length: Int) = "a".repeat(length)

  /** A description of exactly [length] characters. */
  private fun description(length: Int) = "b".repeat(length)

  @Test
  fun initialState_isAnEmptyForm() {
    assertEquals("", state.name)
    assertNull(state.icon)
    assertEquals("", state.description)
    assertEquals(emptySet<TeamFormError>(), state.errors)
    assertFalse(state.isSaving)
    assertFalse(state.saveFailed)
    assertNull(state.createdTeam)
    assertFalse(state.createdTeamHandled)
    assertTrue(state.canSubmit)
  }

  @Test
  fun editsBeforeTheFirstSubmit_areNotValidated() {
    viewModel.onNameChange("Logistics")
    viewModel.onNameChange("")
    viewModel.onDescriptionChange("")

    assertEquals(emptySet<TeamFormError>(), state.errors)
  }

  @Test
  fun submit_withoutAName_reportsItUnderTheNameField() = runTest {
    viewModel.onIconChange("box")
    viewModel.onDescriptionChange("Move the equipment around the site")
    submit()

    assertEquals(TeamFormError.NAME_EMPTY, state.errorFor(TeamFormField.NAME))
    assertNull(state.errorFor(TeamFormField.ICON))
    assertNull(state.errorFor(TeamFormField.DESCRIPTION))
  }

  @Test
  fun submit_withABlankName_reportsAnEmptyName() = runTest {
    fillValidForm()
    viewModel.onNameChange("   ")
    submit()

    assertEquals(TeamFormError.NAME_EMPTY, state.errorFor(TeamFormField.NAME))
  }

  /** Nothing picked yet, so the icon is still null. */
  @Test
  fun submit_withoutAnIcon_reportsItUnderTheIconField() = runTest {
    viewModel.onNameChange("Logistics")
    viewModel.onDescriptionChange("Move the equipment around the site")
    submit()

    assertEquals(TeamFormError.ICON_MISSING, state.errorFor(TeamFormField.ICON))
    assertNull(state.errorFor(TeamFormField.NAME))
    assertNull(state.errorFor(TeamFormField.DESCRIPTION))
  }

  /** A whitespace-only identifier is invalid, even if it is not empty. */
  @Test
  fun submit_withABlankIcon_reportsItAsMissing() = runTest {
    fillValidForm()
    viewModel.onIconChange("   ")
    submit()

    assertEquals(TeamFormError.ICON_MISSING, state.errorFor(TeamFormField.ICON))
    assertEquals(emptyList<Any>(), teams.teams)
  }

  /** Padding never reaches the backend: the stored identifier has to match the catalogue. */
  @Test
  fun createTeam_persistsTheTrimmedIcon() = runTest {
    fillValidForm()
    viewModel.onIconChange("  box  ")
    submit()

    assertEquals(emptySet<TeamFormError>(), state.errors)
    assertEquals("box", teams.teams.single().icon)
  }

  @Test
  fun submit_withoutADescription_reportsItUnderTheDescriptionField() = runTest {
    viewModel.onNameChange("Logistics")
    viewModel.onIconChange("box")
    submit()

    assertEquals(TeamFormError.DESCRIPTION_EMPTY, state.errorFor(TeamFormField.DESCRIPTION))
    assertNull(state.errorFor(TeamFormField.NAME))
    assertNull(state.errorFor(TeamFormField.ICON))
  }

  @Test
  fun submit_withABlankDescription_reportsAnEmptyDescription() = runTest {
    fillValidForm()
    viewModel.onDescriptionChange("   ")
    submit()

    assertEquals(TeamFormError.DESCRIPTION_EMPTY, state.errorFor(TeamFormField.DESCRIPTION))
  }

  @Test
  fun submit_withAnEmptyForm_reportsEveryFieldAtOnce() = runTest {
    submit()

    assertEquals(
        setOf(
            TeamFormError.NAME_EMPTY,
            TeamFormError.ICON_MISSING,
            TeamFormError.DESCRIPTION_EMPTY,
        ),
        state.errors,
    )
  }

  @Test
  fun afterTheFirstSubmit_errorsFollowTheEdits() = runTest {
    submit()

    viewModel.onNameChange("Logistics")
    assertEquals(
        setOf(TeamFormError.ICON_MISSING, TeamFormError.DESCRIPTION_EMPTY),
        state.errors,
    )

    viewModel.onIconChange("welcome")
    assertEquals(setOf(TeamFormError.DESCRIPTION_EMPTY), state.errors)

    viewModel.onDescriptionChange("Greet the volunteers")
    assertEquals(emptySet<TeamFormError>(), state.errors)
  }

  @Test
  fun submit_withANameOfExactlyTheMaximum_createsTheTeam() = runTest {
    fillValidForm()
    viewModel.onNameChange(name(CreateTeamViewModel.NAME_MAX_LENGTH))
    submit()

    assertEquals(emptySet<TeamFormError>(), state.errors)
    assertEquals(CreateTeamViewModel.NAME_MAX_LENGTH, teams.teams.single().name.length)
  }

  @Test
  fun submit_withANameOneCharacterTooLong_reportsIt() = runTest {
    fillValidForm()
    viewModel.onNameChange(name(CreateTeamViewModel.NAME_MAX_LENGTH + 1))
    submit()

    assertEquals(TeamFormError.NAME_TOO_LONG, state.errorFor(TeamFormField.NAME))
    assertEquals(emptyList<Any>(), teams.teams)
  }

  @Test
  fun submit_withADescriptionOfExactlyTheMaximum_createsTheTeam() = runTest {
    fillValidForm()
    viewModel.onDescriptionChange(description(CreateTeamViewModel.DESCRIPTION_MAX_LENGTH))
    submit()

    assertEquals(emptySet<TeamFormError>(), state.errors)
    assertEquals(
        CreateTeamViewModel.DESCRIPTION_MAX_LENGTH,
        teams.teams.single().description.length,
    )
  }

  @Test
  fun submit_withADescriptionOneCharacterTooLong_reportsIt() = runTest {
    fillValidForm()
    viewModel.onDescriptionChange(description(CreateTeamViewModel.DESCRIPTION_MAX_LENGTH + 1))
    submit()

    assertEquals(TeamFormError.DESCRIPTION_TOO_LONG, state.errorFor(TeamFormField.DESCRIPTION))
    assertEquals(emptyList<Any>(), teams.teams)
  }

  /** Padding is trimmed away before the limit is applied, so it cannot push an entry over it. */
  @Test
  fun submit_withSurroundingWhitespace_doesNotCountItTowardTheLimits() = runTest {
    fillValidForm()
    viewModel.onNameChange("   " + name(CreateTeamViewModel.NAME_MAX_LENGTH) + "   ")
    viewModel.onDescriptionChange(
        "   " + description(CreateTeamViewModel.DESCRIPTION_MAX_LENGTH) + "   "
    )
    submit()

    assertEquals(emptySet<TeamFormError>(), state.errors)
    val created = teams.teams.single()
    assertEquals(name(CreateTeamViewModel.NAME_MAX_LENGTH), created.name)
    assertEquals(description(CreateTeamViewModel.DESCRIPTION_MAX_LENGTH), created.description)
  }

  @Test
  fun afterAFailedSubmit_shorteningAnOverlongFieldClearsItsError() = runTest {
    fillValidForm()
    viewModel.onNameChange(name(CreateTeamViewModel.NAME_MAX_LENGTH + 1))
    viewModel.onDescriptionChange(description(CreateTeamViewModel.DESCRIPTION_MAX_LENGTH + 1))
    submit()
    assertEquals(
        setOf(TeamFormError.NAME_TOO_LONG, TeamFormError.DESCRIPTION_TOO_LONG),
        state.errors,
    )

    viewModel.onNameChange(name(CreateTeamViewModel.NAME_MAX_LENGTH))
    assertEquals(setOf(TeamFormError.DESCRIPTION_TOO_LONG), state.errors)

    viewModel.onDescriptionChange(description(CreateTeamViewModel.DESCRIPTION_MAX_LENGTH))
    assertEquals(emptySet<TeamFormError>(), state.errors)
  }

  @Test
  fun createTeam_persistsTheEventTheTrimmedTextsTheIconAndTheClock() = runTest {
    viewModel.onNameChange("  Logistics  ")
    viewModel.onIconChange("kitchen")
    viewModel.onDescriptionChange("  Move the equipment around the site  ")
    submit()

    val created = teams.teams.single()
    assertEquals(eventId, created.eventId)
    assertEquals("Logistics", created.name)
    assertEquals("Move the equipment around the site", created.description)
    assertEquals("kitchen", created.icon)
    assertEquals(now, created.createdAt)
  }

  /**
   * Appointing a manager, assigning volunteers, sizing the staffing need and placing the check-in
   * zone all happen afterwards, from the team itself.
   */
  @Test
  fun createTeam_leavesEverythingConfiguredLaterUnset() = runTest {
    fillValidForm()
    submit()

    val created = teams.teams.single()
    assertNull(created.managerId)
    assertEquals(emptyList<String>(), created.memberIds)
    assertNull(created.volunteersNeeded)
  }

  @Test
  fun createTeam_exposesTheTeamWithTheIdTheBackendAssigned() = runTest {
    fillValidForm()
    submit()

    assertEquals("team-1", state.createdTeam?.id)
    assertEquals(teams.teams.single(), state.createdTeam)
    assertFalse(state.isSaving)
    assertFalse(state.saveFailed)
    // The team exists: the form is done, and waits to be taken away rather than edited.
    assertFalse(state.isEditable)
  }

  @Test
  fun createTeam_whileSaving_doesNotCreateTheTeamTwice() = runTest {
    fillValidForm()

    viewModel.createTeam()
    assertTrue(state.isSaving)
    assertFalse(state.canSubmit)
    viewModel.createTeam()
    advanceUntilIdle()

    assertEquals(1, teams.teams.size)
  }

  @Test
  fun createTeam_afterItSucceeded_doesNotCreateTheTeamTwice() = runTest {
    fillValidForm()
    submit()

    submit()

    assertEquals(1, teams.teams.size)
    assertEquals("team-1", state.createdTeam?.id)
  }

  @Test
  fun createTeam_whenTheRepositoryFails_reportsItAndCanBeRetried() = runTest {
    fillValidForm()
    teams.failure = IllegalStateException("offline")
    submit()

    assertTrue(state.saveFailed)
    assertFalse(state.isSaving)
    assertNull(state.createdTeam)
    assertTrue(state.canSubmit)
    assertTrue(state.isEditable)

    teams.failure = null
    submit()

    assertFalse(state.saveFailed)
    assertEquals("team-1", state.createdTeam?.id)
  }

  @Test
  fun editingAfterAFailure_clearsTheError() = runTest {
    fillValidForm()
    teams.failure = IllegalStateException("offline")
    submit()
    assertTrue(state.saveFailed)

    viewModel.onNameChange("Logistics team")

    assertFalse(state.saveFailed)
  }

  @Test
  fun createTeam_withAnInvalidForm_neverReachesTheRepository() = runTest {
    teams.failure = IllegalStateException("the repository must not be called")

    submit()

    assertEquals(emptyList<Any>(), teams.teams)
    assertFalse(state.saveFailed)
    assertTrue(state.errors.isNotEmpty())
  }

  @Test
  fun onTeamCreatedHandled_marksTheCreationAsHandledAndKeepsTheTeam() = runTest {
    fillValidForm()
    submit()

    viewModel.onTeamCreatedHandled()

    assertTrue(state.createdTeamHandled)
    assertEquals("team-1", state.createdTeam?.id)
  }

  @Test
  fun factory_createsAViewModelForTheEventWithTheSuppliedDependencies() = runTest {
    viewModel =
        CreateTeamViewModel.factory(eventId, teams, clock)
            .create(CreateTeamViewModel::class.java, CreationExtras.Empty)

    fillValidForm()
    submit()

    val created = teams.teams.single()
    assertEquals(eventId, created.eventId)
    assertEquals(now, created.createdAt)
  }

  // ── Editing a team (#226) ──

  private val logistics =
      Team(
          id = "logistics",
          eventId = eventId,
          name = "Logistics",
          description = "Move the equipment around the site",
          icon = "truck",
          managerId = "manager",
          memberIds = listOf("volunteer"),
          createdAt = now,
      )

  /** Edits [logistics], which the backend already holds. */
  private fun editLogistics() {
    teams.teams += logistics
    viewModel = CreateTeamViewModel(eventId, teams, clock, logistics)
  }

  /** Saves and lets the write complete. */
  private fun TestScope.save() {
    viewModel.saveChanges()
    advanceUntilIdle()
  }

  /** Every failed save reported from now on, as the screen collects them. */
  private fun TestScope.saveFailures(): List<Unit> {
    val failures = mutableListOf<Unit>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      viewModel.saveFailures.toList(failures)
    }
    return failures
  }

  @Test
  fun saveChanges_withInvalidName_savesValidDescriptionAndIcon() = runTest {
    editLogistics()
    viewModel.onNameChange("")
    viewModel.onDescriptionChange("Bring the water")
    viewModel.onIconChange("wine")
    save()

    assertEquals(
        logistics.copy(description = "Bring the water", icon = "wine"),
        teams.teams.single(),
    )
    assertEquals(TeamFormError.NAME_EMPTY, state.errorFor(TeamFormField.NAME))
  }

  @Test
  fun saveChanges_withInvalidDescriptionAndIcon_savesValidName() = runTest {
    editLogistics()
    viewModel.onDescriptionChange("")
    viewModel.onIconChange("")
    viewModel.onNameChange("Supply")
    save()

    assertEquals(logistics.copy(name = "Supply"), teams.teams.single())
  }

  @Test
  fun saveChanges_withOverlappingWrites_waitsBeforeStartingTheNext() = runTest {
    checkOverlappingSaves(firstFails = false, secondFails = false)
  }

  @Test
  fun saveChanges_withFirstWriteFailing_stillSavesTheLatestSnapshot() = runTest {
    checkOverlappingSaves(firstFails = true, secondFails = false)
  }

  @Test
  fun saveChanges_withSecondWriteFailing_retriesFromTheConfirmedState() = runTest {
    checkOverlappingSaves(firstFails = false, secondFails = true)
  }

  private suspend fun TestScope.checkOverlappingSaves(firstFails: Boolean, secondFails: Boolean) {
    teams.teams += logistics
    val gates = List(2) { CompletableDeferred<Unit>() }
    val started = mutableListOf<String>()
    val repository =
        object : TeamRepository by teams {
          override suspend fun updateTeam(
              eventId: String,
              teamId: String,
              name: String,
              description: String,
              icon: String,
          ) {
            val index = started.size
            started += name
            if (index < gates.size) gates[index].await()
            if ((index == 0 && firstFails) || (index == 1 && secondFails)) error("denied")
            teams.updateTeam(eventId, teamId, name, description, icon)
          }
        }
    viewModel = CreateTeamViewModel(eventId, repository, clock, logistics)
    val failures = saveFailures()
    viewModel.onNameChange("Supply")
    viewModel.saveChanges()
    runCurrent()
    viewModel.onNameChange("Bar")
    viewModel.saveChanges()
    runCurrent()
    // Even if the second response is ready first, its request must wait for the first write.
    gates[1].complete(Unit)
    runCurrent()
    assertEquals(listOf("Supply"), started)
    gates[0].complete(Unit)
    advanceUntilIdle()
    assertEquals(listOf("Supply", "Bar"), started)
    assertEquals(if (secondFails) "Supply" else "Bar", teams.teams.single().name)
    assertEquals(if (firstFails || secondFails) listOf(Unit) else emptyList<Unit>(), failures)
    if (secondFails) {
      save()
      assertEquals("Bar", teams.teams.single().name)
    }
    // A return to an older value must be written, rather than skipped after late confirmation.
    viewModel.onNameChange("Logistics")
    save()
    assertEquals("Logistics", teams.teams.single().name)
  }

  @Test
  fun editing_opensOnTheTeamWithoutWaitingForASubmitToShowErrors() {
    editLogistics()

    assertEquals("Logistics", state.name)
    assertEquals("truck", state.icon)
    assertEquals("Move the equipment around the site", state.description)
    assertTrue(state.isEditable)
    // There is no submit, so an error shows as soon as it appears.
    viewModel.onNameChange(" ")
    assertEquals(TeamFormError.NAME_EMPTY, state.errorFor(TeamFormField.NAME))
  }

  @Test
  fun saveChanges_writesTheTrimmedFieldsAndKeepsTheRest() = runTest {
    editLogistics()

    val failures = saveFailures()

    viewModel.onNameChange("  Supply ")
    viewModel.onIconChange("wine")
    save()

    assertEquals(
        listOf(logistics.copy(name = "Supply", icon = "wine")),
        teams.teams,
    )
    assertEquals(emptyList<Unit>(), failures)
  }

  @Test
  fun saveChanges_withNothingChanged_writesNothing() = runTest {
    editLogistics()
    val failures = saveFailures()
    teams.failure = IllegalStateException("must not be called")

    viewModel.onNameChange("Logistics ")
    save()

    assertEquals(emptyList<Unit>(), failures)
  }

  @Test
  fun saveChanges_withAnInvalidField_keepsTheSavedTeam() = runTest {
    editLogistics()

    viewModel.onNameChange("")
    save()
    viewModel.onNameChange(name(CreateTeamViewModel.NAME_MAX_LENGTH + 1))
    save()

    assertEquals(listOf(logistics), teams.teams)
    assertEquals(TeamFormError.NAME_TOO_LONG, state.errorFor(TeamFormField.NAME))
  }

  @Test
  fun saveChanges_thatFails_isReportedThenTriedAgain() = runTest {
    editLogistics()
    val failures = saveFailures()
    teams.failure = IllegalStateException("denied")

    viewModel.onDescriptionChange("Bring the water")
    save()
    // Typing on does not take the failure back.
    viewModel.onDescriptionChange("Bring the water around")

    assertEquals(listOf(Unit), failures)
    // The change was not saved, so the next save sends it again.
    teams.failure = null
    save()
    assertEquals("Bring the water around", teams.teams.single().description)
  }

  @Test
  fun saveChanges_twoFailingInARow_goBackToWhatTheBackendHolds() = runTest {
    editLogistics()
    teams.failure = IllegalStateException("denied")

    viewModel.onNameChange("Supply")
    viewModel.saveChanges()
    viewModel.onNameChange("Bar")
    viewModel.saveChanges()
    advanceUntilIdle()
    // Back to the first value, which the backend never took: it is sent again.
    teams.failure = null
    viewModel.onNameChange("Supply")
    save()

    assertEquals("Supply", teams.teams.single().name)
  }

  @Test
  fun saveChanges_onANewTeam_doesNothing() = runTest {
    fillValidForm()

    save()

    assertEquals(emptyList<Team>(), teams.teams)
  }
}
