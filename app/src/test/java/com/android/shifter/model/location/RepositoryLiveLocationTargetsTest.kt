// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.event.EventStatus
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FakeEventRepository
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.FakeMembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestRepositoryException
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.model.team.TeamRepositoryException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [RepositoryLiveLocationTargets] against the in-memory repositories, with the clock given by the
 * test so which events are running never depends on when the suite runs.
 *
 * Each test seeds the repositories with [event], [team] and [accepted], then asserts on the event
 * and team ids returned, which is what a publishing session is addressed with.
 */
class RepositoryLiveLocationTargetsTest {

  private val events = FakeEventRepository()
  private val teams = FakeTeamRepository()
  private val requests = FakeMembershipRequestRepository()
  private val clock = NOW

  private val targets =
      RepositoryLiveLocationTargets(
          eventRepository = events,
          membershipRequestRepository = requests,
          teamRepository = teams,
          currentUserId = { VOLUNTEER },
          now = { clock },
      )

  @Test
  fun eligible_withAnAcceptedVolunteerOfARunningEvent_isTheirTeam() = runTest {
    event(EVENT)
    team(EVENT, TEAM, members = listOf(VOLUNTEER))
    accepted(EVENT, TEAM)

    assertEquals(listOf(LiveLocationTarget(EVENT, TEAM)), targets.eligible())
  }

  @Test
  fun eligible_exactlyWhenTheEventStarts_isTheirTeam() = runTest {
    event(EVENT, startAt = NOW, endAt = NOW + Duration.ofHours(4))
    team(EVENT, TEAM, members = listOf(VOLUNTEER))
    accepted(EVENT, TEAM)

    assertEquals(listOf(LiveLocationTarget(EVENT, TEAM)), targets.eligible())
  }

  @Test
  fun eligible_exactlyWhenTheEventEnds_isTheirTeam() = runTest {
    event(EVENT, startAt = NOW - Duration.ofHours(4), endAt = NOW)
    team(EVENT, TEAM, members = listOf(VOLUNTEER))
    accepted(EVENT, TEAM)

    assertEquals(listOf(LiveLocationTarget(EVENT, TEAM)), targets.eligible())
  }

  @Test
  fun eligible_withAPendingRequest_isEmpty() = runTest {
    event(EVENT)
    team(EVENT, TEAM, members = listOf(VOLUNTEER))
    request(EVENT, TEAM, MembershipRequestStatus.PENDING)

    assertEquals(emptyList<LiveLocationTarget>(), targets.eligible())
  }

  @Test
  fun eligible_withARejectedRequest_isEmpty() = runTest {
    event(EVENT)
    // Still listed in the team, which must not make the event eligible on its own.
    team(EVENT, TEAM, members = listOf(VOLUNTEER))
    request(EVENT, TEAM, MembershipRequestStatus.REJECTED)

    assertEquals(emptyList<LiveLocationTarget>(), targets.eligible())
  }

  @Test
  fun eligible_whenTheVolunteerLeftTheTeam_isEmpty() = runTest {
    event(EVENT)
    // Accepted, then taken out of the team by the organizer: the request still names it.
    team(EVENT, TEAM, members = emptyList())
    accepted(EVENT, TEAM)

    assertEquals(emptyList<LiveLocationTarget>(), targets.eligible())
  }

  @Test
  fun eligible_whenTheVolunteerManagesTheTeam_isEmpty() = runTest {
    event(EVENT)
    team(EVENT, TEAM, managerId = VOLUNTEER)
    accepted(EVENT, TEAM)

    assertEquals(emptyList<LiveLocationTarget>(), targets.eligible())
  }

  @Test
  fun eligible_whenTheyAreBothManagerAndMemberOfTheTeam_isEmpty() = runTest {
    event(EVENT)
    // The two are disjoint on the backend; a document where they are not must still offer nothing.
    team(EVENT, TEAM, managerId = VOLUNTEER, members = listOf(VOLUNTEER))
    accepted(EVENT, TEAM)

    assertEquals(emptyList<LiveLocationTarget>(), targets.eligible())
  }

  @Test
  fun eligible_whenTheyManageOneTeamAndStaffAnother_isOnlyTheOneTheyStaff() = runTest {
    event(EVENT)
    team(EVENT, TEAM, managerId = VOLUNTEER)
    team(EVENT, OTHER_TEAM, members = listOf(VOLUNTEER))
    accepted(EVENT, OTHER_TEAM)

    assertEquals(listOf(LiveLocationTarget(EVENT, OTHER_TEAM)), targets.eligible())
  }

  @Test
  fun eligible_beforeTheEventStarts_isEmpty() = runTest {
    event(EVENT, startAt = NOW + Duration.ofSeconds(1), endAt = NOW + Duration.ofHours(4))
    team(EVENT, TEAM, members = listOf(VOLUNTEER))
    accepted(EVENT, TEAM)

    assertEquals(emptyList<LiveLocationTarget>(), targets.eligible())
  }

