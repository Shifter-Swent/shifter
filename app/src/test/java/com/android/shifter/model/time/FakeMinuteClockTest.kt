// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.model.time

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      clock.ticks.collect { received += it }
    }

    assertEquals(START, clock.now())
    assertEquals(listOf(START), received)
  }

  @Test
  fun set_withAnEarlierTime_updatesNowAndSubscribers() = runTest {
    val clock = FakeMinuteClock(START)
    val received = mutableListOf<Instant>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      clock.ticks.collect { received += it }
    }
    val earlier = START.minusSeconds(60)

    clock.set(earlier)

    assertEquals(earlier, clock.now())
    assertEquals(listOf(START, earlier), received)
  }

  @Test
  fun advance_severalTimes_emitsEachNewTimeToAllSubscribers() = runTest {
    val clock = FakeMinuteClock(START)
    val first = mutableListOf<Instant>()
    val second = mutableListOf<Instant>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      clock.ticks.collect { first += it }
    }
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      clock.ticks.collect { second += it }
    }

    clock.advance(Duration.ofMinutes(1))
    clock.advance(Duration.ofMinutes(2))

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

    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      clock.ticks.collect { received += it }
    }

    assertEquals(listOf(START.plusSeconds(86_400)), received)
  }

  private companion object {
    val START: Instant = Instant.parse("2026-11-14T14:02:12.345Z")
  }
}
