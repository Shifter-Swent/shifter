// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import kotlinx.coroutines.flow.Flow

/**
 * Shares the positions of the volunteers of a team with their manager, on the event day. ViewModels
 * depend on this interface, never on an implementation.
 *
 * The data contract every implementation keeps:
 * - A volunteer has at most one shared position per team, so [shareLocation] replaces the previous
 *   one rather than adding to it.
 * - [removeLocation] takes that position out again, and [observeTeamLocations] emits a team's
 *   positions as they change.
 *
 * Who may do any of it is enforced by `firestore.rules`, not by this interface: only the volunteer
 * themself writes or removes their position, and only the manager of the team and the organizer of
 * the event read the team's. The in-memory fake used by unit tests checks none of that on purpose,
 * being a test double rather than a second implementation of the policy.
 *
 * Two things are the caller's, not this contract's:
 * - **Consent:** the publishing logic must not share a position while the volunteer's
 *   [locationSharingEnabled][com.swent.shifter.model.user.User.locationSharingEnabled] is off, and
 *   `firestore.rules` refuse the write on their own if it tries.
 * - **Staleness:** how old a position may be before it is ignored is left to the consumer, which
 *   knows [LiveLocation.measuredAt].
 */
interface LiveLocationRepository {

  /**
   * Shares [location] as the position of its [userId][LiveLocation.userId] in the team [teamId] of
   * [eventId], replacing the one shared before, if any.
   */
  suspend fun shareLocation(eventId: String, teamId: String, location: LiveLocation)

  /**
   * Stops sharing the position of [userId] in the team [teamId] of [eventId], when they turn
   * sharing off or leave the event. Removing a position that was never shared has no effect.
   */
  suspend fun removeLocation(eventId: String, teamId: String, userId: String)

  /**
   * Emits the shared positions of the team [teamId] of [eventId], then again each time one of them
   * changes, so the manager's map follows the volunteers as they move. Emits an empty list when
   * nobody shares their position.
   */
  fun observeTeamLocations(eventId: String, teamId: String): Flow<List<LiveLocation>>
}
