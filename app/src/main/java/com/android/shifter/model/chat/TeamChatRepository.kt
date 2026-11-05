// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

import kotlinx.coroutines.flow.Flow

/**
 * Sends and observes the messages of a team's chat channel during an event. There is one channel
 * per team of an event. ViewModels depend on this interface, never on an implementation.
 *
 * Rules every implementation enforces:
 * - Only the team's members, its manager and the event's organizer can read and send, as the team
 *   is now: someone removed from the team, or who withdraws, loses access at once.
 * - A message is sent in the name of the signed-in user, and the backend sets [ChatMessage.sentAt],
 *   so nobody can send in someone else's name or backdate a message.
 * - A message is final: it can be neither edited nor deleted, by anyone.
 *
 * Every method throws, or every flow fails with, a [TeamChatRepositoryException] when the operation
 * fails, except [send] for an invalid text.
 */
interface TeamChatRepository {

  /**
   * Emits the last [ChatRules.MAX_MESSAGES] messages of the channel of [teamId] in [eventId],
   * oldest first, then again each time one is sent. Emits an empty list when there are none yet.
   *
   * A message sent from this device but not confirmed yet, typically offline, is emitted at once
   * with an estimated [ChatMessage.sentAt], so it keeps its place in the list, and marked
   * [ChatMessage.pending]. It is emitted again once the backend confirms it.
   *
   * The flow fails with a [TeamChatRepositoryException.NotAllowed] when the collector may not read
   * the channel. That also ends an observation already running, since the right to read can be lost
   * while it is open: the collector is removed from the team, or withdraws.
   */
  fun messages(eventId: String, teamId: String): Flow<List<ChatMessage>>

  /**
   * Sends [text], trimmed, to the channel of [teamId] in [eventId], in the signed-in user's name,
   * and waits for the backend to confirm it, for a bounded time. [messages] already emits the
   * message while this call waits.
   *
   * @throws IllegalArgumentException if the trimmed text is empty or longer than
   *   [ChatRules.MAX_TEXT_LENGTH]; nothing is sent, whatever the state of the backend.
   * @throws TeamChatRepositoryException.NotAllowed if the rules above deny it; the message is taken
   *   out of [messages] again.
   * @throws TeamChatRepositoryException.Unavailable when the backend cannot be reached, or when an
   *   implementation gives up waiting for its confirmation. **It does not mean the message was not
   *   sent:** an implementation that queues writes while offline still sends it once the connection
   *   is back, and [messages] keeps showing it. A caller must read it as "not confirmed" rather
   *   than "not sent", and must not send it again, which would post it twice.
   * @throws TeamChatRepositoryException.Unknown on any other failure.
   */
  suspend fun send(eventId: String, teamId: String, text: String)
}
