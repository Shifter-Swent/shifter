// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.model.time

import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Time for screens and their ViewModels, replaceable by a fake in tests. */
interface MinuteClock {

  /** Reads the current time immediately, even when nobody collects [ticks]. */
  fun now(): Instant

  /**
   * Emits the current time when the first collector subscribes, then on each new minute. Other
   * collectors receive the latest tick immediately. Ticks carry the actual time when they run: a
   * delayed tick does not replay the minutes missed.
   *
   * Collectors share one timer, which stops when the last collector leaves. Screens must collect
   * with their lifecycle, and any intermediate shared flows must release their subscription when
   * unused, so the timer can stop in the background. Restarting observation reads the time again.
   */
  val ticks: Flow<Instant>
}
