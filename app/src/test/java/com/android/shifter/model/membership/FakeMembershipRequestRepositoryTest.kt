// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.membership

import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/**
 * Covers the contract the fake shares with the backend once it is given teams: a decision or a
 * withdrawal keeps the requested team's members, and its manager, in step with the request.
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
  private val other = team.copy(id = "team-2", name = "Bar")
  private val teams = FakeTeamRepository(listOf(team, other))
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
  fun reject_ofTheTeamManager_leavesTheTeamWithoutManager() = runTest {
    repository.apply(EVENT_ID, request())
    repository.accept(EVENT_ID, USER_ID)
    teams.setManager(EVENT_ID, team.id, USER_ID)

    repository.reject(EVENT_ID, USER_ID)

    assertNull(teams.getTeam(EVENT_ID, team.id)?.managerId)
    assertEquals(emptyList<String>(), members())
  }

  @Test
  fun withdraw_ofTheTeamManager_leavesTheTeamWithoutManager() = runTest {
    repository.apply(EVENT_ID, request())
    repository.accept(EVENT_ID, USER_ID)
    teams.setManager(EVENT_ID, team.id, USER_ID)

    repository.withdraw(EVENT_ID, USER_ID)

    assertNull(teams.getTeam(EVENT_ID, team.id)?.managerId)
    assertEquals(emptyList<String>(), members())
  }

  @Test
  fun pendingRequests_leaveTheTeamUntouched() = runTest {
    repository.apply(EVENT_ID, request())
    repository.withdraw(EVENT_ID, USER_ID)

    assertEquals(emptyList<String>(), members())
  }

  @Test
  fun moveToTeam_movesTheVolunteerAndTheirRequest() = runTest {
    repository.apply(EVENT_ID, request())
    repository.accept(EVENT_ID, USER_ID)

    repository.moveToTeam(EVENT_ID, USER_ID, other.id)

    assertEquals(emptyList<String>(), members())
    assertEquals(listOf(USER_ID), teams.getTeam(EVENT_ID, other.id)?.memberIds)
    assertEquals(other.id, repository.getMembershipRequest(EVENT_ID, USER_ID)?.teamId)
  }

  @Test
  fun withdraw_afterAMove_leavesTheNewTeam() = runTest {
    repository.apply(EVENT_ID, request())
    repository.accept(EVENT_ID, USER_ID)
    repository.moveToTeam(EVENT_ID, USER_ID, other.id)

    repository.withdraw(EVENT_ID, USER_ID)

    assertEquals(emptyList<String>(), teams.getTeam(EVENT_ID, other.id)?.memberIds)
  }

  @Test
  fun moveToTeam_fromADeletedTeam_putsTheVolunteerIntoTheNewOne() = runTest {
    repository.apply(EVENT_ID, request())
    repository.accept(EVENT_ID, USER_ID)
    teams.teams.removeAll { it.id == team.id }

    repository.moveToTeam(EVENT_ID, USER_ID, other.id)

    assertEquals(listOf(USER_ID), teams.getTeam(EVENT_ID, other.id)?.memberIds)
  }

  @Test
  fun moveToTeam_ofAManagerAPendingVolunteerOrToAMissingTeam_isRefused() = runTest {
    repository.apply(EVENT_ID, request())
    assertRefused { repository.moveToTeam(EVENT_ID, USER_ID, other.id) }
    repository.accept(EVENT_ID, USER_ID)
    assertRefused { repository.moveToTeam(EVENT_ID, USER_ID, "missing") }
    teams.setManager(EVENT_ID, team.id, USER_ID)
    assertRefused { repository.moveToTeam(EVENT_ID, USER_ID, other.id) }
  }

  @Test
  fun moveToTeam_intoTheTeamTheirRequestNames_putsThemBackAmongItsMembers() = runTest {
    repository.apply(EVENT_ID, request())
    repository.accept(EVENT_ID, USER_ID)
    teams.removeMember(EVENT_ID, team.id, USER_ID)

    repository.moveToTeam(EVENT_ID, USER_ID, team.id)

    assertEquals(listOf(USER_ID), members())
    assertEquals(team.id, repository.getMembershipRequest(EVENT_ID, USER_ID)?.teamId)
  }

  private suspend fun assertRefused(move: suspend () -> Unit) {
    try {
      move()
      fail("The move was not refused")
    } catch (e: MembershipRequestRepositoryException.Unknown) {
      // Refused, as the backend does.
    }
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
