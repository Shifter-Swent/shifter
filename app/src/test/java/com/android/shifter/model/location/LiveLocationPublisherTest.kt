// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
 * [LiveLocationPublisher] against the fake provider and the fake repository, with an unconfined
 * dispatcher so a position emitted after the session started reaches it right away.
 *
 * The clock is moved by hand ([elapse]), so the interval between two writes is decided by the test
 * and not by how long it takes to run.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveLocationPublisherTest {

  private val provider = FakeLocationProvider()
  private val repository = FakeLiveLocationRepository(currentUserId = VOLUNTEER)
  private var clock = START
  private val publisher = LiveLocationPublisher(provider, repository, now = { clock })

  @Test
  fun publish_withTheConsentOn_sharesThePositionItReads() =
      runTest(UnconfinedTestDispatcher()) {
        startSession()

        provider.emit(GENEVA)

        assertEquals(listOf(LiveLocation(VOLUNTEER, GENEVA, START)), shared())
      }

  @Test
  fun publish_withTheConsentOff_sharesNothingAndTakesOutWhatWasStored() =
      runTest(UnconfinedTestDispatcher()) {
        // Shared by an earlier session, then the consent turned off with none running: from the
        // settings with this screen closed, from another device, or with the app killed before the
        // removal went out. Nothing but this start would take it out.
        repository.shareLocation(EVENT, TEAM, GENEVA, START)

        startSession(MutableStateFlow(false))

        provider.emit(GENEVA)

        assertEquals(emptyList<LiveLocation>(), shared())
        // And the GPS is not even read while the consent is off.
        assertEquals(0, provider.activeCollectors)
      }

  /**
   * The same start with nothing stored: an idempotent removal, so it costs nothing and is quiet.
   */
  @Test
  fun publish_withTheConsentOffAndNothingStored_startsWithoutFailing() =
      runTest(UnconfinedTestDispatcher()) {
        val session = startSession(MutableStateFlow(false))

        assertEquals(emptyList<LiveLocation>(), shared())
        assertTrue("Expected the session to still be running", session.isActive)
      }

  @Test
  fun publish_whenTheConsentIsRevoked_stopsTheGpsAndTakesThePositionOut() =
      runTest(UnconfinedTestDispatcher()) {
        val sharing = MutableStateFlow(true)
        startSession(sharing)
        provider.emit(GENEVA)

        sharing.value = false

        assertEquals(emptyList<LiveLocation>(), shared())
        assertEquals(0, provider.activeCollectors)
      }

  @Test
  fun publish_whenConsentIsReenabled_resumesSharing() =
      runTest(UnconfinedTestDispatcher()) {
        val sharing = MutableStateFlow(false)
        startSession(sharing)

        // Off at the start: the GPS is not even read.
        provider.emit(GENEVA)
        assertEquals(emptyList<LiveLocation>(), shared())

        sharing.value = true
        provider.emit(GENEVA)
        assertEquals(listOf(LiveLocation(VOLUNTEER, GENEVA, START)), shared())

        sharing.value = false
        assertEquals(emptyList<LiveLocation>(), shared())
        assertEquals(0, provider.activeCollectors)

        // On again: a volunteer who changes their mind twice is back on the map, and the gates
        // start over with it, so the first position of the new session needs no waiting.
        sharing.value = true
        elapse(MIN_INTERVAL_SECONDS)
        provider.emit(DOWN_THE_STREET)

        val resumedAt = START.plusSeconds(MIN_INTERVAL_SECONDS)
        assertEquals(listOf(LiveLocation(VOLUNTEER, DOWN_THE_STREET, resumedAt)), shared())
      }

  @Test
  fun publish_whileTheLocationIsDenied_sharesNothingUntilItIsGrantedAgain() =
      runTest(UnconfinedTestDispatcher()) {
        provider.setStatus(LocationStatus.DENIED)
        startSession()

        provider.emit(GENEVA)
        assertEquals(emptyList<LiveLocation>(), shared())

        // The provider resumes on its own once the permission is back, and so does the sharing.
        provider.setStatus(LocationStatus.GRANTED)
        provider.emit(GENEVA)

        assertEquals(listOf(LiveLocation(VOLUNTEER, GENEVA, START)), shared())
      }

  @Test
  fun publish_withinThirtySeconds_skipsThePositionHoweverFarItMoved() =
      runTest(UnconfinedTestDispatcher()) {
        // Spelled out rather than read off MIN_INTERVAL, so moving the constant either way fails
        // here instead of moving this test with it.
        assertEquals(Duration.ofSeconds(30), LiveLocationPublisher.MIN_INTERVAL)
        startSession()
        provider.emit(GENEVA)

        elapse(29)
        provider.emit(LAUSANNE)
        assertEquals(listOf(LiveLocation(VOLUNTEER, GENEVA, START)), shared())

        // The thirtieth second is enough, so the bound is where it says it is.
        elapse(1)
        provider.emit(LAUSANNE)
        assertEquals(listOf(LiveLocation(VOLUNTEER, LAUSANNE, START.plusSeconds(30))), shared())
      }

  @Test
  fun publish_afterTheIntervalWithoutMoving_skipsThePosition() =
      runTest(UnconfinedTestDispatcher()) {
        startSession()
        provider.emit(GENEVA)

        // Six metres is below the threshold: a volunteer standing still costs no write until the
        // refresh falls due, so their position is left as it was.
        elapse(MIN_INTERVAL_SECONDS)
        provider.emit(A_FEW_METRES_AWAY)

        assertEquals(listOf(LiveLocation(VOLUNTEER, GENEVA, START)), shared())
      }

  @Test
  fun publish_whenStandingStillPastTheRefresh_sharesThePositionAgain() =
      runTest(UnconfinedTestDispatcher()) {
        startSession()
        provider.emit(GENEVA)

        // Still six metres, so the distance gate still refuses, but a volunteer who is there and
        // sharing must not look stale to their manager for want of having moved.
        elapse(REFRESH_SECONDS)
        provider.emit(A_FEW_METRES_AWAY)

        val refreshedAt = START.plusSeconds(REFRESH_SECONDS)
        assertEquals(
            listOf(LiveLocation(VOLUNTEER, A_FEW_METRES_AWAY, refreshedAt)),
            shared(),
        )
      }

  @Test
  fun publish_afterTheIntervalAndAMove_sharesTheNewPosition() =
      runTest(UnconfinedTestDispatcher()) {
        startSession()
        provider.emit(GENEVA)

        elapse(MIN_INTERVAL_SECONDS)
        provider.emit(DOWN_THE_STREET)

        val sharedAt = START.plusSeconds(MIN_INTERVAL_SECONDS)
        assertEquals(listOf(LiveLocation(VOLUNTEER, DOWN_THE_STREET, sharedAt)), shared())
      }

  @Test
  fun publish_whenCancelled_stopsTheGpsAndSharesNothingMore() =
      runTest(UnconfinedTestDispatcher()) {
        val session = startSession()
        provider.emit(GENEVA)

        session.cancel()

        assertEquals(0, provider.activeCollectors)
        // The position shared before stays: a session ending is not the volunteer withdrawing.
        elapse(MIN_INTERVAL_SECONDS)
        provider.emit(DOWN_THE_STREET)
        assertEquals(listOf(LiveLocation(VOLUNTEER, GENEVA, START)), shared())
      }

  @Test
  fun publish_whenAWriteIsRefused_keepsThePositionAndGoesOn() =
      runTest(UnconfinedTestDispatcher()) {
        startSession()
        provider.emit(GENEVA)

        // A refusal does not necessarily mean consent was revoked: an older measurement
        // can also be rejected. Never delete the stored position based on a refusal alone.
        repository.failure = LiveLocationRepositoryException.PermissionDenied()
        elapse(MIN_INTERVAL_SECONDS)
        provider.emit(DOWN_THE_STREET)
        repository.failure = null

        assertEquals(listOf(LiveLocation(VOLUNTEER, GENEVA, START)), shared())

        // And the session is still running: the next position that passes the gates goes through.
        elapse(MIN_INTERVAL_SECONDS)
        provider.emit(DOWN_THE_STREET)
        val sharedAt = START.plusSeconds(2 * MIN_INTERVAL_SECONDS)
        assertEquals(listOf(LiveLocation(VOLUNTEER, DOWN_THE_STREET, sharedAt)), shared())
      }

  @Test
  fun publish_whenTheFirstWriteFails_sharesTheNextPositionHoweverLittleItMoved() =
      runTest(UnconfinedTestDispatcher()) {
        // Nothing was ever stored, so the retry has no position to measure a move against:
        // waiting for one would leave the manager with no position at all.
        repository.failure = LiveLocationRepositoryException.Unavailable()
        startSession()
        provider.emit(GENEVA)
        repository.failure = null

        assertEquals(emptyList<LiveLocation>(), shared())

        // Six metres, below the distance gate, and shared all the same.
        elapse(MIN_INTERVAL_SECONDS)
        provider.emit(A_FEW_METRES_AWAY)

        val retriedAt = START.plusSeconds(MIN_INTERVAL_SECONDS)
        assertEquals(listOf(LiveLocation(VOLUNTEER, A_FEW_METRES_AWAY, retriedAt)), shared())
      }

  @Test
  fun publish_withoutAClockOfItsOwn_stampsThePositionWithTheTimeItWasCollected() =
      runTest(UnconfinedTestDispatcher()) {
        // The clock the other tests replace is what production leaves out, so the default has to
        // be the real one rather than any fixed instant.
        val withTheDefaultClock = LiveLocationPublisher(provider, repository)
        backgroundScope.launch { withTheDefaultClock.publish(EVENT, TEAM, MutableStateFlow(true)) }

        val before = Instant.now()
        provider.emit(GENEVA)
        val after = Instant.now()

        val shared = shared().single()
        assertEquals(GENEVA, shared.position)
        assertTrue(
            "Expected a measure between $before and $after, got ${shared.measuredAt}",
            shared.measuredAt >= before && shared.measuredAt <= after,
        )
      }

  @Test
  fun publish_whenTakingThePositionOutFails_triesAgainUntilItIsOut() =
      runTest(UnconfinedTestDispatcher()) {
        val sharing = MutableStateFlow(true)
        // Caught, so a session that failed would be this test's answer rather than its crash.
        var thrown: Throwable? = null
        val session = backgroundScope.launch {
          thrown = runCatching { publisher.publish(EVENT, TEAM, sharing) }.exceptionOrNull()
        }
        provider.emit(GENEVA)

        repository.failure = LiveLocationRepositoryException.Unavailable()
        sharing.value = false
        repository.failure = null

        // Still there, and the session still running: nobody else would retry the cleanup, so
        // failing it must neither end the session nor leave the volunteer on the map for good.
        assertEquals(listOf(LiveLocation(VOLUNTEER, GENEVA, START)), shared())
        assertTrue("Expected the session to survive the failure, got $thrown", session.isActive)

        advanceTimeBy(RETRY_DELAY + 1.milliseconds)

        assertEquals(emptyList<LiveLocation>(), shared())
        assertEquals(null, thrown)
      }

  @Test
  fun publish_whenTheConsentReturnsWhileTheRemovalFails_stopsRetryingAndSharesAgain() =
      runTest(UnconfinedTestDispatcher()) {
        val sharing = MutableStateFlow(true)
        startSession(sharing)
        provider.emit(GENEVA)

        repository.failure = LiveLocationRepositoryException.Unavailable()
        sharing.value = false
        repository.failure = null
        // The first attempt failed, so the position is still there and a retry is pending: without
        // this the test would also pass with no retry ever scheduled.
        assertEquals(listOf(LiveLocation(VOLUNTEER, GENEVA, START)), shared())

        sharing.value = true

        // Sharing again replaces the position, so the retries have nothing left to do.
        elapse(MIN_INTERVAL_SECONDS)
        provider.emit(DOWN_THE_STREET)
        val sharedAt = START.plusSeconds(MIN_INTERVAL_SECONDS)
        assertEquals(listOf(LiveLocation(VOLUNTEER, DOWN_THE_STREET, sharedAt)), shared())

        // A retry still pending would now take the position of a volunteer who consents again out.
        advanceTimeBy(RETRY_DELAY * 8)
        assertEquals(listOf(LiveLocation(VOLUNTEER, DOWN_THE_STREET, sharedAt)), shared())
      }

  @Test
  fun publish_whenCancelledWhileTheRemovalFails_stopsRetrying() =
      runTest(UnconfinedTestDispatcher()) {
        val sharing = MutableStateFlow(true)
        val session = startSession(sharing)
        provider.emit(GENEVA)

        repository.failure = LiveLocationRepositoryException.Unavailable()
        sharing.value = false
        repository.failure = null
        // The first attempt failed, so a retry is pending while the session is cancelled.
        assertEquals(listOf(LiveLocation(VOLUNTEER, GENEVA, START)), shared())

        session.cancel()

        // The position stays, as it does after any cancellation: the next session replaces it, and
        // no retry outlives the session that started it.
        advanceTimeBy(RETRY_DELAY * 8)
        assertEquals(listOf(LiveLocation(VOLUNTEER, GENEVA, START)), shared())
      }

  /**
   * #343 bounds the repository's own writes; this is the publisher's side of it. A write that never
   * answers must not keep the consent from being followed: the session has to cancel it and take
   * the position out all the same.
   */
  @Test
  fun publish_whenAWriteNeverAnswers_stillFollowsTheConsentOff() =
      runTest(UnconfinedTestDispatcher()) {
        val unanswering = UnansweringRepository()
        val publisher = LiveLocationPublisher(provider, unanswering, now = { clock })
        val sharing = MutableStateFlow(true)
        val session = backgroundScope.launch { publisher.publish(EVENT, TEAM, sharing) }

        provider.emit(GENEVA)
        assertEquals(1, unanswering.writes)
        assertEquals(0, unanswering.removals)

        sharing.value = false

        // The pending write is cancelled and the removal goes out: the consent is followed even
        // though the write never answered.
        assertEquals(1, unanswering.removals)
        assertEquals(0, provider.activeCollectors)

        // And cancelling the session itself removes nothing more.
        session.cancel()
        assertEquals(1, unanswering.removals)
      }

  /** Starts a session in the background and returns its job, so a test can cancel it. */
  private fun TestScope.startSession(
      sharing: MutableStateFlow<Boolean> = MutableStateFlow(true)
  ): Job = backgroundScope.launch { publisher.publish(EVENT, TEAM, sharing) }

  /** Moves the clock the publisher reads, as the time between two positions would. */
  private fun elapse(seconds: Long) {
    clock = clock.plusSeconds(seconds)
  }

  /**
   * A repository whose writes never answer, as Firestore's did offline before #343 bounded them.
   * Only the counts matter here, so nothing is stored.
   */
  private class UnansweringRepository : LiveLocationRepository {
    var writes = 0
      private set

    var removals = 0
      private set

    override suspend fun shareLocation(
        eventId: String,
        teamId: String,
        position: GeoPosition,
        measuredAt: Instant,
    ) {
      writes++
      awaitCancellation()
    }

    override suspend fun removeLocation(eventId: String, teamId: String) {
      removals++
    }

    override fun observeTeamLocations(
        eventId: String,
        teamId: String,
    ): Flow<List<LiveLocation>> = emptyFlow()
  }

  private suspend fun shared(): List<LiveLocation> =
      repository.observeTeamLocations(EVENT, TEAM).first()

  private companion object {
    const val EVENT = "event-1"
    const val TEAM = "team-1"
    const val VOLUNTEER = "volunteer-1"
    val MIN_INTERVAL_SECONDS = LiveLocationPublisher.MIN_INTERVAL.seconds
    val RETRY_DELAY = LiveLocationPublisher.REMOVAL_RETRY_DELAY.toKotlinDuration()
    val REFRESH_SECONDS = LiveLocationPublisher.REFRESH_INTERVAL.seconds
    val START: Instant = Instant.parse("2026-11-02T09:00:00Z")
    val GENEVA = GeoPosition(46.2044, 6.1432)

    /** About 6 m north of [GENEVA], below [LiveLocationPublisher.MIN_DISTANCE_METERS]. */
    val A_FEW_METRES_AWAY = GeoPosition(46.20445, 6.1432)

    /** About 111 m north of [GENEVA], well above the threshold. */
    val DOWN_THE_STREET = GeoPosition(46.2054, 6.1432)
    val LAUSANNE = GeoPosition(46.5197, 6.6323)
  }
}
