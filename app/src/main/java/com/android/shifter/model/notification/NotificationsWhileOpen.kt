// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import com.swent.shifter.model.time.MinuteClock
import java.time.Duration
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

/** How long to wait before running again what failed: reading the inbox, or a run of the engine. */
private val NOTIFICATIONS_RETRY_DELAY: Duration = Duration.ofSeconds(10)

/**
 * Runs [engine] for [userId] until cancelled, as the app does while open: the inbox at each change
 * of [NotificationInbox.observePending], so a notification shows at once, and the checks at each
 * tick of [clock]. A failure, of the observation or of a run, runs it again after
 * [NOTIFICATIONS_RETRY_DELAY], and never stops the other.
 */
suspend fun runNotificationsWhileOpen(
    userId: String,
    engine: NotificationEngine,
    inbox: NotificationInbox,
    clock: MinuteClock,
): Unit = coroutineScope {
  launch {
    inbox
        .observePending(userId)
        .onEach { engine.handleInbox(userId, clock.now()) }
        .retryingAfterADelay()
        .collect()
  }
  launch { clock.ticks.onEach { engine.runChecks(userId, it) }.retryingAfterADelay().collect() }
}

/**
 * This flow, collected again [NOTIFICATIONS_RETRY_DELAY] after each failure, rather than ended: the
 * notifications must go on while the app is open.
 */
private fun <T> Flow<T>.retryingAfterADelay(): Flow<T> = retryWhen { cause, _ ->
  (cause is Exception).also { if (it) delay(NOTIFICATIONS_RETRY_DELAY.toKotlinDuration()) }
}
