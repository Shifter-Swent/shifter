// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.schedule

import com.swent.shifter.model.mission.Mission
import java.time.Instant

/**
 * A stretch of time a volunteer can work, such as one of the shifts they chose.
 *
 * @property start when the slot starts.
 * @property end when the slot ends, strictly after [start].
 */
data class TimeSlot(val start: Instant, val end: Instant) {
  init {
    require(start < end) { "A time slot must end after it starts" }
  }
}

/**
 * A volunteer the schedule can propose for missions.
 *
 * @property userId id of the volunteer's `/users` document, unique among the candidates; it also
 *   breaks the ties between volunteers with as many hours.
 * @property teamIds ids of the teams of the event the volunteer belongs to.
 * @property slots when the volunteer can work, taken from the shifts they chose, in any order.
 *   Slots that touch or overlap may be listed separately.
 */
data class ScheduleCandidate(
    val userId: String,
    val teamIds: Set<String>,
    val slots: List<TimeSlot>,
)

/**
 * What [proposeSchedule] suggests on top of the current assignments; accepting it only adds people,
 * it never replaces anyone.
 *
 * @property proposed for each mission id, the ids of the volunteers proposed in addition to its
 *   assignees, in the order they were chosen. A mission with nobody proposed is left out.
 * @property unfilled for each mission id, how many spots are still empty once the proposed
 *   volunteers are added. A mission with no empty spot is left out.
 */
data class ScheduleProposal(
    val proposed: Map<String, List<String>> = emptyMap(),
    val unfilled: Map<String, Int> = emptyMap(),
)

/**
 * Proposes volunteers among the [candidates] for the empty spots of the [missions] of an event.
 *
 * The missions are gone through by [Mission.startAt], then by [Mission.id] for those that start at
 * the same time, so the result does not depend on the order of [missions]. A mission completed at
 * [now] (see [com.swent.shifter.model.mission.isCompleted]) is skipped. A mission already started
 * gets nobody, but its empty spots still go to [ScheduleProposal.unfilled], so the organizer can
 * finish it by hand.
 *
 * A candidate is proposed for an upcoming mission only if:
 * - they belong to the mission's team: General missions (no team) are never filled, so their empty
 *   spots always go to [ScheduleProposal.unfilled];
 * - the union of their slots covers the whole mission, slots that touch or overlap being merged;
 * - no mission they are assigned to, General ones included, or proposed for in this run, overlaps
 *   it. Two missions that only touch, one ending when the other starts, do not overlap. Every
 *   mission counts, with or without a team or a place, unlike [Mission.conflictsWith];
 * - they are not already assigned to it.
 *
 * Among the candidates of a mission, the one with the fewest hours, assigned and proposed across
 * the [missions], goes first, and a tie goes to the smallest [ScheduleCandidate.userId]. Nobody is
 * ever removed from a mission: the spots still empty go to [ScheduleProposal.unfilled]. Running it
 * again once the proposal is accepted proposes nobody new.
 *
 * Its body comes with #605; until then it proposes nothing.
 *
 * @param now the current time, passed in so the tests can fix it.
 * @throws IllegalArgumentException when two [candidates] have the same [ScheduleCandidate.userId],
 *   since one volunteer would then be proposed twice.
 */
@Suppress("UNUSED_PARAMETER")
fun proposeSchedule(
    missions: List<Mission>,
    candidates: List<ScheduleCandidate>,
    now: Instant,
): ScheduleProposal {
  require(candidates.map { it.userId }.toSet().size == candidates.size) {
    "Each candidate must have a distinct userId"
  }
  return ScheduleProposal()
}
