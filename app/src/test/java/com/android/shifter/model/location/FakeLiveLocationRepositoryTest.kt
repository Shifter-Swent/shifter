// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the contracts the fake has to keep on its own: one position per volunteer, kept apart from
 * the other teams and emitted again as it changes. What the backend guarantees is left to the
 * emulator tests, and what a screen does with it to the ViewModel tests.
 */
class FakeLiveLocationRepositoryTest {

  private val repository = FakeLiveLocationRepository()

  @Test
  fun removeLocation_dropsOnlyThatVolunteersPosition() = runTest {
    repository.shareLocation(EVENT, TEAM, location(VOLUNTEER))
    repository.shareLocation(EVENT, TEAM, location(OTHER_VOLUNTEER))

    repository.removeLocation(EVENT, TEAM, VOLUNTEER)

    assertEquals(
        listOf(location(OTHER_VOLUNTEER)),
        repository.observeTeamLocations(EVENT, TEAM).first(),
    )
  }

  @Test
  fun observeTeamLocations_emitsAgainAsThePositionsChange() =
      runTest(UnconfinedTestDispatcher()) {
        val emissions = mutableListOf<List<LiveLocation>>()
        backgroundScope.launch { repository.observeTeamLocations(EVENT, TEAM).toList(emissions) }

        repository.shareLocation(EVENT, TEAM, location(VOLUNTEER))
        repository.shareLocation(EVENT, TEAM, location(VOLUNTEER, LAUSANNE, LATER))
        repository.removeLocation(EVENT, TEAM, VOLUNTEER)

        assertEquals(
            listOf(
                emptyList(),
                listOf(location(VOLUNTEER)),
                listOf(location(VOLUNTEER, LAUSANNE, LATER)),
                emptyList(),
            ),
            emissions,
        )
      }

  @Test
  fun shareLocation_inAnotherTeamOrEvent_leavesTheTeamsPositionsAlone() = runTest {
    repository.shareLocation(EVENT, TEAM, location(VOLUNTEER))

    repository.shareLocation(EVENT, OTHER_TEAM, location(OTHER_VOLUNTEER))
    repository.shareLocation(OTHER_EVENT, TEAM, location(OTHER_VOLUNTEER))

    assertEquals(listOf(location(VOLUNTEER)), repository.observeTeamLocations(EVENT, TEAM).first())
    assertEquals(
        listOf(location(OTHER_VOLUNTEER)),
        repository.observeTeamLocations(EVENT, OTHER_TEAM).first(),
    )
    assertEquals(
        listOf(location(OTHER_VOLUNTEER)),
        repository.observeTeamLocations(OTHER_EVENT, TEAM).first(),
    )
  }

  private fun location(
      userId: String,
      position: GeoPosition = GENEVA,
      measuredAt: Instant = NOW,
  ) = LiveLocation(userId, position, measuredAt)

  private companion object {
    const val EVENT = "event-1"
    const val OTHER_EVENT = "event-2"
    const val TEAM = "team-1"
    const val OTHER_TEAM = "team-2"
    const val VOLUNTEER = "volunteer-1"
    const val OTHER_VOLUNTEER = "volunteer-2"
    val GENEVA = GeoPosition(46.2044, 6.1432)
    val LAUSANNE = GeoPosition(46.5197, 6.6323)
    val NOW: Instant = Instant.parse("2026-10-31T09:00:00Z")
    val LATER: Instant = NOW.plusSeconds(60)
  }
}
