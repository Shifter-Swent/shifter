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
   * with an estimated [ChatMessage.sentAt], so it keeps its place in the list.
   */
  fun messages(eventId: String, teamId: String): Flow<List<ChatMessage>>

  /**
   * Sends [text], trimmed, to the channel of [teamId] in [eventId], in the signed-in user's name.
   *
   * Offline, the message is kept and sent once the connection is back: this call suspends until
   * then, while [messages] already emits it.
   *
   * @throws IllegalArgumentException if the trimmed text is empty or longer than
   *   [ChatRules.MAX_TEXT_LENGTH]; nothing is sent.
   * @throws TeamChatRepositoryException.NotAllowed if the rules above deny it.
   */
  suspend fun send(eventId: String, teamId: String, text: String)
}
