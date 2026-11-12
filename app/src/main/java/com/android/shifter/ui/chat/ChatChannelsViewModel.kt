// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.model.team.TeamRepositoryException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A team channel the user can open, named after its team. */
data class ChatChannel(val teamId: String, val name: String, val icon: String)

/**
 * The channels of an event the user can open. The event's general channel is open to everyone in
 * it, so it is not listed here: the screen always shows it, above [channels].
 *
 * @property asOrganizer whether the user organizes the event, which tells why there is no team
 *   channel.
 * @property channels the team channels the user can open.
 * @property showsAdmin whether the user may open the event's admin channel: its organizer, and the
 *   managers of its teams.
 * @property error why the channels could not be listed; only [ChatError.OFFLINE] and
 *   [ChatError.UNEXPECTED] are used here.
 */
data class ChatChannelsUiState(
    val asOrganizer: Boolean = false,
    val channels: List<ChatChannel> = emptyList(),
    val showsAdmin: Boolean = false,
    val isLoading: Boolean = true,
    val error: ChatError? = null,
)

/**
 * Lists the team channels of the event [eventId] the user can open: every team of the event for its
 * organizer, [asOrganizer], and otherwise the teams the user belongs to or manages, by name.
 *
 * The admin channel is shown to the organizer and to whoever manages a team of the event. The rules
 * of `channels/admin` check the index of the event's managers instead, which a rule can read, but
 * it is written in the same commit as each team's manager, so both give the same answer. A manager
 * replaced while the list is open finds the channel closed when they open it.
 *
 * @param currentUserId returns the signed-in user's id, or null when nobody is signed in.
 */
class ChatChannelsViewModel(
    private val eventId: String,
    private val asOrganizer: Boolean,
    private val teamRepository: TeamRepository,
    private val currentUserId: () -> String?,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ChatChannelsUiState(asOrganizer))
  val uiState: StateFlow<ChatChannelsUiState> = _uiState.asStateFlow()

  private var loading: Job? = null

  init {
    load()
  }

  /** Lists the channels, and again after an error. */
  fun load() {
    loading?.cancel()
    _uiState.value = ChatChannelsUiState(asOrganizer)
    loading = viewModelScope.launch {
      try {
        val userId = currentUserId()
        val (teams, showsAdmin) =
            when {
              // Every team of the event, which needs no user id.
              asOrganizer -> teamRepository.getTeamsByEvent(eventId) to true
              // An empty list would say the user is in no team: the error lets them retry.
              userId == null -> error("No signed-in user to list the teams of")
              // Independent queries, run together so a volunteer waits for one round trip.
              else ->
                  coroutineScope {
                    val member = async { teamRepository.getTeamsOfMember(eventId, userId) }
                    val managed = async { teamRepository.getTeamsManagedBy(eventId, userId) }
                    val managedTeams = managed.await()
                    member.await() + managedTeams to managedTeams.isNotEmpty()
                  }
            }
        val channels =
            teams
                .distinctBy { it.id }
                .map { ChatChannel(it.id, it.name, it.icon) }
                .sortedBy { it.name.lowercase() }
        _uiState.value = ChatChannelsUiState(asOrganizer, channels, showsAdmin, isLoading = false)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val error =
            if (e is TeamRepositoryException.Unavailable) ChatError.OFFLINE
            else ChatError.UNEXPECTED
        _uiState.value = ChatChannelsUiState(asOrganizer, isLoading = false, error = error)
      }
    }
  }

  companion object {
    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(
        eventId: String,
        asOrganizer: Boolean,
        teamRepository: TeamRepository,
        currentUserId: () -> String?,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { ChatChannelsViewModel(eventId, asOrganizer, teamRepository, currentUserId) }
    }
  }
}
