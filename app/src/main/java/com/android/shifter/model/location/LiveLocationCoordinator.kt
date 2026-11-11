// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.membership.MembershipRequestRepositoryException
import com.swent.shifter.model.team.TeamRepositoryException
import java.time.Duration
import java.time.Instant
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Publishes the signed-in volunteer's position for one target until cancelled, reporting what it is
 * doing to `onState`: [LiveLocationPublisher.publish] behind one call per target, so a coordinator
 * can be driven without a GPS.
 */
fun interface LiveLocationSessions {

  suspend fun publish(
      target: LiveLocationTarget,
      sharingEnabled: Flow<Boolean>,
      onState: (LiveLocationSharingState) -> Unit,
  )

  companion object {
    /** The sessions [publisher] runs, which is what production shares positions with. */
    fun of(publisher: LiveLocationPublisher) =
        LiveLocationSessions { target, sharingEnabled, onState ->
          publisher.publish(target.eventId, target.teamId, sharingEnabled, onState)
        }
  }
}

/**
 * Keeps one publishing session running per eligible target, for as long as [run] is not cancelled.
 *
 * It owns no position and reads no device: [LiveLocationTargets] says where a position may go,
 * [LiveLocationSessions] puts it there, and this only decides which sessions exist. One session per
 * `(eventId, teamId)` pair, never two for the same pair.
 *
 * **Eligibility is polled**, every [refreshInterval], because [LiveLocationTargets.eligible] is a
 * snapshot and no repository offers a reactive query for it. So a target that stops being eligible
 * - its event ended, the volunteer left the team or became its manager - keeps publishing until the
 *   next refresh notices, and `firestore.rules` accept those writes for an hour after the event
 *   ends rather than refusing them. A session is cancelled when a successful refresh no longer
 *   lists its target: normally up to about one [refreshInterval] after eligibility is lost, later
 *   when a refresh is slow, fails or times out (see [run]). Ending a session exactly with its event
 *   is a separate concern (#406).
 *
 * @param now the clock measuring staleness, replaceable in tests.
 */
