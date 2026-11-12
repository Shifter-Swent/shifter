// Co-authored-by: Claude Opus 4.8 <noreply@anthropic.com>
package com.swent.shifter.model.location

import com.swent.shifter.model.user.UserRepository
import com.swent.shifter.model.user.UserRepositoryException
import java.time.Duration
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * How long to wait before observing the consent again after a failed observation. The gap is spent
 * with sharing reported off, so GPS stays stopped until the observation recovers.
 */
val CONSENT_RETRY_DELAY: Duration = Duration.ofSeconds(10)

/**
 * The signed-in volunteer's sharing consent, made safe to drive GPS with: it emits the real
 * [com.swent.shifter.model.user.User.locationSharingEnabled] as
 * [UserRepository.observeLocationSharing] reports it, but **never leaves a stale `true` authorizing
 * GPS after a failure**.
 *
 * Two kinds of "not known yet" are handled differently, on purpose:
 * - **While the profile first loads, nothing is emitted** - no placeholder `false`. The first value
 *   is the real setting, from [UserRepository.observeLocationSharing], whose no-placeholder
 *   contract this keeps: a leading `false` would remove a valid position and share it again a
 *   moment later. [runLiveLocationPublishing] guards the same case with `filterNotNull` on a `null`
 *   start.
 * - **When the observation fails, it deliberately emits `false`** on the [UserRepositoryException]
 *   and tries again after [CONSENT_RETRY_DELAY], so a lost observation stops the sharing rather
 *   than freezing it on the last value read. This is fail-closed and has a visible consequence: the
 *   publisher reads `false` as consent off and **attempts to remove the last shared position**, so
 *   the volunteer may drop off their manager's map until the observation recovers and reads `true`
 *   again (about [CONSENT_RETRY_DELAY] later), when sharing resumes. It is a removal *attempt*, not
 *   a guaranteed deletion: offline the delete is queued and may not reach the backend at once. The
 *   trade-off is intended - privacy over uninterrupted visibility when consent cannot be verified.
 *
 * It is cold: share it (see [runLiveLocationPublishing]) so every session reads one observation.
 */
fun UserRepository.sharingConsent(userId: String): Flow<Boolean> =
    observeLocationSharing(userId).retryWhen { cause, _ ->
      // A failed observation must not keep authorizing GPS: report off, wait, then observe again.
      if (cause is UserRepositoryException) {
        emit(false)
        delay(CONSENT_RETRY_DELAY.toKotlinDuration())
        true
      } else {
        false
      }
    }

/**
 * Runs [coordinator] until cancelled, feeding it [consent] shared across every session and catching
 * the session faults the coordinator reports.
 *
 * It does the three things a caller of [LiveLocationCoordinator.run] has to:
 * - **Shares the consent.** [consent] is a cold flow, so it is collected once through a
 *   [StateFlow]: every session reads that one subscription rather than opening its own observation
 *   (one snapshot listener, not one per eligible team). The state starts `null`, the loading
 *   placeholder the sessions never see, since they collect it through [filterNotNull]: no GPS runs
 *   before the real consent is known.
 * - **Installs a [CoroutineExceptionHandler].** [LiveLocationCoordinator.run] lets an unexpected
 *   session fault reach the handler of the coroutine that runs it, so it is launched with one here;
 *   [onSessionFault] reports it instead of crashing the app, and the coordinator starts the target
 *   again at its next refresh.
 * - **Lives as long as the caller.** It returns only when cancelled, so a lifecycle boundary around
 *   this call is what starts and stops the whole publishing. Cancelling stops the consent
 *   observation with it.
 *
 * It also reports [LiveLocationCoordinator.sharingStates] to [onSharingStates], for the indicator
 * telling the volunteer about the sharing (#405), and **an empty map once cancelled**: in the
 * background, signed out or on another account, no session runs, so the indicator must not keep the
 * last state of one.
 */
suspend fun runLiveLocationPublishing(
    coordinator: LiveLocationCoordinator,
    consent: Flow<Boolean>,
    onSessionFault: (Throwable) -> Unit,
    onSharingStates: (Map<LiveLocationTarget, LiveLocationSharingState>) -> Unit,
): Unit =
    try {
      coroutineScope {
        launch { coordinator.sharingStates.collect(onSharingStates) }
        val shared: StateFlow<Boolean?> = consent.stateIn(this, SharingStarted.Eagerly, null)
        launch(CoroutineExceptionHandler { _, e -> onSessionFault(e) }) {
              coordinator.run(shared.filterNotNull())
            }
            .join()
      }
    } finally {
      onSharingStates(emptyMap())
    }
