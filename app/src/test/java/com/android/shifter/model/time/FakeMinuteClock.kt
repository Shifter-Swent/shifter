// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.model.time

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A manually controlled clock: no timer, and each changed instant is emitted to its collectors. */
class FakeMinuteClock(initial: Instant = Instant.EPOCH) : MinuteClock {

  private val time = MutableStateFlow(initial)
  override val ticks: StateFlow<Instant> = time.asStateFlow()

  override fun now(): Instant = time.value

  fun set(instant: Instant) {
    time.value = instant
  }

  fun advance(duration: Duration) = set(now().plus(duration))
}
