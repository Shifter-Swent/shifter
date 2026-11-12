// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.chat.ChatChannelId
import com.swent.shifter.model.chat.ChatMessage
import com.swent.shifter.model.chat.ChatRepository
import com.swent.shifter.model.chat.ChatRepositoryException
import com.swent.shifter.model.chat.ChatRules
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.model.user.UserRepository
import com.swent.shifter.model.user.displayNames
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
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
 * @property isAnnouncement whether it was sent as an announcement, to stand out from the others.
 */
data class ChatMessageItem(
    val id: String,
    val text: String,
    val senderName: String?,
    val sentAt: Instant?,
    val isMine: Boolean,
    val isPending: Boolean = false,
    val isAnnouncement: Boolean = false,
)

/**
 * What the team channel shows.
 *
 * @property draft the text being typed, not yet sent.
 * @property sendFailed whether the last message could not be sent; its text is back in [draft].
 * @property canAnnounce whether the user may send announcements in the channel, i.e. manages the
 *   team. The backend still decides: this only shows the option.
 * @property asAnnouncement whether [draft] is to be sent as an announcement.
 * @property announcementRefused whether the last announcement was refused; its text is back in
 *   [draft], still as an announcement.
 */
data class TeamChatUiState(
    val messages: List<ChatMessageItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: ChatError? = null,
    val draft: String = "",
    val sendFailed: Boolean = false,
    val canAnnounce: Boolean = false,
    val asAnnouncement: Boolean = false,
    val announcementRefused: Boolean = false,
) {
  /**
   * Whether [draft] can be sent: not blank, not too long, once the channel is listed and while the
   * user may still post in it.
   */
  val canSend: Boolean
    get() =
        !isLoading &&
            error != ChatError.NOT_ALLOWED &&
            draft.isNotBlank() &&
            draft.trim().length <= ChatRules.MAX_TEXT_LENGTH
}

/**
 * Shows the channel of the team [teamId] of the event [eventId] in real time, with the senders'
 * names, and sends the user's messages.
 *
 * A message sent stays pending until [ChatRepository.send] returns, i.e. until the server has
 * confirmed it. Offline, the repository already lists it: that message is the one shown as pending.
 *
 * When [ChatRepository.send] gives up with [ChatRepositoryException.Unavailable], the message is
 * not confirmed but still queued, and the backend sends it once the connection is back: it stays
 * pending and its text is not given back, since sending it again would post it twice. Any other
 * failure means it was not sent: its text goes back into the draft, as an announcement if it was
 * one, so a refused announcement is never posted as a normal message without the user seeing it.
 *
 * @param currentUserId returns the signed-in user's id, or null when nobody is signed in.
 */