class LiveLocationCoordinator(
    private val targets: LiveLocationTargets,
    private val sessions: LiveLocationSessions,
    private val refreshInterval: Duration = REFRESH_INTERVAL,
    private val maxStaleness: Duration = MAX_STALENESS,
    private val now: () -> Instant = Instant::now,
) {

  /**
   * The state each target's session last reported, with the session that reported it: a session
   * cancelled and started again for the same target may end after its replacement started, and must
   * then leave the replacement's state alone.
   */
  private val sessionStates =
      MutableStateFlow<Map<LiveLocationTarget, Pair<Job, LiveLocationSharingState>>>(emptyMap())

  /**
   * What the sharing is doing for each target a session runs for, so the volunteer can see which
   * event and team gets their position. Empty while none runs - before the first refresh, with no
   * eligible target, once [run] is cancelled - and a target is missing until its session reports.
   *
   * A session that ends - cancelled because its target dropped out, failed, or ended with [run] -
   * takes its state with it, so nothing is shown for a session that no longer exists.
   */
  val sharingStates: Flow<Map<LiveLocationTarget, LiveLocationSharingState>> =
      sessionStates.map { states -> states.mapValues { it.value.second } }.distinctUntilChanged()

  /**
   * The state of [sharingStates] as a whole, `null` when it is empty. The most urgent state wins
   * (see [LiveLocationSharingState]), so a team whose position is refused is not hidden behind
   * another whose position is shared.
   */
  val sharingState: Flow<LiveLocationSharingState?> =
      sharingStates.map { it.values.maxOrNull() }.distinctUntilChanged()

  /**
   * Runs the sessions of the eligible targets until cancelled, refreshing every [refreshInterval]
   * and whenever [refreshes] emits.
   *
   * Cancelling this cancels every session; a cancelled session leaves its last position behind, as
   * [LiveLocationPublisher] documents. The sessions run under a supervisor, so **one failing
   * session neither stops the others nor ends the refreshing**, and the target is started again at
   * the next refresh. It is not caught, though: [LiveLocationPublisher.publish] does not throw on a
   * refused write, so a throw is a fault, and it reaches the caller's
   * [kotlinx.coroutines.CoroutineExceptionHandler] - without one it crashes the app, so a caller
   * must install one.
   *
   * **[sharingEnabled] is passed to every session unchanged**, so a change reaches them as fast as
   * the flow delivers it, whatever [refreshInterval] says. It must emit the real setting and never
   * a placeholder `false` while it loads, as [LiveLocationPublisher.publish] requires, and should
   * be hot and shared (a `StateFlow`): every session collects it separately, so a cold flow is read
   * once per session and one that completes ends the session it feeds. Sessions start whatever the
   * consent says, consent off being a session that removes the position.
   *
   * Two different failures, on purpose:
   * - **A refresh that fails or times out keeps the running sessions**, until one arrives more than
   *   [maxStaleness] after the last eligibility that could be read, which cancels them all. The
   *   check happens on a refresh, so the sessions stop at the first refresh past [maxStaleness],
   *   not at the moment it elapses.
   * - **A refresh that merely omits a target cancels its session at once**, with no grace period,
   *   because it is a success as far as this class can tell: the resolver swallows a per-event
   *   repository failure and answers fewer targets, which is indistinguishable from the event
   *   having ended. The session starts again when that event reads again, so a flaky event costs a
   *   restart rather than a position shared past its eligibility.
   *
   * @param refreshes extra moments to refresh at, such as the app returning to the foreground. A
   *   refresh does not reset the interval, and this flow is collected under backpressure, so it
   *   should be buffered or conflated rather than block its emitter.
   */
  suspend fun run(
      sharingEnabled: Flow<Boolean>,
      refreshes: Flow<Unit> = emptyFlow(),
  ): Unit = supervisorScope {
    val running = mutableMapOf<LiveLocationTarget, Job>()
    var confirmedAt = now()
    merge(ticks(), refreshes).collect {
      val eligible = eligibleOrNull()
      if (eligible == null) {
        if (now() >= confirmedAt + maxStaleness) cancelAll(running)
      } else {
        confirmedAt = now()
        reconcile(running, eligible, sharingEnabled)
      }
    }
  }

  /** Now, then every [refreshInterval]: the first refresh starts the sessions without waiting. */
  private fun ticks(): Flow<Unit> = flow {
    while (true) {
      emit(Unit)
      delay(refreshInterval.toKotlinDuration())
    }
  }

  /**
   * The eligible targets, or null when they cannot be read: only a failure of the repositories the
   * resolver reads, or a call still unanswered after [refreshInterval], which would otherwise block
   * the refreshing and with it the staleness check. Everything else propagates, cancellation and a
   * programming error in the resolver included.
   */
  private suspend fun eligibleOrNull(): Set<LiveLocationTarget>? =
      try {
        withTimeoutOrNull(refreshInterval.toKotlinDuration()) { targets.eligible().toSet() }
      } catch (_: MembershipRequestRepositoryException) {
        null
      } catch (_: EventRepositoryException) {
        null
      } catch (_: TeamRepositoryException) {
        null
      }

  /**
   * Starts a session per newly eligible target and cancels those that dropped out, leaving the rest
   * running. A [Set] makes a pair answered twice one session.
   */
  private fun CoroutineScope.reconcile(
      running: MutableMap<LiveLocationTarget, Job>,
      eligible: Set<LiveLocationTarget>,
      sharingEnabled: Flow<Boolean>,
  ) {
    // A session that ended on its own is forgotten, so this refresh starts it again.
    running.values.removeAll { !it.isActive }
    (running.keys - eligible).forEach { running.remove(it)?.cancel() }
    (eligible - running.keys).forEach { target ->
      running[target] = launch {
        val session = coroutineContext.job
        try {
          sessions.publish(target, sharingEnabled) { state ->
            // A cancelled session may have a replacement already: it reports nothing more.
            if (session.isActive) sessionStates.update { it + (target to (session to state)) }
          }
        } finally {
          // After publish has returned, so nothing it reports can bring the state back. Only its
          // own: the target may already have a new session.
          sessionStates.update { if (it[target]?.first == session) it - target else it }
        }
      }
    }
  }

  /** Cancels and forgets every session, without waiting for them: a refresh must not be held up. */
  private fun cancelAll(running: MutableMap<LiveLocationTarget, Job>) {
    running.values.forEach { it.cancel() }
    running.clear()
  }

  companion object {
    /**
     * How long between two eligibility refreshes, and how long one may take before it counts as
     * unread. It matches [LiveLocationPublisher.REFRESH_INTERVAL], so a session kept past its
     * eligibility writes about one position more than it should.
     */
    val REFRESH_INTERVAL: Duration = Duration.ofMinutes(2)

    /**
     * How long the running sessions outlive the last eligibility that could be read at all. Five
     * refreshes: long enough to ride out a lost request without the sharing flapping, short enough
     * to stop well inside the hour the rules keep accepting a position after the event.
     */
    val MAX_STALENESS: Duration = Duration.ofMinutes(10)
  }
}
