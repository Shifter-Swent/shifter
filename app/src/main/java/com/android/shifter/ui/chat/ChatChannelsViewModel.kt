// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A team channel the user can open, named after its team. */
data class ChatChannel(val teamId: String, val name: String, val icon: String)

/**
 * The channels of an event the user can open.
 *
 * @property asOrganizer whether the user organizes the event, which tells why there is no channel.
 * @property error why the channels could not be listed; only [ChatError.OFFLINE] and
 *   [ChatError.UNEXPECTED] are used here.
 * @property channelToOpen the only channel the user can open, to open it at once: set the first
 *   time the list holds exactly one, and cleared by [ChatChannelsViewModel.onChannelOpened].
 */
data class ChatChannelsUiState(
    val asOrganizer: Boolean = false,
    val channels: List<ChatChannel> = emptyList(),
    val isLoading: Boolean = true,
    val error: ChatError? = null,
    val channelToOpen: ChatChannel? = null,
)

/**
 * Lists the channels of the event [eventId] the user can open: every team of the event for its
 * organizer, [asOrganizer], and otherwise the teams the user belongs to or manages, by name.
 *
 * When there is a single channel, it is offered once through [ChatChannelsUiState.channelToOpen],
 * so the screen opens it directly. Coming back to the list then shows that channel instead of
 * opening it again, which would leave no way back.
 *
 * @param savedState keeps whether the single channel was already opened when Android recreates the
 *   ViewModel after killing the app, so coming back to the list still does not open it again.
 * @param currentUserId returns the signed-in user's id, or null when nobody is signed in.
 */
class ChatChannelsViewModel(
    private val eventId: String,
    private val asOrganizer: Boolean,
    private val teamRepository: TeamRepository,
    private val savedState: SavedStateHandle,
    private val currentUserId: () -> String?,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ChatChannelsUiState(asOrganizer))
  val uiState: StateFlow<ChatChannelsUiState> = _uiState.asStateFlow()

  private var loading: Job? = null

  /** Whether the single channel was already opened directly, which only happens once. */
  private var openedDirectly: Boolean
    get() = savedState[OPENED_DIRECTLY] ?: false
    set(value) {
      savedState[OPENED_DIRECTLY] = value
    }

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
        val teams =
            when {
              // Every team of the event, which needs no user id.
              asOrganizer -> teamRepository.getTeamsByEvent(eventId)
              // An empty list would say the user is in no team: the error lets them retry.
              userId == null -> error("No signed-in user to list the teams of")
              // Independent queries, run together so a volunteer waits for one round trip.
              else ->
                  coroutineScope {
                    val member = async { teamRepository.getTeamsOfMember(eventId, userId) }
                    val managed = async { teamRepository.getTeamsManagedBy(eventId, userId) }
                    member.await() + managed.await()
                  }
            }
        val channels =
            teams
                .distinctBy { it.id }
                .map { ChatChannel(it.id, it.name, it.icon) }
                .sortedBy { it.name.lowercase() }
        _uiState.value =
            ChatChannelsUiState(
                asOrganizer,
                channels,
                isLoading = false,
                channelToOpen = channels.singleOrNull()?.takeUnless { openedDirectly },
            )
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

  /** The screen opened [ChatChannelsUiState.channelToOpen]: it is never offered again. */
  fun onChannelOpened() {
    openedDirectly = true
    _uiState.update { it.copy(channelToOpen = null) }
  }

  companion object {
    private const val OPENED_DIRECTLY = "openedDirectly"

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
      initializer {
        ChatChannelsViewModel(
            eventId,
            asOrganizer,
            teamRepository,
            createSavedStateHandle(),
            currentUserId,
        )
      }
    }
  }
}
