// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.schedule

import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.team.Team
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleCandidatesTest {

  private val start = Instant.parse("2027-06-20T08:00:00Z")
  private val morning = AvailabilitySlot(start, start.plus(Duration.ofHours(2)))
  private val noon =
      AvailabilitySlot(start.plus(Duration.ofHours(4)), start.plus(Duration.ofHours(6)))

  @Test
  fun scheduleCandidates_acceptedMember_takesTeamsAndSlots() {
    val teams =
        listOf(
            team("bar", memberIds = listOf("ana")),
            team("gate", memberIds = listOf("ana", "ben")),
        )

    val candidates = scheduleCandidates(listOf(request("ana", listOf(morning, noon))), teams)

    assertEquals(
        listOf(
            ScheduleCandidate(
                userId = "ana",
                teamIds = setOf("bar", "gate"),
                slots =
                    listOf(
                        TimeSlot(morning.startAt, morning.endAt),
                        TimeSlot(noon.startAt, noon.endAt),
                    ),
            )
        ),
        candidates,
    )
  }

  @Test
  fun scheduleCandidates_pendingOrRejected_leavesThemOut() {
    val teams = listOf(team("bar", memberIds = listOf("ana", "ben", "eva")))
    val requests =
        listOf(
            request("ana"),
            request("ben", status = MembershipRequestStatus.PENDING),
            request("eva", status = MembershipRequestStatus.REJECTED),
        )

    assertEquals(listOf("ana"), scheduleCandidates(requests, teams).map { it.userId })
  }

  @Test
  fun scheduleCandidates_manager_hasNoTeamTheyManage() {
    // A manager is never among the members of the team they manage, whatever their request names.
    val teams = listOf(team("bar", managerId = "max", memberIds = listOf("ana")))

    val candidates = scheduleCandidates(listOf(request("max")), teams)

    assertEquals(listOf(emptySet<String>()), candidates.map { it.teamIds })
  }

  @Test
  fun scheduleCandidates_emptySlot_isDropped() {
    // Nothing checks a slot when it is stored: one that does not end after it starts must not make
    // every proposal fail.
    val empty = AvailabilitySlot(start, start)
    val reversed = AvailabilitySlot(morning.endAt, morning.startAt)

    val candidates =
        scheduleCandidates(listOf(request("ana", listOf(empty, reversed, noon))), emptyList())

    assertEquals(listOf(listOf(TimeSlot(noon.startAt, noon.endAt))), candidates.map { it.slots })
  }

  private fun request(
      userId: String,
      availability: List<AvailabilitySlot> = listOf(morning),
      status: MembershipRequestStatus = MembershipRequestStatus.ACCEPTED,
  ) =
      MembershipRequest(
          id = userId,
          userId = userId,
          teamId = "bar",
          availability = availability,
          status = status,
          createdAt = start,
      )

  private fun team(id: String, managerId: String? = null, memberIds: List<String>) =
      Team(
          id = id,
          eventId = "event-1",
          name = id,
          description = "",
          icon = "truck",
          managerId = managerId,
          memberIds = memberIds,
          createdAt = start,
      )
}
