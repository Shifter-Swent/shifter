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
 * [scope] owns the sharing coroutine and must outlive its consumers. Creating this clock starts no
 * timer; only collecting [ticks] does. Cancelling the scope stops the clock permanently.
 * [timeSource] allows tests to move wall time independently of the coroutine scheduler.
 */
class DeviceMinuteClock(
    scope: CoroutineScope,
    private val timeSource: () -> Instant = Instant::now,
) : MinuteClock {

  override fun now(): Instant = timeSource()

  override val ticks: Flow<Instant> = flow {
    while (true) {
      val current = now()
      emit(current)
      val nextMinute = current.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES)
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
