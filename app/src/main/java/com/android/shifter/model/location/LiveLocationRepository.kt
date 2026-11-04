// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Shares the positions of the volunteers of a team with their manager, on the event day. ViewModels
 * depend on this interface, never on an implementation.
 *
 * The data contract every implementation keeps:
 * - A volunteer has at most one shared position per team, so [shareLocation] replaces the previous
 *   one rather than adding to it. Whose position it is never comes from the caller: it is always
 *   the signed-in user, so a caller cannot share or remove someone else's.
 * - A measure older than the one already shared is refused, so the position the manager sees never
 *   moves back in time when two devices or offline writes arrive out of order.
 * - [removeLocation] takes the signed-in user's position out again, and [observeTeamLocations]
 *   emits a team's positions as they change.
 *
 * Who may do any of it is enforced by `firestore.rules`, not by this interface, and writing is not
 * the mirror of reading:
 * - **Writing** is the volunteer's own, and only while they are in the team, have sharing on and
 *   the event is running (its period, with an hour of margin). Removing their own position is the
 *   exception: the rules always allow it.
 * - **Reading** is the team's manager's and the event's organizer's, and only while the event is
 *   running. Neither the consent nor the membership is checked there, so a position already shared
 *   stays readable to them once sharing is turned off or the volunteer leaves the team, until it is
 *   deleted or the event's period ends.
 *
 * The in-memory fake used by unit tests checks none of that on purpose, being a test double rather
 * than a second implementation of the policy.
 *
 * Two things are the caller's, not this contract's:
 * - **Consent:** the publishing logic must not share a position while the volunteer's
 *   [locationSharingEnabled][com.swent.shifter.model.user.User.locationSharingEnabled] is off, and
 *   `firestore.rules` refuse the write on their own if it tries. Turning it off stops the updates
 *   but does not remove the position already shared: the publishing logic has to call
 *   [removeLocation], and `docs/firestore-schema.md` records what that leaves open.
 * - **Staleness:** how old a position may be before it is ignored is left to the consumer, which
 *   knows [LiveLocation.measuredAt]. The rules only bound it in the future, so a wrong device clock
 *   cannot make a position look fresh for ever.
 */
interface LiveLocationRepository {

  /**
   * Shares [position], measured at [measuredAt], as the position of the signed-in volunteer in the
   * team [teamId] of [eventId], replacing the one they shared before, if any.
   *
   * @throws LiveLocationRepositoryException.PermissionDenied when the rules refuse the write, which
   *   includes sharing being off, no longer being in the team, the event not running, and
   *   [measuredAt] being older than the measure already stored.
   * @throws LiveLocationRepositoryException.Unavailable when the backend cannot be reached, or when
   *   an implementation gives up waiting for its acknowledgement. **It does not mean the position
   *   was not stored:** an implementation that queues writes while offline may still send this one
   *   when the connection is back, so a caller must read it as "not confirmed" rather than "not
   *   written". Retrying is allowed and sending the same [measuredAt] twice is not refused.
   */
  suspend fun shareLocation(
      eventId: String,
      teamId: String,
      position: GeoPosition,
      measuredAt: Instant,
  )

  /**
   * Stops sharing the position of the signed-in volunteer in the team [teamId] of [eventId], when
   * they turn sharing off or leave the event. Removing a position that was never shared has no
   * effect, and the rules allow it whatever the consent, the team and the event say.
   *
   * @throws LiveLocationRepositoryException.Unavailable when the backend cannot be reached, or when
   *   an implementation gives up waiting for its acknowledgement. As for [shareLocation], **it does
   *   not mean the position is still there:** a queued deletion may go out later. Removing again is
   *   harmless, so a caller may retry.
   */
  suspend fun removeLocation(eventId: String, teamId: String)

  /**
   * Emits the shared positions of the team [teamId] of [eventId], then again each time one of them
   * changes, so the manager's map follows the volunteers as they move. Emits an empty list when
   * nobody shares their position.
   *
   * The flow fails with a [LiveLocationRepositoryException.PermissionDenied] when the collector is
   * neither the team's manager nor the event's organizer, or when the event is not running.
   *
   * That refusal also ends an observation already running, since the right to read can be lost
   * while it is open: the event's period ends, or the organizer gives the team another manager. It
   * is the expected end of every event rather than a fault, so a consumer should distinguish it
   * from an unexpected failure and present it as sharing having ended. When it arrives is the
   * implementation's business: a backend may only notice on the next change to the team's
   * positions.
   */
  fun observeTeamLocations(eventId: String, teamId: String): Flow<List<LiveLocation>>
}