class TeamChatViewModel(
    private val eventId: String,
    private val teamId: String,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val currentUserId: () -> String?,
) : ViewModel() {

  /**
   * A message being sent, with the ids of the messages already listed when it was sent: the message
   * of the channel it stands for is a new one.
   */
  private class Sending(
      val localId: Int,
      val text: String,
      val announcement: Boolean,
      val knownIds: Set<String>,
  )

  private val _uiState = MutableStateFlow(TeamChatUiState())
  val uiState: StateFlow<TeamChatUiState> = _uiState.asStateFlow()

  private var received: List<ChatMessage> = emptyList()
  private var sending: List<Sending> = emptyList()
  private var nextLocalId = 0

  /** The names loaded so far, by uid; null for a profile that has no name. */
  private val names = mutableMapOf<String, String?>()

  /** The senders whose name was asked for, loaded or not: each is read once per [load]. */
  private val requested = mutableSetOf<String>()

  private var loading: Job? = null

  init {
    load()
    loadAnnounceRight()
  }

  /** Starts listening to the channel, again after an error. */
  fun load() {
    loading?.cancel()
    // A name that failed to load is tried again.
    requested.retainAll(names.keys)
    _uiState.update { it.copy(isLoading = true, error = null) }
    loading = viewModelScope.launch {
      try {
        chatRepository.messages(ChatChannelId.Team(eventId, teamId)).collect { messages ->
          received = messages
          show(isLoading = false)
          loadNames(messages)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val error =
            when (e) {
              is ChatRepositoryException.Unavailable -> ChatError.OFFLINE
              is ChatRepositoryException.NotAllowed -> ChatError.NOT_ALLOWED
              else -> ChatError.UNEXPECTED
            }
        if (error == ChatError.NOT_ALLOWED) {
          // Access is lost: the messages already shown must not stay readable, nor those being
          // sent.
          received = emptyList()
          sending = emptyList()
          show()
        }
        _uiState.update { it.copy(isLoading = false, error = error) }
      }
    }
  }

  fun onDraftChange(text: String) {
    _uiState.update { it.copy(draft = text, sendFailed = false, announcementRefused = false) }
  }

  /** Chooses whether to send the draft as an announcement; ignored without the right to. */
  fun onAnnouncementChange(asAnnouncement: Boolean) {
    if (!_uiState.value.canAnnounce) return
    _uiState.update { it.copy(asAnnouncement = asAnnouncement, announcementRefused = false) }
  }

  /** Sends the draft, if it [can be sent][TeamChatUiState.canSend]. */
  fun send() {
    val state = _uiState.value
    if (!state.canSend) return
    val text = state.draft.trim()
    val announcement = state.canAnnounce && state.asAnnouncement
    val message = Sending(nextLocalId++, text, announcement, received.map { it.id }.toSet())
    sending = sending + message
    // The option is chosen again for each message, so the next one is not announced by mistake.
    _uiState.update {
      it.copy(draft = "", sendFailed = false, asAnnouncement = false, announcementRefused = false)
    }
    show()
    viewModelScope.launch {
      try {
        chatRepository.send(ChatChannelId.Team(eventId, teamId), text, announcement)
      } catch (e: CancellationException) {
        throw e
      } catch (e: ChatRepositoryException.Unavailable) {
        // Not confirmed, but still queued: it stays pending and is not offered to be sent again.
        return@launch
      } catch (e: Exception) {
        val refused = announcement && e is ChatRepositoryException.NotAllowed
        // Gives the message back as it was, unless the user already typed another one.
        _uiState.update {
          val giveBack = it.draft.isBlank()
          it.copy(
              draft = if (giveBack) text else it.draft,
              asAnnouncement = if (giveBack) announcement else it.asAnnouncement,
              sendFailed = !refused,
              announcementRefused = refused,
          )
        }
        // The manager may have been replaced meanwhile: without the right any more, the option
        // goes, so the same announcement is not refused again and again.
        if (refused) loadAnnounceRight()
      }
      sending = sending - message
      show()
    }
  }

  /**
   * Reads whether the user manages the team, the only one who may announce in its channel. A team
   * that cannot be read leaves the option hidden.
   */
  private fun loadAnnounceRight() {
    viewModelScope.launch {
      val manager =
          try {
            teamRepository.getTeam(eventId, teamId)?.managerId
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            null
          }
      val me = currentUserId()
      val canAnnounce = me != null && manager == me
      // Without the right, nothing may stay chosen as an announcement.
      _uiState.update {
        it.copy(canAnnounce = canAnnounce, asAnnouncement = canAnnounce && it.asAnnouncement)
      }
    }
  }

  /**
   * Loads, in parallel and apart from the messages, the names of the senders not asked for yet,
   * then shows them, so that a slow profile read does not hold the next messages back.
   */
  private fun loadNames(messages: List<ChatMessage>) {
    val unknown = messages.map { it.senderId }.distinct().filter { it !in requested }
    if (unknown.isEmpty()) return
    requested += unknown
    viewModelScope.launch {
      names += userRepository.displayNames(unknown)
      show()
    }
  }

  private fun show(isLoading: Boolean = _uiState.value.isLoading) {
    val me = currentUserId()
    val waiting = sending.toMutableList()
    val items = received.map { message ->
      val mine = message.senderId == me
      val match = waiting.firstOrNull {
        mine &&
            it.text == message.text &&
            it.announcement == message.announcement &&
            message.id !in it.knownIds
      }
      if (match != null) waiting -= match
      ChatMessageItem(
          id = message.id,
          text = message.text,
          senderName = names[message.senderId],
          sentAt = message.sentAt,
          isMine = mine,
          isPending = match != null,
          isAnnouncement = message.announcement,
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
          isAnnouncement = it.announcement,
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
        chatRepository: ChatRepository,
        userRepository: UserRepository,
        teamRepository: TeamRepository,
        currentUserId: () -> String?,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        TeamChatViewModel(
            eventId,
            teamId,
            chatRepository,
            userRepository,
            teamRepository,
            currentUserId,
        )
      }
    }
  }
}
