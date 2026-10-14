// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FakeEventRepository
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionLocation
import com.swent.shifter.utils.MainDispatcherRule
import java.time.Instant
import java.time.ZoneOffset.UTC
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class MapViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val events = FakeEventRepository().apply { this.events += EVENT }
  private val missions = FakeMissionRepository()

  private var userId: String? = USER

  private fun viewModel() =
      MapViewModel(EVENT.id, events, missions, { userId }, { DAY.plusSeconds(12 * 3600L) }, UTC)

  @Test
  fun load_showsTheEventAndOneMarkerPerLocatedMissionInTimeOrder() = runTest {
    missions.createMission(mission("Cleanup", startHour = 14, endHour = 15, at = TENT))
    missions.createMission(mission("No place", startHour = 9, endHour = 10, at = null))
    missions.createMission(mission("Water", startHour = 10, endHour = 11, at = STAND))

    val state = viewModel().uiState.value

    assertEquals(EVENT.title, state.eventTitle)
    assertEquals(EVENT.location.address, state.venue)
    assertEquals(MapPoint(46.52, 6.57), state.center)
    assertEquals(
        listOf(
            MapMarker("mission-3", STAND.latitude, STAND.longitude, "Water", "10:00–11:00 · Stand"),
            MapMarker("mission-1", TENT.latitude, TENT.longitude, "Cleanup", "14:00–15:00 · Tent"),
        ),
        state.markers,
    )
    assertFalse(state.isLoading)
    assertNull(state.error)
  }

  @Test
  fun load_centersOnTheFirstMissionWhenTheVenueHasNoCoordinates() = runTest {
    events.events[0] = EVENT.copy(location = EventLocation("Somewhere"))
    missions.createMission(mission("Water", startHour = 10, endHour = 11, at = STAND))

    assertEquals(MapPoint(STAND.latitude, STAND.longitude), viewModel().uiState.value.center)
  }

  @Test
  fun load_reportsAnEventThatDoesNotExist() {
    events.events.clear()

    val state = viewModel().uiState.value

    assertEquals(MapError.EVENT_NOT_FOUND, state.error)
    assertFalse(state.isLoading)
  }

  @Test
  fun load_reportsEachFailureAndRetryClearsIt() {
    events.failure = EventRepositoryException.Unavailable()
    val viewModel = viewModel()
    assertEquals(MapError.OFFLINE, viewModel.uiState.value.error)

    events.failure = null
    missions.failure = IllegalStateException("boom")
    viewModel.load()
    assertEquals(MapError.UNEXPECTED, viewModel.uiState.value.error)

    missions.failure = null
    viewModel.load()
    assertNull(viewModel.uiState.value.error)
    assertEquals(EVENT.title, viewModel.uiState.value.eventTitle)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun load_highlightsTheNextMissionTheUserIsAssignedTo() = runTest {
    // At noon: the morning mission is over, the evening one is not theirs.
    missions.createMission(mission("Morning", 9, 10, STAND, assignees = listOf(USER)))
    missions.createMission(mission("Evening", 18, 19, TENT, assignees = listOf("someone")))
    missions.createMission(mission("Afternoon", 14, 15, TENT, assignees = listOf(USER)))

    assertEquals(NextRendezvous("mission-3", "14:00"), viewModel().uiState.value.next)
  }

  @Test
  fun load_hasNoNextRendezvousWithoutAMissionLeftOrAUser() = runTest {
    missions.createMission(mission("Morning", 9, 10, STAND, assignees = listOf(USER)))
    assertNull(viewModel().uiState.value.next)

    missions.createMission(mission("Afternoon", 14, 15, TENT, assignees = listOf(USER)))
    userId = null
    assertNull(viewModel().uiState.value.next)
  }

  @Test
  fun selectMarker_showsItsCardAndClearSelectionClosesIt() = runTest {
    missions.createMission(mission("Water", startHour = 10, endHour = 11, at = STAND))
    val viewModel = viewModel()
    val marker = viewModel.uiState.value.markers.single()

    viewModel.selectMarker(marker)
    assertEquals(marker, viewModel.uiState.value.selected)

    viewModel.clearSelection()
    assertNull(viewModel.uiState.value.selected)
  }

  private fun mission(
      title: String,
      startHour: Int,
      endHour: Int,
      at: MissionLocation?,
      assignees: List<String> = emptyList(),
  ) =
      Mission(
          eventId = EVENT.id,
          title = title,
          description = "",
          volunteersNeeded = 2,
          startAt = DAY.plusSeconds(startHour * 3600L),
          endAt = DAY.plusSeconds(endHour * 3600L),
          location = at,
          assigneeIds = assignees,
          createdAt = DAY,
      )

  private companion object {
    const val USER = "volunteer"
    val DAY: Instant = Instant.parse("2026-06-01T00:00:00Z")
    val TENT = MissionLocation("Tent", 46.521, 6.571)
    val STAND = MissionLocation("Stand", 46.519, 6.569)
    val EVENT =
        Event(
            id = "event-1",
            organizerId = "organizer",
            title = "City Marathon",
            description = "",
            type = EventType.SPORT,
            startAt = DAY,
            endAt = DAY.plusSeconds(86_400),
            location = EventLocation("Place Bellecour", 46.52, 6.57),
            createdAt = DAY,
        )
  }
}
