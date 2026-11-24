// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.schedule

import com.swent.shifter.model.mission.Mission
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers each rule of [proposeSchedule]: the team, the slots that must cover the mission, the
 * missions that overlap, the fewest hours first, the missions completed, in progress or with too
 * many people, the order the missions are gone through, and that a second run proposes nothing.
 */
class ProposeScheduleTest {

  private val now = at("12:00")

  /** [time] on the day of these tests, such as "14:00". */
  private fun at(time: String): Instant = Instant.parse("2026-11-21T$time:00Z")

  /** The slot written "14:00-16:00". */
  private fun slot(range: String): TimeSlot =
      range.split("-").let { (start, end) -> TimeSlot(at(start), at(end)) }

  private fun mission(
      id: String,
      range: String,
      team: String? = "team",
      needed: Int = 1,
      assignees: List<String> = emptyList(),
      completed: Boolean = false,
  ): Mission {
    val (start, end) = slot(range)
    return Mission(
        id = id,
        eventId = "event",
        title = "Mission $id",
        description = "",
        teamId = team,
        volunteersNeeded = needed,
        startAt = start,
        endAt = end,
        assigneeIds = assignees,
        createdAt = at("00:00"),
        completed = completed,
    )
  }

  /** A volunteer of [teams], free all day unless [slots] says otherwise. */
  private fun candidate(
      userId: String,
      vararg slots: String = arrayOf("06:00-22:00"),
      teams: Set<String> = setOf("team"),
  ) = ScheduleCandidate(userId, teams, slots.map(::slot))

  private fun propose(missions: List<Mission>, vararg candidates: ScheduleCandidate) =
      proposeSchedule(missions, candidates.toList(), now)

  /** The [missions] once [proposal] is accepted: its volunteers added to their missions. */
  private fun accept(missions: List<Mission>, proposal: ScheduleProposal) = missions.map {
    it.copy(assigneeIds = it.assigneeIds + proposal.proposed[it.id].orEmpty())
  }

  @Test
  fun proposeSchedule_withNoMission_proposesNothing() {
    assertEquals(ScheduleProposal(), propose(emptyList(), candidate("alice")))
  }

  @Test(expected = IllegalArgumentException::class)
  fun proposeSchedule_withTwiceTheSameUserId_throws() {
    propose(listOf(mission("m", "14:00-16:00")), candidate("alice"), candidate("alice"))
  }

  @Test
  fun proposeSchedule_otherTeam_proposesOnlyTheMember() {
    val proposal =
        propose(
            listOf(mission("m", "14:00-16:00")),
            candidate("aaron", teams = setOf("other")),
            candidate("bob"),
        )

    assertEquals(ScheduleProposal(proposed = mapOf("m" to listOf("bob"))), proposal)
  }

  @Test
  fun proposeSchedule_generalMission_goesToUnfilled() {
    val proposal =
        propose(listOf(mission("m", "14:00-16:00", team = null, needed = 2)), candidate("alice"))

    assertEquals(ScheduleProposal(unfilled = mapOf("m" to 2)), proposal)
  }

  @Test
  fun proposeSchedule_slotEndingEarly_leavesItUnfilled() {
    val proposal = propose(listOf(mission("m", "14:00-16:00")), candidate("alice", "14:00-15:30"))

    assertEquals(ScheduleProposal(unfilled = mapOf("m" to 1)), proposal)
  }

  @Test
  fun proposeSchedule_backToBackSlots_coverTheMission() {
    val proposal =
        propose(
            listOf(mission("m", "14:00-16:00")),
            candidate("alice", "13:00-15:00", "15:00-17:00"),
        )

    assertEquals(ScheduleProposal(proposed = mapOf("m" to listOf("alice"))), proposal)
  }

  @Test
  fun proposeSchedule_nestedUnorderedSlots_coverTheMission() {
    // Merged in order of start, 08:00-15:00 keeps its end over the nested 09:00-10:00, then
    // touches 15:00-17:00.
    val alice = candidate("alice", "15:00-17:00", "09:00-10:00", "08:00-15:00")

    val proposal = propose(listOf(mission("m", "14:00-16:00")), alice)

    assertEquals(ScheduleProposal(proposed = mapOf("m" to listOf("alice"))), proposal)
  }

  @Test
  fun proposeSchedule_assignedGeneralOverlap_proposesSomeoneElse() {
    // Alice has fewer hours than Bob, so only her General mission keeps her out.
    val missions =
        listOf(
            mission("general", "13:00-15:00", team = null, assignees = listOf("alice")),
            mission("past", "08:00-11:00", assignees = listOf("bob")),
            mission("m", "14:00-16:00"),
        )

    val proposal = propose(missions, candidate("alice"), candidate("bob"))

    assertEquals(ScheduleProposal(proposed = mapOf("m" to listOf("bob"))), proposal)
  }

  @Test
  fun proposeSchedule_closedAssignedOverlap_leavesItUnfilled() {
    val missions =
        listOf(
            mission("closed", "13:00-15:00", assignees = listOf("alice"), completed = true),
            mission("m", "14:00-16:00"),
        )

    assertEquals(
        ScheduleProposal(unfilled = mapOf("m" to 1)),
        propose(missions, candidate("alice")),
    )
  }

  @Test
  fun proposeSchedule_proposedOverlap_proposesOnce() {
    val missions = listOf(mission("m1", "14:00-16:00"), mission("m2", "15:00-17:00"))

    assertEquals(
        ScheduleProposal(proposed = mapOf("m1" to listOf("alice")), unfilled = mapOf("m2" to 1)),
        propose(missions, candidate("alice")),
    )
  }

