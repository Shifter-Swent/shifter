// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
 * @property failure when set, every call and every new collection throws it, to simulate a backend
 *   error.
 * @param clock gives [ChatMessage.sentAt], as the backend's clock would.
 */
class FakeTeamChatRepository(
    var currentUserId: String = "user-1",
    var failure: Exception? = null,
    private val clock: () -> Instant = { Instant.EPOCH },
) : TeamChatRepository {

  /** Every message stored so far, with its channel, in sending order. */
  private val state = MutableStateFlow<List<Stored>>(emptyList())

  private var nextId = 1

  /** Stores [message] in the channel of [teamId] in [eventId] as is, to set up a test. */
  fun add(eventId: String, teamId: String, message: ChatMessage) {
    state.update { it + Stored(eventId, teamId, message) }
  }

  override fun messages(eventId: String, teamId: String): Flow<List<ChatMessage>> = flow {
    failure?.let { throw it }
    // A message sent to another channel leaves this list as is: not emitted, like a Firestore
    // listener that only sees its own query.
    emitAll(
        state
            .map { stored ->
              stored
                  .filter { it.eventId == eventId && it.teamId == teamId }
                  .map { it.message }
                  // Stable, so messages sent at the same time stay in sending order.
                  .sortedBy { it.sentAt }
                  .takeLast(ChatRules.MAX_MESSAGES)
            }
            .distinctUntilChanged()
    )
  }

  override suspend fun send(eventId: String, teamId: String, text: String) {
    val trimmed = chatMessageText(text)
    failure?.let { throw it }
    add(eventId, teamId, ChatMessage("message-${nextId++}", currentUserId, trimmed, clock()))
  }

  private data class Stored(val eventId: String, val teamId: String, val message: ChatMessage)
}
