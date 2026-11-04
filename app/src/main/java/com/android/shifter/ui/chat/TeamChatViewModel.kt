// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.chat.ChatMessage
import com.swent.shifter.model.chat.ChatRules
import com.swent.shifter.model.chat.TeamChatRepository
import com.swent.shifter.model.chat.TeamChatRepositoryException
import com.swent.shifter.model.user.UserRepository
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the channel shows an error. The screen turns each into a translated message. */
enum class ChatError {
  OFFLINE,

  /** The user may not read the channel, for instance after being removed from the team. */
  NOT_ALLOWED,
  UNEXPECTED,
}

/**
 * A message as the channel shows it.
 *
 * @property senderName the sender's public name, or null when it could not be loaded.
 * @property sentAt when it was sent, or null for a message not yet in the channel.
 * @property isPending whether it is sent by the user and not confirmed by the server yet.
 */
data class ChatMessageItem(
    val id: String,
    val text: String,
    val senderName: String?,
    val sentAt: Instant?,
    val isMine: Boolean,
    val isPending: Boolean = false,
)

/**
 * What the team channel shows.
 *
 * @property draft the text being typed, not yet sent.
 * @property sendFailed whether the last message could not be sent; its text is back in [draft].
 */
data class TeamChatUiState(
    val messages: List<ChatMessageItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: ChatError? = null,
    val draft: String = "",
    val sendFailed: Boolean = false,
) {
  /** Whether [draft] can be sent: not blank, and not too long. */
  val canSend: Boolean
    get() = draft.isNotBlank() && draft.trim().length <= ChatRules.MAX_TEXT_LENGTH
}

/**
 * Shows the channel of the team [teamId] of the event [eventId] in real time, with the senders'
 * names, and sends the user's messages.
 *
 * A message sent stays pending until [TeamChatRepository.send] returns, i.e. until the server has
 * confirmed it. Offline, the repository already lists it: that message is the one shown as pending.
 *
 * When [TeamChatRepository.send] gives up with [TeamChatRepositoryException.Unavailable], the
 * message is not confirmed but still queued, and the backend sends it once the connection is back:
 * it stays pending and its text is not given back, since sending it again would post it twice. Any
 * other failure means it was not sent: its text goes back into the draft.
 *
 * @param currentUserId returns the signed-in user's id, or null when nobody is signed in.
 */
class TeamChatViewModel(
    private val eventId: String,
    private val teamId: String,
    private val chatRepository: TeamChatRepository,
    private val userRepository: UserRepository,
    private val currentUserId: () -> String?,
) : ViewModel() {

  /**
   * A message being sent, with the ids of the messages already listed when it was sent: the message
   * of the channel it stands for is a new one.
   */
  private class Sending(val localId: Int, val text: String, val knownIds: Set<String>)

  private val _uiState = MutableStateFlow(TeamChatUiState())
  val uiState: StateFlow<TeamChatUiState> = _uiState.asStateFlow()

  private var received: List<ChatMessage> = emptyList()
  private var sending: List<Sending> = emptyList()
  private var nextLocalId = 0

  /** The names loaded so far, by uid; null for a profile that has no name. */
  private val names = mutableMapOf<String, String?>()

  private var loading: Job? = null

  init {
    load()
  }

  /** Starts listening to the channel, again after an error. */
  fun load() {
    loading?.cancel()
    _uiState.update { it.copy(isLoading = true, error = null) }
    loading = viewModelScope.launch {
      try {
        chatRepository.messages(eventId, teamId).collect { messages ->
          received = messages
          show(isLoading = false)
          loadNames(messages)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val error =
            when (e) {
              is TeamChatRepositoryException.Unavailable -> ChatError.OFFLINE
              is TeamChatRepositoryException.NotAllowed -> ChatError.NOT_ALLOWED
              else -> ChatError.UNEXPECTED
            }
        if (error == ChatError.NOT_ALLOWED) {
          // Access is lost: the messages already shown must not stay readable.
          received = emptyList()
          show()
        }
        _uiState.update { it.copy(isLoading = false, error = error) }
      }
    }
  }

  fun onDraftChange(text: String) {
    _uiState.update { it.copy(draft = text, sendFailed = false) }
  }

  /** Sends the draft, if it [can be sent][TeamChatUiState.canSend]. */
  fun send() {
    val state = _uiState.value
    if (!state.canSend) return
    val text = state.draft.trim()
    val message = Sending(nextLocalId++, text, received.map { it.id }.toSet())
    sending = sending + message
    _uiState.update { it.copy(draft = "", sendFailed = false) }
    show()
    viewModelScope.launch {
      try {
        chatRepository.send(eventId, teamId, text)
      } catch (e: CancellationException) {
        throw e
      } catch (e: TeamChatRepositoryException.Unavailable) {
        // Not confirmed, but still queued: it stays pending and is not offered to be sent again.
        return@launch
      } catch (e: Exception) {
        // Gives the text back, unless the user already typed another one.
        _uiState.update { it.copy(draft = it.draft.ifEmpty { text }, sendFailed = true) }
      }
      sending = sending - message
      show()
    }
  }

  /** Loads, in parallel, the names of the senders not loaded yet, then shows them. */
  private suspend fun loadNames(messages: List<ChatMessage>) {
    val unknown = messages.map { it.senderId }.distinct().filter { it !in names }
    if (unknown.isEmpty()) return
    val loaded = coroutineScope { unknown.map { async { it to name(it) } }.awaitAll() }
    // A name that failed to load is tried again with the next messages.
    loaded.forEach { (uid, name) -> name.onSuccess { names[uid] = it } }
    show()
  }

  /** The public name of [uid], null for a profile with none, or a failure to load it. */
  private suspend fun name(uid: String): Result<String?> =
      try {
        Result.success(
            userRepository.getPublicProfile(uid)?.displayName?.takeIf { it.isNotBlank() }
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }

  private fun show(isLoading: Boolean = _uiState.value.isLoading) {
    val me = currentUserId()
    val waiting = sending.toMutableList()
    val items = received.map { message ->
      val mine = message.senderId == me
      val match = waiting.firstOrNull {
        mine && it.text == message.text && message.id !in it.knownIds
      }
      if (match != null) waiting -= match
      ChatMessageItem(
          id = message.id,
          text = message.text,
          senderName = names[message.senderId],
          sentAt = message.sentAt,
          isMine = mine,
          isPending = match != null,
      )
    }
    // The messages being sent that the channel does not list yet.
    val notListed = waiting.map {
      ChatMessageItem(
          id = "sending_${it.localId}",
          text = it.text,
          senderName = me?.let { uid -> names[uid] },
          sentAt = null,
          isMine = true,
          isPending = true,
      )
    }
    _uiState.update { it.copy(messages = items + notListed, isLoading = isLoading) }
  }

  companion object {
    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(
        eventId: String,
        teamId: String,
        chatRepository: TeamChatRepository,
        userRepository: UserRepository,
        currentUserId: () -> String?,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        TeamChatViewModel(eventId, teamId, chatRepository, userRepository, currentUserId)
      }
    }
  }
}
