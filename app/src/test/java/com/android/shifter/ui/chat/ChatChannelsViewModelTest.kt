// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepositoryException
import com.swent.shifter.utils.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatChannelsViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val teams =
      FakeTeamRepository(
          listOf(
              team("bar", "Bar", memberIds = listOf(ME)),
              team("kitchen", "kitchen", managerId = ME),
              team("security", "Security", memberIds = listOf(OTHER)),
              team("elsewhere", "Elsewhere", eventId = "other-event", memberIds = listOf(ME)),
          )
      )

  private fun viewModel(asOrganizer: Boolean = false, userId: String? = ME) =
      ChatChannelsViewModel(EVENT, asOrganizer, teams) { userId }

  @Test
  fun load_asVolunteer_listsTheTeamsIBelongToOrManage() = runTest {
    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertNull(state.error)
    assertEquals(listOf("bar", "kitchen"), state.channels.map { it.teamId })
    assertEquals(ChatChannel("bar", "Bar", "truck"), state.channels.first())
  }

  @Test
  fun load_asMemberAndManagerOfATeam_listsItOnce() = runTest {
    val team = team("bar", "Bar", managerId = ME, memberIds = listOf(ME))
    val viewModel = ChatChannelsViewModel(EVENT, false, FakeTeamRepository(listOf(team))) { ME }

    assertEquals(listOf("bar"), viewModel.uiState.value.channels.map { it.teamId })
  }

  @Test
  fun load_asOrganizer_listsEveryTeamOfTheEventByName() = runTest {
    val state = viewModel(asOrganizer = true).uiState.value

    assertTrue(state.asOrganizer)
    assertEquals(listOf("Bar", "kitchen", "Security"), state.channels.map { it.name })
  }

  @Test
  fun load_withoutTeam_listsNoChannel() = runTest {
    val state = viewModel(userId = "newcomer").uiState.value

    assertFalse(state.isLoading)
    assertNull(state.error)
    assertTrue(state.channels.isEmpty())
  }

  @Test
  fun load_asOrganizerWithoutUserId_listsEveryTeamOfTheEvent() = runTest {
    val state = viewModel(asOrganizer = true, userId = null).uiState.value

    assertNull(state.error)
    assertEquals(listOf("Bar", "kitchen", "Security"), state.channels.map { it.name })
  }

  @Test
  fun load_asVolunteerWithoutUserId_showsTheUnexpectedError() = runTest {
    val state = viewModel(userId = null).uiState.value

    assertFalse(state.isLoading)
    assertEquals(ChatError.UNEXPECTED, state.error)
    assertTrue(state.channels.isEmpty())
  }

  @Test
  fun load_offline_showsTheOfflineError() = runTest {
    teams.failure = TeamRepositoryException.Unavailable()

    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertEquals(ChatError.OFFLINE, state.error)
  }

  @Test
  fun load_withAnotherFailure_showsTheUnexpectedError() = runTest {
    teams.failure = TeamRepositoryException.Unknown()

    assertEquals(ChatError.UNEXPECTED, viewModel().uiState.value.error)
  }

  @Test
  fun load_afterAnError_listsTheChannels() = runTest {
    teams.failure = TeamRepositoryException.Unavailable()
    val viewModel = viewModel()
    teams.failure = null

    viewModel.load()

    assertNull(viewModel.uiState.value.error)
    assertEquals(2, viewModel.uiState.value.channels.size)
  }

  @Test
  fun load_failingAfterASuccess_clearsTheChannels() = runTest {
    val viewModel = viewModel()
    teams.failure = TeamRepositoryException.Unavailable()

    viewModel.load()

    assertEquals(ChatError.OFFLINE, viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.channels.isEmpty())
  }

  private fun team(
      id: String,
      name: String,
      eventId: String = EVENT,
      managerId: String? = null,
      memberIds: List<String> = emptyList(),
  ) =
      Team(
          id = id,
          eventId = eventId,
          name = name,
          description = "",
          icon = "truck",
          managerId = managerId,
          memberIds = memberIds,
          createdAt = Instant.EPOCH,
      )

  private companion object {
    const val EVENT = "event-1"
    const val ME = "me"
    const val OTHER = "other"
  }
}
