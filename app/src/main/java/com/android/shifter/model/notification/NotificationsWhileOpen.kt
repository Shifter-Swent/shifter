// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import com.swent.shifter.model.time.MinuteClock
import java.time.Duration
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen

/** How long to wait before observing the inbox again after its observation failed. */
private val OBSERVE_RETRY_DELAY: Duration = Duration.ofSeconds(10)

/**
 * Runs [engine] for [userId] until cancelled, as the app does while open: the inbox at each change
 * of [NotificationInbox.observePending], so a notification shows at once, and the whole engine at
 * each tick of [clock] when [wholeRuns] says it is due: the checks read every event of an
 * organizer, and the inbox is handled again, so a run that failed is run again.
 *
 * A run that fails goes to [onFailure] and is run again at the next one, without restarting the
 * observation: an item its format fails on stays in the inbox, so restarting at each failure would
 * read the inbox again and again. Only a failed observation is observed again, after
 * [OBSERVE_RETRY_DELAY]. Neither stops the other.
 */
suspend fun runNotificationsWhileOpen(
    userId: String,
    engine: NotificationEngine,
    inbox: NotificationInbox,
    clock: MinuteClock,
    wholeRuns: PeriodicRuns,
    onFailure: (Exception) -> Unit,
): Unit = coroutineScope {
  inbox
      .observePending(userId)
      .retryWhen { cause, _ -> // NOSONAR: only SharedFlow.retryWhen is deprecated (S1874)
        (cause is Exception).also { if (it) delay(OBSERVE_RETRY_DELAY.toKotlinDuration()) }
      }
      .onEach { reportingFailure(onFailure) { engine.handleInbox(userId, clock.now()) } }
      .launchIn(this)
  clock.ticks
      .filter { tick -> wholeRuns.takeIfDue(userId, tick) }
      .onEach { tick -> reportingFailure(onFailure) { engine.run(userId, tick) } }
      .launchIn(this)
}

/**
 * Runs [run], giving what it throws to [onFailure] rather than throwing it, a cancellation
 * excepted: a run of the notifications that failed is run again later, and must not stop the
 * others.
 */
internal suspend fun reportingFailure(onFailure: (Exception) -> Unit, run: suspend () -> Unit) {
  try {
    run()
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    onFailure(e)
  }
}
