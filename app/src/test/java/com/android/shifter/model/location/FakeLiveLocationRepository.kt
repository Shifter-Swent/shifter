// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [LiveLocationRepository] for unit tests. Its flows are live: a position shared while a
 * test collects them is emitted right away.
 *
 * Like the backend, it keeps one position per volunteer and team, so sharing again replaces the
 * previous one. It does not know who manages which team: any caller may share, remove and observe.
 */
class FakeLiveLocationRepository : LiveLocationRepository {

  private val state = MutableStateFlow<Map<TeamKey, List<LiveLocation>>>(emptyMap())

  override suspend fun shareLocation(eventId: String, teamId: String, location: LiveLocation) {
    state.update { locations ->
      val team = locations[TeamKey(eventId, teamId)].orEmpty()
      locations +
          (TeamKey(eventId, teamId) to team.filterNot { it.userId == location.userId } + location)
    }
  }

  override suspend fun removeLocation(eventId: String, teamId: String, userId: String) {
    state.update { locations ->
      val team = locations[TeamKey(eventId, teamId)] ?: return@update locations
      locations + (TeamKey(eventId, teamId) to team.filterNot { it.userId == userId })
    }
  }

  /**
   * A change in another team leaves this team's list as is: not emitted, like a Firestore listener
   * that only sees its own query.
   */
  override fun observeTeamLocations(eventId: String, teamId: String): Flow<List<LiveLocation>> =
      state.map { it[TeamKey(eventId, teamId)].orEmpty() }.distinctUntilChanged()

  /** The team a position was shared in, which [LiveLocation] does not carry itself. */
  private data class TeamKey(val eventId: String, val teamId: String)
}
