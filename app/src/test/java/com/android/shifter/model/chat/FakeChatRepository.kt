// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [ChatRepository] for unit tests. Its flows are live: a message sent while a test
 * collects them is emitted right away.
 *
 * Like the backend, it trims the text, refuses an empty or too long one, and keeps the last
 * [ChatRules.MAX_MESSAGES] messages of a channel, oldest first. It does not know who belongs to
 * which channel, so [currentUserId] can send to any channel.
 *
 * @property currentUserId the signed-in user, recorded as [ChatMessage.senderId].
 * @param clock gives [ChatMessage.sentAt], as the backend's clock would.
 */
class FakeChatRepository(
    var currentUserId: String = "user-1",
    failure: ChatRepositoryException? = null,
    private val clock: () -> Instant = { Instant.EPOCH },
) : ChatRepository {

  /**
   * Every message stored so far, with its channel, in sending order, and the failure. Both are in
   * one flow, so a failure set while collected is thrown by [messages] in the collector itself.
   */
  private val state = MutableStateFlow(State(emptyList(), failure))

  private var nextId = 1

  /**
   * When set, every call and every new collection throws it, to simulate a backend error. Setting
   * it while a flow is collected ends that flow with it too, like a Firestore listener that fails
   * once the user is removed from the team.
   */
  var failure: ChatRepositoryException?
    get() = state.value.failure
    set(value) {
      state.update { it.copy(failure = value) }
    }

  /**
   * When set, a message is stored at once and [ChatMessage.pending], like a write Firestore queues
   * offline, and [send] only returns once it completes. Completing it with a
   * [ChatRepositoryException] stands for the backend's answer, which [send] throws:
   * [ChatRepositoryException.NotAllowed] takes the message out again, as Firestore rolls a refused
   * write back, while any other one leaves it pending, as a write that is still queued.
   */
  var confirmation: CompletableDeferred<Unit>? = null

  /**
   * The channels where [currentUserId] may send announcements, which the backend only allows the
   * person heading each channel. Empty by default, like the backend for anyone else, so a test of a
   * manager has to say which channel they head. In any other channel, an announcement throws
   * [ChatRepositoryException.NotAllowed] and is never stored, while a normal message still goes
   * through.
   *
   * Firestore instead shows the refused announcement in [messages] at once, then takes it out when
   * the backend refuses it: to test that, add the channel here and complete [confirmation] with
   * [ChatRepositoryException.NotAllowed].
   */
  var announcingChannels: Set<ChatChannelId> = emptySet()

  /** Stores [message] in [channel] as is, to set up a test. */
  fun add(channel: ChatChannelId, message: ChatMessage) {
    state.update { it.copy(stored = it.stored + Stored(channel, message)) }
  }

  /**
   * Confirms every pending message, as Firestore does once the connection is back, including the
   * ones [send] gave up waiting for.
   */
  fun confirmPending() {
    state.update { state -> state.copy(stored = state.stored.map { it.confirmed() }) }
  }

  override fun messages(channel: ChatChannelId): Flow<List<ChatMessage>> =
      state
          .map { (stored, failure) ->
            failure?.let { throw it }
            stored
                .filter { it.channel == channel }
                .map { it.message }
                // Stable, so messages sent at the same time stay in sending order.
                .sortedBy { it.sentAt }
                .takeLast(ChatRules.MAX_MESSAGES)
          }
          // A message sent to another channel leaves this list as is: not emitted, like a Firestore
          // listener that only sees its own query.
          .distinctUntilChanged()

  override suspend fun send(channel: ChatChannelId, text: String, announcement: Boolean) {
    val trimmed = chatMessageText(text)
    failure?.let { throw it }
    if (announcement && channel !in announcingChannels) throw ChatRepositoryException.NotAllowed()
    val confirmation = confirmation
    val message =
        ChatMessage(
            id = "message-${nextId++}",
            senderId = currentUserId,
            text = trimmed,
            sentAt = clock(),
            pending = confirmation != null,
            announcement = announcement,
        )
    val stored = Stored(channel, message)
    state.update { it.copy(stored = it.stored + stored) }
    if (confirmation == null) return
    try {
      confirmation.await()
    } catch (e: ChatRepositoryException.NotAllowed) {
      state.update { it.copy(stored = it.stored - stored) }
      throw e
    }
    state.update { state ->
      state.copy(stored = state.stored.map { if (it == stored) it.confirmed() else it })
    }
  }

  private data class State(val stored: List<Stored>, val failure: ChatRepositoryException?)

  private data class Stored(val channel: ChatChannelId, val message: ChatMessage) {
    fun confirmed() = copy(message = message.copy(pending = false))
  }
}
