// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.attendance

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.checkin.CheckIn
import com.swent.shifter.model.checkin.CheckInMethod
import com.swent.shifter.model.checkin.CheckInRepository
import com.swent.shifter.model.checkin.CheckInRepositoryException
import com.swent.shifter.model.checkin.FakeCheckInRepository
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FakeEventRepository
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.User
import com.swent.shifter.model.user.UserRepositoryException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
class AttendanceViewModelTest {

  private val start = Instant.parse("2027-06-20T08:00:00Z")
  private val events = FakeEventRepository()
  private val missions = FakeMissionRepository()
  private val teams = FakeTeamRepository()
  private val users =
      FakeUserRepository(listOf(User("ann", "Ann", "ann@x.org"), User("bob", "Bob", "bob@x.org")))
  private val checkIns = FakeCheckInRepository()

  private lateinit var viewModel: AttendanceViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
    events.events +=
        Event(
            id = "event-1",
            organizerId = "organizer",
            title = "Festival",
            description = "",
            type = EventType.MUSIC,
            startAt = start,
            endAt = start.plus(Duration.ofDays(1)),
            location = EventLocation("Lausanne"),
            createdAt = start,
        )
    // "manager" manages two of the teams, "other-manager" the third.
    teams.teams +=
        listOf(
            team("logistics", "Logistics", "manager"),
            team("bar", "Bar", "manager"),
            team("security", "Security", "other-manager"),
        )
  }

  @After fun tearDown() = Dispatchers.resetMain()

  private fun team(id: String, name: String, managerId: String) =
      Team(
          id = id,
          eventId = "event-1",
          name = name,
          description = "",
          icon = "truck",
          managerId = managerId,
          createdAt = start,
      )

  /** Adds a mission starting [hour] hours after [start], so a test controls the order. */
  private suspend fun addMission(
      title: String,
      teamId: String?,
      hour: Long,
      assigneeIds: List<String> = emptyList(),
      eventId: String = "event-1",
  ) =
      missions.createMission(
          Mission(
              eventId = eventId,
              title = title,
              description = "",
              teamId = teamId,
              volunteersNeeded = 3,
              startAt = start.plus(Duration.ofHours(hour)),
              endAt = start.plus(Duration.ofHours(hour + 1)),
              assigneeIds = assigneeIds,
              createdAt = start,
          )
      )

  /** Records [userId] as having arrived at [missionId], [minute] minutes after [start]. */
  private fun arrive(
      missionId: String,
      userId: String,
      minute: Long = 0,
      eventId: String = "event-1",
  ) =
      checkIns.add(
          CheckIn(
              eventId = eventId,
              missionId = missionId,
              userId = userId,
              checkedInAt = start.plus(Duration.ofMinutes(minute)),
              method = CheckInMethod.SELF,
              checkedInBy = userId,
          )
      )

  /** When a manual check-in is made in the tests below, as the screen's clock reads it. */
  private val tapTime = start.plus(Duration.ofMinutes(65))

  private fun TestScope.open(userId: String) {
    viewModel =
        AttendanceViewModel(
            "event-1",
            userId,
            events,
            missions,
            teams,
            users,
            checkIns,
            now = { tapTime },
        )
    advanceUntilIdle()
  }

  /** The assignee [userId] of the only mission loaded. */
  private fun assignee(userId: String) =
      viewModel.uiState.value.missions.single().assignees.single { it.userId == userId }

  /**
   * Lets a manual check-in wait until it is sent anyway, as when the screen never says its "Undo"
   * is gone. The tests of the screen cover the snackbar ending the window instead.
   */
  private fun TestScope.waitForTheUndoWindow() {
    advanceTimeBy(AttendanceViewModel.UNDO_TIMEOUT_MILLIS + 1)
    runCurrent()
  }

  private fun titles() = viewModel.uiState.value.missions.map { it.title }

  @Test
  fun load_asOrganizer_listsEveryMissionByStartTimeWithItsTeamAndAssignees() = runTest {
    addMission("Serve drinks", "bar", hour = 3)
    addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("bob", "ann"))
    addMission("Welcome", null, hour = 2)
    // Starts first, so it would head the list if the event were not filtered on.
    addMission("Elsewhere", "logistics", hour = 0, eventId = "event-2")

    open("organizer")

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertFalse(state.loadFailed)
    assertTrue(state.isOrganizer)
    // The organizer watches the whole event, so they may watch its attendance, under its title.
    assertEquals(true, state.canViewAttendance)
    assertEquals("Festival", state.eventTitle)
    assertEquals(listOf("Set up", "Welcome", "Serve drinks"), titles())
    assertEquals(listOf("Logistics", null, "Bar"), state.missions.map { it.teamName })
    val setUp = state.missions.first()
    // The assignees keep the order of the mission, and are not a count of who is present.
    assertEquals(
        listOf(AttendanceVolunteer("bob", "Bob"), AttendanceVolunteer("ann", "Ann")),
        setUp.assignees,
    )
    assertEquals(3, setUp.volunteersNeeded)
  }

  @Test
  fun load_asManager_listsOnlyTheMissionsOfTheirTeams() = runTest {
    addMission("Set up", "logistics", hour = 1)
    addMission("Serve drinks", "bar", hour = 2)
    addMission("Guard the gate", "security", hour = 3)
    addMission("Welcome", null, hour = 4)
    addMission("Elsewhere", "logistics", hour = 0, eventId = "event-2")

    open("manager")

    assertFalse(viewModel.uiState.value.isOrganizer)
    assertEquals(listOf("Set up", "Serve drinks"), titles())
  }

  @Test
  fun load_asManager_queriesTheirTeamsMissionsAndNotTheWholeEvent() = runTest {
    addMission("Set up", "logistics", hour = 1)
    val recording = RecordingMissions()

    viewModel = AttendanceViewModel("event-1", "manager", events, recording, teams, users, checkIns)
    advanceUntilIdle()

    assertEquals(listOf(setOf("logistics", "bar")), recording.byTeams)
    assertEquals(0, recording.byEventCalls)
    assertEquals(listOf("Set up"), titles())
  }

  @Test
  fun load_withoutAnyManagedTeam_queriesNoMissionAndListsNone() = runTest {
    addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann"))
    addMission("Welcome", null, hour = 2)
    val recording = RecordingMissions()

    viewModel = AttendanceViewModel("event-1", "ann", events, recording, teams, users, checkIns)
    advanceUntilIdle()

    // A volunteer manages no team, so there is nothing to ask the mission repository for.
    assertEquals(emptyList<Set<String>>(), recording.byTeams)
    assertEquals(0, recording.byEventCalls)
    val state = viewModel.uiState.value
    assertFalse(state.isOrganizer)
    assertFalse(state.loadFailed)
    // Managing no team is no attendance to watch, whatever the missions of the event.
    assertEquals(false, state.canViewAttendance)
    assertTrue(state.isEmpty)
  }

  @Test
  fun load_asManagerOfTeamsWithoutAnyMission_isStillAManager() = runTest {
    open("manager")

    val state = viewModel.uiState.value
    // It comes from the teams they manage, never from having missions to show.
    assertEquals(true, state.canViewAttendance)
    assertEquals("Festival", state.eventTitle)
    assertTrue(state.isEmpty)
  }

  @Test
  fun load_failingBeforeTheRoleIsRead_leavesItUnknown() = runTest {
    events.failure = EventRepositoryException.Unavailable()

    open("manager")

    val state = viewModel.uiState.value
    assertTrue(state.loadFailed)
    // Unknown rather than false, so the attendance is neither offered nor denied on a guess.
    assertNull(state.canViewAttendance)
  }

  @Test
  fun load_withoutAPublicProfile_keepsTheAssigneeWithoutAName() = runTest {
    addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann", "ghost"))

    open("manager")

    assertFalse(viewModel.uiState.value.loadFailed)
    assertEquals(
        listOf(AttendanceVolunteer("ann", "Ann"), AttendanceVolunteer("ghost", null)),
        viewModel.uiState.value.missions.single().assignees,
    )
  }

  @Test
  fun load_whenThePublicProfileReadFails_keepsTheAssigneeWithoutAName() = runTest {
    addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann"))
    users.failure = UserRepositoryException.Unavailable()

    open("manager")

    assertFalse(viewModel.uiState.value.loadFailed)
    assertEquals(
        listOf(AttendanceVolunteer("ann", null)),
        viewModel.uiState.value.missions.single().assignees,
    )
  }

  @Test
  fun load_withoutAnyMission_loadsThenShowsAnEmptyState() = runTest {
    viewModel =
        AttendanceViewModel("event-1", "organizer", events, missions, teams, users, checkIns)
    assertTrue(viewModel.uiState.value.isLoading)
    assertFalse(viewModel.uiState.value.isEmpty)

    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertFalse(state.loadFailed)
    assertTrue(state.isEmpty)
  }

  @Test
  fun load_whenAReadFails_showsTheFailureAndNoMission() = runTest {
    addMission("Set up", "logistics", hour = 1)
    open("manager")
    assertEquals(listOf("Set up"), titles())
    events.failure = EventRepositoryException.Unavailable()

    viewModel.refresh()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertTrue(state.loadFailed)
    assertFalse(state.isEmpty)
    assertTrue(state.missions.isEmpty())
  }

  @Test
  fun refresh_listsTheMissionsAddedSince() = runTest {
    addMission("Set up", "logistics", hour = 1)
    open("manager")
    addMission("Serve drinks", "bar", hour = 2, assigneeIds = listOf("ann"))

    viewModel.refresh()
    advanceUntilIdle()

    assertEquals(listOf("Set up", "Serve drinks"), titles())
    assertEquals(
        listOf(AttendanceVolunteer("ann", "Ann")),
        viewModel.uiState.value.missions.last().assignees,
    )
  }

  @Test
  fun refresh_whileALoadRuns_keepsOnlyTheNewestMissions() = runTest {
    addMission("Set up", "logistics", hour = 1)
    // The first load reads the missions, then is held long enough to be overtaken.
    viewModel =
        AttendanceViewModel("event-1", "manager", events, SlowFirstRead(), teams, users, checkIns)
    advanceTimeBy(500)
    addMission("Serve drinks", "bar", hour = 2)

    viewModel.refresh()
    advanceUntilIdle()

    assertEquals(listOf("Set up", "Serve drinks"), titles())
  }

  @Test
  fun load_withoutAnyCheckIn_hasNobodyPresentAndEveryAssigneeMissing() = runTest {
    addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("bob", "ann"))

    open("manager")

    val mission = viewModel.uiState.value.missions.single()
    assertTrue("the check-ins of the mission have arrived", mission.attendanceKnown)
    assertEquals(0, mission.presentCount)
    assertEquals(emptyList<AttendanceVolunteer>(), mission.presentAssignees)
    // Missing in the order the mission lists them, and nobody is present out of the three needed.
    assertEquals(listOf("bob", "ann"), mission.missingAssignees.map { it.userId })
    assertTrue(mission.isUnderstaffed)
  }

  @Test
  fun arrival_whileWatching_updatesTheAttendanceWithoutARefresh() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("bob", "ann"))
    open("manager")

    arrive(setUp.id, "ann", minute = 5)
    advanceUntilIdle()

    val mission = viewModel.uiState.value.missions.single()
    assertEquals(1, mission.presentCount)
    assertEquals(listOf("ann"), mission.presentAssignees.map { it.userId })
    assertEquals(start.plus(Duration.ofMinutes(5)), mission.presentAssignees.single().checkedInAt)
    assertEquals(listOf("bob"), mission.missingAssignees.map { it.userId })
    assertTrue(mission.isUnderstaffed)
  }

  @Test
  fun arrival_atOneMission_leavesTheOtherMissionsAttendanceAlone() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann"))
    val drinks = addMission("Serve drinks", "bar", hour = 2, assigneeIds = listOf("bob"))
    open("manager")
    arrive(drinks.id, "bob")
    advanceUntilIdle()

    arrive(setUp.id, "ann")
    advanceUntilIdle()

    val byTitle = viewModel.uiState.value.missions.associateBy { it.title }
    assertEquals(listOf("ann"), byTitle.getValue("Set up").presentAssignees.map { it.userId })
    assertEquals(listOf("bob"), byTitle.getValue("Serve drinks").presentAssignees.map { it.userId })
  }

  @Test
  fun arrival_ofSomeoneUnassignedOrElsewhere_isNotCounted() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann"))
    val drinks = addMission("Serve drinks", "bar", hour = 2, assigneeIds = listOf("bob"))
    open("manager")

    arrive(setUp.id, "bob") // Assigned to the other mission, so not present on this one.
    arrive(setUp.id, "ann", minute = 5) // Counted.
    arrive(setUp.id, "ann", minute = 1, eventId = "event-2") // Another event's mission.
    advanceUntilIdle()

    val byTitle = viewModel.uiState.value.missions.associateBy { it.title }
    val mission = byTitle.getValue("Set up")
    assertEquals(1, mission.presentCount)
    assertEquals(start.plus(Duration.ofMinutes(5)), mission.presentAssignees.single().checkedInAt)
    assertEquals(0, byTitle.getValue("Serve drinks").presentCount)
  }

  @Test
  fun arrivals_reachingTheVolunteersNeeded_clearTheUnderstaffedWarning() = runTest {
    // Three needed and three assigned, so everyone arriving is enough.
    val setUp =
        addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann", "bob", "cat"))
    open("manager")
    arrive(setUp.id, "ann")
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.missions.single().isUnderstaffed)

    arrive(setUp.id, "bob")
    arrive(setUp.id, "cat")
    advanceUntilIdle()

    val mission = viewModel.uiState.value.missions.single()
    assertEquals(3, mission.presentCount)
    assertEquals(emptyList<AttendanceVolunteer>(), mission.missingAssignees)
    assertFalse(mission.isUnderstaffed)
  }

  @Test
  fun refresh_replacesTheCheckInCollectionsOfTheLoadItSupersedes() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann"))
    val observing = RecordingCheckIns()
    viewModel = AttendanceViewModel("event-1", "manager", events, missions, teams, users, observing)
    advanceUntilIdle()
    arrive(setUp.id, "ann")
    advanceUntilIdle()
    assertEquals(1, observing.active)

    viewModel.refresh()
    advanceUntilIdle()

    // One collection per load, and the one of the superseded load is gone rather than left running.
    assertEquals(listOf(setUp.id, setUp.id), observing.observed)
    assertEquals(1, observing.active)
    assertEquals(
        listOf("ann"),
        viewModel.uiState.value.missions.single().presentAssignees.map { it.userId },
    )
  }

  @Test
  fun load_withoutAnyMission_collectsNoCheckIns() = runTest {
    val observing = RecordingCheckIns()

    viewModel =
        AttendanceViewModel("event-1", "organizer", events, missions, teams, users, observing)
    advanceUntilIdle()

    assertEquals(emptyList<String>(), observing.observed)
    assertEquals(0, observing.active)
  }

  @Test
  fun clearing_stopsTheCheckInCollections() = runTest {
    addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann"))
    val observing = RecordingCheckIns()
    val store = ViewModelStore()
    ViewModelProvider(
        store,
        AttendanceViewModel.factory(
            "event-1",
            "manager",
            events,
            missions,
            teams,
            users,
            observing,
        ),
    )[AttendanceViewModel::class.java]
    advanceUntilIdle()
    assertEquals(1, observing.active)

    store.clear()
    advanceUntilIdle()

    assertEquals(0, observing.active)
  }

  @Test
  fun checkIn_showsThemPresentAtOnceAndSendsItOnceTheUndoWindowIsOver() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann", "bob"))
    checkIns.currentUserId = "manager"
    open("manager")

    viewModel.checkIn(setUp.id, "ann")
    runCurrent()

    // Green right away, marked as not confirmed, and nothing sent while it can be undone.
    assertEquals(
        AttendanceVolunteer("ann", "Ann", tapTime, checkInPending = true, checkedInManually = true),
        assignee("ann"),
    )
    assertEquals(UndoableCheckIn(setUp.id, "ann", "Ann"), viewModel.uiState.value.undoableCheckIn)
    assertTrue(checkIns.checkIns.isEmpty())

    waitForTheUndoWindow()

    val sent = checkIns.checkIns.single()
    assertEquals("ann", sent.userId)
    assertEquals(CheckInMethod.MANAGER, sent.method)
    assertEquals("manager", sent.checkedInBy)
    assertNull(viewModel.uiState.value.undoableCheckIn)
    assertFalse(assignee("ann").checkInPending)
    assertTrue(assignee("ann").checkedInManually)
  }

  @Test
  fun undoCheckIn_withinTheWindow_sendsNothingAndPutsThemBackMissing() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann"))
    open("manager")
    viewModel.checkIn(setUp.id, "ann")
    runCurrent()

    viewModel.undoCheckIn()
    waitForTheUndoWindow()

    assertTrue(checkIns.checkIns.isEmpty())
    assertNull(viewModel.uiState.value.undoableCheckIn)
    assertEquals(
        listOf("ann"),
        viewModel.uiState.value.missions.single().missingAssignees.map { it.userId },
    )
  }

  @Test
  fun checkIn_twiceOrOfSomeonePresent_sendsOnlyOneCheckIn() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann", "bob"))
    arrive(setUp.id, "bob")
    open("manager")

    // A double tap, and a tap on someone already there.
    viewModel.checkIn(setUp.id, "ann")
    viewModel.checkIn(setUp.id, "ann")
    viewModel.checkIn(setUp.id, "bob")
    waitForTheUndoWindow()

    assertEquals(listOf("bob", "ann"), checkIns.checkIns.map { it.userId })
    assertEquals(CheckInMethod.SELF, checkIns.checkIns.first().method)
  }

  @Test
  fun checkIn_ofAnotherVolunteer_sendsThePreviousOneAtOnce() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann", "bob"))
    open("manager")

    viewModel.checkIn(setUp.id, "ann")
    runCurrent()
    viewModel.checkIn(setUp.id, "bob")
    runCurrent()

    // Only the last one can still be undone: the first one is no longer what "Undo" would undo.
    assertEquals(listOf("ann"), checkIns.checkIns.map { it.userId })
    assertEquals("bob", viewModel.uiState.value.undoableCheckIn?.userId)
  }

  @Test
  fun sendUndoableCheckIn_sendsItWithoutWaitingForTheWindow() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann"))
    open("manager")
    viewModel.checkIn(setUp.id, "ann")
    runCurrent()

    // What the screen does when it is left.
    viewModel.sendUndoableCheckIn()

    assertEquals(listOf("ann"), checkIns.checkIns.map { it.userId })
    assertNull(viewModel.uiState.value.undoableCheckIn)
  }

  @Test
  fun checkIn_waitingForTheNetwork_staysPendingUntilTheBackendConfirmsIt() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann"))
    val network = CompletableDeferred<Unit>()
    checkIns.confirmation = network
    open("manager")

    viewModel.checkIn(setUp.id, "ann")
    waitForTheUndoWindow()

    // Sent, but only queued: it stays marked, and is counted for the banner.
    assertEquals(1, checkIns.checkIns.size)
    assertTrue(assignee("ann").checkInPending)
    assertEquals(1, viewModel.uiState.value.pendingCheckInCount)

    network.complete(Unit)
    advanceUntilIdle()

    assertFalse(assignee("ann").checkInPending)
    assertEquals(0, viewModel.uiState.value.pendingCheckInCount)
  }

  @Test
  fun checkIn_refusedOnceBackOnline_putsThemBackMissingAndNamesThem() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann"))
    val network = CompletableDeferred<Unit>()
    checkIns.confirmation = network
    open("manager")
    viewModel.checkIn(setUp.id, "ann")
    waitForTheUndoWindow()
    assertTrue(assignee("ann").checkInPending)

    // Queued offline, then refused once online: Firestore rolls the write back.
    network.completeExceptionally(CheckInRepositoryException.NotAllowed())
    advanceUntilIdle()

    assertEquals(
        ManualCheckInFailure(ManualCheckInError.NOT_ALLOWED, "Ann"),
        viewModel.uiState.value.checkInErrors.firstOrNull(),
    )
    assertEquals(AttendanceVolunteer("ann", "Ann"), assignee("ann"))
    assertEquals(0, viewModel.uiState.value.pendingCheckInCount)
  }

  @Test
  fun checkIn_refusedBecauseTheyCheckedInFirst_staysSilent() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann"))
    val network = CompletableDeferred<Unit>()
    checkIns.confirmation = network
    open("manager")
    viewModel.checkIn(setUp.id, "ann")
    waitForTheUndoWindow()

    // Their own check-in reached the backend first: nothing for the manager to act on.
    network.completeExceptionally(CheckInRepositoryException.AlreadyCheckedIn())
    advanceUntilIdle()

    assertNull(viewModel.uiState.value.checkInErrors.firstOrNull())
    assertEquals(0, viewModel.uiState.value.pendingCheckInCount)
  }

  @Test
  fun checkIn_ofSomeoneWhoCheckedInThemselfMeanwhile_isSilentAndKeepsTheirCheckIn() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann"))
    open("manager")
    viewModel.checkIn(setUp.id, "ann")
    runCurrent()

    // Their own phone checks them in during the undo window: the manual one is then refused.
    arrive(setUp.id, "ann", minute = 6)
    waitForTheUndoWindow()

    assertNull(viewModel.uiState.value.checkInErrors.firstOrNull())
    assertEquals(CheckInMethod.SELF, checkIns.checkIns.single().method)
    assertEquals(
        AttendanceVolunteer("ann", "Ann", start.plus(Duration.ofMinutes(6))),
        assignee("ann"),
    )
  }

  @Test
  fun checkIn_refusedByTheBackend_showsWhyAndPutsThemBackMissing() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann"))
    open("manager")
    // Set once the check-ins are observed, so only the check-in itself fails.
    checkIns.failure = CheckInRepositoryException.NotAllowed()

    viewModel.checkIn(setUp.id, "ann")
    waitForTheUndoWindow()

    // Named, since several check-ins may fail at once when the device is back online.
    assertEquals(
        ManualCheckInFailure(ManualCheckInError.NOT_ALLOWED, "Ann"),
        viewModel.uiState.value.checkInErrors.firstOrNull(),
    )
    assertEquals(AttendanceVolunteer("ann", "Ann"), assignee("ann"))

    viewModel.onCheckInErrorShown()
    assertNull(viewModel.uiState.value.checkInErrors.firstOrNull())
  }

  @Test
  fun checkIn_ofSomeoneElse_keepsAFailureNotShownYet() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann", "bob"))
    open("manager")
    checkIns.failure = CheckInRepositoryException.NotAllowed()
    viewModel.checkIn(setUp.id, "ann")
    waitForTheUndoWindow()
    checkIns.failure = null

    viewModel.checkIn(setUp.id, "bob")
    runCurrent()

    // Ann's failure is still there for the screen to show, though Bob's check-in came after it.
    assertEquals(
        ManualCheckInFailure(ManualCheckInError.NOT_ALLOWED, "Ann"),
        viewModel.uiState.value.checkInErrors.firstOrNull(),
    )
  }

  @Test
  fun checkIn_afterTheMissionIsOver_doesNothing() = runTest {
    // Over three hours before the tap: a check-in would record the tap, not an arrival.
    val over = addMission("Set up", "logistics", hour = -3, assigneeIds = listOf("ann"))
    open("manager")

    viewModel.checkIn(over.id, "ann")
    waitForTheUndoWindow()

    assertTrue(checkIns.checkIns.isEmpty())
    assertNull(viewModel.uiState.value.undoableCheckIn)
  }

  @Test
  fun checkIn_beforeTheWindowOpens_doesNothing() = runTest {
    // Starts almost two hours after the tap, while check-in opens 15 minutes before the start.
    val later = addMission("Clean up", "logistics", hour = 3, assigneeIds = listOf("ann"))
    open("manager")

    viewModel.checkIn(later.id, "ann")
    waitForTheUndoWindow()

    assertTrue(checkIns.checkIns.isEmpty())
    assertNull(viewModel.uiState.value.undoableCheckIn)
    assertEquals(AttendanceVolunteer("ann", "Ann"), assignee("ann"))
  }

  @Test
  fun checkIn_twoRefusedOnceBackOnline_reportsBothInTurn() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann", "bob"))
    val network = CompletableDeferred<Unit>()
    checkIns.confirmation = network
    open("manager")
    viewModel.checkIn(setUp.id, "ann")
    viewModel.checkIn(setUp.id, "bob")
    waitForTheUndoWindow()

    // Both were queued offline, and both are refused once back online.
    network.completeExceptionally(CheckInRepositoryException.NotAllowed())
    advanceUntilIdle()

    assertEquals(
        listOf(
            ManualCheckInFailure(ManualCheckInError.NOT_ALLOWED, "Ann"),
            ManualCheckInFailure(ManualCheckInError.NOT_ALLOWED, "Bob"),
        ),
        viewModel.uiState.value.checkInErrors,
    )
    viewModel.onCheckInErrorShown()
    assertEquals(
        listOf(ManualCheckInFailure(ManualCheckInError.NOT_ALLOWED, "Bob")),
        viewModel.uiState.value.checkInErrors,
    )
  }

  @Test
  fun pendingCheckInCount_leavesOutTheCheckInStillUndoable() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann"))
    val network = CompletableDeferred<Unit>()
    checkIns.confirmation = network
    open("manager")

    viewModel.checkIn(setUp.id, "ann")
    runCurrent()

    // Shown pending, but nothing was sent yet, so nothing is waiting for the network.
    assertTrue(assignee("ann").checkInPending)
    assertEquals(0, viewModel.uiState.value.pendingCheckInCount)

    viewModel.sendUndoableCheckIn()
    runCurrent()

    assertEquals(1, viewModel.uiState.value.pendingCheckInCount)
  }

  @Test
  fun sendUndoableCheckIn_beforeTheScreenIsCleared_isStillConfirmed() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann"))
    val network = CompletableDeferred<Unit>()
    checkIns.confirmation = network
    val store = ViewModelStore()
    val screen =
        ViewModelProvider(
            store,
            viewModelFactory {
              initializer {
                AttendanceViewModel(
                    "event-1",
                    "manager",
                    events,
                    missions,
                    teams,
                    users,
                    checkIns,
                    now = { tapTime },
                )
              }
            },
        )[AttendanceViewModel::class.java]
    advanceUntilIdle()
    screen.checkIn(setUp.id, "ann")

    // What the screen does when it is left, offline: the write is queued, then the screen goes.
    screen.sendUndoableCheckIn()
    store.clear()
    advanceUntilIdle()
    network.complete(Unit)

    val sent = checkIns.checkIns.single()
    assertEquals(CheckInMethod.MANAGER, sent.method)
    assertFalse(sent.pending)
  }

  @Test
  fun checkIn_failingOtherwise_reportsAFailure() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann"))
    open("manager")
    checkIns.failure = CheckInRepositoryException.Unavailable()

    viewModel.checkIn(setUp.id, "ann")
    waitForTheUndoWindow()

    assertEquals(
        ManualCheckInFailure(ManualCheckInError.FAILED, "Ann"),
        viewModel.uiState.value.checkInErrors.firstOrNull(),
    )
    assertEquals(AttendanceVolunteer("ann", "Ann"), assignee("ann"))
  }

  @Test
  fun checkIn_whileTheAttendanceIsNotKnown_doesNothing() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann"))
    checkIns.failure = CheckInRepositoryException.Unavailable()
    open("manager")
    checkIns.failure = null

    viewModel.checkIn(setUp.id, "ann")
    waitForTheUndoWindow()

    // Nobody is known to be missing, so nobody can be checked in.
    assertTrue(checkIns.checkIns.isEmpty())
    assertNull(viewModel.uiState.value.undoableCheckIn)
  }

  @Test
  fun load_whenTheCheckInsCannotBeObserved_marksThatMissionWithoutFailingTheLoad() = runTest {
    addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann"))
    checkIns.failure = CheckInRepositoryException.Unavailable()

    open("manager")

    val state = viewModel.uiState.value
    // The missions did load: only their attendance did not, which is the mission's own failure.
    assertFalse(state.loadFailed)
    // The mission stays listed, with an attendance that is unknown rather than read as empty.
    val mission = state.missions.single()
    assertTrue(mission.attendanceFailed)
    assertFalse(mission.attendanceKnown)
    assertEquals(0, mission.presentCount)
    // Nobody unseen is reported late, and nothing is warned about as short of volunteers.
    assertEquals(emptyList<AttendanceVolunteer>(), mission.missingAssignees)
    assertFalse(mission.isUnderstaffed)
  }

  @Test
  fun observation_failingAfterAnEmission_stopsThatMissionsAttendanceBeingKnown() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann"))
    addMission("Serve drinks", "bar", hour = 2, assigneeIds = listOf("bob"))
    viewModel =
        AttendanceViewModel(
            "event-1",
            "manager",
            events,
            missions,
            teams,
            users,
            FailingOnArrival(),
        )
    advanceUntilIdle()
    // The first snapshot came through, so the attendance is known at this point.
    assertTrue(viewModel.uiState.value.missions.all { it.attendanceKnown })

    arrive(setUp.id, "ann")
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.loadFailed)
    val byTitle = state.missions.associateBy { it.title }
    // What was collected may be out of date now, so it is no longer presented as the attendance.
    assertFalse(byTitle.getValue("Set up").attendanceKnown)
    assertTrue(byTitle.getValue("Set up").attendanceFailed)
    // The mission that is still being followed keeps its attendance.
    assertTrue(byTitle.getValue("Serve drinks").attendanceKnown)
    assertFalse(byTitle.getValue("Serve drinks").attendanceFailed)
  }

  @Test
  fun observation_failingWithACheckInPending_stopsShowingIt() = runTest {
    val setUp = addMission("Set up", "logistics", hour = 1, assigneeIds = listOf("ann"))
    checkIns.confirmation = CompletableDeferred()
    viewModel =
        AttendanceViewModel(
            "event-1",
            "manager",
            events,
            missions,
            teams,
            users,
            FailingOnArrival(),
            now = { tapTime },
        )
    advanceUntilIdle()

    // Sent and queued: the snapshot holding it makes the observation fail.
    viewModel.checkIn(setUp.id, "ann")
    viewModel.sendUndoableCheckIn()
    advanceUntilIdle()

    // The attendance is no longer known, so nobody is shown present nor waiting for the network.
    assertFalse(viewModel.uiState.value.missions.single().attendanceKnown)
    assertEquals(AttendanceVolunteer("ann", "Ann"), assignee("ann"))
    assertEquals(0, viewModel.uiState.value.pendingCheckInCount)
  }

  /** A [CheckInRepository] whose observation emits the first snapshot, then fails on the next. */
  private inner class FailingOnArrival : CheckInRepository by checkIns {
    override fun observeCheckIns(eventId: String, missionId: String): Flow<List<CheckIn>> =
        checkIns.observeCheckIns(eventId, missionId).onEach {
          if (it.isNotEmpty()) throw CheckInRepositoryException.Unavailable()
        }
  }

  /** Counts the check-in collections, to check a load replaces those of the one it supersedes. */
  private inner class RecordingCheckIns : CheckInRepository by checkIns {
    val observed = mutableListOf<String>()
    var active = 0

    override fun observeCheckIns(eventId: String, missionId: String): Flow<List<CheckIn>> {
      observed += missionId
      return checkIns
          .observeCheckIns(eventId, missionId)
          .onStart { active++ }
          .onCompletion { active-- }
    }
  }

  @Test
  fun load_keepsWhetherTheMissionWasClosedByHand() = runTest {
    val closed = addMission("Set up", "logistics", hour = 1)
    addMission("Serve drinks", "bar", hour = 2)
    missions.markCompleted("event-1", closed.id)

    open("manager")

    assertEquals(listOf(true, false), viewModel.uiState.value.missions.map { it.completed })
  }

  @Test
  fun markCompleted_closesTheMissionOnceStored() = runTest {
    val mission = addMission("Set up", "logistics", hour = 1)
    open("manager")

    viewModel.markCompleted(mission.id)

    assertEquals(setOf(mission.id), viewModel.uiState.value.markingCompletedIds)
    assertFalse(viewModel.uiState.value.missions.single().completed)

    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.missions.single().completed)
    assertTrue(viewModel.uiState.value.markingCompletedIds.isEmpty())
    assertEquals(true, missions.getMission("event-1", mission.id)?.completed)
  }

  @Test
  fun markCompleted_whenItFails_reportsItAndLeavesTheMissionAsItWas() = runTest {
    val mission = addMission("Set up", "logistics", hour = 1)
    open("manager")
    missions.failure = IllegalStateException("backend down")

    viewModel.markCompleted(mission.id)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(mission.id, state.completionFailedMissionId)
    assertFalse(state.missions.single().completed)
    assertTrue(state.markingCompletedIds.isEmpty())

    viewModel.onCompletionFailureShown()

    assertNull(viewModel.uiState.value.completionFailedMissionId)
  }

  @Test
  fun markCompleted_whenTheBackendNeverAnswers_timesOutAsAFailure() = runTest {
    val mission = addMission("Set up", "logistics", hour = 1)
    val silent =
        object : MissionRepository by missions {
          override suspend fun markCompleted(eventId: String, missionId: String) {
            awaitCancellation()
          }
        }
    viewModel = AttendanceViewModel("event-1", "manager", events, silent, teams, users, checkIns)
    advanceUntilIdle()

    viewModel.markCompleted(mission.id)
    advanceUntilIdle()

    assertEquals(mission.id, viewModel.uiState.value.completionFailedMissionId)
    assertTrue(viewModel.uiState.value.markingCompletedIds.isEmpty())
  }

  @Test
  fun canBeMarkedCompleted_isTrueOnlyWhileTheMissionRuns() {
    val mission =
        AttendanceMission(
            id = "m",
            title = "Set up",
            teamId = null,
            teamName = null,
            volunteersNeeded = 1,
            startAt = start,
            endAt = start.plus(Duration.ofHours(1)),
            assignees = emptyList(),
        )

    assertFalse(mission.canBeMarkedCompleted(start.minusSeconds(1)))
    assertTrue(mission.canBeMarkedCompleted(start))
    assertFalse(mission.canBeMarkedCompleted(mission.endAt))
    assertFalse(mission.copy(completed = true).canBeMarkedCompleted(start))
  }

  /** Records which mission query a load used, to pin down what each role asks the backend for. */
  private inner class RecordingMissions : MissionRepository by missions {
    var byEventCalls = 0
    val byTeams = mutableListOf<Set<String>>()

    override suspend fun getMissionsByEvent(eventId: String): List<Mission> {
      byEventCalls++
      return missions.getMissionsByEvent(eventId)
    }

    override suspend fun getMissionsByTeams(eventId: String, teamIds: Set<String>): List<Mission> {
      byTeams += teamIds
      return missions.getMissionsByTeams(eventId, teamIds)
    }
  }

  /**
   * A [MissionRepository] whose first read answers late, after a newer load has published. It reads
   * before waiting, so the held load carries the missions as they were when it started.
   */
  private inner class SlowFirstRead : MissionRepository by missions {
    private var reads = 0

    override suspend fun getMissionsByTeams(eventId: String, teamIds: Set<String>): List<Mission> {
      val read = missions.getMissionsByTeams(eventId, teamIds)
      if (reads++ == 0) delay(1_000)
      return read
    }
  }
}
