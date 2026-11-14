// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.model.time

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeMinuteClockTest {

  @Test
  fun now_withoutInitialTime_returnsEpoch() {
    assertEquals(Instant.EPOCH, FakeMinuteClock().now())
  }

  @Test
  fun ticks_onSubscription_emitsInitialTime() = runTest {
    val clock = FakeMinuteClock(START)
    val received = mutableListOf<Instant>()
    collect(clock, received)

    assertEquals(START, clock.now())
    assertEquals(listOf(START), received)
  }

  @Test
  fun set_withAnEarlierTime_updatesNowAndSubscribers() = runTest {
    val clock = FakeMinuteClock(START)
    val received = mutableListOf<Instant>()
    collect(clock, received)
    val earlier = START.minusSeconds(60)

    clock.set(earlier)
    runCurrent()

    assertEquals(earlier, clock.now())
    assertEquals(listOf(START, earlier), received)
  }

  @Test
  fun advance_whenCollectorsRunBetweenChanges_emitsEachTimeToAllSubscribers() = runTest {
    val clock = FakeMinuteClock(START)
    val first = mutableListOf<Instant>()
    val second = mutableListOf<Instant>()
    collect(clock, first)
    collect(clock, second)

    clock.advance(Duration.ofMinutes(1))
    runCurrent()
    clock.advance(Duration.ofMinutes(2))
    runCurrent()

    val expected = listOf(START, START.plusSeconds(60), START.plusSeconds(180))
    assertEquals(START.plusSeconds(180), clock.now())
    assertEquals(expected, first)
    assertEquals(expected, second)
  }

  @Test
  fun ticks_afterTimeChangedWithoutSubscribers_emitsCurrentTime() = runTest {
    val clock = FakeMinuteClock(START)
    clock.advance(Duration.ofDays(1))
    val received = mutableListOf<Instant>()

    collect(clock, received)

    assertEquals(listOf(START.plusSeconds(86_400)), received)
  }

  @Test
  fun set_withTheSameInstant_doesNotEmitAgain() = runTest {
    val clock = FakeMinuteClock(START)
    val received = mutableListOf<Instant>()
    collect(clock, received)

    clock.set(START)
    runCurrent()

    assertEquals(START, clock.now())
    assertEquals(listOf(START), received)
  }

  @Test
  fun advance_withoutRunningCollectors_conflatesToTheLatestTime() = runTest {
    val clock = FakeMinuteClock(START)
    val received = mutableListOf<Instant>()
    collect(clock, received)

    clock.advance(Duration.ofMinutes(1))
    clock.advance(Duration.ofMinutes(2))

    assertEquals(START.plusSeconds(180), clock.now())
    assertEquals(listOf(START), received)
    runCurrent()
    assertEquals(listOf(START, START.plusSeconds(180)), received)
  }

  @Test
  fun subscriptionCount_whenCollectorsJoinAndLeave_tracksActiveCollectors() = runTest {
    val clock = FakeMinuteClock(START)
    assertEquals(0, clock.subscriptionCount.value)
    val first = collect(clock, mutableListOf())
    val second = collect(clock, mutableListOf())
    assertEquals(2, clock.subscriptionCount.value)

    first.cancel()
    runCurrent()
    assertEquals(1, clock.subscriptionCount.value)

    second.cancel()
    runCurrent()
    assertEquals(0, clock.subscriptionCount.value)
  }

  private fun TestScope.collect(clock: FakeMinuteClock, received: MutableList<Instant>): Job {
    val job =
        backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
          clock.ticks.collect { received += it }
        }
    runCurrent()
    return job
  }

  private companion object {
    val START: Instant = Instant.parse("2026-11-14T14:02:12.345Z")
  }
}