  @Test
  fun eligible_afterTheEventEnded_isEmpty() = runTest {
    event(EVENT, startAt = NOW - Duration.ofHours(4), endAt = NOW - Duration.ofSeconds(1))
    team(EVENT, TEAM, members = listOf(VOLUNTEER))
    accepted(EVENT, TEAM)

    assertEquals(emptyList<LiveLocationTarget>(), targets.eligible())
  }

  @Test
  fun eligible_whenTheEventIsRunningButNotMarkedOngoing_isTheirTeam() = runTest {
    // The status is the organizer's label; the rules only read the period, and so does this.
    event(EVENT, status = EventStatus.PREPARATION)
    team(EVENT, TEAM, members = listOf(VOLUNTEER))
    accepted(EVENT, TEAM)

    assertEquals(listOf(LiveLocationTarget(EVENT, TEAM)), targets.eligible())
  }

  @Test
  fun eligible_withTheEventDeleted_isEmpty() = runTest {
    team(EVENT, TEAM, members = listOf(VOLUNTEER))
    accepted(EVENT, TEAM)

    assertEquals(emptyList<LiveLocationTarget>(), targets.eligible())
  }

  @Test
  fun eligible_withTheTeamDeleted_isEmpty() = runTest {
    event(EVENT)
    accepted(EVENT, TEAM)

    assertEquals(emptyList<LiveLocationTarget>(), targets.eligible())
  }

  @Test
  fun eligible_withSeveralRunningEventsAndTeams_isEveryPairOnce() = runTest {
    event(EVENT)
    event(OTHER_EVENT)
    team(EVENT, TEAM, members = listOf(VOLUNTEER))
    team(EVENT, OTHER_TEAM, members = listOf(VOLUNTEER))
    team(OTHER_EVENT, TEAM, members = listOf(VOLUNTEER))
    accepted(EVENT, TEAM)
    accepted(OTHER_EVENT, TEAM)

    val eligible = targets.eligible()

    assertEquals(
        setOf(
            LiveLocationTarget(EVENT, TEAM),
            LiveLocationTarget(EVENT, OTHER_TEAM),
            LiveLocationTarget(OTHER_EVENT, TEAM),
        ),
        eligible.toSet(),
    )
    assertEquals(3, eligible.size)
  }

  @Test
  fun eligible_whenATeamIsAnsweredTwice_isOneTarget() = runTest {
    event(EVENT)
    team(EVENT, TEAM, members = listOf(VOLUNTEER))
    accepted(EVENT, TEAM)
    // A backend answering the same team twice must not open two sessions for it.
    val repeatingTeams =
        object : TeamRepository by teams {
          override suspend fun getTeamsOfMember(eventId: String, userId: String): List<Team> =
              teams.getTeamsOfMember(eventId, userId).flatMap { listOf(it, it) }
        }
    val resolver =
        RepositoryLiveLocationTargets(events, requests, repeatingTeams, { VOLUNTEER }, { clock })

    assertEquals(listOf(LiveLocationTarget(EVENT, TEAM)), resolver.eligible())
  }

  @Test
  fun eligible_withOnlyOneOfTwoEventsRunning_isTheRunningOne() = runTest {
    event(EVENT)
    event(OTHER_EVENT, startAt = NOW + Duration.ofDays(7), endAt = NOW + Duration.ofDays(8))
    team(EVENT, TEAM, members = listOf(VOLUNTEER))
    team(OTHER_EVENT, OTHER_TEAM, members = listOf(VOLUNTEER))
    accepted(EVENT, TEAM)
    accepted(OTHER_EVENT, OTHER_TEAM)

    assertEquals(listOf(LiveLocationTarget(EVENT, TEAM)), targets.eligible())
  }

  @Test
  fun eligible_withNobodySignedIn_isEmpty() = runTest {
    event(EVENT)
    team(EVENT, TEAM, members = listOf(VOLUNTEER))
    accepted(EVENT, TEAM)

    val signedOut =
        RepositoryLiveLocationTargets(events, requests, teams, currentUserId = { null }, { clock })

    assertEquals(emptyList<LiveLocationTarget>(), signedOut.eligible())
  }

  @Test
  fun eligible_withAnotherVolunteersTeam_leavesItOut() = runTest {
    event(EVENT)
    team(EVENT, TEAM, members = listOf(SOMEONE_ELSE))
    team(EVENT, OTHER_TEAM, members = listOf(VOLUNTEER))
    accepted(EVENT, TEAM)
    requests.add(
        EVENT,
        MembershipRequest(
            userId = SOMEONE_ELSE,
            teamId = TEAM,
            availability = listOf(AVAILABILITY),
            status = MembershipRequestStatus.ACCEPTED,
            createdAt = CREATED_AT,
        ),
    )

    assertEquals(listOf(LiveLocationTarget(EVENT, OTHER_TEAM)), targets.eligible())
  }

