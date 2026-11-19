// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
package com.swent.shifter.ui.attendance

import com.swent.shifter.ui.components.days.eventDay
import com.swent.shifter.ui.components.days.runsOnEventDay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * The delays the team's now state is read with, in one place so they are easy to change.
 *
 * They are the manager's, and differ from the volunteer's own now state on purpose: the manager
 * watches a whole team, so a mission is announced 30 minutes ahead rather than 45, and its
 * absentees are expected for a grace period rather than late from the start. Do not align the two.
 */
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
 * A volunteer expected on a mission who has not checked in, one entry of
 * [TeamNowState.Missing.missing].
 */
data class MissingAssignee(
    val mission: AttendanceMission,
    val volunteer: AttendanceVolunteer,
)

/**
 * What the manager's now card says about the missions of their teams: is the team where it should
 * be, now and in the next minutes? Decided by [teamNowState], the most urgent state first. Every
 * list of missions is by start, missions starting together in the order they were given.
 *
 * A state that counts people counts only the missions whose check-ins are followed. The ones whose
 * attendance has not arrived yet, or could not be observed, are listed apart, in
 * `attendanceUnknown`: the card warns about them instead of reading them as nobody present.
 */
sealed interface TeamNowState {
  /**
   * Assignees of a mission that started more than its [grace period][TeamNowRules.gracePeriod] ago
   * have not checked in.
   *
   * A volunteer just checked in by hand leaves [missing] as soon as the write is pending, so the
   * card, not this state, keeps them a moment in green.
   *
   * @property missing who is missing from which mission, by mission start then as the mission lists
   *   them; a volunteer missing from two missions at once is listed under each, so "N volunteers
   *   missing" counts distinct `userId`s, not entries.
   * @property onDuty how many distinct volunteers are present on the missions under way and are not
   *   in [missing]: the others, whom the card reports as checked in. A volunteer absent from one
   *   mission and present on an overlapping one is missing, not on duty.
   * @property attendanceUnknown the missions under way whose check-ins have not arrived or could
   *   not be observed; they report nobody missing and count for nobody in [onDuty].
   */
  data class Missing(
      val missing: List<MissingAssignee>,
      val onDuty: Int,
      val attendanceUnknown: List<AttendanceMission> = emptyList(),
  ) : TeamNowState

  /**
   * People are expected: on the [handovers], or on the [next] missions when nothing is under way.
   *
   * @property handovers the missions under way for at most their
   *   [grace period][TeamNowRules.gracePeriod] with assignees who have not checked in, the handover
   *   of back-to-back missions; empty when nothing is under way.
   * @property next the missions that start within [TeamNowRules.UPCOMING_WINDOW]; never empty when
   *   [handovers] is empty. A mission that has not opened its check-in is NOT_OPEN and has nobody
   *   present: read its [status] before writing how many arrived, and say how many are expected
   *   rather than "0 arrived".
   * @property attendanceUnknown the missions under way whose check-ins have not arrived or could
   *   not be observed, which are neither handovers nor settled.
   */
  data class Arriving(
      val handovers: List<AttendanceMission>,
      val next: List<AttendanceMission>,
      val attendanceUnknown: List<AttendanceMission> = emptyList(),
  ) : TeamNowState

  /**
   * The team is at work, nobody is reported missing nor expected.
   *
   * @property ongoing the missions under way, [attendanceUnknown] included.
   * @property onDuty how many distinct volunteers are confirmed present, counted on the missions
   *   whose check-ins are followed; a mission in [attendanceUnknown] adds nobody. A count of 0
   *   means either that nobody has checked in or that the attendance is not known: read
   *   [attendanceUnknown] to tell the two apart.
   * @property next the missions that start within [TeamNowRules.UPCOMING_WINDOW], if any. As in
   *   [Arriving], one that has not opened its check-in has nobody present yet.
   * @property attendanceUnknown the missions under way whose check-ins have not arrived or could
   *   not be observed. Every mission is in it on the first load, until its check-ins arrive.
   */
  data class Running(
      val ongoing: List<AttendanceMission>,
      val onDuty: Int,
      val next: List<AttendanceMission>,
      val attendanceUnknown: List<AttendanceMission> = emptyList(),
  ) : TeamNowState

