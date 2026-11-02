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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a consumer of the fake can get wrong if it does not hold: its flows are live, each event and
 * team is a collection of its own, one volunteer's position is independent of their teammates', a
 * measure older than the stored one is refused without replacing it, and a failure reaches the
 * caller as the type it was given. Storing a position is covered along the way by the emissions,
 * and whether the backend allows any of it is left to `LiveLocationRulesTest`.
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

  @Test
  fun shareLocation_withAnOlderMeasure_isRefusedAndKeepsTheStoredPosition() = runTest {
    repository.shareLocation(EVENT, TEAM, LAUSANNE, LATER)

    val shared = runCatching { repository.shareLocation(EVENT, TEAM, GENEVA, NOW) }

    // Refused like the rules refuse it, and refused in full: the position and the time it was
    // measured at both stay the newer ones, rather than the older write landing in part.
    assertTrue(shared.exceptionOrNull() is LiveLocationRepositoryException.PermissionDenied)
    assertEquals(
        listOf(location(VOLUNTEER, LAUSANNE, LATER)),
        repository.observeTeamLocations(EVENT, TEAM).first(),
    )
  }

  @Test
  fun removeLocation_keepsTheOtherVolunteersPositionOfTheSameTeam() = runTest {
    repository.shareLocation(EVENT, TEAM, GENEVA, NOW)
    repository.currentUserId = OTHER_VOLUNTEER
    repository.shareLocation(EVENT, TEAM, LAUSANNE, LATER)

    repository.currentUserId = VOLUNTEER
    repository.removeLocation(EVENT, TEAM)

    // One volunteer leaving the map does not take their teammates off it.
    assertEquals(
        listOf(location(OTHER_VOLUNTEER, LAUSANNE, LATER)),
        repository.observeTeamLocations(EVENT, TEAM).first(),
    )
  }

  @Test
  fun failure_isPropagatedUnchanged() = runTest {
    // The publisher has to tell a refusal from a missing connection to decide between cleaning up
    // and retrying, so the fake must hand back the very exception it was given rather than one of
    // its own. One operation is enough: each of them throws [failure] before touching the state.
    for (failure in
        listOf(
            LiveLocationRepositoryException.PermissionDenied(),
            LiveLocationRepositoryException.Unavailable(),
            LiveLocationRepositoryException.Unknown(),
        )) {
      repository.failure = failure

      val shared = runCatching { repository.shareLocation(EVENT, TEAM, GENEVA, NOW) }

      assertSame(failure, shared.exceptionOrNull())
    }
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
