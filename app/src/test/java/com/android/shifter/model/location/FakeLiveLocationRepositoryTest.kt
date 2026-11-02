// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two things a consumer of the fake can get wrong if it does not hold: its flows are live, and
 * each event and team is a collection of its own. Storing and removing a position are covered along
 * the way by the emissions, and what the backend allows is left to `LiveLocationRulesTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeLiveLocationRepositoryTest {

  private val repository = FakeLiveLocationRepository(currentUserId = VOLUNTEER)

  @Test
  fun observeTeamLocations_emitsAgainAsThePositionsChange() =
      runTest(UnconfinedTestDispatcher()) {
        val emissions = mutableListOf<List<LiveLocation>>()
        backgroundScope.launch { repository.observeTeamLocations(EVENT, TEAM).toList(emissions) }

        repository.shareLocation(EVENT, TEAM, GENEVA, NOW)
        repository.shareLocation(EVENT, TEAM, LAUSANNE, LATER)
        repository.removeLocation(EVENT, TEAM)

        // Empty before anything is shared, then the position, then the one replacing it rather than
        // a second one, then empty again: a collector sees every change without resubscribing.
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
    repository.shareLocation(EVENT, TEAM, GENEVA, NOW)

    repository.currentUserId = OTHER_VOLUNTEER
    repository.shareLocation(EVENT, OTHER_TEAM, GENEVA, NOW)
    repository.shareLocation(OTHER_EVENT, TEAM, GENEVA, NOW)

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
