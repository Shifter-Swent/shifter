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
 * In-memory [TeamChatRepository] for unit tests. Its flows are live: a message sent while a test
 * collects them is emitted right away.
 *
 * Like the backend, it trims the text, refuses an empty or too long one, and keeps the last
 * [ChatRules.MAX_MESSAGES] messages of a channel, oldest first. It does not know who is in which
 * team, so [currentUserId] can send to any channel.
 *
 * @property currentUserId the signed-in user, recorded as [ChatMessage.senderId].
 * @param clock gives [ChatMessage.sentAt], as the backend's clock would.
 */
class FakeTeamChatRepository(
    var currentUserId: String = "user-1",
    failure: TeamChatRepositoryException? = null,
    private val clock: () -> Instant = { Instant.EPOCH },
) : TeamChatRepository {

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
  var failure: TeamChatRepositoryException?
    get() = state.value.failure
    set(value) {
      state.update { it.copy(failure = value) }
    }

  /**
   * When set, a message is stored at once, like a write Firestore queues offline, and [send] only
   * returns once it completes. Completing it with a [TeamChatRepositoryException] stands for the
   * backend's answer, which [send] throws: [TeamChatRepositoryException.NotAllowed] takes the
   * message out again, as Firestore rolls a refused write back, while any other one leaves it in
   * place, as a write that is still queued.
   */
  var confirmation: CompletableDeferred<Unit>? = null

  /** Stores [message] in the channel of [teamId] in [eventId] as is, to set up a test. */
  fun add(eventId: String, teamId: String, message: ChatMessage) {
    state.update { it.copy(stored = it.stored + Stored(eventId, teamId, message)) }
  }

  override fun messages(eventId: String, teamId: String): Flow<List<ChatMessage>> =
      state
          .map { (stored, failure) ->
            failure?.let { throw it }
            stored
                .filter { it.eventId == eventId && it.teamId == teamId }
                .map { it.message }
                // Stable, so messages sent at the same time stay in sending order.
                .sortedBy { it.sentAt }
                .takeLast(ChatRules.MAX_MESSAGES)
          }
          // A message sent to another channel leaves this list as is: not emitted, like a Firestore
          // listener that only sees its own query.
          .distinctUntilChanged()

  override suspend fun send(eventId: String, teamId: String, text: String) {
    val trimmed = chatMessageText(text)
    failure?.let { throw it }
    val stored =
        Stored(eventId, teamId, ChatMessage("message-${nextId++}", currentUserId, trimmed, clock()))
    state.update { it.copy(stored = it.stored + stored) }
    try {
      confirmation?.await()
    } catch (e: TeamChatRepositoryException.NotAllowed) {
      state.update { it.copy(stored = it.stored - stored) }
      throw e
    }
  }

  private data class State(val stored: List<Stored>, val failure: TeamChatRepositoryException?)

  private data class Stored(val eventId: String, val teamId: String, val message: ChatMessage)
}
