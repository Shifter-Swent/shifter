// Co-authored-by: Claude Opus 4.8 <noreply@anthropic.com>
// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
package com.swent.shifter.model.location

import com.swent.shifter.model.location.LiveLocationSharingState.OFF
import com.swent.shifter.model.location.LiveLocationSharingState.SHARED
import com.swent.shifter.model.location.LiveLocationSharingState.WAITING
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LiveLocationSessions.of] against the fake provider and repository, bounding a session to its
 * target's event period (#406).
 *
 * The clock the session reads tracks the test scheduler, so delaying to
 * [LiveLocationTarget.startAt] and stopping at [LiveLocationTarget.endAt] stay consistent with
 * [advanceTimeBy]. A boundary is crossed by advancing one millisecond past it, since
 * [advanceTimeBy] does not run a task scheduled exactly at the instant it reaches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveLocationSessionsTest {

  private val provider = FakeLocationProvider()
  private val repository = FakeLiveLocationRepository(currentUserId = VOLUNTEER)

  /** Every state the session reported, in order, as the indicator of #405 reads it. */
  private val states = mutableListOf<LiveLocationSharingState>()

  @Test
  fun publish_beforeTheEventStarts_collectsNoGpsAndSharesNothing() =
      runTest(UnconfinedTestDispatcher()) {
        // Starts in an hour: eligibility would not open this session yet, but one that does must
        // still publish nothing before the event.
        startSession(target(START + Duration.ofHours(1), START + Duration.ofHours(4)))

        provider.emit(GENEVA)
        assertEquals(0, provider.activeCollectors)
        assertEquals(emptyList<LiveLocation>(), shared())
        // Nor is any state reported: the indicator has nothing to show for an event not begun.
        assertEquals(emptyList<LiveLocationSharingState>(), states)

        // At the start it begins, shares, and reports as any session does.
        advanceTimeBy(Duration.ofHours(1).toKotlinDuration() + 1.milliseconds)
        provider.emit(GENEVA)
        assertEquals(GENEVA, shared().single().position)
        assertEquals(listOf(WAITING, SHARED), states)
      }

  @Test
  fun publish_duringTheEvent_sharesThePosition() =
      runTest(UnconfinedTestDispatcher()) {
        startSession(target(START - Duration.ofHours(1), START + Duration.ofHours(3)))

        provider.emit(GENEVA)

        assertEquals(GENEVA, shared().single().position)
        assertEquals(1, provider.activeCollectors)
      }

  @Test
  fun publish_stopsAtTheEnd_thenCollectsNoGpsAndSharesNoMore() =
      runTest(UnconfinedTestDispatcher()) {
        startSession(target(START - Duration.ofHours(1), START + Duration.ofHours(2)))
        provider.emit(GENEVA)
        assertEquals(1, provider.activeCollectors)

        // A millisecond before the end it is still sharing.
        advanceTimeBy(Duration.ofHours(2).toKotlinDuration() - 1.milliseconds)
        assertEquals(1, provider.activeCollectors)

        // At the end the session stops, and the GPS collection stops with it.
        advanceTimeBy(2.milliseconds)
        assertEquals(0, provider.activeCollectors)

        // A later position is shared no more; the one stored before the end stays: the end is not
        // the consent turning off, so no OFF is reported and nothing is taken out. Nothing is
        // reported at all any more; dropping the state it had reported is the coordinator's.
        provider.emit(DOWN_THE_STREET)
        assertEquals(GENEVA, shared().single().position)
        assertEquals(listOf(WAITING, SHARED), states)
      }

  @Test
  fun publish_afterTheEventEnded_sharesNothingAndCollectsNoGps() =
      runTest(UnconfinedTestDispatcher()) {
        startSession(target(START - Duration.ofHours(4), START - Duration.ofSeconds(1)))

        provider.emit(GENEVA)

        assertEquals(0, provider.activeCollectors)
        assertEquals(emptyList<LiveLocation>(), shared())
        assertEquals(emptyList<LiveLocationSharingState>(), states)
      }

  /**
   * The consent turning off shortly before the end is still the consent turning off (#377): the
   * position goes out at once. The end that follows ends the session as usual, and turning sharing
   * back on afterwards starts nothing, the event being over.
   */
  @Test
  fun publish_whenTheConsentTurnsOffBeforeTheEnd_takesThePositionOutThenEndsWithTheEvent() =
      runTest(UnconfinedTestDispatcher()) {
        val sharing = MutableStateFlow(true)
        val session =
            startSession(target(START - Duration.ofHours(1), START + Duration.ofHours(1)), sharing)
        provider.emit(GENEVA)
        assertEquals(GENEVA, shared().single().position)

        advanceTimeBy(Duration.ofMinutes(50).toKotlinDuration())
        sharing.value = false

        assertEquals(emptyList<LiveLocation>(), shared())
        assertEquals(0, provider.activeCollectors)
        assertEquals(listOf(WAITING, SHARED, OFF), states)

        advanceTimeBy(Duration.ofMinutes(10).toKotlinDuration() + 1.milliseconds)
        assertTrue(session.isCompleted)
        sharing.value = true
        provider.emit(GENEVA)

        assertEquals(0, provider.activeCollectors)
        assertEquals(emptyList<LiveLocation>(), shared())
        assertEquals(listOf(WAITING, SHARED, OFF), states)
      }

  @Test
  fun publish_concurrentEvents_stopEachAtItsOwnEnd() =
      runTest(UnconfinedTestDispatcher()) {
        // Two events of the same volunteer, ending an hour apart: each session is bounded on its
        // own.
        startSession(target(START - Duration.ofHours(1), START + Duration.ofHours(1), "early"))
        startSession(target(START - Duration.ofHours(1), START + Duration.ofHours(3), "late"))
        provider.emit(GENEVA)
        assertEquals(2, provider.activeCollectors)

        // Past the early event's end, before the late one's: only the late session remains.
        advanceTimeBy(Duration.ofHours(1).toKotlinDuration() + 1.milliseconds)
        assertEquals(1, provider.activeCollectors)

        // Past the late event's end too: both have stopped.
        advanceTimeBy(Duration.ofHours(2).toKotlinDuration())
        assertEquals(0, provider.activeCollectors)
      }

  @Test
  fun publish_whenCancelledBeforeTheEnd_stopsTheGpsAndKeepsThePosition() =
      runTest(UnconfinedTestDispatcher()) {
        val session = startSession(target(START - Duration.ofHours(1), START + Duration.ofHours(3)))
        provider.emit(GENEVA)
        assertEquals(1, provider.activeCollectors)

        session.cancel()

        assertEquals(0, provider.activeCollectors)
        // The position stored before the cancellation stays, as after any session ending.
        assertEquals(GENEVA, shared().single().position)
      }

  @Test
  fun publish_whenWritesKeepFailing_stillStopsAtTheEnd() =
      runTest(UnconfinedTestDispatcher()) {
        // A backend that keeps refusing must not end the session early, but must not keep it past
        // the event either: the end stops it whatever the writes did.
        repository.failure = LiveLocationRepositoryException.Unavailable()
        startSession(target(START - Duration.ofHours(1), START + Duration.ofHours(1)))
        provider.emit(GENEVA)
        assertEquals(1, provider.activeCollectors)

        advanceTimeBy(Duration.ofHours(1).toKotlinDuration() + 1.milliseconds)

        assertEquals(0, provider.activeCollectors)
      }

  @Test
  fun publish_whenAWriteNeverAnswers_isCancelledAtTheEndAndStartsNoMoreWrites() =
      runTest(UnconfinedTestDispatcher()) {
        // A write that never answers, as an offline one does not until the device reconnects.
        val suspending = UnansweringLiveLocationRepository()
        val now = { START + Duration.ofMillis(testScheduler.currentTime) }
        val publisher = LiveLocationPublisher(provider, suspending, now = now)
        val sessions = LiveLocationSessions.of(publisher, now = now)
        backgroundScope.launch {
          sessions.publish(
              target(START - Duration.ofHours(1), START + Duration.ofHours(1)),
              MutableStateFlow(true),
              states::add,
          )
        }

        // The write starts within the event and then hangs, with the GPS still collected.
        provider.emit(GENEVA)
        assertEquals(1, suspending.writes)
        assertEquals(1, provider.activeCollectors)

        // Reaching the end cancels the suspended write and stops the GPS collection with it.
        advanceTimeBy(Duration.ofHours(1).toKotlinDuration() + 1.milliseconds)
        assertEquals(0, provider.activeCollectors)

        // A later position starts no new write: the client initiates nothing past the end. What
        // Firestore may have already queued is its own to send; this does not recall it.
        provider.emit(DOWN_THE_STREET)
        assertEquals(1, suspending.writes)
      }

  /**
   * Starts a windowed session in the background and returns its job. The clock the publisher and
   * the session read tracks the scheduler, so the window and [advanceTimeBy] agree.
   */
  private fun TestScope.startSession(
      target: LiveLocationTarget,
      sharing: MutableStateFlow<Boolean> = MutableStateFlow(true),
  ): Job {
    val now = { START + Duration.ofMillis(testScheduler.currentTime) }
    val publisher = LiveLocationPublisher(provider, repository, now = now)
    val sessions = LiveLocationSessions.of(publisher, now = now)
    return backgroundScope.launch { sessions.publish(target, sharing, states::add) }
  }

  private suspend fun shared(): List<LiveLocation> =
      repository.observeTeamLocations(EVENT, TEAM).first()

  private companion object {
    const val EVENT = "event-1"
    const val TEAM = "team-1"
    const val VOLUNTEER = "volunteer-1"
    val START: Instant = Instant.parse("2026-11-02T09:00:00Z")
    val GENEVA = GeoPosition(46.2044, 6.1432)

    /** About 111 m north of [GENEVA], well above the publisher's distance threshold. */
    val DOWN_THE_STREET = GeoPosition(46.2054, 6.1432)

    /** The target for [eventId]/[TEAM] over the given period. */
    fun target(startAt: Instant, endAt: Instant, eventId: String = EVENT) =
        LiveLocationTarget(eventId, TEAM, startAt, endAt)
  }
}
