// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.model.time

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn

/**
 * The device's time, shared by every consumer of this instance.
 *
 * [scope] owns the sharing coroutine and must live as long as this clock. For the application's
 * shared instance, keep an application-owned scope alongside dependency construction, for example:
 * ```kotlin
 * val clockScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
 * val clock: MinuteClock = DeviceMinuteClock(clockScope)
 * ```
 *
 * An Activity's `lifecycleScope` or a screen's `viewModelScope` is too short-lived for that shared
 * instance. In `runTest`, pass `backgroundScope`, rather than the test's main scope: the sharing
 * coroutine waits for subscribers indefinitely, even when its timer is idle.
 *
 * Creating this clock starts no timer; only collecting [ticks] does. Canceling [scope] permanently
 * stops emissions, but the shared flow does not complete: subscribers retain the last replayed
 * tick, new subscribers can still receive it, and only [now] continues reading fresh time. Pause
 * ticking by releasing subscriptions, as described by [MinuteClock], not by canceling its owner.
 *
 * [timeSource] allows tests to move wall time independently of the coroutine scheduler. A clock
 * correction is observed at the next scheduled wake-up. A wake-up in the same minute emits nothing;
 * a jump to a different minute, including backwards, emits its actual time.
 */
class DeviceMinuteClock(
    scope: CoroutineScope,
    private val timeSource: () -> Instant = Instant::now,
) : MinuteClock {

  override fun now(): Instant = timeSource()

  override val ticks: Flow<Instant> = flow {
    var lastMinute: Instant? = null
    while (true) {
      val current = now()
      val minute = current.truncatedTo(ChronoUnit.MINUTES)
      if (minute != lastMinute) {
        emit(current)
        lastMinute = minute
      }
      val nextMinute = minute.plus(1, ChronoUnit.MINUTES)
      delay(Duration.between(current, nextMinute).toKotlinDuration())
    }
  }
      .shareIn(
          scope,
          // Drop the cached tick too: after a pause, the next observer needs the current time,
          // not the last instant shown before the app went into the background.
          SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
          replay = 1,
      )
}
