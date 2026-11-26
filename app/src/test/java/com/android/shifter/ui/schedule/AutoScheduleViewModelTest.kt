// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.schedule

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FakeEventRepository
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.FakeMembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestRepositoryException
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.MAX_MISSIONS_PER_ADDITION
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.mission.MissionRepositoryException
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.PublicProfile
import com.swent.shifter.model.user.User
import com.swent.shifter.model.user.UserRepository
import com.swent.shifter.ui.mission.AssigneeOption
import com.swent.shifter.utils.MainDispatcherRule
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AutoScheduleViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val eventId = "event-1"
  private val start = Instant.parse("2027-06-20T08:00:00Z")

  /** The time the ViewModel and the missions read, moved by a test. */
  private var at: Instant = start

  private val clock =
      object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = at
      }

  private lateinit var missions: FakeMissionRepository
  private lateinit var teams: FakeTeamRepository
  private lateinit var requests: FakeMembershipRequestRepository
  private lateinit var events: FakeEventRepository
  private lateinit var users: FakeUserRepository
  private lateinit var viewModel: AutoScheduleViewModel

  /** Who the assign dialog offers, as read by the ViewModel, and how often it was read. */
  private var candidates: List<AssigneeOption> =
      listOf(AssigneeOption("ben", "Ben"), AssigneeOption("eva", null))
  private var candidatesFailure: Exception? = null
  private var candidatesRead = 0

  private val state: AutoScheduleUiState
    get() = viewModel.uiState.value

  @Before
  fun setUp() {
    missions = FakeMissionRepository(clock)
    teams =
        FakeTeamRepository(
            listOf(
                team("bar", "Bar", memberIds = listOf("ana", "ben")),
                team("gate", "Gate", managerId = "max", memberIds = listOf("eva")),
            )
        )
    requests = FakeMembershipRequestRepository()
    events = FakeEventRepository().apply { this.events += event() }
    // Eva has no profile, so her name cannot be read.
    users =
        FakeUserRepository(listOf(User("ana", "Ana", "ana@x.ch"), User("ben", "Ben", "ben@x.ch")))
  }

  /**
   * The festival of the tests, at 08:00 with no mission started:
   * - "Bar" (2 needed) has Ana, and gets Ben;
   * - "Gate" (2 needed) gets Eva; its manager Max is never proposed, so one spot stays empty;
   * - "General" is never filled, and "Lost" belongs to a team that no longer exists;
   * - "Done" is completed, but still books Ben at 14:00, so nobody fits "Late bar", as Ana is only
   *   free in the morning.
   */
  private suspend fun festival() {
    addMission("Bar", "bar", hour = 2, volunteersNeeded = 2, assigneeIds = listOf("ana"))
    addMission("Gate", "gate", hour = 2, volunteersNeeded = 2)
    addMission("General", null, hour = 4)
    addMission("Lost", "deleted", hour = 5)
    addMission("Done", "bar", hour = 6, assigneeIds = listOf("ben"), completed = true)
    addMission("Late bar", "bar", hour = 6)
    accepted("ana", until = 4)
    accepted("ben")
    accepted("eva")
    accepted("max")
  }

  @Test
  fun load_buildsTheProposedMissions() = runTest {
    festival()
    requests.add(eventId, request("pat", status = MembershipRequestStatus.PENDING))

    open()

    assertEquals(
        listOf(
            proposed(
                "mission-1",
                "Bar",
                hour = 2,
                teamName = "Bar",
                needed = 2,
                assigned = listOf("Ana"),
                proposed = listOf("Ben"),
            ),
            proposed(
                "mission-2",
                "Gate",
                hour = 2,
                teamName = "Gate",
                needed = 2,
                proposed = listOf(null),
                missing = 1,
            ),
            proposed(
                "mission-3",
                "General",
                hour = 4,
                teamName = null,
                missing = 1,
                isGeneral = true,
            ),
            proposed("mission-4", "Lost", hour = 5, teamName = null, missing = 1),
            proposed("mission-6", "Late bar", hour = 6, teamName = "Bar", missing = 1),
        ),
        state.missions,
    )
    // Eva counts though her name could not be read.
    assertEquals(2, state.newAssignments)
    assertEquals(start..start.plus(Duration.ofDays(1)), state.eventPeriod)
    assertFalse(state.isLoading)
  }

  @Test
  fun load_afterAMissionStarted_marksItStartedWithItsSpotsToFill() = runTest {
    festival()
    // 10:00: "Bar" and "Gate" have started, "General" not yet.
    at = start.plus(Duration.ofHours(2))

    open()

    val bar = state.missions.single { it.title == "Bar" }
    assertTrue(bar.started)
    // Ben is free, but the proposal skips a started mission: its spot stays to fill by hand.
    assertEquals(emptyList<String?>(), bar.proposed)
    assertEquals(1, bar.missing)
    assertFalse(state.missions.single { it.title == "General" }.started)
  }

  @Test
  fun openAssign_locksWhoIsOnTheMissionAndLimitsToTheSpotsLeft() = runTest {
    startedBar()

    viewModel.openAssign("mission-1")

    val assigning = checkNotNull(state.assigning)
    assertEquals("Bar", assigning.title)
    // Ana and the one spot left.
    assertEquals(2, assigning.limit)
    assertEquals(listOf("ana"), assigning.selection.selectedIds)
    assertEquals(setOf("ana"), assigning.selection.lockedIds)
    // Ana is not a candidate of the fake, so she stays listed, with her name, to match the count.
    assertEquals(
        listOf(
            AssigneeOption("ben", "Ben"),
            AssigneeOption("eva", null),
            AssigneeOption("ana", "Ana"),
        ),
        assigning.selection.candidates,
    )
    assertFalse(assigning.selection.isLoading)
  }

  @Test
  fun openAssign_keepsTheSpotsOfThePeopleProposed() = runTest {
    festival()
    open()

    // "Gate" needs 2: Eva is proposed, so only one spot is left to pick.
    viewModel.openAssign("mission-2")

    assertEquals(1, state.assigning?.limit)
    assertEquals(emptyList<String>(), state.assigning?.selection?.selectedIds)
  }

  @Test
  fun openAssign_withNothingToFillOrNoTeamLeft_isIgnored() = runTest {
    festival()
    open()

    // "Bar" is full once the proposal is accepted, and "Lost" has no team to pick from.
    viewModel.openAssign("mission-1")
    viewModel.openAssign("mission-4")
    viewModel.openAssign("unknown")

    assertNull(state.assigning)
    assertEquals(0, candidatesRead)
  }

  @Test
  fun saveAssignees_addsOnlyThePeoplePickedThenReloads() = runTest {
    startedBar()
    viewModel.openAssign("mission-1")

    // Ana is locked, so tapping her does nothing; Ben fills the last spot.
    viewModel.toggleAssignee("ana")
    viewModel.toggleAssignee("ben")
    viewModel.saveAssignees()

    assertNull(state.assigning)
    assertEquals(listOf("ana", "ben"), missions.getMission(eventId, "mission-1")?.assigneeIds)
    // The reload shows him as assigned, with nothing left to fill on the mission.
    val bar = state.missions.single { it.title == "Bar" }
    assertEquals(listOf("Ana", "Ben"), bar.assigned)
    assertEquals(0, bar.missing)
  }

  @Test
  fun saveAssignees_neverRemovesSomeoneAssignedMeanwhile() = runTest {
    startedBar()
    viewModel.openAssign("mission-1")
    viewModel.toggleAssignee("ben")
    // From the Overview, while the dialog is open: Eva takes the last spot.
    missions.updateAssignees(eventId, "mission-1", listOf("ana", "eva"))

    viewModel.saveAssignees()

    // The mission filled up: nothing is added, Eva stays, and the dialog gives way to the reload.
    assertNull(state.assigning)
    assertEquals(listOf("ana", "eva"), missions.getMission(eventId, "mission-1")?.assigneeIds)
    assertEquals(listOf("Ana", null), state.missions.single { it.title == "Bar" }.assigned)
  }

  @Test
  fun saveAssignees_onAnotherFailure_keepsTheDialogToTryAgain() = runTest {
    startedBar()
    viewModel.openAssign("mission-1")
    viewModel.toggleAssignee("ben")
    missions.failure = IllegalStateException("offline")

    viewModel.saveAssignees()

    assertTrue(checkNotNull(state.assigning).saveFailed)
    assertFalse(checkNotNull(state.assigning).isSaving)
    assertEquals(listOf("ana", "ben"), state.assigning?.selection?.selectedIds)
  }

  @Test
  fun saveAssignees_withNobodyNewPicked_closesWithoutWriting() = runTest {
    startedBar()
    viewModel.openAssign("mission-1")

    viewModel.saveAssignees()

    assertNull(state.assigning)
    assertEquals(listOf("ana"), missions.getMission(eventId, "mission-1")?.assigneeIds)
  }

  @Test
  fun assignCandidates_onFailure_canBeRetried() = runTest {
    startedBar()
    candidatesFailure = IllegalStateException("offline")
    viewModel.openAssign("mission-1")
    assertTrue(checkNotNull(state.assigning).selection.loadFailed)
    // Nothing is saved before the candidates are read.
    viewModel.saveAssignees()
    assertEquals(listOf("ana"), missions.getMission(eventId, "mission-1")?.assigneeIds)

    candidatesFailure = null
    viewModel.retryAssignees()

    assertFalse(checkNotNull(state.assigning).selection.loadFailed)
    assertEquals(3, state.assigning?.selection?.candidates?.size)
  }

  @Test
  fun dismissAssign_closesWithoutWriting() = runTest {
    startedBar()
    viewModel.openAssign("mission-1")
    viewModel.toggleAssignee("ben")

    viewModel.dismissAssign()

    assertNull(state.assigning)
    assertEquals(listOf("ana"), missions.getMission(eventId, "mission-1")?.assigneeIds)
  }

  @Test
  fun retry_afterAFailedLoad_keepsThenReloads() = runTest {
    festival()
    open()
    val shown = state.missions

    requests.failure = MembershipRequestRepositoryException.Unavailable()
    viewModel.retry()

    assertTrue(state.loadFailed)
    assertEquals(shown, state.missions)
    assertFalse(state.canAccept)

    requests.failure = null
    viewModel.retry()

    assertFalse(state.loadFailed)
    assertTrue(state.canAccept)
  }

  @Test
  fun load_withEveryMissionFull_hasNothingToPropose() = runTest {
    addMission("Bar", "bar", hour = 2, assigneeIds = listOf("ana"))
    accepted("ben")

    open()
    viewModel.accept()

    assertTrue(state.nothingToPropose)
    assertFalse(state.saved)
    assertEquals(listOf("ana"), missions.getMission(eventId, "mission-1")?.assigneeIds)
  }

  @Test
  fun accept_savesThenReloads() = runTest {
    festival()
    open()

    viewModel.accept()

    assertTrue(state.saved)
    assertFalse(state.isSaving)
    assertEquals(listOf("ana", "ben"), missions.getMission(eventId, "mission-1")?.assigneeIds)
    assertEquals(listOf("eva"), missions.getMission(eventId, "mission-2")?.assigneeIds)
    // The reload shows them as already assigned, with nothing left to accept.
    assertEquals(listOf("Ana", "Ben"), state.missions.first().assigned)
    assertEquals(0, state.newAssignments)
    assertFalse(state.canAccept)
  }

  @Test
  fun accept_onAMissionStartedMeanwhile_isOutdated() = runTest {
    festival()
    open()

    at = start.plus(Duration.ofHours(2))
    viewModel.accept()

    assertEquals(ScheduleSaveError.OUTDATED, state.saveError)
    assertEquals(listOf("ana"), missions.getMission(eventId, "mission-1")?.assigneeIds)
  }

  @Test
  fun accept_whenTheMissionsChanged_isOutdated() = runTest {
    festival()
    open()

    // Someone else filled "Bar" meanwhile.
    missions.updateAssignees(eventId, "mission-1", listOf("ana", "zoe"))
    viewModel.accept()

    assertEquals(ScheduleSaveError.OUTDATED, state.saveError)
    assertFalse(state.canAccept)
    // One write for every mission: "Gate" did not get Eva either.
    assertEquals(emptyList<String>(), missions.getMission(eventId, "mission-2")?.assigneeIds)

    viewModel.retry()

    assertNull(state.saveError)
    assertEquals(listOf("Ana", null), state.missions.first().assigned)
  }

  @Test
  fun accept_onACompletedOrDeletedMission_isOutdated() = runTest {
    festival()
    open()

    missions.markCompleted(eventId, "mission-1")
    viewModel.accept()

    assertEquals(ScheduleSaveError.OUTDATED, state.saveError)

    viewModel.retry()
    missions.failure = MissionRepositoryException.NotFound()
    viewModel.accept()

    assertEquals(ScheduleSaveError.OUTDATED, state.saveError)
  }

  @Test
  fun accept_whenAVolunteerWasAssignedMeanwhile_isOutdated() = runTest {
    festival()
    open()

    // Ben, proposed for "Bar" at 10:00, was put by hand on "Gate", at the same time.
    missions.updateAssignees(eventId, "mission-2", listOf("ben"))
    viewModel.accept()

    assertEquals(ScheduleSaveError.OUTDATED, state.saveError)
    assertFalse(state.isSaving)
    assertEquals(listOf("ana"), missions.getMission(eventId, "mission-1")?.assigneeIds)
  }

  @Test
  fun accept_aboveTheLimit_isTooLarge() = runTest {
    val crew = (0..MAX_MISSIONS_PER_ADDITION).map { "volunteer-$it" }
    teams.teams += team("crew", "Crew", memberIds = crew)
    crew.forEach { userId ->
      addMission("Crew", "crew", hour = 2)
      accepted(userId)
    }
    open()

    viewModel.accept()

    assertEquals(ScheduleSaveError.TOO_LARGE, state.saveError)
    assertFalse(state.canAccept)
    assertTrue(missions.getMissionsByEvent(eventId).all { it.assigneeIds.isEmpty() })
  }

  @Test
  fun accept_onAnyOtherFailure_canBeRetried() = runTest {
    festival()
    open()

    missions.failure = MissionRepositoryException.Unavailable()
    viewModel.accept()

    assertEquals(ScheduleSaveError.RETRY, state.saveError)
    assertTrue(state.canAccept)

    missions.failure = null
    viewModel.accept()

    assertTrue(state.saved)
    assertEquals(listOf("ana", "ben"), missions.getMission(eventId, "mission-1")?.assigneeIds)
  }

  @Test
  fun retry_whileSaving_isIgnored() = runTest {
    festival()
    val held = HeldMissionRepository(missions)
    open(held)

    viewModel.accept()
    viewModel.retry()

    assertTrue(state.isSaving)
    assertFalse(state.isLoading)

    held.save.complete(Unit)

    assertTrue(state.saved)
  }

  @Test
  fun accept_whenTheScreenIsLeft_reportsNoError() = runTest {
    festival()
    val held = HeldMissionRepository(missions)
    val store = ViewModelStore()
    viewModel =
        ViewModelProvider(
            store,
            AutoScheduleViewModel.factory(
                eventId,
                held,
                teams,
                requests,
                users,
                events,
                { _, _ -> emptyList() },
            ),
        )[AutoScheduleViewModel::class.java]

    viewModel.accept()
    store.clear()
    held.save.complete(Unit)

    assertNull(state.saveError)
    assertFalse(state.saved)
  }

  @Test
  fun retry_whileLoading_keepsOnlyTheLatestLoad() = runTest {
    festival()
    val eventReads = HeldEventRepository(events)
    val profiles = HeldUserRepository(users)
    viewModel =
        AutoScheduleViewModel(
            eventId,
            missions,
            teams,
            requests,
            profiles,
            eventReads,
            { _, _ -> emptyList() },
        ) {
          at
        }

    // The first load still waits for the event and the names: it is dropped for this one.
    viewModel.retry()
    eventReads.read.complete(Unit)
    profiles.read.complete(Unit)

    assertFalse(state.isLoading)
    assertEquals(listOf("Ana"), state.missions.first().assigned)
    assertEquals(start..start.plus(Duration.ofDays(1)), state.eventPeriod)
  }

  @Test
  fun load_whenTheEventCannotBeRead_stillProposes() = runTest {
    festival()
    events.events.clear()
    open()

    assertNull(state.eventPeriod)
    assertEquals(5, state.missions.size)

    events.failure = IllegalStateException("Offline")
    viewModel.retry()

    assertNull(state.eventPeriod)
    assertFalse(state.loadFailed)
  }

  @Test
  fun load_whenTheNamesCannotBeRead_stillCountsThem() = runTest {
    festival()
    users.failure = IllegalStateException("Offline")
    open()

    assertEquals(listOf<String?>(null), state.missions.first().assigned)
    assertEquals(2, state.newAssignments)
    assertFalse(state.loadFailed)
  }

  @Test
  fun factory_createsTheViewModel() = runTest {
    festival()

    viewModel =
        AutoScheduleViewModel.factory(
                eventId,
                missions,
                teams,
                requests,
                users,
                events,
                { _, _ -> emptyList() },
            )
            .create(AutoScheduleViewModel::class.java, CreationExtras.Empty)

    assertEquals(5, state.missions.size)
  }

  private fun open(missionRepository: MissionRepository = missions) {
    viewModel =
        AutoScheduleViewModel(
            eventId,
            missionRepository,
            teams,
            requests,
            users,
            events,
            { _, _ ->
              candidatesRead++
              candidatesFailure?.let { throw it }
              candidates
            },
        ) {
          at
        }
  }

  /** At 10:00 "Bar" has started: Ana is on it, and its last spot is left to fill by hand. */
  private suspend fun startedBar() {
    festival()
    at = start.plus(Duration.ofHours(2))
    open()
  }

  private suspend fun addMission(
      title: String,
      teamId: String?,
      hour: Long,
      volunteersNeeded: Int = 1,
      assigneeIds: List<String> = emptyList(),
      completed: Boolean = false,
  ) =
      missions.createMission(
          Mission(
              eventId = eventId,
              title = title,
              description = "",
              teamId = teamId,
              volunteersNeeded = volunteersNeeded,
              startAt = start.plus(Duration.ofHours(hour)),
              endAt = start.plus(Duration.ofHours(hour + 1)),
              assigneeIds = assigneeIds,
              createdAt = start,
              completed = completed,
          )
      )

  /** An accepted request from [userId], free from 08:00 for [until] hours. */
  private fun accepted(userId: String, until: Long = 12) {
    requests.add(eventId, request(userId, until = until))
  }

  private fun request(
      userId: String,
      until: Long = 12,
      status: MembershipRequestStatus = MembershipRequestStatus.ACCEPTED,
  ) =
      MembershipRequest(
          id = userId,
          userId = userId,
          teamId = "bar",
          availability = listOf(AvailabilitySlot(start, start.plus(Duration.ofHours(until)))),
          status = status,
          createdAt = start,
      )

  private fun proposed(
      id: String,
      title: String,
      hour: Long,
      teamName: String?,
      needed: Int = 1,
      assigned: List<String?> = emptyList(),
      proposed: List<String?> = emptyList(),
      missing: Int = 0,
      isGeneral: Boolean = false,
  ) =
      ProposedMission(
          id = id,
          title = title,
          startAt = start.plus(Duration.ofHours(hour)),
          endAt = start.plus(Duration.ofHours(hour + 1)),
          isGeneral = isGeneral,
          teamName = teamName,
          volunteersNeeded = needed,
          assigned = assigned,
          proposed = proposed,
          missing = missing,
      )

  private fun team(
      id: String,
      name: String,
      managerId: String? = null,
      memberIds: List<String> = emptyList(),
  ) =
      Team(
          id = id,
          eventId = eventId,
          name = name,
          description = "",
          icon = "truck",
          managerId = managerId,
          memberIds = memberIds,
          createdAt = start,
      )

  private fun event() =
      Event(
          id = eventId,
          organizerId = "organizer-1",
          title = "Paléo Festival",
          description = "",
          type = EventType.MUSIC,
          startAt = start,
          endAt = start.plus(Duration.ofDays(1)),
          location = EventLocation("Nyon"),
          joinCode = "ABC234",
          createdAt = start,
      )
}

/** Delegates to [missions], and holds the save of the proposal until [save] completes. */
private class HeldMissionRepository(private val missions: FakeMissionRepository) :
    MissionRepository by missions {

  val save = CompletableDeferred<Unit>()

  override suspend fun addAssignees(eventId: String, additions: Map<String, List<String>>) {
    save.await()
    missions.addAssignees(eventId, additions)
  }
}

/** Delegates to [events], and holds each read of the event until [read] completes. */
private class HeldEventRepository(private val events: FakeEventRepository) :
    EventRepository by events {

  val read = CompletableDeferred<Unit>()

  override suspend fun getEvent(eventId: String): Event? {
    read.await()
    return events.getEvent(eventId)
  }
}

/** Delegates to [users], and holds each read of a name until [read] completes. */
private class HeldUserRepository(private val users: FakeUserRepository) : UserRepository by users {

  val read = CompletableDeferred<Unit>()

  override suspend fun getPublicProfile(uid: String): PublicProfile? {
    read.await()
    return users.getPublicProfile(uid)
  }
}