  /**
   * Nothing is under way or imminent: the [next] missions are those starting at the earliest start
   * to come, after the window.
   *
   * How far ahead that start may be is the caller's choice, not this state's: pass the missions of
   * the span the card speaks for, today's as the volunteer's own now state does, or it will
   * announce tomorrow's mission as if it were today's.
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
 * read the same classification: a completed mission is neither under way nor upcoming, and a
 * mission whose attendance is not known yet, or could not be observed, reports nobody missing,
 * since not being seen is not being absent. Those missions are carried apart, in
 * `attendanceUnknown`, rather than counted as nobody present.
 *
 * A mission is under way from its start included, in its grace period up to the end of the period
 * included, and upcoming until the end of the window included. Missions starting at the same time
 * keep their order in [missions], which is the order the caller lists them in.
 */
fun teamNowState(missions: List<AttendanceMission>, now: Instant): TeamNowState {
  val active =
      missions.filter { it.status(now) != AttendanceStatus.COMPLETED }.sortedBy { it.startAt }
  val (ongoing, upcoming) = active.partition { it.startAt <= now }
  val next = upcoming.takeWhile {
    Duration.between(now, it.startAt) <= TeamNowRules.UPCOMING_WINDOW
  }
  val unknown = ongoing.filterNot { it.attendanceIsKnown(now) }

  val late = ongoing.filter { it.isPastGrace(now) && it.hasAbsentees(now) }
  if (late.isNotEmpty()) {
    val missing = late.flatMap { mission ->
      mission.missingAssignees.map { MissingAssignee(mission, it) }
    }
    val absent = missing.mapTo(mutableSetOf()) { it.volunteer.userId }
    return TeamNowState.Missing(missing, onDuty(ongoing, now, except = absent), unknown)
  }
  val handovers = ongoing.filter { !it.isPastGrace(now) && it.hasAbsentees(now) }
  if (handovers.isNotEmpty()) return TeamNowState.Arriving(handovers, next, unknown)
  if (ongoing.isNotEmpty())
      return TeamNowState.Running(ongoing, onDuty(ongoing, now), next, unknown)
  if (next.isNotEmpty()) return TeamNowState.Arriving(emptyList(), next)
  val soonest = upcoming.firstOrNull() ?: return TeamNowState.Done
  return TeamNowState.Quiet(upcoming.takeWhile { it.startAt == soonest.startAt })
}

/**
 * The missions of [this] the now card speaks for at [now]: those of the event day it falls on,
 * which runs from 04:00 to 04:00 ([eventDay]) as the Planning groups them. A mission running across
 * 04:00 belongs to both days, so one still under way at the turn of the day stays on the card.
 *
 * [TeamNowState.Quiet] leaves this span to its caller, so this is what a screen passes to
 * [teamNowState]: without it the card would announce tomorrow's mission as if it were today's.
 */
internal fun List<AttendanceMission>.onEventDayOf(
    now: Instant,
    zone: ZoneId,
): List<AttendanceMission> {
  val day = now.eventDay(zone)
  return filter { runsOnEventDay(day, it.startAt, it.endAt, zone) }
}

/** Whether this mission started more than its [grace period][TeamNowRules.gracePeriod] ago. */
private fun AttendanceMission.isPastGrace(now: Instant): Boolean =
    Duration.between(startAt, now) > TeamNowRules.gracePeriod(Duration.between(startAt, endAt))

/**
 * Whether the check-ins of this mission are followed at [now], by the shared [status]: only then is
 * an assignee who has not checked in absent, and only then is one who has present.
 */
private fun AttendanceMission.attendanceIsKnown(now: Instant): Boolean =
    when (status(now)) {
      AttendanceStatus.UNDERSTAFFED,
      AttendanceStatus.STAFFED -> true
      AttendanceStatus.COMPLETED,
      AttendanceStatus.NOT_OPEN,
      AttendanceStatus.FAILED,
      AttendanceStatus.WAITING,
      AttendanceStatus.EARLY -> false
    }

/**
 * Whether assignees of this mission are known not to be there: not being seen is not being absent.
 */
private fun AttendanceMission.hasAbsentees(now: Instant): Boolean =
    attendanceIsKnown(now) && missingAssignees.isNotEmpty()

/**
 * How many distinct volunteers are present on the [missions] whose attendance is known, leaving out
 * those in [except]: one person on two missions counts once, and one listed as missing counts none.
 */
private fun onDuty(
    missions: List<AttendanceMission>,
    now: Instant,
    except: Set<String> = emptySet(),
): Int =
    missions
        .filter { it.attendanceIsKnown(now) }
        .flatMap { it.presentAssignees }
        .mapTo(mutableSetOf()) { it.userId }
        .count { it !in except }
