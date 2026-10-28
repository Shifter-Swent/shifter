// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.people

import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.FakeMembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequestRepositoryException
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.User
import com.swent.shifter.model.user.UserRepositoryException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ParticipantsViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private val requests = FakeMembershipRequestRepository()
  private val teams = FakeTeamRepository()
  private val users = FakeUserRepository()

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun viewModel() = ParticipantsViewModel(EVENT_ID, requests, teams, users)

  @Test
  fun load_listsOnlyTheAcceptedRequests() = runTest {
    request("ann", MembershipRequestStatus.ACCEPTED)
    request("bob", MembershipRequestStatus.PENDING)
    request("cat", MembershipRequestStatus.REJECTED)
    team("bar", memberIds = listOf("ann", "bob", "cat"))

    val viewModel = viewModel()
    advanceUntilIdle()

    assertEquals(listOf("ann"), viewModel.userIdsOf("bar"))
  }

  @Test
  fun load_groupsTheParticipantsByTeamWithTheManagerFirst() = runTest {
    listOf("ann", "bob", "cat").forEach { request(it, MembershipRequestStatus.ACCEPTED) }
    team("bar", memberIds = listOf("ann"))
    team("kitchen", managerId = "cat", memberIds = listOf("bob"))
    team("logistics")

    val viewModel = viewModel()
    advanceUntilIdle()

    val groups = viewModel.uiState.value.groups
    assertEquals(listOf("bar", "kitchen", "logistics"), groups.map { it.team?.id })
    assertEquals(
        listOf(
            ParticipantItem("cat", "cat Smith", true),
            ParticipantItem("bob", "bob Smith", false),
        ),
        groups[1].participants,
    )
    assertTrue(groups[2].participants.isEmpty())
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun load_withAParticipantInNoTeam_listsThemLast() = runTest {
    request("ann", MembershipRequestStatus.ACCEPTED)
    request("bob", MembershipRequestStatus.ACCEPTED)
    // Bob's team was deleted after he was accepted.
    team("bar", memberIds = listOf("ann"))

    val viewModel = viewModel()
    advanceUntilIdle()

    val groups = viewModel.uiState.value.groups
    assertEquals(listOf("bar", null), groups.map { it.team?.id })
    assertEquals(listOf("bob"), groups.last().participants.map { it.userId })
  }

  @Test
  fun load_withoutParticipantsInNoTeam_hasNoGroupForThem() = runTest {
    team("bar")

    val viewModel = viewModel()
    advanceUntilIdle()

    assertEquals(listOf("bar"), viewModel.uiState.value.groups.map { it.team?.id })
  }

  @Test
  fun load_withAMissingOrUnreadableProfile_leavesTheNameOut() = runTest {
    request("ann", MembershipRequestStatus.ACCEPTED)
    request("ghost", MembershipRequestStatus.ACCEPTED)
    team("bar", memberIds = listOf("ann", "ghost"))
    users.users.remove("ghost")

    val viewModel = viewModel()
    advanceUntilIdle()
    assertEquals(listOf("ann Smith", null), viewModel.uiState.value.groups[0].names())

    users.failure = UserRepositoryException.PermissionDenied()
    viewModel.load()
    advanceUntilIdle()
    assertEquals(listOf(null, null), viewModel.uiState.value.groups[0].names())
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun load_whenOffline_showsTheErrorThenRetrySucceeds() = runTest {
    request("ann", MembershipRequestStatus.ACCEPTED)
    team("bar", memberIds = listOf("ann"))
    requests.failure = MembershipRequestRepositoryException.Unavailable()

    val viewModel = viewModel()
    advanceUntilIdle()
    assertEquals(ParticipantsError.OFFLINE, viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.groups.isEmpty())
    assertFalse(viewModel.uiState.value.isLoading)

    requests.failure = null
    viewModel.load()
    advanceUntilIdle()
    assertNull(viewModel.uiState.value.error)
    assertEquals(listOf("ann"), viewModel.userIdsOf("bar"))
  }

  @Test
  fun load_mapsEachFailureToItsError() = runTest {
    requests.failure = MembershipRequestRepositoryException.PermissionDenied()
    val viewModel = viewModel()
    advanceUntilIdle()
    assertEquals(ParticipantsError.PERMISSION_DENIED, viewModel.uiState.value.error)

    requests.failure = null
    teams.failure = IllegalStateException("Teams cannot be read")
    viewModel.load()
    advanceUntilIdle()
    assertEquals(ParticipantsError.UNEXPECTED, viewModel.uiState.value.error)
  }

  @Test
  fun load_isIgnoredWhileLoading() = runTest {
    var reads = 0
    val counted =
        object : MembershipRequestRepository by requests {
          override suspend fun getMembershipRequestsByEId(
              eventId: String
          ): List<MembershipRequest> {
            reads++
            return requests.getMembershipRequestsByEId(eventId)
          }
        }
    val viewModel = ParticipantsViewModel(EVENT_ID, counted, teams, users)
    assertTrue(viewModel.uiState.value.isLoading)

    viewModel.load()
    advanceUntilIdle()

    assertEquals(1, reads)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun appointManager_withAMember_makesThemTheManagerOfTheirTeam() = runTest {
    listOf("ann", "bob").forEach { request(it, MembershipRequestStatus.ACCEPTED) }
    team("bar", memberIds = listOf("ann", "bob"))
    val viewModel = viewModel()
    advanceUntilIdle()

    viewModel.appointManager("bar", "bob")
    advanceUntilIdle()

    assertEquals(
        listOf(
            ParticipantItem("bob", "bob Smith", true),
            ParticipantItem("ann", "ann Smith", false),
        ),
        viewModel.uiState.value.groups.single().participants,
    )
    assertEquals(listOf("ann"), teamOf("bar").memberIds)
    assertFalse(viewModel.uiState.value.isUpdating)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun appointManager_whenTheTeamHasOne_sendsThePreviousManagerBackToTheTeam() = runTest {
    listOf("ann", "bob").forEach { request(it, MembershipRequestStatus.ACCEPTED) }
    team("bar", managerId = "ann", memberIds = listOf("bob"))
    val viewModel = viewModel()
    advanceUntilIdle()

    viewModel.appointManager("bar", "bob")
    advanceUntilIdle()

    assertEquals("bob", teamOf("bar").managerId)
    assertEquals(listOf("ann"), teamOf("bar").memberIds)
    assertEquals(
        listOf(
            ParticipantItem("bob", "bob Smith", true),
            ParticipantItem("ann", "ann Smith", false),
        ),
        viewModel.uiState.value.groups.single().participants,
    )
  }

  @Test
  fun removeManager_sendsTheManagerBackToTheTeam() = runTest {
    request("ann", MembershipRequestStatus.ACCEPTED)
    team("bar", managerId = "ann")
    val viewModel = viewModel()
    advanceUntilIdle()

    viewModel.removeManager("bar")
    advanceUntilIdle()

    assertNull(teamOf("bar").managerId)
    assertEquals(listOf("ann"), teamOf("bar").memberIds)
    assertEquals(
        listOf(ParticipantItem("ann", "ann Smith", false)),
        viewModel.uiState.value.groups.single().participants,
    )
  }

  @Test
  fun appointManager_whenTheWriteFails_keepsTheListAndShowsTheFailure() = runTest {
    request("ann", MembershipRequestStatus.ACCEPTED)
    team("bar", memberIds = listOf("ann"))
    val viewModel = viewModel()
    advanceUntilIdle()
    val before = viewModel.uiState.value.groups

    teams.failure = IllegalStateException("Offline")
    viewModel.appointManager("bar", "ann")
    advanceUntilIdle()

    assertEquals(ParticipantsError.UPDATE_FAILED, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isUpdating)
    assertEquals(before, viewModel.uiState.value.groups)

    // The next attempt starts clean.
    teams.failure = null
    viewModel.appointManager("bar", "ann")
    advanceUntilIdle()
    assertNull(viewModel.uiState.value.error)
    assertEquals("ann", teamOf("bar").managerId)
  }

  @Test
  fun appointManager_whileALoadIsRunning_isAppliedAndShown() = runTest {
    listOf("ann", "bob").forEach { request(it, MembershipRequestStatus.ACCEPTED) }
    team("bar", managerId = "ann", memberIds = listOf("bob"))
    // Reads the teams at once but answers a second later, so the load straddles the write.
    val slowTeams =
        object : TeamRepository by teams {
          override suspend fun getTeamsByEvent(eventId: String): List<Team> {
            val read = teams.getTeamsByEvent(eventId)
            delay(1_000)
            return read
          }
        }
    val viewModel = ParticipantsViewModel(EVENT_ID, requests, slowTeams, users)
    runCurrent()
    assertTrue(viewModel.uiState.value.isLoading)

    viewModel.appointManager("bar", "bob")
    advanceUntilIdle()

    assertEquals("bob", teamOf("bar").managerId)
    assertEquals(
        listOf(
            ParticipantItem("bob", "bob Smith", true),
            ParticipantItem("ann", "ann Smith", false),
        ),
        viewModel.uiState.value.groups.single().participants,
    )
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun appointManager_isIgnoredWhileAnotherChangeRuns() = runTest {
    listOf("ann", "bob").forEach { request(it, MembershipRequestStatus.ACCEPTED) }
    team("bar", memberIds = listOf("ann", "bob"))
    val viewModel = viewModel()
    advanceUntilIdle()

    viewModel.appointManager("bar", "ann")
    viewModel.appointManager("bar", "bob")
    assertTrue(viewModel.uiState.value.isUpdating)
    advanceUntilIdle()

    assertEquals("ann", teamOf("bar").managerId)
    assertFalse(viewModel.uiState.value.isUpdating)
  }

  private fun request(userId: String, status: MembershipRequestStatus) {
    users.users[userId] = User(uid = userId, displayName = "$userId Smith", email = "$userId@x.ch")
    requests.add(
        EVENT_ID,
        MembershipRequest(
            userId = userId,
            teamId = "bar",
            availability = listOf(AvailabilitySlot(Instant.EPOCH, Instant.EPOCH.plusSeconds(60))),
            createdAt = Instant.EPOCH,
            status = status,
        ),
    )
  }

  private fun team(id: String, managerId: String? = null, memberIds: List<String> = emptyList()) {
    teams.teams +=
        Team(
            id = id,
            eventId = EVENT_ID,
            name = id,
            description = "The $id team",
            icon = "wine",
            managerId = managerId,
            memberIds = memberIds,
            createdAt = Instant.EPOCH,
        )
  }

  /** The team [id] as the fake stores it now, read directly so a set failure does not apply. */
  private fun teamOf(id: String): Team = teams.teams.single { it.id == id }

  private fun ParticipantsViewModel.userIdsOf(teamId: String): List<String> =
      uiState.value.groups.single { it.team?.id == teamId }.participants.map { it.userId }

  private fun ParticipantGroup.names() = participants.map { it.name }

  private companion object {
    const val EVENT_ID = "event-1"
  }
}
