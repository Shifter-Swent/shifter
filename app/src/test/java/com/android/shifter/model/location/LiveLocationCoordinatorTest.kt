// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.location.LiveLocationSharingState.NO_LOCATION_ACCESS
import com.swent.shifter.model.location.LiveLocationSharingState.OFF
import com.swent.shifter.model.location.LiveLocationSharingState.REFUSED
import com.swent.shifter.model.location.LiveLocationSharingState.SHARED
import com.swent.shifter.model.location.LiveLocationSharingState.WAITING
import com.swent.shifter.model.membership.MembershipRequestRepositoryException
import com.swent.shifter.model.team.TeamRepositoryException
import java.time.Duration
import java.time.Instant
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LiveLocationCoordinator] against a double for the sessions, so what is asserted is which
 * sessions exist rather than what a publisher does with a GPS.
 *
 * Time is virtual: [elapse] moves the test scheduler and the coordinator's clock together, so a
 * refresh happens exactly when the test says and never because the suite was slow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveLocationCoordinatorTest {

  private val sessions = FakeSessions()

  /** What the resolver answers, or throws when [failure] is set. */
  private var eligible: List<LiveLocationTarget> = emptyList()
  private var failure: Exception? = null

  /** When true, the resolver never answers, as a stalled repository read would not. */
  private var hanging = false

  private var clock = START
  private val consent = MutableStateFlow(true)
  private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  /** How many times the resolver was asked, which shows whether the refreshing is blocked. */
  private var resolveCalls = 0

  private val targets = LiveLocationTargets {
    resolveCalls++
    failure?.let { throw it }
    if (hanging) awaitCancellation()
    eligible
  }

  private val coordinator =
      LiveLocationCoordinator(targets, sessions, INTERVAL, STALENESS, now = { clock })

  @Test
  fun run_withOneEligibleTarget_startsOneSession() = runTest {
    eligible = listOf(BAR)

    start()

    assertEquals(listOf(BAR), sessions.started)
    assertEquals(setOf(BAR), sessions.active)
  }

  @Test
  fun run_withTargetsInSeveralEventsAndTeams_startsOneSessionEach() = runTest {
    eligible = listOf(BAR, STAGE, OTHER_EVENT_BAR)

    start()

    assertEquals(setOf(BAR, STAGE, OTHER_EVENT_BAR), sessions.active)
    assertEquals(3, sessions.started.size)
  }

  @Test
  fun run_withTheSameTargetTwice_startsOneSession() = runTest {
    // Two teams of the same event differ by teamId alone, so a pair is only ever one session.
    eligible = listOf(BAR, BAR)

    start()

    assertEquals(listOf(BAR), sessions.started)
  }

  @Test
  fun run_whenARefreshAnswersTheSameTargets_keepsTheSessionsRunning() = runTest {
    eligible = listOf(BAR, STAGE)
    start()

    elapse(INTERVAL)
    elapse(INTERVAL)

    assertEquals(2, sessions.started.size)
    assertEquals(setOf(BAR, STAGE), sessions.active)
  }

  @Test
  fun run_whenATargetBecomesEligible_startsItsSession() = runTest {
    eligible = listOf(BAR)
    start()

    eligible = listOf(BAR, STAGE)
    elapse(INTERVAL)

    assertEquals(listOf(BAR, STAGE), sessions.started)
    assertEquals(setOf(BAR, STAGE), sessions.active)
  }

  @Test
  fun run_whenATargetStopsBeingEligible_cancelsItsSessionOnly() = runTest {
    eligible = listOf(BAR, STAGE)
    start()

    eligible = listOf(STAGE)
    elapse(INTERVAL)

    assertEquals(setOf(STAGE), sessions.active)
  }

  @Test
  fun run_whenARefreshOmitsATargetAndThenAnswersItAgain_cancelsThenRestartsIt() = runTest {
    eligible = listOf(BAR, STAGE)
    start()

    // A read that lost one event to a per-event repository failure looks exactly like this: a
    // success with fewer targets. The session goes at once rather than after STALENESS.
    eligible = listOf(STAGE)
    elapse(INTERVAL)
    assertEquals(setOf(STAGE), sessions.active)

    eligible = listOf(BAR, STAGE)
    elapse(INTERVAL)

    assertEquals(setOf(BAR, STAGE), sessions.active)
    assertEquals(listOf(BAR, STAGE, BAR), sessions.started)
  }

  @Test
  fun run_whenNothingIsEligibleAnyMore_cancelsEverySession() = runTest {
    eligible = listOf(BAR, STAGE)
    start()

    eligible = emptyList()
    elapse(INTERVAL)

    assertTrue(sessions.active.isEmpty())
  }

  @Test
  fun run_whenARefreshIsTriggered_reconcilesWithoutWaitingForTheInterval() = runTest {
    start()

    eligible = listOf(BAR)
    refreshes.emit(Unit)
    runCurrent()

    assertEquals(setOf(BAR), sessions.active)
  }

  @Test
  fun run_cancelled_cancelsEverySession() = runTest {
    eligible = listOf(BAR, STAGE)
    val running = start()

    running.cancelAndJoin()

    assertTrue(sessions.active.isEmpty())
  }

  @Test
  fun run_whenEligibilityCannotBeReadAtFirst_startsNothingAndRetries() = runTest {
    failure = unavailable()
    eligible = listOf(BAR)

    start()
    assertTrue(sessions.started.isEmpty())

    failure = null
    elapse(INTERVAL)

    assertEquals(setOf(BAR), sessions.active)
  }

  @Test
  fun run_whenEligibilityCannotBeRead_keepsTheSessionsUntilTheStalenessHasPassed() = runTest {
    eligible = listOf(BAR)
    start()
    failure = unavailable()

    // STALENESS is three intervals, so the first two refreshes keep the sessions and the third,
    // the first one at or past the bound, drops them.
    elapse(INTERVAL)
    assertEquals(setOf(BAR), sessions.active)
    elapse(INTERVAL)
    assertEquals(setOf(BAR), sessions.active)

    elapse(INTERVAL)

    assertTrue(sessions.active.isEmpty())
  }

  @Test
  fun run_whenEligibilityNeverAnswers_timesOutAndStillCancelsTheSessions() = runTest {
    eligible = listOf(BAR)
    start()

    // A read that neither answers nor throws would block the refreshing, and with it the staleness
    // check, if the refresh were not bounded. The ticks are buffered, so one still arrives per
    // interval and the timeout is what lets the next one be taken.
    hanging = true

    elapse(INTERVAL) // A refresh is taken and hangs, holding the only answer there is.
    assertEquals(2, resolveCalls)
    assertEquals(setOf(BAR), sessions.active)

    // That one was interrupted by its timeout, which is the only way another can be taken: a
    // refresh left hanging would leave this at two for ever. Unread, but not yet stale enough.
    elapse(INTERVAL)
    assertEquals(3, resolveCalls)
    assertEquals(setOf(BAR), sessions.active)

    elapse(INTERVAL) // The timeout now lands at the bound.

    assertTrue(sessions.active.isEmpty())
  }

  @Test
  fun run_whenEligibilityAnswersAgainAfterHanging_startsTheSessionsAgain() = runTest {
    eligible = listOf(BAR)
    start()
    hanging = true
    repeat(STALENESS_REFRESHES) { elapse(INTERVAL) }
    assertTrue(sessions.active.isEmpty())

    hanging = false
    elapse(INTERVAL)

    // Started twice, so this is a second session and not the first one still running: a timeout
    // that never fired would leave that one alive and looking just as eligible.
    assertEquals(listOf(BAR, BAR), sessions.started)
    assertEquals(setOf(BAR), sessions.active)
  }

  @Test
  fun run_whenEligibilityComesBackAfterTheStaleness_startsTheSessionsAgain() = runTest {
    eligible = listOf(BAR)
    start()
    failure = unavailable()

    // One refresh per interval up to the bound; which refresh cancels is held down above.
    repeat(STALENESS_REFRESHES) { elapse(INTERVAL) }
    assertTrue(sessions.active.isEmpty())

    failure = null
    elapse(INTERVAL)

    assertEquals(setOf(BAR), sessions.active)
    assertEquals(listOf(BAR, BAR), sessions.started)
  }

  @Test
  fun run_whenAnyRepositoryOfTheResolverFails_alsoCountsAsUnread() = runTest {
    eligible = listOf(BAR)
    val faults = mutableListOf<Throwable>()
    start(faults)

    // The resolver reads three repositories, each raising failures of its own kind, and the tests
    // above only ever raise the membership one. Any of them means the same thing - eligibility
    // could not be read - and none of them is a fault: one left uncaught would come out of run(),
    // ending the refreshing and every session with it. Two refreshes stay inside STALENESS, so the
    // session is kept rather than cancelled.
    failure = EventRepositoryException.Unavailable()
    elapse(INTERVAL)
    assertEquals(setOf(BAR), sessions.active)

    failure = TeamRepositoryException.Unavailable()
    elapse(INTERVAL)
    assertEquals(setOf(BAR), sessions.active)

    failure = null
    elapse(INTERVAL)

    // Still the one session, so the refreshing survived both failures without ever restarting it.
    assertEquals(listOf(BAR), sessions.started)
    assertEquals(setOf(BAR), sessions.active)
    assertTrue(faults.isEmpty())
  }

  @Test
  fun run_whenOneSessionFails_leavesTheOthersRunning() = runTest {
    sessions.failing += BAR
    eligible = listOf(BAR, STAGE)
    val faults = mutableListOf<Throwable>()

    start(faults)

    assertEquals(setOf(STAGE), sessions.active)
    assertEquals(1, faults.size)
  }

  @Test
  fun run_whenOneSessionFails_startsItAgainAtTheNextRefresh() = runTest {
    sessions.failing += BAR
    eligible = listOf(BAR)
    val faults = mutableListOf<Throwable>()
    start(faults)

    sessions.failing -= BAR
    elapse(INTERVAL)

    assertEquals(setOf(BAR), sessions.active)
    assertEquals(listOf(BAR, BAR), sessions.started)
  }

  @Test
  fun run_whenASessionEndsOnItsOwn_startsItAgainAtTheNextRefresh() = runTest {
    sessions.completing += BAR
    eligible = listOf(BAR)
    start()

    elapse(INTERVAL)

    assertEquals(listOf(BAR, BAR), sessions.started)
  }

  @Test
  fun run_whenTheConsentChanges_forwardsEveryValueAsItIs() = runTest {
    eligible = listOf(BAR)
    start()

    consent.value = false
    runCurrent()
    consent.value = true
    runCurrent()

    // No placeholder before the first real value, and the change reached the session without any
    // refresh: not a single interval passed in this test.
    assertEquals(listOf(true, false, true), sessions.consentSeen[BAR])
  }

  @Test
  fun run_withTheConsentOff_stillStartsTheSession() = runTest {
    // The session is what takes the position out again, so consent off is not "no session".
    consent.value = false
    eligible = listOf(BAR)

    start()

    assertEquals(setOf(BAR), sessions.active)
    assertEquals(listOf(false), sessions.consentSeen[BAR])
  }

  @Test
  fun run_givesEverySessionTheSameConsentFlow() = runTest {
    eligible = listOf(BAR, STAGE)
    start()

    consent.value = false
    runCurrent()

    assertEquals(listOf(true, false), sessions.consentSeen[BAR])
    assertEquals(listOf(true, false), sessions.consentSeen[STAGE])
  }

  @Test
  fun run_withTheProductionDefaults_refreshesOnTheIntervalItDocuments() = runTest {
    // Every other test overrides the intervals and the clock, so this one runs what production
    // runs: the documented interval alone drives the refreshing, with no refreshes flow to help.
    val coordinator = LiveLocationCoordinator(targets, sessions)
    eligible = listOf(BAR)
    backgroundScope.launch { coordinator.run(consent) }
    runCurrent()
    assertEquals(setOf(BAR), sessions.active)
    assertEquals(1, resolveCalls)

    // This coordinator reads the wall clock, not `clock`; only the scheduler matters here.
    elapse(LiveLocationCoordinator.REFRESH_INTERVAL)

    assertEquals(2, resolveCalls)
    assertEquals(setOf(BAR), sessions.active)
  }

  @Test
  fun sharingStates_withNoEligibleTarget_isEmpty() = runTest {
    start()

    assertEquals(emptyMap<LiveLocationTarget, LiveLocationSharingState>(), states())
  }

  @Test
  fun states_areDeclaredFromTheLeastToTheMostUrgent() {
    // The indicator shows the greatest state of the teams, so this order is what keeps a problem in
    // one team from being hidden behind a success in another.
    assertEquals(
        listOf(OFF, SHARED, WAITING, NO_LOCATION_ACCESS, REFUSED),
        LiveLocationSharingState.entries,
    )
  }

  @Test
  fun sharingStates_whenATargetDropsOut_forgetsItsSessionsState() = runTest {
    eligible = listOf(BAR, STAGE)
    start()
    sessions.report(BAR, SHARED)
    sessions.report(STAGE, REFUSED)

    eligible = listOf(BAR)
    elapse(INTERVAL)

    // The refusal belonged to a session that no longer runs: showing it would be wrong.
    assertEquals(mapOf(BAR to SHARED), states())
  }

  @Test
  fun sharingStates_keepsEachTargetsOwnState() = runTest {
    eligible = listOf(BAR, STAGE, OTHER_EVENT_BAR)
    start()
    sessions.report(BAR, SHARED)
    sessions.report(STAGE, REFUSED)
    sessions.report(OTHER_EVENT_BAR, WAITING)

    // Each target with its own state, the same team of another event included: the sheet lists
    // which team does not get the position, not just that one does not.
    assertEquals(
        mapOf(BAR to SHARED, STAGE to REFUSED, OTHER_EVENT_BAR to WAITING),
        states(),
    )
  }

  @Test
  fun sharingStates_whenAnOldSessionEndsAfterItsReplacementStarted_keepsTheReplacementsState() =
      runTest {
        eligible = listOf(BAR)
        start()
        sessions.report(BAR, SHARED)
        val oldSessionEnds = CompletableDeferred<Unit>()
        sessions.slowToEnd[BAR] = oldSessionEnds

        // BAR drops out, its session is cancelled but still ending, and BAR comes back.
        eligible = emptyList()
        elapse(INTERVAL)
        eligible = listOf(BAR)
        elapse(INTERVAL)
        sessions.report(BAR, WAITING)

        oldSessionEnds.complete(Unit)
        runCurrent()

        assertEquals(mapOf(BAR to WAITING), states())
      }

  /** A session still finishing a write once cancelled must not be shown as sharing meanwhile. */
  @Test
  fun sharingStates_whenATargetDropsOut_forgetsItsStateBeforeItsSessionHasEnded() = runTest {
    eligible = listOf(BAR, STAGE)
    start()
    sessions.report(BAR, SHARED)
    sessions.report(STAGE, SHARED)
    val sessionEnds = CompletableDeferred<Unit>()
    sessions.slowToEnd[BAR] = sessionEnds

    eligible = listOf(STAGE)
    elapse(INTERVAL)

    assertEquals(mapOf(STAGE to SHARED), states())
    sessionEnds.complete(Unit)
  }

  @Test
  fun sharingStates_whenTheSessionsGoStale_forgetsTheirStatesBeforeTheyHaveEnded() = runTest {
    eligible = listOf(BAR)
    start()
    sessions.report(BAR, SHARED)
    val sessionEnds = CompletableDeferred<Unit>()
    sessions.slowToEnd[BAR] = sessionEnds

    failure = unavailable()
    repeat(STALENESS_REFRESHES) { elapse(INTERVAL) }

    assertEquals(emptyMap<LiveLocationTarget, LiveLocationSharingState>(), states())
    sessionEnds.complete(Unit)
  }

  @Test
  fun sharingStates_whenACancelledSessionStillReports_keepsTheReplacementsState() = runTest {
    eligible = listOf(BAR)
    start()
    val oldSession = sessions.reporterOf(BAR)

    eligible = emptyList()
    elapse(INTERVAL)
    eligible = listOf(BAR)
    elapse(INTERVAL)
    sessions.report(BAR, WAITING)

    // Too late: that session was cancelled, and its replacement is the one that says what BAR is.
    oldSession(REFUSED)

    assertEquals(mapOf(BAR to WAITING), states())
  }

  @Test
  fun sharingStates_whenASessionFails_forgetsItsState() = runTest {
    eligible = listOf(BAR, STAGE)
    start()
    sessions.report(BAR, SHARED)
    sessions.report(STAGE, REFUSED)

    sessions.fail(STAGE, IllegalStateException("The session of STAGE failed"))
    runCurrent()

    // A failed session shares nothing any more, until the next refresh starts it again.
    assertEquals(mapOf(BAR to SHARED), states())
  }

  @Test
  fun sharingStates_listsATargetOnlyOnceItsSessionReported() = runTest {
    eligible = listOf(BAR, STAGE)
    start()
    sessions.report(BAR, WAITING)

    assertEquals(mapOf(BAR to WAITING), states())
  }

  @Test
  fun sharingStates_whenRunIsCancelled_isEmpty() = runTest {
    eligible = listOf(BAR)
    val running = start()
    sessions.report(BAR, SHARED)

    running.cancelAndJoin()

    assertEquals(emptyMap<LiveLocationTarget, LiveLocationSharingState>(), states())
  }

  @Test
  fun of_addressesTheSessionWithTheTargetsEventAndTeamAndPassesOnItsStates() =
      runTest(UnconfinedTestDispatcher()) {
        val repository = FakeLiveLocationRepository(currentUserId = VOLUNTEER)
        val provider = FakeLocationProvider()
        val publisher = LiveLocationPublisher(provider, repository, now = { clock })
        val reported = mutableListOf<LiveLocationSharingState>()
        backgroundScope.launch {
          LiveLocationSessions.of(publisher).publish(BAR, MutableStateFlow(true), reported::add)
        }

        provider.emit(GENEVA)

        // What the publisher reports reaches the coordinator: without it, no state would ever show.
        assertEquals(listOf(WAITING, SHARED), reported)

        assertEquals(
            listOf(LiveLocation(VOLUNTEER, GENEVA, clock)),
            repository.observeTeamLocations(BAR.eventId, BAR.teamId).first(),
        )
        // Nothing under the pair the other way round, which a transposed adapter would write to.
        assertEquals(
            emptyList<LiveLocation>(),
            repository.observeTeamLocations(BAR.teamId, BAR.eventId).first(),
        )
      }

  /** What the coordinator reports for each target right now. */
  private suspend fun states(): Map<LiveLocationTarget, LiveLocationSharingState> =
      coordinator.sharingStates.first()

  /** The kind of failure a repository raises when the backend cannot be reached. */
  private fun unavailable() = MembershipRequestRepositoryException.Unavailable()

  /**
   * Starts the coordinator and lets its first refresh run, reporting faults to [faults].
   *
   * In [TestScope.backgroundScope], since [LiveLocationCoordinator.run] only ends when it is
   * cancelled: the test would otherwise wait for it for ever.
   */
  private fun TestScope.start(faults: MutableList<Throwable> = mutableListOf()): Job {
    val running =
        backgroundScope.launch(CoroutineExceptionHandler { _, e -> faults += e }) {
          coordinator.run(consent, refreshes)
        }
    runCurrent()
    return running
  }

  /**
   * Moves the scheduler and the coordinator's clock by [duration], then runs what is due.
   *
   * The clock moves first, so everything the step runs reads it at the end of the step. A test that
   * cares which refresh observes which staleness therefore walks one [INTERVAL] at a time rather
   * than jumping, a jump letting the first refresh of the step already see the final clock.
   */
  private fun TestScope.elapse(duration: Duration) {
    clock += duration
    advanceTimeBy(duration.toKotlinDuration())
    runCurrent()
  }

  /**
   * A double for [LiveLocationSessions] recording the sessions asked for, the ones running, and the
   * consent each one saw.
   */
  private class FakeSessions : LiveLocationSessions {

    /** Every target a session was started for, in order, restarts included. */
    val started = mutableListOf<LiveLocationTarget>()

    /** The targets with a session running right now. */
    val active = mutableSetOf<LiveLocationTarget>()

    /** What each session read from the consent flow, in order. */
    val consentSeen = mutableMapOf<LiveLocationTarget, MutableList<Boolean>>()

    /** Targets whose session fails at once, as an unexpected fault would. */
    val failing = mutableSetOf<LiveLocationTarget>()

    /** Targets whose session returns at once instead of running until cancelled. */
    val completing = mutableSetOf<LiveLocationTarget>()

    /**
     * Targets whose next session, once cancelled, only ends when its gate opens, as a publisher
     * still finishing a write would. Taken by that session, so the one after ends at once.
     */
    val slowToEnd = mutableMapOf<LiveLocationTarget, CompletableDeferred<Unit>>()

    /** How each running session reports its state, so a test can play the publisher's part. */
    private val reporters = mutableMapOf<LiveLocationTarget, (LiveLocationSharingState) -> Unit>()

    /** Reports [state] from the running session of [target], as the publisher would. */
    fun report(target: LiveLocationTarget, state: LiveLocationSharingState) =
        reporters.getValue(target)(state)

    /** How the latest session of [target] reports, kept by a test to report from it later. */
    fun reporterOf(target: LiveLocationTarget) = reporters.getValue(target)

    /** Makes the running session of [target] fail with [cause], as an unexpected fault would. */
    fun fail(target: LiveLocationTarget, cause: Throwable) =
        faults.getValue(target).completeExceptionally(cause)

    private val faults = mutableMapOf<LiveLocationTarget, CompletableDeferred<Nothing>>()

    override suspend fun publish(
        target: LiveLocationTarget,
        sharingEnabled: Flow<Boolean>,
        onState: (LiveLocationSharingState) -> Unit,
    ) {
      started += target
      reporters[target] = onState
      if (target in failing) throw IllegalStateException("The session of $target failed")
      if (target in completing) return
      active += target
      val fault = CompletableDeferred<Nothing>().also { faults[target] = it }
      try {
        coroutineScope {
          launch {
            sharingEnabled.collect { consentSeen.getOrPut(target) { mutableListOf() } += it }
          }
          // Runs until cancelled or made to fail: a consent flow that completes does not end the
          // session, as the real publisher's does not.
          fault.await()
        }
      } finally {
        active -= target
        slowToEnd.remove(target)?.let { gate -> withContext(NonCancellable) { gate.await() } }
      }
    }
  }

  private companion object {
    val START: Instant = Instant.parse("2026-11-08T14:00:00Z")

    val INTERVAL: Duration = Duration.ofMinutes(1)

    /** How many refreshes [STALENESS] spans, so a test can walk up to the bound one at a time. */
    const val STALENESS_REFRESHES = 3

    /** Three refreshes' worth, so a test can fail two of them and still keep the sessions. */
    val STALENESS: Duration = INTERVAL.multipliedBy(STALENESS_REFRESHES.toLong())

    const val VOLUNTEER = "volunteer"
    val GENEVA = GeoPosition(46.2044, 6.1432)

    val BAR = LiveLocationTarget("event", "team-bar")
    val STAGE = LiveLocationTarget("event", "team-stage")
    val OTHER_EVENT_BAR = LiveLocationTarget("other-event", "team-bar")
  }
}
