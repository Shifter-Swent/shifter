// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.model.time

import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Live minute ticks for screens and their ViewModels, replaceable by a fake in tests. Use
 * [java.time.Clock] when only an on-demand time reading is needed.
 *
 * Keep the subscription tied to visible consumers, including through intermediate shared flows. For
 * example, expose this from a ViewModel (or combine ticks with its other inputs before `stateIn`):
 * ```kotlin
 * val now: StateFlow<Instant?> = clock.ticks.stateIn(
 *     viewModelScope,
 *     SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
 *     initialValue = null,
 * )
 * ```
 *
 * Collect it in Compose with `val now by viewModel.now.collectAsStateWithLifecycle()`. Treat null
 * as awaiting a fresh tick. A permanent `viewModelScope.launch { ticks.collect { ... } }` would
 * keep the timer subscribed in the background, even if Compose stops collecting the UI state.
 */
interface MinuteClock {

  /** Reads the current time immediately, even when nobody collects [ticks]. */
  fun now(): Instant

  /**
   * Emits the current time when the first collector subscribes, then on each new minute. Other
   * collectors receive the latest tick immediately. Ticks carry the actual time when they run: a
   * delayed tick does not replay the minutes missed.
   *
   * Joining an active clock replays its last tick, normally less than a minute old (longer if
   * execution was delayed). Use that emitted instant consistently for the calculation it triggers,
   * rather than mixing it with a separate [now] reading. An observation session restarted after the
   * last subscriber left reads fresh time instead.
   *
   * Collectors share one timer, which stops when the last collector leaves. Screens must collect
   * with their lifecycle, and any intermediate shared flows must release their subscription when
   * unused, so the timer can stop in the background. Restarting observation reads the time again.
   */
  val ticks: Flow<Instant>
}
