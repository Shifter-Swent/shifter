// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.team

import androidx.lifecycle.viewmodel.CreationExtras
import com.swent.shifter.model.team.FakeTeamRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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

  @Test
  fun submit_withoutAnIcon_reportsItUnderTheIconField() = runTest {
    viewModel.onNameChange("Logistics")
    viewModel.onDescriptionChange("Move the equipment around the site")
    submit()

    assertEquals(TeamFormError.ICON_MISSING, state.errorFor(TeamFormField.ICON))
    assertNull(state.errorFor(TeamFormField.NAME))
    assertNull(state.errorFor(TeamFormField.DESCRIPTION))
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
    assertNull(created.checkInZone)
  }

  @Test
  fun createTeam_exposesTheTeamWithTheIdTheBackendAssigned() = runTest {
    fillValidForm()
    submit()

    assertEquals("team-1", state.createdTeam?.id)
    assertEquals(teams.teams.single(), state.createdTeam)
    assertFalse(state.isSaving)
    assertFalse(state.saveFailed)
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
}
