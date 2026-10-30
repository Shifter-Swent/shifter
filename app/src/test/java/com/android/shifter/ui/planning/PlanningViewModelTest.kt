// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.planning

import com.swent.shifter.model.checkin.CheckIn
import com.swent.shifter.model.checkin.CheckInMethod
import com.swent.shifter.model.checkin.CheckInRepository
import com.swent.shifter.model.checkin.CheckInRepositoryException
import com.swent.shifter.model.checkin.FakeCheckInRepository
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionLocation
import com.swent.shifter.model.mission.MissionRepositoryException
import com.swent.shifter.utils.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlanningViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val missions = FakeMissionRepository()
  private val checkIns = FakeCheckInRepository(currentUserId = USER)

  private var userId: String? = USER

  private fun viewModel() = PlanningViewModel(EVENT, missions, checkIns) { userId }

  private suspend fun create(
      title: String,
      eventId: String = EVENT,
      assignees: List<String> = listOf(USER),
      location: MissionLocation? = null,
  ): Mission =
      missions.createMission(
          Mission(
              eventId = eventId,
              title = title,
              description = "",
              volunteersNeeded = 2,
              startAt = START,
              endAt = START.plusSeconds(3600),
              location = location,
              assigneeIds = assignees,
              createdAt = START,
          )
      )

  private fun checkIn(mission: Mission, at: Instant) =
      checkIns.add(CheckIn(mission.eventId, mission.id, USER, at, CheckInMethod.SELF, USER))

  @Test
  fun load_keepsOnlyTheMissionsOfTheEventTheUserIsAssignedTo() = runTest {
    val mine = create("Hand out water")
    create("Another event", eventId = "other-event")
    create("Someone else's", assignees = listOf("someone-else"))

    val state = viewModel().uiState.value

    assertEquals(listOf(mine.id), state.missions.map { it.id })
    assertFalse(state.isLoading)
    assertNull(state.error)
  }

  @Test
  fun load_mapsTheMissionWithItsAddressAndCheckIn() = runTest {
    val located = create("Hand out water", location = STATION)
    val unlocated = create("Break")
    checkIn(located, CHECKED_IN_AT)

    val shown = viewModel().uiState.value.missions.associateBy { it.id }

    assertEquals(
        PlanningMission(
            located.id,
            "Hand out water",
            START,
            located.endAt,
            STATION.address,
            CHECKED_IN_AT,
        ),
        shown[located.id],
    )
    assertEquals(
        PlanningMission(unlocated.id, "Break", START, unlocated.endAt, null, null),
        shown[unlocated.id],
    )
  }

  @Test
  fun load_withoutMissions_showsAnEmptyList() {
    val state = viewModel().uiState.value

    assertTrue(state.missions.isEmpty())
    assertFalse(state.isLoading)
    assertNull(state.error)
  }

  @Test
  fun checkIn_whileThePlanningIsOpen_showsUp() = runTest {
    val mission = create("Hand out water")
    val viewModel = viewModel()
    assertNull(viewModel.uiState.value.missions.single().checkedInAt)

    checkIn(mission, CHECKED_IN_AT)

    assertEquals(CHECKED_IN_AT, viewModel.uiState.value.missions.single().checkedInAt)
  }

  @Test
  fun load_whenOffline_showsTheOfflineError() {
    missions.failure = MissionRepositoryException.Unavailable()

    val state = viewModel().uiState.value

    assertEquals(PlanningError.OFFLINE, state.error)
    assertFalse(state.isLoading)
  }

  @Test
  fun load_whenTheBackendFails_showsTheUnexpectedError() {
    missions.failure = MissionRepositoryException.Unknown()

    assertEquals(PlanningError.UNEXPECTED, viewModel().uiState.value.error)
  }

  @Test
  fun load_withoutSignedInUser_showsTheUnexpectedError() {
    userId = null

    assertEquals(PlanningError.UNEXPECTED, viewModel().uiState.value.error)
  }

  @Test
  fun load_afterAnError_showsTheMissions() = runTest {
    val mission = create("Hand out water")
    missions.failure = MissionRepositoryException.Unavailable()
    val viewModel = viewModel()

    missions.failure = null
    viewModel.load()

    assertNull(viewModel.uiState.value.error)
    assertEquals(listOf(mission.id), viewModel.uiState.value.missions.map { it.id })
  }

  @Test
  fun load_again_stillUpdatesTheCheckIns() = runTest {
    val mission = create("Hand out water")
    val viewModel = viewModel()

    viewModel.load()
    checkIn(mission, CHECKED_IN_AT)

    assertEquals(CHECKED_IN_AT, viewModel.uiState.value.missions.single().checkedInAt)
  }

  @Test
  fun load_whenTheCheckInsFail_showsTheMissionsWithoutStatus() = runTest {
    val mission = create("Hand out water")
    checkIns.failure = CheckInRepositoryException.Unavailable()

    val state = viewModel().uiState.value

    assertEquals(listOf(mission.id), state.missions.map { it.id })
    assertNull(state.missions.single().checkedInAt)
    assertFalse(state.isLoading)
    assertNull(state.error)
    assertTrue(state.checkInsUnavailable)
  }

  @Test
  fun load_whenTheCheckInsFailAfterAStatus_keepsTheStatus() = runTest {
    val mission = create("Hand out water")
    // Emits the check-in, then fails, as a listener that loses the backend would.
    val failingAfterAStatus =
        object : CheckInRepository by checkIns {
          override fun observeMyCheckIns(eventId: String, userId: String) = flow {
            emit(listOf(CheckIn(EVENT, mission.id, USER, CHECKED_IN_AT, CheckInMethod.SELF, USER)))
            throw CheckInRepositoryException.Unavailable()
          }
        }

    val state = PlanningViewModel(EVENT, missions, failingAfterAStatus) { userId }.uiState.value

    assertEquals(CHECKED_IN_AT, state.missions.single().checkedInAt)
    assertTrue(state.checkInsUnavailable)
  }

  @Test
  fun load_afterTheCheckInsFailed_clearsTheMessage() = runTest {
    create("Hand out water")
    checkIns.failure = CheckInRepositoryException.Unavailable()
    val viewModel = viewModel()

    checkIns.failure = null
    viewModel.load()

    assertFalse(viewModel.uiState.value.checkInsUnavailable)
  }

  private companion object {
    const val EVENT = "event-1"
    const val USER = "volunteer-1"
    val START: Instant = Instant.parse("2025-06-14T08:30:00Z")
    val CHECKED_IN_AT: Instant = Instant.parse("2025-06-14T08:31:00Z")
    val STATION = MissionLocation("Hydration station A", 45.75, 4.83)
  }
}
