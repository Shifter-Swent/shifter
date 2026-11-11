// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

import kotlinx.coroutines.flow.Flow

/**
 * Sends and observes the messages of the chat channels of an event, each named by a
 * [ChatChannelId]. A channel stays open after the event ends. ViewModels depend on this interface,
 * never on an implementation.
 *
 * Rules every implementation enforces:
 * - Only the members of a channel, given by its [ChatChannelId], can read and send, as they are
 *   now: someone who stops being one, for instance removed from the team, loses access at once.
 * - A message is sent in the name of the signed-in user, and the backend sets [ChatMessage.sentAt],
 *   so nobody can send in someone else's name or backdate a message.
 * - Only the person heading a channel, given by its [ChatChannelId], can send an announcement.
 * - A message is final: it can be neither edited nor deleted, by anyone, nor turned into an
 *   announcement or back.
 *
 * Every method throws, or every flow fails with, a [ChatRepositoryException] when the operation
 * fails, except [send] for an invalid text.
 */
interface ChatRepository {

  /**
   * Emits the last [ChatRules.MAX_MESSAGES] messages of [channel], oldest first, then again each
   * time one is sent. Emits an empty list when there are none yet. Offline, the list is read from
   * what this device has cached: opening a channel never read before then emits an empty list too,
   * even if it has messages.
   *
   * A message sent from this device but not confirmed yet, typically offline, is emitted at once,
   * last, with an estimated [ChatMessage.sentAt], and marked [ChatMessage.pending]. It is emitted
   * again once the backend confirms it, placed by the time the backend received it: a message
   * written offline at 10:00 and delivered at 10:30 comes after the ones sent at 10:05.
   *
   * The flow fails with a [ChatRepositoryException.NotAllowed] when the collector may not read the
   * channel. That also ends an observation already running, since the right to read can be lost
   * while it is open: for instance, the collector is removed from the team, or withdraws.
   */
  fun messages(channel: ChatChannelId): Flow<List<ChatMessage>>

  /**
   * Sends [text], trimmed, to [channel], in the signed-in user's name, as an [announcement] or not,
   * and waits for the backend to confirm it, for a bounded time. [messages] already emits the
   * message while this call waits.
   *
   * @throws IllegalArgumentException if the trimmed text is empty or longer than
   *   [ChatRules.MAX_TEXT_LENGTH]; nothing is sent, whatever the state of the backend.
   * @throws ChatRepositoryException.NotAllowed if the rules above deny it, including an
   *   announcement by someone not heading the channel; the message is taken out of [messages]
   *   again.
   * @throws ChatRepositoryException.Unavailable when the backend cannot be reached, or when an
   *   implementation gives up waiting for its confirmation. **It does not mean the message was not
   *   sent:** an implementation that queues writes while offline still sends it once the connection
   *   is back, and [messages] keeps showing it. A caller must read it as "not confirmed" rather
   *   than "not sent", and must not send it again, which would post it twice. If the backend still
   *   refuses it once it gets it, for instance because the sender was removed from the team in the
   *   meantime, it is taken out of [messages] with no error reported anywhere.
   * @throws ChatRepositoryException.Unknown on any other failure.
   */
  suspend fun send(channel: ChatChannelId, text: String, announcement: Boolean = false)
}
