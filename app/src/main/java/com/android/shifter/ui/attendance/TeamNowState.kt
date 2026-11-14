// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
package com.swent.shifter.ui.attendance

import java.time.Duration
import java.time.Instant

/** The delays the team's now state is read with, in one place so they are easy to change. */
object TeamNowRules {
  /** How long before its start a mission is announced as one of the next ones. */
  val UPCOMING_WINDOW: Duration = Duration.ofMinutes(30)

  /** A mission lasting this long or less is short, and gets [SHORT_GRACE_PERIOD]. */
  val SHORT_MISSION_MAX_DURATION: Duration = Duration.ofMinutes(30)

  /** The grace period of a mission lasting [SHORT_MISSION_MAX_DURATION] or less. */
  val SHORT_GRACE_PERIOD: Duration = Duration.ofMinutes(3)

  /** The grace period of a mission lasting more than [SHORT_MISSION_MAX_DURATION]. */
  val LONG_GRACE_PERIOD: Duration = Duration.ofMinutes(5)

  /**
   * How long after the start of a mission lasting [duration] its absentees are expected rather than
   * missing: the handover of back-to-back missions, shorter for a short mission.
   */
  fun gracePeriod(duration: Duration): Duration =
      if (duration > SHORT_MISSION_MAX_DURATION) LONG_GRACE_PERIOD else SHORT_GRACE_PERIOD
}

/**
 * What the manager's now card says about the missions of their teams: is the team where it should
 * be, now and in the next minutes? Decided by [teamNowState], the most urgent state first. Every
 * list of missions is by start, missions starting together in the order they were given.
 */
sealed interface TeamNowState {
  /**
   * Assignees of a mission that started more than its [grace period][TeamNowRules.gracePeriod] ago
   * have not checked in.
   *
   * @property missing who is missing from which mission, by mission start then as the mission lists
   *   them; a volunteer missing from two missions at once is listed under each.
   * @property onDuty how many distinct volunteers are present on the missions under way.
   */
  data class Missing(
      val missing: List<Pair<AttendanceMission, AttendanceVolunteer>>,
      val onDuty: Int,
  ) : TeamNowState

  /**
   * People are expected: on the [handovers], or on the [next] missions when nothing is under way.
   *
   * @property handovers the missions under way for at most their
   *   [grace period][TeamNowRules.gracePeriod] with assignees who have not checked in, the handover
   *   of back-to-back missions; empty when nothing is under way.
   * @property next the missions that start within [TeamNowRules.UPCOMING_WINDOW]; not empty when
   *   [handovers] is.
   */
  data class Arriving(val handovers: List<AttendanceMission>, val next: List<AttendanceMission>) :
      TeamNowState

  /**
   * The team is at work, nobody is reported missing nor expected.
   *
   * @property ongoing the missions under way.
   * @property onDuty how many distinct volunteers are present on them.
   * @property next the missions that start within [TeamNowRules.UPCOMING_WINDOW], if any.
   */
  data class Running(
      val ongoing: List<AttendanceMission>,
      val onDuty: Int,
      val next: List<AttendanceMission>,
  ) : TeamNowState

  /**
   * Nothing is under way or imminent: the [next] missions are those starting at the earliest start
   * to come, after the window.
   */
  data class Quiet(val next: List<AttendanceMission>) : TeamNowState

  /** No mission remains: all are completed, or there were none. */
  data object Done : TeamNowState
}

/**
 * The state of the team at [now], from the [missions] of the teams the manager manages, the most
 * urgent first:
 * 1. [TeamNowState.Missing] when a mission that started more than its grace period ago has
 *    assignees known not to be there;
 * 2. [TeamNowState.Arriving] when missions under way for at most their grace period have assignees
 *    known not to be there yet, whatever else is under way;
 * 3. [TeamNowState.Running] when missions are under way, with those starting within
 *    [TeamNowRules.UPCOMING_WINDOW];
 * 4. [TeamNowState.Arriving] again when nothing is under way and missions start within the window;
 * 5. [TeamNowState.Quiet] when the next missions are further away;
 * 6. [TeamNowState.Done] otherwise.
 *
 * A mission is read through the shared [AttendanceMission.status], so the card and the mission list
 * never disagree: a completed mission is neither under way nor upcoming, and a mission whose
 * attendance is not known yet, or could not be observed, reports nobody missing, since not being
 * seen is not being absent. A mission is under way from its start included, in its grace period up
 * to the end of the period included, and upcoming until the end of the window included. Missions
 * starting at the same time keep their order in [missions].
 */
fun teamNowState(missions: List<AttendanceMission>, now: Instant): TeamNowState {
  val active =
      missions.filter { it.status(now) != AttendanceStatus.COMPLETED }.sortedBy { it.startAt }
  val (ongoing, upcoming) = active.partition { it.startAt <= now }
  val next = upcoming.takeWhile {
    Duration.between(now, it.startAt) <= TeamNowRules.UPCOMING_WINDOW
  }

  val late = ongoing.filter { it.isPastGrace(now) && it.hasAbsentees(now) }
  if (late.isNotEmpty()) {
    val missing = late.flatMap { mission -> mission.missingAssignees.map { mission to it } }
    return TeamNowState.Missing(missing, onDuty(ongoing))
  }
  val handovers = ongoing.filter { !it.isPastGrace(now) && it.hasAbsentees(now) }
  if (handovers.isNotEmpty()) return TeamNowState.Arriving(handovers, next)
  if (ongoing.isNotEmpty()) return TeamNowState.Running(ongoing, onDuty(ongoing), next)
  if (next.isNotEmpty()) return TeamNowState.Arriving(emptyList(), next)
  val soonest = upcoming.firstOrNull() ?: return TeamNowState.Done
  return TeamNowState.Quiet(upcoming.takeWhile { it.startAt == soonest.startAt })
}

/** Whether this mission started more than its [grace period][TeamNowRules.gracePeriod] ago. */
private fun AttendanceMission.isPastGrace(now: Instant): Boolean =
    Duration.between(startAt, now) > TeamNowRules.gracePeriod(Duration.between(startAt, endAt))

/**
 * Whether assignees of this mission are known not to be there: only once its check-ins are
 * followed, by the shared [status], since not being seen is not being absent.
 */
private fun AttendanceMission.hasAbsentees(now: Instant): Boolean =
    when (status(now)) {
      AttendanceStatus.UNDERSTAFFED,
      AttendanceStatus.STAFFED -> missingAssignees.isNotEmpty()
      AttendanceStatus.COMPLETED,
      AttendanceStatus.NOT_OPEN,
      AttendanceStatus.FAILED,
      AttendanceStatus.WAITING,
      AttendanceStatus.EARLY -> false
    }

/** How many distinct volunteers are present on [missions]: one on two of them counts once. */
private fun onDuty(missions: List<AttendanceMission>): Int =
    missions.flatMap { it.presentAssignees }.distinctBy { it.userId }.size
