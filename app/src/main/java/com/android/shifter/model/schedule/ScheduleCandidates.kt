// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.schedule

import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.team.Team

/**
 * The volunteers of an event that [proposeSchedule] can propose: one per accepted request among
 * [requests], with the [teams] they are a member of and the slots of their request.
 *
 * Their teams are those whose [Team.memberIds] hold them, not the team their request names: a
 * volunteer can belong to several teams, and the manager of a team is never one of its members, so
 * they are never proposed for the missions of the team they manage.
 *
 * Their slots are the request's [MembershipRequest.availability], which holds the slots of the
 * shifts they chose, or the whole event when it has none: a shift is never edited, and its slots
 * can no longer change once the request is accepted. A slot that does not end after it starts is
 * left out, as nothing checks it when it is stored, and it covers no mission anyway.
 */
fun scheduleCandidates(
    requests: List<MembershipRequest>,
    teams: List<Team>,
): List<ScheduleCandidate> =
    requests
        .filter { it.status == MembershipRequestStatus.ACCEPTED }
        // One request per volunteer, but a second would make proposeSchedule fail every load.
        .distinctBy { it.userId }
        .map { request ->
          ScheduleCandidate(
              userId = request.userId,
              teamIds = teams.filter { request.userId in it.memberIds }.map { it.id }.toSet(),
              slots =
                  request.availability
                      .filter { it.startAt < it.endAt }
                      .map { TimeSlot(it.startAt, it.endAt) },
          )
        }
