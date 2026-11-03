// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [LiveLocationRepository] for unit tests. Its flows are live: a position shared while a
 * test collects them is emitted right away.
 *
 * Like the backend, it keeps one position per volunteer and team, always [currentUserId]'s, so
 * sharing again replaces the previous one, and it refuses a measure older than the one already
 * shared. It does not know who manages which team, nor whether sharing is on: any caller may share,
 * remove and observe, so a test that wants a refusal sets [failure].
 *
 * @property currentUserId the signed-in volunteer, recorded as [LiveLocation.userId].
 * @property failure when set, every call and every new collection throws it, to simulate a backend
 *   error.
 */
class FakeLiveLocationRepository(
    var currentUserId: String = "user-1",
    var failure: LiveLocationRepositoryException? = null,
) : LiveLocationRepository {

  private val state = MutableStateFlow<Map<TeamKey, List<LiveLocation>>>(emptyMap())

  /**
   * Refuses a measure older than the one already shared, like the rules do, with the
   * [LiveLocationRepositoryException.PermissionDenied] the backend answers in that case.
   */
  override suspend fun shareLocation(
      eventId: String,
      teamId: String,
      position: GeoPosition,
      measuredAt: Instant,
  ) {
    failure?.let { throw it }
    val key = TeamKey(eventId, teamId)
    // Checks and replaces in one atomic update, so a stale read cannot let a regression through.
    state.update { locations ->
      val team = locations[key].orEmpty()
      val shared = team.firstOrNull { it.userId == currentUserId }
      if (shared != null && measuredAt < shared.measuredAt) {
        throw LiveLocationRepositoryException.PermissionDenied()
      }
      val others = team.filterNot { it.userId == currentUserId }
      locations + (key to others + LiveLocation(currentUserId, position, measuredAt))
    }
  }

  override suspend fun removeLocation(eventId: String, teamId: String) {
    failure?.let { throw it }
    val key = TeamKey(eventId, teamId)
    state.update { locations ->
      val team = locations[key] ?: return@update locations
      locations + (key to team.filterNot { it.userId == currentUserId })
    }
  }

  override fun observeTeamLocations(eventId: String, teamId: String): Flow<List<LiveLocation>> =
      flow {
        failure?.let { throw it }
        // A change in another team leaves this team's list as is: not emitted, like a Firestore
        // listener that only sees its own query.
        emitAll(state.map { it[TeamKey(eventId, teamId)].orEmpty() }.distinctUntilChanged())
      }

  /** The team a position was shared in, which [LiveLocation] does not carry itself. */
  private data class TeamKey(val eventId: String, val teamId: String)
}