  @Test
  fun proposeSchedule_touchingMissions_proposesBoth() {
    val missions = listOf(mission("m1", "14:00-16:00"), mission("m2", "16:00-18:00"))

    assertEquals(
        ScheduleProposal(proposed = mapOf("m1" to listOf("alice"), "m2" to listOf("alice"))),
        propose(missions, candidate("alice")),
    )
  }

  @Test
  fun proposeSchedule_alreadyAssigned_keepsThemAndFillsTheRest() {
    val missions = listOf(mission("m", "14:00-16:00", needed = 2, assignees = listOf("alice")))

    assertEquals(
        ScheduleProposal(proposed = mapOf("m" to listOf("bob"))),
        propose(missions, candidate("alice"), candidate("bob")),
    )
  }

  @Test
  fun proposeSchedule_fewestHours_goFirst() {
    // Past missions: 2 h for Alice, 3 h for Carol, none for Bob.
    val missions =
        listOf(
            mission("p1", "08:00-10:00", assignees = listOf("alice")),
            mission("p2", "08:00-11:00", assignees = listOf("carol")),
            mission("m", "14:00-16:00", needed = 2),
        )

    val proposal = propose(missions, candidate("alice"), candidate("bob"), candidate("carol"))

    assertEquals(ScheduleProposal(proposed = mapOf("m" to listOf("bob", "alice"))), proposal)
  }

  @Test
  fun proposeSchedule_equalHours_splitByUserId() {
    // A tie gives the first mission to Alice, whose 2 h then give the second one to Bob.
    val missions = listOf(mission("m1", "14:00-16:00"), mission("m2", "17:00-19:00"))

    assertEquals(
        ScheduleProposal(proposed = mapOf("m1" to listOf("alice"), "m2" to listOf("bob"))),
        propose(missions, candidate("bob"), candidate("alice")),
    )
  }

  @Test
  fun proposeSchedule_missingPeople_countsUnfilled() {
    val missions = listOf(mission("m", "14:00-16:00", needed = 3))

    assertEquals(
        ScheduleProposal(proposed = mapOf("m" to listOf("alice")), unfilled = mapOf("m" to 2)),
        propose(missions, candidate("alice")),
    )
  }

  @Test
  fun proposeSchedule_overAssigned_isLeftOut() {
    val missions =
        listOf(
            mission("full", "14:00-16:00", assignees = listOf("alice", "bob")),
            mission("general", "14:00-16:00", team = null),
        )

    assertEquals(
        ScheduleProposal(unfilled = mapOf("general" to 1)),
        propose(missions, candidate("carol")),
    )
  }

  @Test
  fun proposeSchedule_completedMission_skipsIt() {
    val missions =
        listOf(mission("closed", "14:00-16:00", completed = true), mission("m", "17:00-19:00"))

    assertEquals(
        ScheduleProposal(proposed = mapOf("m" to listOf("alice"))),
        propose(missions, candidate("alice")),
    )
  }

  @Test
  fun proposeSchedule_endedAtNow_skipsIt() {
    val missions = listOf(mission("ended", "10:00-12:00"), mission("running", "10:00-12:01"))

    assertEquals(
        ScheduleProposal(unfilled = mapOf("running" to 1)),
        propose(missions, candidate("alice")),
    )
  }

  @Test
  fun proposeSchedule_inProgress_leavesItUnfilled() {
    val proposal = propose(listOf(mission("m", "11:00-13:00")), candidate("alice"))

    assertEquals(ScheduleProposal(unfilled = mapOf("m" to 1)), proposal)
  }

  @Test
  fun proposeSchedule_startingAtNow_leavesItUnfilled() {
    val proposal = propose(listOf(mission("m", "12:00-14:00")), candidate("alice"))

    assertEquals(ScheduleProposal(unfilled = mapOf("m" to 1)), proposal)
  }

  @Test
  fun proposeSchedule_missionsInAnyOrder_goEarliestFirst() {
    val missions = listOf(mission("later", "15:00-17:00"), mission("earlier", "14:00-16:00"))

    assertEquals(
        ScheduleProposal(
            proposed = mapOf("earlier" to listOf("alice")),
            unfilled = mapOf("later" to 1),
        ),
        propose(missions, candidate("alice")),
    )
  }

  @Test
  fun proposeSchedule_sameStart_goesToSmallerId() {
    val missions = listOf(mission("b", "14:00-16:00"), mission("a", "14:00-16:00"))

    assertEquals(
        ScheduleProposal(proposed = mapOf("a" to listOf("alice")), unfilled = mapOf("b" to 1)),
        propose(missions, candidate("alice")),
    )
  }

  @Test
  fun proposeSchedule_rerunOnAccepted_proposesNothing() {
    // m2 overlaps m1, which takes both volunteers, and the General mission is never filled.
    val missions =
        listOf(
            mission("m1", "14:00-16:00", needed = 2),
            mission("m2", "15:00-17:00"),
            mission("general", "16:00-18:00", team = null),
        )
    val volunteers = arrayOf(candidate("alice"), candidate("bob"))
    val first = propose(missions, *volunteers)
    assertTrue(first.proposed.isNotEmpty())

    val second = propose(accept(missions, first), *volunteers)

    assertEquals(ScheduleProposal(unfilled = first.unfilled), second)
    assertEquals(mapOf("m2" to 1, "general" to 1), second.unfilled)
  }
}
