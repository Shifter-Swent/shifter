// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.model.time

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A manually controlled current-time state with no timer. Setting time updates [now] immediately
 * and makes the latest value available through [ticks], without waiting for a minute boundary.
 *
 * Like any [StateFlow], equal values are not emitted and a slow collector can skip intermediate
 * updates. In coroutine tests, call `runCurrent()` between advances when asserting each transition;
 * consecutive changes without letting collectors run intentionally conflate to the latest time.
 * This fake lives in JVM test sources; instrumented tests need their own test-source helper.
 */
class FakeMinuteClock(initial: Instant = Instant.EPOCH) : MinuteClock {

  private val time = MutableStateFlow(initial)
  override val ticks: StateFlow<Instant> = time.asStateFlow()

  /** Active tick collectors, so lifecycle tests can verify that subscriptions are released. */
  val subscriptionCount: StateFlow<Int>
    get() = time.subscriptionCount

  override fun now(): Instant = time.value

  fun set(instant: Instant) {
    time.value = instant
  }

  fun advance(duration: Duration) = set(now().plus(duration))
}
