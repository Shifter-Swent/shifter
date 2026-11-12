// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
package com.swent.shifter.model.location

import java.time.Instant
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A [LiveLocationRepository] whose writes never answer, as Firestore's did offline before #343
 * bounded them, and as a write queued offline does not until the device reconnects. Only the counts
 * matter: whether the client began another write or removal. Nothing is stored.
 */
internal class UnansweringLiveLocationRepository : LiveLocationRepository {
  var writes = 0
    private set

  var removals = 0
    private set

  override suspend fun shareLocation(
      eventId: String,
      teamId: String,
      position: GeoPosition,
      measuredAt: Instant,
  ) {
    writes++
    awaitCancellation()
  }

  override suspend fun removeLocation(eventId: String, teamId: String) {
    removals++
  }

  override fun observeTeamLocations(eventId: String, teamId: String): Flow<List<LiveLocation>> =
      emptyFlow()
}
