// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.model.time

import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceMinuteClockTest {

  @Test
  fun now_withDefaultSource_readsDeviceTime() = runTest {
    val clock = DeviceMinuteClock(backgroundScope)
    val before = Instant.now()

    val actual = clock.now()

    assertTrue(actual >= before && actual <= Instant.now())
  }

  @Test
  fun now_withoutSubscribers_readsFreshTime() = runTest {
    val clock = clock()
    assertEquals(START, clock.now())

    advanceTimeBy(10_000)

    assertEquals(START.plusSeconds(10), clock.now())
  }

  @Test
  fun ticks_withoutSubscribers_doesNotReadTime() = runTest {
    var reads = 0
    DeviceMinuteClock(backgroundScope) {
      reads++
      START
    }

    advanceTimeBy(180_000)
    runCurrent()

    assertEquals(0, reads)
  }

  @Test
  fun ticks_onSubscription_emitsCurrentTimeImmediately() = runTest {
    val received = mutableListOf<Instant>()

    collect(clock(), received)

    assertEquals(listOf(START), received)
  }

  @Test
  fun ticks_betweenMinutes_waitsUntilEachNewMinute() = runTest {
    val received = mutableListOf<Instant>()
    collect(clock(), received)

    advanceTimeBy(47_654)
    runCurrent()
    assertEquals(listOf(START), received)

    advanceTimeBy(1)
    runCurrent()
    assertEquals(listOf(START, NEXT_MINUTE), received)

    advanceTimeBy(60_000)
    runCurrent()
    assertEquals(listOf(START, NEXT_MINUTE, NEXT_MINUTE.plusSeconds(60)), received)
  }

  @Test
  fun ticks_exactlyOnTheMinute_waitsAFullMinute() = runTest {
    val received = mutableListOf<Instant>()
    collect(clock(NEXT_MINUTE), received)

    advanceTimeBy(59_999)
    runCurrent()
    assertEquals(listOf(NEXT_MINUTE), received)

    advanceTimeBy(1)
    runCurrent()
    assertEquals(listOf(NEXT_MINUTE, NEXT_MINUTE.plusSeconds(60)), received)
  }

  @Test
  fun ticks_acrossMidnight_emitsTheNewDay() = runTest {
    val start = Instant.parse("2026-11-14T23:59:59Z")
    val received = mutableListOf<Instant>()
    collect(clock(start), received)

    advanceTimeBy(1_000)
    runCurrent()

    assertEquals(listOf(start, Instant.parse("2026-11-15T00:00:00Z")), received)
  }

  @Test
  fun ticks_afterWallTimeJumps_readsActualTimeAndRealigns() = runTest {
    var offsetMillis = 0L
    val clock =
        DeviceMinuteClock(backgroundScope) { START.plusMillis(testScheduler.currentTime + offsetMillis) }
    val received = mutableListOf<Instant>()
    collect(clock, received)

    // Simulates a late wake-up or a device clock correction: do not count the missed minutes.
    offsetMillis = 137_000
    advanceTimeBy(47_655)
    runCurrent()
    assertEquals(listOf(START, NEXT_MINUTE.plusSeconds(137)), received)

    advanceTimeBy(43_000)
    runCurrent()
    assertEquals(Instant.parse("2026-11-14T14:06:00Z"), received.last())
    assertEquals(3, received.size)
  }

  @Test
  fun ticks_afterWallTimeMovesBack_readsActualTimeAndRealigns() = runTest {
    var offsetMillis = 0L
    val clock =
        DeviceMinuteClock(backgroundScope) { START.plusMillis(testScheduler.currentTime + offsetMillis) }
    val received = mutableListOf<Instant>()
    collect(clock, received)

    offsetMillis = -90_000
    advanceTimeBy(47_655)
    runCurrent()
    assertEquals(listOf(START, Instant.parse("2026-11-14T14:01:30Z")), received)

    advanceTimeBy(30_000)
    runCurrent()
    assertEquals(Instant.parse("2026-11-14T14:02:00Z"), received.last())
    assertEquals(3, received.size)
  }

  @Test
  fun ticks_withTwoSubscribers_usesOneTimerUntilBothLeave() = runTest {
    var reads = 0
    val clock =
        DeviceMinuteClock(backgroundScope) {
          reads++
          START.plusMillis(testScheduler.currentTime)
        }
    val first = mutableListOf<Instant>()
    val second = mutableListOf<Instant>()
    val firstJob = collect(clock, first)
    val secondJob = collect(clock, second)
    assertEquals(1, reads)
    assertEquals(first, second)

    firstJob.cancel()
    runCurrent()
    advanceTimeBy(47_655)
    runCurrent()
    assertEquals(2, reads)
    assertEquals(listOf(START), first)
    assertEquals(listOf(START, NEXT_MINUTE), second)

    secondJob.cancel()
    runCurrent()
    advanceTimeBy(180_000)
    runCurrent()
    assertEquals(2, reads)
  }

  @Test
  fun ticks_afterLastSubscriberLeaves_stopsAndRestartsWithFreshTime() = runTest {
    val clock = clock()
    val first = mutableListOf<Instant>()
    collect(clock, first).cancel()
    runCurrent()

    advanceTimeBy(600_000)
    runCurrent()
    val resumed = mutableListOf<Instant>()
    collect(clock, resumed)

    assertEquals(listOf(START), first)
    // No replay of the old tick, and no burst of the ten minutes missed.
    assertEquals(listOf(START.plusSeconds(600)), resumed)

    advanceTimeBy(47_655)
    runCurrent()
    assertEquals(listOf(START.plusSeconds(600), NEXT_MINUTE.plusSeconds(600)), resumed)
  }

  @Test
  fun ticks_whenOwnerIsCancelled_stopsTheTimer() = runTest {
    val owner =
        CoroutineScope(
            backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job])
        )
    var reads = 0
    val clock =
        DeviceMinuteClock(owner) {
          reads++
          START.plusMillis(testScheduler.currentTime)
        }
    val received = mutableListOf<Instant>()
    collect(clock, received)

    owner.cancel()
    advanceTimeBy(180_000)
    runCurrent()

    assertEquals(1, reads)
    assertEquals(listOf(START), received)
  }

  private fun TestScope.clock(start: Instant = START): DeviceMinuteClock =
      DeviceMinuteClock(backgroundScope) { start.plusMillis(testScheduler.currentTime) }

  private fun TestScope.collect(clock: MinuteClock, received: MutableList<Instant>): Job {
    val job = backgroundScope.launch { clock.ticks.collect { received += it } }
    runCurrent()
    return job
  }

  private companion object {
    val START: Instant = Instant.parse("2026-11-14T14:02:12.345Z")
    val NEXT_MINUTE: Instant = Instant.parse("2026-11-14T14:03:00Z")
  }
}
