// Co-authored-by: Claude Opus 4.8 <noreply@anthropic.com>
package com.swent.shifter.ui.location

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.swent.shifter.model.RepositoryProvider
import com.swent.shifter.model.location.LiveLocationCoordinator
import com.swent.shifter.model.location.LiveLocationPublisher
import com.swent.shifter.model.location.LiveLocationSessions
import com.swent.shifter.model.location.LocationProvider
import com.swent.shifter.model.location.RepositoryLiveLocationTargets
import com.swent.shifter.model.location.runLiveLocationPublishing
import com.swent.shifter.model.location.sharingConsent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val TAG = "LiveLocationPublishing"

/**
 * Publishes the signed-in volunteer's eligible positions while Shifter is in the foreground,
 * whatever the screen. It draws nothing: it is an application-level effect, placed once at the
 * navigation root rather than on a screen, so navigating between screens neither starts a second
 * coordinator nor stops the one running.
 *
 * Publishing follows the foreground and the session together, through [publishWhileForeground]:
 * backgrounding cancels every session and stops the GPS, returning to the foreground starts a fresh
 * eligibility check, and signing out or switching accounts cancels the previous user's sessions at
 * once. A lifecycle cancellation is not a consent revoked, so it leaves the stored position for the
 * next session rather than deleting it, as [LiveLocationPublisher] documents.
 *
 * @param repositories the repositories the targets, the sessions and the consent are read from.
 * @param locationProvider the device's position, the same provider the automatic check-in and the
 *   map collect; a session only collects it while sharing is on and the permission is granted.
 */
@Composable
fun LiveLocationPublishing(
    repositories: RepositoryProvider,
    locationProvider: LocationProvider,
) {
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val auth = repositories.authRepository
  LaunchedEffect(lifecycle, repositories, locationProvider) {
    publishWhileForeground(
        lifecycle = lifecycle,
        // The session restored at launch, then every sign-in and sign-out.
        userIds = auth.authState.map { it?.uid }.distinctUntilChanged(),
        publish = { userId ->
          runLiveLocationPublishing(
              coordinator = buildCoordinator(userId, repositories, locationProvider),
              consent = repositories.userRepository.sharingConsent(userId),
              onSessionFault = { Log.e(TAG, "A live location session failed unexpectedly", it) },
          )
        },
    )
  }
}

/**
 * Runs [publish] for the signed-in user while the app is at least [Lifecycle.State.STARTED], and
 * only then.
 *
 * It is the lifecycle and sign-in boundary, kept apart from Compose so it can be tested with a
 * [androidx.lifecycle.testing.TestLifecycleOwner] and virtual time:
 * - **[repeatOnLifecycle] bounds the foreground.** The app going to the background cancels
 *   [publish]
 *     - and with it the GPS and every session - and returning to the foreground runs it again from
 *       scratch, a fresh eligibility check rather than a resumed one.
 * - **[collectLatest] bounds the session.** Signing out (`null`) cancels [publish] at once,
 *   whatever the lifecycle, and switching accounts cancels the previous user's run before starting
 *   the new one, so no session outlives the account it belongs to.
 *
 * @param userIds the signed-in user's id over time, `null` while signed out.
 * @param publish shares the given user's eligible positions until cancelled.
 */
suspend fun publishWhileForeground(
    lifecycle: Lifecycle,
    userIds: Flow<String?>,
    publish: suspend (userId: String) -> Unit,
): Unit =
    userIds.distinctUntilChanged().collectLatest { userId ->
      if (userId != null) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { publish(userId) }
      }
    }

/** A coordinator publishing [userId]'s eligible positions, built from [repositories]. */
private fun buildCoordinator(
    userId: String,
    repositories: RepositoryProvider,
    locationProvider: LocationProvider,
): LiveLocationCoordinator =
    LiveLocationCoordinator(
        targets =
            RepositoryLiveLocationTargets(
                eventRepository = repositories.eventRepository,
                membershipRequestRepository = repositories.membershipRequestRepository,
                teamRepository = repositories.teamRepository,
                currentUserId = { userId },
            ),
        sessions =
            LiveLocationSessions.of(
                LiveLocationPublisher(locationProvider, repositories.liveLocationRepository)
            ),
    )
