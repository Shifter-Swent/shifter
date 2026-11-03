// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import java.time.Duration
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Shares the signed-in volunteer's position with their team while they consent to it, reading the
 * device through [locationProvider] and writing it through [repository].
 *
 * [publish] runs until it is cancelled, so the caller decides how long a session lasts: a ViewModel
 * collecting it while its screen is on keeps the sharing to the foreground, as [LocationProvider]
 * requires. Running it twice for the same team at once would publish twice, so a caller keeps one
 * session per team.
 *
 * What it does not do:
 * - **It does not read the consent itself.** The volunteer's
 *   [locationSharingEnabled][com.swent.shifter.model.user.User.locationSharingEnabled] has no
 *   reactive API, so [publish] takes it as a flow from whoever already knows it. The rules check it
 *   again on every write, so a stale `true` is refused rather than shared.
 * - **It does not check the location permission.** [LocationProvider.positions] already emits only
 *   while the status is [LocationStatus.GRANTED] and resumes on its own when it becomes so again,
 *   which is exactly the behaviour wanted here.
 *
 * [LocationProvider.positions] emits a [GeoPosition] with no time of its own, so each position is
 * stamped with [now] when it is collected rather than when the GPS measured it:
 * [LiveLocation.measuredAt] is the delivery time, a little later than the fix it describes.
 * Carrying the real fix time means emitting more than a bare [GeoPosition], a change to
 * [LocationProvider] left out of here on purpose; #276 already plans to widen that emission for the
 * accuracy, and the fix time belongs with it.
 *
 * @param now the clock stamping each position, replaceable in tests.
 */
