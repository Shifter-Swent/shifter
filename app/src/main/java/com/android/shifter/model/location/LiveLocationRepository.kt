// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import kotlinx.coroutines.flow.Flow

/**
 * Shares the positions of the volunteers of a team with their manager, on the event day. ViewModels
 * depend on this interface, never on an implementation.
 *
 * Rules every implementation enforces:
 * - A volunteer has at most one shared position per team, so [shareLocation] replaces the previous
 *   one rather than adding to it.
 * - Only the volunteer themself shares or removes their position.
 * - Only the manager of the team and the organizer of the event observe the team's positions.
 *
 * Whether a position may be shared at all is not decided here: the volunteer's
 * [locationSharingEnabled][com.swent.shifter.model.user.User.locationSharingEnabled] is checked by
 * the caller, and how old a position may be is left to the reader, which knows
 * [LiveLocation.measuredAt].
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
