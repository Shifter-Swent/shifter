// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.mission

import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.FakeMembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.User
import com.swent.shifter.model.user.UserRepositoryException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Covers [RepositoryAssigneeCandidates] and [AssigneeSelection.toggle]. */
class AssigneeCandidatesTest {

  private val teams =
      FakeTeamRepository(
          listOf(
              Team(
                  id = "logistics",
                  eventId = "event-1",
                  name = "Logistics",
                  description = "",
                  icon = "",
                  managerId = "manager",
                  memberIds = listOf("zoe", "ana", "ghost"),
                  createdAt = Instant.EPOCH,
              )
          )
      )
  private val requests = FakeMembershipRequestRepository()
  private val users =
      FakeUserRepository(listOf(user("zoe", "Zoé"), user("ana", "ana"), user("ben", "Ben")))
  private val candidates = RepositoryAssigneeCandidates(teams, requests, users)

  @Test
  fun load_forATeam_returnsItsMembersSortedByNameWithTheUnnamedLast() = runTest {
    assertEquals(
        listOf(
            AssigneeOption("ana", "ana"),
            AssigneeOption("zoe", "Zoé"),
            AssigneeOption("ghost", null),
        ),
        candidates.load("event-1", "logistics"),
    )
  }

  @Test
  fun load_forAMissingTeam_returnsNobody() = runTest {
    assertEquals(emptyList<AssigneeOption>(), candidates.load("event-1", "bar"))
  }

  @Test
  fun load_forGeneral_returnsOnlyTheAcceptedParticipants() = runTest {
    requests.add("event-1", request("ben", MembershipRequestStatus.ACCEPTED))
    requests.add("event-1", request("ana", MembershipRequestStatus.PENDING))
    requests.add("event-1", request("zoe", MembershipRequestStatus.REJECTED))
    requests.add("event-2", request("zoe", MembershipRequestStatus.ACCEPTED))

    assertEquals(listOf(AssigneeOption("ben", "Ben")), candidates.load("event-1", null))
  }

  @Test
  fun load_whenAProfileCannotBeRead_keepsThePersonWithoutAName() = runTest {
    users.failure = UserRepositoryException.Unavailable()

    assertEquals(
        listOf("zoe", "ana", "ghost"),
        candidates.load("event-1", "logistics").map { it.id },
    )
  }

  @Test
  fun load_whenTheRequestsCannotBeRead_fails() = runTest {
    val failure = IllegalStateException("offline")
    requests.failure = failure

    assertSame(failure, runCatching { candidates.load("event-1", null) }.exceptionOrNull())
  }

  @Test
  fun toggle_picksUpToTheLimitAndUnpicks() {
    val selection =
        AssigneeSelection(candidates = listOf(AssigneeOption("a", "A"), AssigneeOption("b", "B")))

    val one = selection.toggle("a", limit = 1)
    assertEquals(listOf("a"), one.selectedIds)
    assertEquals("the limit is reached", one, one.toggle("b", limit = 1))
    assertEquals(emptyList<String>(), one.toggle("a", limit = 1).selectedIds)
  }

  @Test
  fun toggle_ignoresSomeoneWhoIsNotACandidate() {
    val selection = AssigneeSelection(candidates = listOf(AssigneeOption("a", "A")))

    assertEquals(selection, selection.toggle("stranger", limit = 3))
  }

  private fun user(uid: String, name: String) = User(uid = uid, displayName = name, email = "")

  private fun request(userId: String, status: MembershipRequestStatus) =
      MembershipRequest(
          userId = userId,
          teamId = "logistics",
          availability = listOf(AvailabilitySlot(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600))),
          status = status,
          createdAt = Instant.EPOCH,
      )
}
