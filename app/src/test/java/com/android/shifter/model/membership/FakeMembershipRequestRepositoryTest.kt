// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.membership

import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the contract the fake shares with the backend once it is given teams: a decision or a
 * withdrawal keeps the requested team's members in step with the request.
 */
class FakeMembershipRequestRepositoryTest {

  private val team =
      Team(
          id = "team-1",
          eventId = EVENT_ID,
          name = "Logistics",
          description = "Move the equipment around the site",
          icon = "box",
          createdAt = Instant.EPOCH,
      )
  private val teams = FakeTeamRepository(listOf(team))
  private val repository = FakeMembershipRequestRepository(teams = teams)

  @Test
  fun accept_addsTheVolunteerToTheRequestedTeam() = runTest {
    repository.apply(EVENT_ID, request())

    repository.accept(EVENT_ID, USER_ID)

    assertEquals(listOf(USER_ID), members())
  }

  @Test
  fun reject_afterAccept_removesTheVolunteerFromTheTeam() = runTest {
    repository.apply(EVENT_ID, request())
    repository.accept(EVENT_ID, USER_ID)

    repository.reject(EVENT_ID, USER_ID)

    assertEquals(emptyList<String>(), members())
  }

  @Test
  fun withdraw_ofAnAcceptedRequest_removesTheVolunteerFromTheTeam() = runTest {
    repository.apply(EVENT_ID, request())
    repository.accept(EVENT_ID, USER_ID)

    repository.withdraw(EVENT_ID, USER_ID)

    assertEquals(emptyList<String>(), members())
  }

  @Test
  fun pendingRequests_leaveTheTeamUntouched() = runTest {
    repository.apply(EVENT_ID, request())
    repository.withdraw(EVENT_ID, USER_ID)

    assertEquals(emptyList<String>(), members())
  }

  private suspend fun members(): List<String>? = teams.getTeam(EVENT_ID, team.id)?.memberIds

  private fun request() =
      MembershipRequest(
          userId = USER_ID,
          teamId = team.id,
          availability = listOf(AvailabilitySlot(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600))),
          createdAt = Instant.EPOCH,
      )

  private companion object {
    const val EVENT_ID = "event-1"
    const val USER_ID = "volunteer-1"
  }
}