  @Test
  fun eligible_whenOneEventCannotBeRead_keepsTheOthers() = runTest {
    // Only the teams are seeded: the event repository below answers instead of the fake.
    team(EVENT, TEAM, members = listOf(VOLUNTEER))
    team(OTHER_EVENT, OTHER_TEAM, members = listOf(VOLUNTEER))
    accepted(EVENT, TEAM)
    accepted(OTHER_EVENT, OTHER_TEAM)
    val unreadableEvent =
        object : EventRepository by events {
          override suspend fun getEvent(eventId: String): Event? =
              if (eventId == EVENT) throw EventRepositoryException.Unavailable()
              else runningEvent(eventId)
        }
    val failing =
        RepositoryLiveLocationTargets(unreadableEvent, requests, teams, { VOLUNTEER }, { clock })

    assertEquals(listOf(LiveLocationTarget(OTHER_EVENT, OTHER_TEAM)), failing.eligible())
  }

  @Test
  fun eligible_whenTheTeamsOfOneEventCannotBeRead_keepsTheOthers() = runTest {
    event(EVENT)
    event(OTHER_EVENT)
    team(EVENT, TEAM, members = listOf(VOLUNTEER))
    team(OTHER_EVENT, OTHER_TEAM, members = listOf(VOLUNTEER))
    accepted(EVENT, TEAM)
    accepted(OTHER_EVENT, OTHER_TEAM)
    val unreadableTeams =
        object : TeamRepository by teams {
          override suspend fun getTeamsOfMember(eventId: String, userId: String): List<Team> =
              if (eventId == EVENT) throw TeamRepositoryException.Unavailable()
              else teams.getTeamsOfMember(eventId, userId)
        }
    val failing =
        RepositoryLiveLocationTargets(events, requests, unreadableTeams, { VOLUNTEER }, { clock })

    assertEquals(listOf(LiveLocationTarget(OTHER_EVENT, OTHER_TEAM)), failing.eligible())
  }

  @Test
  fun eligible_whenAnEventReadFailsUnexpectedly_fails() {
    // A bug, not a repository failure: it must not pass for a volunteer with nothing to share.
    team(EVENT, TEAM, members = listOf(VOLUNTEER))
    accepted(EVENT, TEAM)
    val broken =
        object : EventRepository by events {
          override suspend fun getEvent(eventId: String): Event? =
              throw IllegalStateException("A bug, not an ineligible volunteer")
        }
    val resolver = RepositoryLiveLocationTargets(broken, requests, teams, { VOLUNTEER }, { clock })

    assertThrows(IllegalStateException::class.java) { runBlocking { resolver.eligible() } }
  }

  @Test
  fun eligible_whenTheRequestsCannotBeRead_fails() {
    requests.failure = MembershipRequestRepositoryException.Unavailable()

    assertThrows(MembershipRequestRepositoryException.Unavailable::class.java) {
      runBlocking { targets.eligible() }
    }
  }

  /** Stores an event running at [NOW] unless another period is given. */
  private suspend fun event(
      eventId: String,
      startAt: Instant = NOW - Duration.ofHours(1),
      endAt: Instant = NOW + Duration.ofHours(3),
      status: EventStatus = EventStatus.ONGOING,
  ) {
    events.createEvent(runningEvent(eventId, startAt, endAt, status))
  }

  /** Stores a team of [eventId] with the given manager and members. */
  private fun team(
      eventId: String,
      teamId: String,
      managerId: String? = null,
      members: List<String> = emptyList(),
  ) {
    teams.teams +=
        Team(
            id = teamId,
            eventId = eventId,
            name = teamId,
            description = "",
            icon = "star",
            managerId = managerId,
            memberIds = members,
            createdAt = CREATED_AT,
        )
  }

  /** The volunteer's accepted request for [eventId], applied to the team [teamId]. */
  private fun accepted(eventId: String, teamId: String) =
      request(eventId, teamId, MembershipRequestStatus.ACCEPTED)

  private fun request(eventId: String, teamId: String, status: MembershipRequestStatus) {
    requests.add(
        eventId,
        MembershipRequest(
            userId = VOLUNTEER,
            teamId = teamId,
            availability = listOf(AVAILABILITY),
            status = status,
            createdAt = CREATED_AT,
        ),
    )
  }

  private companion object {
    const val VOLUNTEER = "volunteer"
    const val SOMEONE_ELSE = "someone-else"
    const val EVENT = "event"
    const val OTHER_EVENT = "other-event"
    const val TEAM = "team"
    const val OTHER_TEAM = "other-team"

    val NOW: Instant = Instant.parse("2026-11-07T14:00:00Z")
    val CREATED_AT: Instant = Instant.parse("2026-10-01T00:00:00Z")
    val AVAILABILITY = AvailabilitySlot(NOW, NOW + Duration.ofHours(4))

    fun runningEvent(
        eventId: String,
        startAt: Instant = NOW - Duration.ofHours(1),
        endAt: Instant = NOW + Duration.ofHours(3),
        status: EventStatus = EventStatus.ONGOING,
    ) =
        Event(
            id = eventId,
            organizerId = "organizer",
            title = eventId,
            description = "",
            type = EventType.MUSIC,
            startAt = startAt,
            endAt = endAt,
            location = EventLocation("EPFL"),
            status = status,
            createdAt = CREATED_AT,
        )
  }
}
