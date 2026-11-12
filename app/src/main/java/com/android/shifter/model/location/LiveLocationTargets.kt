// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.model.team.TeamRepositoryException
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * A team of an event the signed-in volunteer may share their position with, as
 * [LiveLocationRepository] addresses it, together with the event's period.
 *
 * Whose position it is never appears: a volunteer can only ever write their own.
 *
 * [startAt] and [endAt] are the event's period. A session for this target publishes only within it
 * and stops starting new work at [endAt], rather than waiting for the coordinator's next
 * eligibility refresh; see [LiveLocationSessions.of], which also covers the one write Firestore may
 * still send after the end. They are part of the target's identity, so an event whose period is
 * edited makes a different target and the coordinator restarts its session on the new window.
 */
data class LiveLocationTarget(
    val eventId: String,
    val teamId: String,
    val startAt: Instant,
    val endAt: Instant,
)

/**
 * Where the signed-in volunteer may share their position, which is half of the sharing: it reads
 * nothing from the device and writes nothing, so a [LiveLocationPublisher] session per target is
 * the caller's job.
 *
 * Consent is not part of it, being read elsewhere and passed to [LiveLocationPublisher.publish]:
 * the targets are the same whether sharing is on or off, and a session starting with it off still
 * has a position to take out.
 */
fun interface LiveLocationTargets {

  /**
   * The pairs eligible at the moment of the call, without duplicates, empty when there is none.
   *
   * **A snapshot, not a subscription**: an event starting or ending, a request accepted, a
   * volunteer moved between teams or appointed manager all change the answer, and none of it is
   * reported here. Refreshing it, and ending the sessions of the targets that drop out, is the
   * coordinator's job.
   *
   * **A stale snapshot is no permission to go on publishing.** `firestore.rules` are wider than
   * this resolver, accepting a position until an hour after the event ends, so a session kept past
   * eligibility may well keep succeeding and track a volunteer whose event is over.
   */
  suspend fun eligible(): List<LiveLocationTarget>
}

/**
 * The eligible targets read from the repositories: the teams the signed-in volunteer belongs to, in
 * the events they were accepted into, while those events are running.
 *
 * Each condition comes from the one place that can answer it:
 * - **The event**, from an accepted [com.swent.shifter.model.membership.MembershipRequest]: a
 *   volunteer cannot list the participants of an event, so their own request is the only record of
 *   staffing it.
 * - **The team**, from [TeamRepository.getTeamsOfMember], never from
 *   [MembershipRequest.teamId][com.swent.shifter.model.membership.MembershipRequest.teamId], which
 *   records what they applied to and an organizer may have changed since. `firestore.rules` check
 *   the membership itself, so anything else would offer targets whose writes are refused. A
 *   volunteer in several teams of one event gets one target per team.
 * - **The period**, from [Event.startAt] and [Event.endAt], the two fields the rules check.
 *   [Event.status] is deliberately ignored, as the rules ignore it, so an organizer who forgets to
 *   mark their event ongoing does not stop their volunteers being located. The hour of margin the
 *   rules allow on each side is not taken: sharing starts with the event, never before it.
 *
 * **Only the teams the volunteer staffs, not the ones they lead.** A manager reads their team's
 * positions rather than sharing one of their own, and the rules accept a position only from a
 * member, which a manager never is. That is the current scope rather than a rule about managers -
 * showing a manager's position to their organizer would need its own readers, rules and consent -
 * and a manager keeps the targets of the teams they merely staff. [TeamRepository.getTeamsOfMember]
 * already leaves out the teams they lead; their
 * [managerId][com.swent.shifter.model.team.Team.managerId] is checked again so that a document
 * where the two overlap cannot slip one through.
 *
 * An event or a team deleted since the request was sent simply has no target: Firestore keeps the
 * request under the deleted event.
 *
 * @param currentUserId the signed-in user's id, or null when nobody is signed in.
 * @param now the clock telling which events are running, replaceable in tests.
 */
class RepositoryLiveLocationTargets(
    private val eventRepository: EventRepository,
    private val membershipRequestRepository: MembershipRequestRepository,
    private val teamRepository: TeamRepository,
    private val currentUserId: () -> String?,
    private val now: () -> Instant = Instant::now,
) : LiveLocationTargets {

  /**
   * Reads the accepted requests, then each event and its teams in parallel.
   *
   * A failure to read the requests propagates: there is no list of events to work from at all, and
   * the caller decides whether to try again.
   */
  override suspend fun eligible(): List<LiveLocationTarget> = coroutineScope {
    val userId = currentUserId() ?: return@coroutineScope emptyList()
    val now = now()
    membershipRequestRepository
        .getMembershipRequestsByUId(userId)
        .filterValues { it.status == MembershipRequestStatus.ACCEPTED }
        .map { (eventId, _) -> async { targetsIn(eventId, userId, now) } }
        .awaitAll()
        .flatten()
        // Guards against the same pair arriving twice, which two sessions would publish twice over.
        .distinct()
  }

  /**
   * The teams of [eventId] that [userId] may share their position with at [now], and none when the
   * event or its teams cannot be read.
   *
   * Each event is read on its own, so one answering badly loses its own targets instead of taking
   * the whole list down with it. A failure is therefore indistinguishable from ineligibility: an
   * event briefly unreadable, offline for a moment, drops out of the result and comes back on a
   * later call, so a caller must not read its absence as the event having ended.
   *
   * Only the two repositories' own failures are swallowed, so cancellation propagates, and so does
   * a programming error - a blank id reaching a query, say - rather than passing for a volunteer
   * who happens to be ineligible.
   */
  private suspend fun targetsIn(
      eventId: String,
      userId: String,
      now: Instant,
  ): List<LiveLocationTarget> =
      try {
        val event = eventRepository.getEvent(eventId)
        if (event == null || !event.isRunning(now)) emptyList()
        else
            teamRepository
                .getTeamsOfMember(eventId, userId)
                .filter { it.managerId != userId }
                .map { LiveLocationTarget(eventId, it.id, event.startAt, event.endAt) }
      } catch (_: EventRepositoryException) {
        emptyList()
      } catch (_: TeamRepositoryException) {
        emptyList()
      }
}

/**
 * Whether the event is in progress at [now], both bounds included, as `firestore.rules` read it
 * from the same two fields. An event ending before it starts is never running.
 */
private fun Event.isRunning(now: Instant): Boolean = now in startAt..endAt
