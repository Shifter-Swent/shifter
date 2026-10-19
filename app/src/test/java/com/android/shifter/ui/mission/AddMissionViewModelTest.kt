// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.mission

import androidx.lifecycle.viewmodel.CreationExtras
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionLocation
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
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
class AddMissionViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private val zone = ZoneId.of("Europe/Zurich")
  /** 1 June 2027, 12:00 in Zurich. */
  private val now = Instant.parse("2027-06-01T10:00:00Z")
  private val clock = Clock.fixed(now, zone)

  private val eventDay = LocalDate.of(2027, 6, 20)
  private val organizerId = "organizer-1"
  private val managerId = "manager-1"

  /** The teams of the event, as the organizer sees them. Only Logistics has [managerId]. */
  private val teams = listOf(TeamOption("logistics", "Logistics"), TeamOption("bar", "Bar"))

  /** Local Food Drive, 20 June 2027 from 10:00 to 18:00 in Zurich. */
  private val event =
      Event(
          id = "event-1",
          organizerId = organizerId,
          title = "Local Food Drive",
          description = "Collect food for the local food bank",
          type = EventType.FOOD,
          startAt = eventDay.atTime(10, 0).atZone(zone).toInstant(),
          endAt = eventDay.atTime(18, 0).atZone(zone).toInstant(),
          location = EventLocation(address = "Grand Place, Lille"),
          createdAt = now,
      )

  private lateinit var missions: FakeMissionRepository
  private lateinit var events: StubEventRepository
  private lateinit var teamRepository: FakeTeamRepository
  private lateinit var viewModel: AddMissionViewModel

  /** Who is signed in; the organizer unless a test says otherwise. */
  private var userId: String? = organizerId

  /** Who may be assigned, by team id, null standing for "General". */
  private val candidatesByTeam =
      mapOf(
          "logistics" to
              listOf(
                  AssigneeOption("ana", "Ana"),
                  AssigneeOption("ben", "Ben"),
                  AssigneeOption("cleo", "Cléo"),
              ),
          "bar" to listOf(AssigneeOption("dan", "Dan")),
          null to listOf(AssigneeOption("eva", "Eva")),
      )

  /** Thrown by every load of the candidates when set. */
  private var candidatesFailure: Exception? = null

  /** When set, the candidates of the "logistics" team only arrive once it completes. */
  private var logisticsAnswer: CompletableDeferred<List<AssigneeOption>>? = null

  private val candidates = AssigneeCandidates { _, teamId ->
    candidatesFailure?.let { throw it }
    logisticsAnswer
        ?.takeIf { teamId == "logistics" }
        ?.let {
          return@AssigneeCandidates it.await()
        }
    candidatesByTeam[teamId].orEmpty()
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    missions = FakeMissionRepository()
    events = StubEventRepository(event)
    teamRepository =
        FakeTeamRepository(
            listOf(
                team("logistics", "Logistics", event.id, managerId = managerId),
                team("bar", "Bar", event.id),
                // Managed by the same manager, but in another event.
                team("stage", "Stage", "event-2", managerId = managerId),
            )
        )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** Creates the ViewModel and lets it load the event. */
  private fun TestScope.openForm(eventId: String = event.id) {
    viewModel = newViewModel(eventId)
    advanceUntilIdle()
  }

  private fun newViewModel(eventId: String = event.id) =
      AddMissionViewModel(eventId, missions, events, teamRepository, candidates, { userId }, clock)

  private fun team(id: String, name: String, eventId: String, managerId: String? = null) =
      Team(
          id = id,
          eventId = eventId,
          name = name,
          description = "",
          icon = "",
          managerId = managerId,
          createdAt = now,
      )

  private fun fillValidForm() {
    viewModel.onTitleChange("  Sort the food donations ")
    viewModel.onDescriptionChange("Sort the products and check the expiry dates. ")
    viewModel.onTeamChange("logistics")
    repeat(2) { viewModel.onIncreaseVolunteers() }
    viewModel.onStartTimeChange(LocalTime.of(14, 0))
    viewModel.onEndTimeChange(LocalTime.of(15, 30))
  }

  private val state
    get() = viewModel.uiState.value

  @Test
  fun loadingTheEvent_exposesItsPeriodAndPreselectsItsDay() = runTest {
    openForm()

    assertEquals(
        EventPeriod(
            title = "Local Food Drive",
            start = LocalDateTime.of(eventDay, LocalTime.of(10, 0)),
            end = LocalDateTime.of(eventDay, LocalTime.of(18, 0)),
        ),
        state.event,
    )
    assertFalse(state.isLoadingEvent)
    assertFalse(state.loadFailed)
    assertEquals(eventDay, state.day)
    assertEquals(teams, state.teams)
    assertTrue(state.canSubmit)
  }

  @Test
  fun beforeTheEventLoads_theFormCannotBeSubmitted() = runTest {
    viewModel = newViewModel()

    assertTrue(state.isLoadingEvent)
    assertFalse(state.canSubmit)
    viewModel.addMission()
    advanceUntilIdle()

    assertTrue(missions.created.isEmpty())
    assertTrue(state.errors.isEmpty())
  }

  @Test
  fun anUnknownEvent_failsToLoad() = runTest {
    openForm(eventId = "missing")

    assertNull(state.event)
    assertTrue(state.loadFailed)
    assertFalse(state.canSubmit)
  }

  @Test
  fun aFailingRepository_failsToLoad_andRetryingRecovers() = runTest {
    events.failure = IllegalStateException("offline")
    openForm()
    assertTrue(state.loadFailed)

    events.failure = null
    viewModel.loadEvent()
    advanceUntilIdle()

    assertFalse(state.loadFailed)
    assertEquals("Local Food Drive", state.event?.title)
  }

  @Test
  fun editingFields_updatesStateWithoutValidating() = runTest {
    openForm()

    viewModel.onTitleChange("A")
    viewModel.onDescriptionChange("B")
    viewModel.onDayChange(eventDay)
    viewModel.onStartTimeChange(LocalTime.of(20, 0))

    assertEquals("A", state.title)
    assertEquals("B", state.description)
    assertEquals(LocalTime.of(20, 0), state.startTime)
    assertTrue(state.errors.isEmpty())
  }

  @Test
  fun teams_canBeSelectedAndResetToGeneral_butAnUnknownOneIsIgnored() = runTest {
    openForm()
    assertNull(state.teamId)

    viewModel.onTeamChange("bar")
    assertEquals("bar", state.teamId)

    viewModel.onTeamChange("unknown")
    assertEquals("bar", state.teamId)

    viewModel.onTeamChange(null)
    assertNull(state.teamId)
  }

  @Test
  fun theOrganizer_choosesAmongEveryTeamOfTheEventOrGeneral() = runTest {
    openForm()

    assertEquals(teams, state.teams)
    assertTrue(state.canChooseGeneral)
    assertNull(state.teamId)
    assertFalse(state.notAllowed)
  }

  @Test
  fun aManager_choosesOnlyAmongTheTeamsTheyManage_withOneSelected() = runTest {
    userId = managerId
    openForm()

    assertEquals(listOf(TeamOption("logistics", "Logistics")), state.teams)
    assertFalse(state.canChooseGeneral)
    assertEquals("logistics", state.teamId)
    assertFalse(state.notAllowed)
    assertTrue(state.canSubmit)
  }

  @Test
  fun aManager_cannotSwitchToGeneralOrToATeamTheyDoNotManage() = runTest {
    userId = managerId
    openForm()

    viewModel.onTeamChange(null)
    viewModel.onTeamChange("bar")

    assertEquals("logistics", state.teamId)
  }

  @Test
  fun aManager_createsTheMissionForTheirTeam() = runTest {
    userId = managerId
    openForm()
    viewModel.onTitleChange("Unload the truck")
    viewModel.onDescriptionChange("Bring the crates to the storage room.")
    viewModel.onStartTimeChange(LocalTime.of(11, 0))
    viewModel.onEndTimeChange(LocalTime.of(12, 0))

    viewModel.addMission()
    advanceUntilIdle()

    assertEquals("logistics", missions.created.single().teamId)
  }

  @Test
  fun aUserWhoIsNeitherOrganizerNorManager_isNotAllowed() = runTest {
    userId = "volunteer-1"
    openForm()

    assertTrue(state.notAllowed)
    assertFalse(state.loadFailed)
    assertTrue(state.teams.isEmpty())
    assertFalse(state.canSubmit)
    viewModel.onTitleChange("Sneaky")
    viewModel.addMission()
    advanceUntilIdle()
    assertTrue(missions.created.isEmpty())
  }

  @Test
  fun nobodySignedIn_isNotAllowed() = runTest {
    userId = null
    openForm()

    assertTrue(state.notAllowed)
    assertFalse(state.canSubmit)
  }

  @Test
  fun failingToLoadTheTeams_failsToLoad_andRetryingRecovers() = runTest {
    teamRepository.failure = IllegalStateException("offline")
    openForm()
    assertTrue(state.loadFailed)
    assertFalse(state.canSubmit)

    teamRepository.failure = null
    viewModel.loadEvent()
    advanceUntilIdle()

    assertFalse(state.loadFailed)
    assertEquals(teams, state.teams)
  }

  @Test
  fun retrying_keepsTheSelectedTeamWhenItIsStillOffered() = runTest {
    openForm()
    viewModel.onTeamChange("bar")

    viewModel.loadEvent()
    advanceUntilIdle()

    assertEquals("bar", state.teamId)
  }

  @Test
  fun volunteersNeeded_startsAtOneAndNeverGoesBelow() = runTest {
    openForm()
    assertEquals(1, state.volunteersNeeded)

    viewModel.onDecreaseVolunteers()
    assertEquals(1, state.volunteersNeeded)

    viewModel.onIncreaseVolunteers()
    viewModel.onIncreaseVolunteers()
    assertEquals(3, state.volunteersNeeded)

    viewModel.onDecreaseVolunteers()
    assertEquals(2, state.volunteersNeeded)
  }

  @Test
  fun addMission_withEmptyForm_reportsEveryMissingField() = runTest {
    openForm()

    viewModel.addMission()

    assertEquals(
        setOf(
            MissionFormError.TITLE_EMPTY,
            MissionFormError.DESCRIPTION_EMPTY,
            MissionFormError.START_MISSING,
        ),
        state.errors,
    )
    assertTrue(missions.created.isEmpty())
  }

  @Test
  fun addMission_withoutEndTime_reportsIt() = runTest {
    openForm()
    viewModel.onTitleChange("Sort")
    viewModel.onDescriptionChange("Sort the products")
    viewModel.onStartTimeChange(LocalTime.of(14, 0))

    viewModel.addMission()

    assertEquals(setOf(MissionFormError.END_MISSING), state.errors)
  }

  @Test
  fun addMission_endingWhenItStarts_reportsIt() = runTest {
    openForm()
    fillValidForm()
    viewModel.onEndTimeChange(LocalTime.of(14, 0))

    viewModel.addMission()

    assertEquals(setOf(MissionFormError.END_NOT_AFTER_START), state.errors)
    assertEquals(MissionFormError.END_NOT_AFTER_START, state.errorFor(MissionFormField.SCHEDULE))
  }

  @Test
  fun addMission_endingBeforeItStarts_endsTheNextDayWithinTheEvent() = runTest {
    openForm()
    fillValidForm()
    viewModel.onStartTimeChange(LocalTime.of(17, 0))
    viewModel.onEndTimeChange(LocalTime.of(9, 0))

    assertTrue(state.endsNextDay)
    viewModel.addMission()

    // 17:00 to 09:00 the next morning overflows an event ending at 18:00 the same day.
    assertEquals(setOf(MissionFormError.OUTSIDE_EVENT), state.errors)
    assertTrue(missions.created.isEmpty())
  }

  @Test
  fun addMission_crossingMidnightWithinANightEvent_createsTheMissionUntilTheNextDay() = runTest {
    // A festival night, 21 June 18:00 to 22 June 03:00.
    events =
        StubEventRepository(
            event.copy(
                startAt = eventDay.plusDays(1).atTime(18, 0).atZone(zone).toInstant(),
                endAt = eventDay.plusDays(2).atTime(3, 0).atZone(zone).toInstant(),
            )
        )
    openForm()
    fillValidForm()
    viewModel.onStartTimeChange(LocalTime.of(22, 0))
    viewModel.onEndTimeChange(LocalTime.of(2, 0))

    viewModel.addMission()
    advanceUntilIdle()

    assertTrue(state.errors.isEmpty())
    val mission = missions.created.single()
    assertEquals(eventDay.plusDays(1).atTime(22, 0).atZone(zone).toInstant(), mission.startAt)
    assertEquals(eventDay.plusDays(2).atTime(2, 0).atZone(zone).toInstant(), mission.endAt)
  }

  @Test
  fun endsNextDay_isFalseUntilBothTimesAreSetAndTheEndIsEarlier() = runTest {
    openForm()
    assertFalse(state.endsNextDay)

    viewModel.onStartTimeChange(LocalTime.of(22, 0))
    assertFalse(state.endsNextDay)

    viewModel.onEndTimeChange(LocalTime.of(23, 0))
    assertFalse(state.endsNextDay)

    viewModel.onEndTimeChange(LocalTime.of(1, 0))
    assertTrue(state.endsNextDay)
  }

  @Test
  fun addMission_withTooLongTitleAndDescription_reportsThem() = runTest {
    openForm()
    fillValidForm()
    // Surrounding blanks are trimmed before counting, so they alone cannot make a text too long.
    viewModel.onTitleChange(" " + "a".repeat(AddMissionUiState.TITLE_MAX_LENGTH) + " ")
    viewModel.onDescriptionChange("b".repeat(AddMissionUiState.DESCRIPTION_MAX_LENGTH))

    viewModel.addMission()
    advanceUntilIdle()
    assertTrue(state.errors.isEmpty())

    openForm()
    fillValidForm()
    viewModel.onTitleChange("a".repeat(AddMissionUiState.TITLE_MAX_LENGTH + 1))
    viewModel.onDescriptionChange("b".repeat(AddMissionUiState.DESCRIPTION_MAX_LENGTH + 1))

    viewModel.addMission()

    assertEquals(
        setOf(MissionFormError.TITLE_TOO_LONG, MissionFormError.DESCRIPTION_TOO_LONG),
        state.errors,
    )
  }

  @Test
  fun addMission_outsideTheEventHours_reportsIt() = runTest {
    openForm()
    fillValidForm()
    viewModel.onStartTimeChange(LocalTime.of(17, 0))
    viewModel.onEndTimeChange(LocalTime.of(18, 30))

    viewModel.addMission()

    assertEquals(setOf(MissionFormError.OUTSIDE_EVENT), state.errors)
  }

  @Test
  fun addMission_onAnotherDay_reportsIt() = runTest {
    openForm()
    fillValidForm()
    viewModel.onDayChange(eventDay.plusDays(1))

    viewModel.addMission()

    assertEquals(setOf(MissionFormError.OUTSIDE_EVENT), state.errors)
  }

  @Test
  fun addMission_exactlyOnTheEventBounds_isAccepted() = runTest {
    openForm()
    fillValidForm()
    viewModel.onStartTimeChange(LocalTime.of(10, 0))
    viewModel.onEndTimeChange(LocalTime.of(18, 0))

    viewModel.addMission()
    advanceUntilIdle()

    assertTrue(state.errors.isEmpty())
    assertEquals(1, missions.created.size)
  }

  @Test
  fun afterAFailedSubmit_errorsFollowEveryEdit() = runTest {
    openForm()
    viewModel.addMission()
    assertTrue(MissionFormError.TITLE_EMPTY in state.errors)

    viewModel.onTitleChange("Sort")

    assertFalse(MissionFormError.TITLE_EMPTY in state.errors)
    assertTrue(MissionFormError.DESCRIPTION_EMPTY in state.errors)
  }

  @Test
  fun addMission_withValidForm_createsTheMission() = runTest {
    openForm()
    fillValidForm()

    viewModel.addMission()
    assertTrue(state.isSaving)
    advanceUntilIdle()

    val expected =
        Mission(
            id = "mission-1",
            eventId = "event-1",
            title = "Sort the food donations",
            description = "Sort the products and check the expiry dates.",
            teamId = "logistics",
            volunteersNeeded = 3,
            startAt = eventDay.atTime(14, 0).atZone(zone).toInstant(),
            endAt = eventDay.atTime(15, 30).atZone(zone).toInstant(),
            createdAt = now,
        )
    assertEquals(listOf(expected), missions.created)
    assertEquals(expected, state.createdMission)
    assertFalse(state.isSaving)
    assertFalse(state.saveFailed)
  }

  @Test
  fun addMission_withGeneral_createsAMissionWithoutTeam() = runTest {
    openForm()
    fillValidForm()
    viewModel.onTeamChange(null)

    viewModel.addMission()
    advanceUntilIdle()

    assertNull(missions.created.single().teamId)
  }

  @Test
  fun addMission_isIgnoredWhileSaving() = runTest {
    openForm()
    fillValidForm()

    viewModel.addMission()
    viewModel.addMission()
    advanceUntilIdle()

    assertEquals(1, missions.created.size)
  }

  @Test
  fun addMission_isIgnoredAfterSuccess() = runTest {
    openForm()
    fillValidForm()

    viewModel.addMission()
    advanceUntilIdle()
    assertFalse(state.canSubmit)
    // A second tap before the screen is left must not create the mission twice.
    viewModel.addMission()
    advanceUntilIdle()

    assertEquals(1, missions.created.size)
  }

  @Test
  fun onMissionAddedHandled_marksTheCreatedMissionAsHandled() = runTest {
    openForm()
    fillValidForm()
    viewModel.addMission()
    advanceUntilIdle()
    assertFalse(state.createdMissionHandled)

    viewModel.onMissionAddedHandled()

    assertTrue(state.createdMissionHandled)
  }

  @Test
  fun openingTheForm_loadsWhoMayBeAssignedToTheSelectedTeam() = runTest {
    openForm()

    // The organizer starts on "General".
    assertEquals(listOf(AssigneeOption("eva", "Eva")), state.assignees.candidates)
    assertFalse(state.assignees.isLoading)

    userId = managerId
    openForm()

    // A manager starts on the team they manage.
    assertEquals(listOf("ana", "ben", "cleo"), state.assignees.candidates.map { it.id })
  }

  @Test
  fun changingTheTeam_clearsTheSelectionAndLoadsItsCandidates() = runTest {
    openForm()
    viewModel.onTeamChange("logistics")
    advanceUntilIdle()
    viewModel.onToggleAssignee("ana")
    assertEquals(listOf("ana"), state.assignees.selectedIds)

    viewModel.onTeamChange("bar")
    assertTrue("the new team's candidates are loading", state.assignees.isLoading)
    advanceUntilIdle()

    assertEquals(emptyList<String>(), state.assignees.selectedIds)
    assertEquals(listOf(AssigneeOption("dan", "Dan")), state.assignees.candidates)
  }

  @Test
  fun onTeamChange_beforeThePreviousTeamLoads_ignoresItsAnswer() = runTest {
    val answer = CompletableDeferred<List<AssigneeOption>>()
    logisticsAnswer = answer
    openForm()

    viewModel.onTeamChange("logistics")
    viewModel.onTeamChange("bar")
    advanceUntilIdle()
    answer.complete(listOf(AssigneeOption("ana", "Ana")))
    advanceUntilIdle()

    assertEquals(listOf(AssigneeOption("dan", "Dan")), state.assignees.candidates)
  }

  @Test
  fun selectingTheSameTeamAgain_keepsTheSelection() = runTest {
    openForm()
    viewModel.onTeamChange("logistics")
    advanceUntilIdle()
    viewModel.onToggleAssignee("ana")

    viewModel.onTeamChange("logistics")
    advanceUntilIdle()

    assertEquals(listOf("ana"), state.assignees.selectedIds)
  }

  @Test
  fun toggleAssignee_picksNoMorePeopleThanNeeded() = runTest {
    openForm()
    viewModel.onTeamChange("logistics")
    advanceUntilIdle()

    viewModel.onToggleAssignee("ana")
    viewModel.onToggleAssignee("ben")
    assertEquals("one person is needed", listOf("ana"), state.assignees.selectedIds)

    viewModel.onIncreaseVolunteers()
    viewModel.onToggleAssignee("ben")
    viewModel.onToggleAssignee("ana")
    assertEquals(listOf("ben"), state.assignees.selectedIds)
  }

  @Test
  fun decreaseVolunteers_neverGoesBelowThePeopleAssigned() = runTest {
    openForm()
    viewModel.onTeamChange("logistics")
    advanceUntilIdle()
    repeat(2) { viewModel.onIncreaseVolunteers() }
    viewModel.onToggleAssignee("ana")
    viewModel.onToggleAssignee("ben")

    repeat(3) { viewModel.onDecreaseVolunteers() }

    assertEquals(2, state.volunteersNeeded)
    assertEquals(2, state.minVolunteers)
  }

  @Test
  fun addMission_savesThePeoplePicked() = runTest {
    openForm()
    fillValidForm()
    advanceUntilIdle()
    viewModel.onToggleAssignee("cleo")
    viewModel.onToggleAssignee("ana")

    viewModel.addMission()
    advanceUntilIdle()

    assertEquals(listOf("cleo", "ana"), missions.created.single().assigneeIds)
  }

  @Test
  fun addMission_withNobodyPicked_savesAMissionWithoutAssignees() = runTest {
    openForm()
    fillValidForm()
    advanceUntilIdle()

    viewModel.addMission()
    advanceUntilIdle()

    assertEquals(emptyList<String>(), missions.created.single().assigneeIds)
  }

  @Test
  fun candidatesFailure_isReportedAndCanBeRetried() = runTest {
    candidatesFailure = IllegalStateException("offline")
    openForm()

    assertTrue(state.assignees.loadFailed)
    assertFalse(state.assignees.isLoading)

    candidatesFailure = null
    viewModel.retryLoadAssignees()
    advanceUntilIdle()

    assertFalse(state.assignees.loadFailed)
    assertEquals(listOf(AssigneeOption("eva", "Eva")), state.assignees.candidates)
  }

  @Test
  fun factory_createsAViewModelForTheEvent() = runTest {
    viewModel =
        AddMissionViewModel.factory(
                event.id,
                missions,
                events,
                teamRepository,
                candidates,
                { userId },
                clock,
            )
            .create(AddMissionViewModel::class.java, CreationExtras.Empty)
    advanceUntilIdle()

    assertEquals("Local Food Drive", state.event?.title)
    assertEquals(teams, state.teams)
  }

  @Test
  fun addMission_whenTheRepositoryFails_reportsItAndClearsOnEdit() = runTest {
    openForm()
    fillValidForm()
    missions.failure = IllegalStateException("offline")

    viewModel.addMission()
    advanceUntilIdle()

    assertTrue(state.saveFailed)
    assertFalse(state.isSaving)
    assertNull(state.createdMission)

    viewModel.onTitleChange("Sort the donations")
    assertFalse(state.saveFailed)
  }

  private fun fillLocation(address: String, latitude: String, longitude: String) {
    viewModel.onAddressChange(address)
    viewModel.onLatitudeChange(latitude)
    viewModel.onLongitudeChange(longitude)
  }

  @Test
  fun addMission_withoutLocation_createsAMissionThatIsNotGeoTimed() = runTest {
    openForm()
    fillValidForm()

    viewModel.addMission()
    advanceUntilIdle()

    assertNull(missions.created.single().location)
  }

  @Test
  fun addMission_withLocation_createsAGeoTimedMission() = runTest {
    openForm()
    fillValidForm()
    fillLocation("  Grand Place, Lille ", "50.6366", " 3,0635")

    viewModel.addMission()
    advanceUntilIdle()

    assertEquals(
        MissionLocation("Grand Place, Lille", 50.6366, 3.0635),
        missions.created.single().location,
    )
  }

  @Test
  fun addMission_withPartialLocation_reportsEveryMissingPart() = runTest {
    openForm()
    fillValidForm()
    viewModel.onLatitudeChange("50.6")

    viewModel.addMission()
    advanceUntilIdle()

    assertEquals(
        setOf(MissionFormError.ADDRESS_MISSING, MissionFormError.LONGITUDE_INVALID),
        state.errors,
    )
    assertTrue(missions.created.isEmpty())
  }

  @Test
  fun addMission_withInvalidCoordinates_reportsThem() = runTest {
    openForm()
    fillValidForm()
    fillLocation("Grand Place, Lille", "90.1", "north")

    viewModel.addMission()
    advanceUntilIdle()

    assertEquals(
        setOf(MissionFormError.LATITUDE_INVALID, MissionFormError.LONGITUDE_INVALID),
        state.errors,
    )
    assertTrue(missions.created.isEmpty())
  }

  @Test
  fun clearingTheLocation_removesItsErrors() = runTest {
    openForm()
    fillValidForm()
    viewModel.onAddressChange("Grand Place, Lille")
    viewModel.addMission()
    assertTrue(MissionFormError.LATITUDE_INVALID in state.errors)

    viewModel.onAddressChange(" ")

    assertTrue(state.errors.isEmpty())
  }

  @Test
  fun addMission_overlappingAMissionOfTheSameTeam_reportsTheConflictAndClearsOnEdit() = runTest {
    val existing =
        missions.createMission(
            Mission(
                eventId = "event-1",
                title = "Unload the trucks",
                description = "Unload the donations.",
                teamId = "logistics",
                volunteersNeeded = 2,
                startAt = eventDay.atTime(15, 0).atZone(zone).toInstant(),
                endAt = eventDay.atTime(16, 0).atZone(zone).toInstant(),
                location = MissionLocation("Grand Place, Lille", 50.6366, 3.0635),
                createdAt = now,
            )
        )
    openForm()
    fillValidForm()
    fillLocation("Grand Place, Lille", "50.6366", "3.0635")

    viewModel.addMission()
    advanceUntilIdle()

    assertEquals(
        MissionConflict(
            title = "Unload the trucks",
            start = LocalDateTime.of(eventDay, LocalTime.of(15, 0)),
            end = LocalDateTime.of(eventDay, LocalTime.of(16, 0)),
        ),
        state.conflict,
    )
    assertFalse(state.saveFailed)
    assertFalse(state.isSaving)
    assertNull(state.createdMission)
    assertEquals(listOf(existing), missions.created)

    viewModel.onTeamChange("bar")
    assertNull(state.conflict)
    viewModel.addMission()
    advanceUntilIdle()
    assertEquals(2, missions.created.size)
  }

  @Test
  fun parseCoordinate_acceptsAPointOrACommaWithinTheRange() {
    assertEquals(46.5, parseCoordinate(" 46.5 ", -90.0..90.0))
    assertEquals(-6.25, parseCoordinate("-6,25", -90.0..90.0))
    assertNull(parseCoordinate("", -90.0..90.0))
    assertNull(parseCoordinate("abc", -90.0..90.0))
    assertNull(parseCoordinate("-90.5", -90.0..90.0))
  }

  @Test
  fun eventPeriod_containsOnlySlotsWithinIt() {
    val period =
        EventPeriod(
            title = "Festival",
            start = LocalDateTime.of(2027, 7, 21, 18, 0),
            end = LocalDateTime.of(2027, 7, 22, 2, 0),
        )

    assertTrue(period.contains(at(21, 18, 0), at(21, 23, 0)))
    assertTrue(period.contains(at(21, 23, 0), at(22, 2, 0)))
    assertTrue(period.contains(at(22, 0, 30), at(22, 2, 0)))
    assertFalse(period.contains(at(21, 17, 0), at(21, 19, 0)))
    assertFalse(period.contains(at(22, 1, 0), at(22, 3, 0)))
  }

  @Test
  fun missionSlot_putsAnEarlierEndOnTheNextDay() {
    val day = LocalDate.of(2027, 7, 21)

    assertEquals(
        at(21, 14, 0) to at(21, 15, 30),
        missionSlot(day, LocalTime.of(14, 0), LocalTime.of(15, 30)),
    )
    assertEquals(
        at(21, 22, 0) to at(22, 2, 0),
        missionSlot(day, LocalTime.of(22, 0), LocalTime.of(2, 0)),
    )
    // An equal end stays on the same day: validation reports it rather than reading 24 hours.
    assertEquals(
        at(21, 14, 0) to at(21, 14, 0),
        missionSlot(day, LocalTime.of(14, 0), LocalTime.of(14, 0)),
    )
  }

  /** [hour]:[minute] on [dayOfMonth] July 2027. */
  private fun at(dayOfMonth: Int, hour: Int, minute: Int) =
      LocalDateTime.of(2027, 7, dayOfMonth, hour, minute)
}

/** [EventRepository] that only knows [event]; setting [failure] makes every call throw it. */
private class StubEventRepository(private val event: Event) : EventRepository {

  var failure: Exception? = null

  override suspend fun createEvent(event: Event): Event = throw UnsupportedOperationException()

  override suspend fun getEvent(eventId: String): Event? {
    failure?.let { throw it }
    return event.takeIf { it.id == eventId }
  }

  override suspend fun getEventByJoinCode(joinCode: String): Event? =
      throw UnsupportedOperationException()

  override suspend fun getEventsByOrganizer(organizerId: String): List<Event> =
      throw UnsupportedOperationException()
}
