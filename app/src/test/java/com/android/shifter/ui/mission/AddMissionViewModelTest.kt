// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.mission

import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
class AddMissionViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private val zone = ZoneId.of("Europe/Zurich")
  /** 1 June 2027, 12:00 in Zurich. */
  private val now = Instant.parse("2027-06-01T10:00:00Z")
  private val clock = Clock.fixed(now, zone)

  private val eventDay = LocalDate.of(2027, 6, 20)
  private val teams = listOf(TeamOption("logistics", "Logistics"), TeamOption("bar", "Bar"))

  /** Local Food Drive, 20 June 2027 from 10:00 to 18:00 in Zurich. */
  private val event =
      Event(
          id = "event-1",
          organizerId = "organizer-1",
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
  private lateinit var viewModel: AddMissionViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    missions = FakeMissionRepository()
    events = StubEventRepository(event)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** Creates the ViewModel and lets it load the event. */
  private fun TestScope.openForm(eventId: String = event.id) {
    viewModel = AddMissionViewModel(eventId, missions, events, teams, clock)
    advanceUntilIdle()
  }

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
    viewModel = AddMissionViewModel(event.id, missions, events, teams, clock)

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
  fun addMission_withEndNotAfterStart_reportsIt() = runTest {
    openForm()
    fillValidForm()
    viewModel.onEndTimeChange(LocalTime.of(14, 0))

    viewModel.addMission()

    assertEquals(setOf(MissionFormError.END_NOT_AFTER_START), state.errors)
    assertEquals(MissionFormError.END_NOT_AFTER_START, state.errorFor(MissionFormField.SCHEDULE))
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

  @Test
  fun eventPeriod_containsOnlySlotsWithinIt() {
    val period =
        EventPeriod(
            title = "Festival",
            start = LocalDateTime.of(2027, 7, 21, 18, 0),
            end = LocalDateTime.of(2027, 7, 22, 2, 0),
        )

    assertTrue(period.contains(LocalDate.of(2027, 7, 21), LocalTime.of(18, 0), LocalTime.of(23, 0)))
    assertTrue(period.contains(LocalDate.of(2027, 7, 22), LocalTime.of(0, 30), LocalTime.of(2, 0)))
    assertFalse(
        period.contains(LocalDate.of(2027, 7, 21), LocalTime.of(17, 0), LocalTime.of(19, 0))
    )
    assertFalse(period.contains(LocalDate.of(2027, 7, 22), LocalTime.of(1, 0), LocalTime.of(3, 0)))
  }
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

  override suspend fun getEventsByMember(userId: String): List<Event> =
      throw UnsupportedOperationException()
}