class LiveLocationPublisher(
    private val locationProvider: LocationProvider,
    private val repository: LiveLocationRepository,
    private val now: () -> Instant = Instant::now,
) {

  /**
   * Shares the volunteer's position in the team [teamId] of [eventId] while [sharingEnabled] says
   * they consent to it, until this call is cancelled.
   *
   * **The consent off means no position stored**, whether it is turned off during this call or
   * already off when it starts. A session that starts off removes it too, because the volunteer may
   * have turned it off with no session running, from another device, or with the app killed before
   * the removal went out, and nothing else would take it out. Removing a position that is not there
   * is a no-op, which is what [LiveLocationRepository.removeLocation] being idempotent is for, so
   * this costs a write only when there is something to remove. No position is read while the
   * consent is off either: the GPS is not even collected.
   *
   * Cancelling this call removes nothing: the position stays for the next session to replace, since
   * a screen going away is not a volunteer changing their mind. Turning the consent back on starts
   * sharing again.
   *
   * A write refused or lost is not fatal: the next position that passes the gates tries again, and
   * nothing is ever deleted because of it. A refusal may well be a consent revoked behind the app's
   * back, but it may just as well be a measure that arrived out of order, an event whose period is
   * over or a volunteer no longer in the team, and the backend does not tell them apart (see
   * [LiveLocationRepositoryException.PermissionDenied]). Removing the position on that alone would
   * throw away a position that is still valid, so only [sharingEnabled] turning off does it.
   *
   * **A removal the backend refuses is tried again**, after [REMOVAL_RETRY_DELAY] and then twice as
   * long each time up to [MAX_REMOVAL_RETRY_DELAY], for as long as the consent stays off and this
   * call runs. Nothing else would retry it, and the volunteer would stay on their manager's map. So
   * this call does not fail on a removal the backend refuses, and a ViewModel collecting it has
   * nothing to catch. The consent coming back cancels the retries, sharing again replacing the
   * position anyway, and so does cancelling this call, which leaves the position to the next
   * session. A device that never comes back online therefore keeps the position until the event's
   * period ends: closing that needs the backend mechanism #305 plans separately.
   */
  suspend fun publish(eventId: String, teamId: String, sharingEnabled: Flow<Boolean>) {
    // collectLatest cancels the sharing as soon as the consent is turned off, so no write starts
    // after it, and cancels the removal as soon as it is turned back on.
    sharingEnabled.distinctUntilChanged().collectLatest { enabled ->
      if (enabled) shareWhileGranted(eventId, teamId) else removeUntilItIsOut(eventId, teamId)
    }
  }

  /**
   * Takes the position out, trying again until it is really out. Only
   * [LiveLocationRepositoryException] is caught, so cancelling this call - the consent coming back,
   * or the session ending - interrupts the wait and stops the retries at once.
   */
  private suspend fun removeUntilItIsOut(eventId: String, teamId: String) {
    var retryIn = REMOVAL_RETRY_DELAY
    while (true) {
      try {
        repository.removeLocation(eventId, teamId)
        return
      } catch (_: LiveLocationRepositoryException) {
        delay(retryIn.toKotlinDuration())
        retryIn = minOf(retryIn.multipliedBy(2), MAX_REMOVAL_RETRY_DELAY)
      }
    }
  }

  /** Shares every position worth a write, for as long as this call is not cancelled. */
  private suspend fun shareWhileGranted(eventId: String, teamId: String) {
    var lastAttemptAt: Instant? = null
    var lastShared: GeoPosition? = null
    // Sequential on purpose: the next position waits for the write of the previous one, so two
    // writes of the same volunteer never overlap.
    locationProvider.positions().collect { position ->
      val measuredAt = now()
      if (!isWorthSharing(position, measuredAt, lastShared, lastAttemptAt)) return@collect
      // Counted as attempted whether it succeeds or not, so a backend that keeps refusing costs
      // one write per interval rather than one per position.
      lastAttemptAt = measuredAt
      if (share(eventId, teamId, position, measuredAt)) lastShared = position
    }
  }

  /**
   * Whether [position] is worth a write: never before [MIN_INTERVAL] has passed, then as soon as
   * the volunteer has moved [MIN_DISTANCE_METERS], and in any case once [REFRESH_INTERVAL] has
   * passed, so a volunteer who stands still keeps a position that does not look stale.
   *
   * A clock that jumps backwards only delays the next write, instead of sending a measure older
   * than the stored one, which the rules would refuse.
   */
  private fun isWorthSharing(
      position: GeoPosition,
      measuredAt: Instant,
      lastShared: GeoPosition?,
      lastAttemptAt: Instant?,
  ): Boolean =
      when {
        // The first position of a session is always worth it: the manager has nothing yet.
        lastAttemptAt == null -> true
        measuredAt < lastAttemptAt + MIN_INTERVAL -> false
        // Nothing stored yet, because the attempts so far failed: distance has no baseline.
        lastShared == null -> true
        measuredAt >= lastAttemptAt + REFRESH_INTERVAL -> true
        else -> lastShared.distanceTo(position) >= MIN_DISTANCE_METERS
      }

  /**
   * Shares [position] and says whether the backend **confirmed** the write. A refusal, a missing
   * connection or an unexpected failure all answer false and are left to the next position: this
   * session goes on, and the position already shared is kept, whatever the cause.
   *
   * False does not mean nothing was stored. Firestore keeps a write it could not confirm and sends
   * it when the connection is back, so a position reported here as unconfirmed may still reach the
   * backend later. False only means this session cannot count on it: it keeps the older position as
   * the distance baseline, which at worst costs one write more than needed.
   */
  private suspend fun share(
      eventId: String,
      teamId: String,
      position: GeoPosition,
      measuredAt: Instant,
  ): Boolean =
      try {
        repository.shareLocation(eventId, teamId, position, measuredAt)
        true
      } catch (e: CancellationException) {
        throw e
      } catch (_: LiveLocationRepositoryException) {
        false
      }

  companion object {
    /**
     * How long before another position is written, to keep the writes and the reads down. It spaces
     * the writes only: [LocationProvider] goes on sampling the GPS as often as it does, so this is
     * no saving of battery.
     */
    val MIN_INTERVAL: Duration = Duration.ofSeconds(30)

    /** How far the volunteer has to have moved for a new position to be worth a write. */
    const val MIN_DISTANCE_METERS = 10.0

    /**
     * How long a stored position may go without being written again. Without it a volunteer who
     * stands still would keep a [LiveLocation.measuredAt] that ages until a reader drops it as
     * stale, even though they are there and still sharing.
     */
    val REFRESH_INTERVAL: Duration = Duration.ofMinutes(2)

    /** How long before a refused removal is tried again, the first time. */
    val REMOVAL_RETRY_DELAY: Duration = Duration.ofSeconds(5)

    /**
     * The longest the retries of a removal ever wait. Each attempt waits twice as long as the one
     * before, up to this, so a backend that is down for an hour costs a handful of attempts rather
     * than one every five seconds, and the position still goes out as soon as it answers.
     */
    val MAX_REMOVAL_RETRY_DELAY: Duration = Duration.ofMinutes(2)
  }
}
