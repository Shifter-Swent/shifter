// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

import java.time.Instant

/**
 * A message of a chat channel. Once sent it never changes: see [ChatRepository] for the rules.
 *
 * The model stays free of Firebase types so it can be used by ViewModels and tested without a
 * backend. The channel is not repeated here: it is the one the message was read from.
 *
 * @property id the message's id, unique in its channel.
 * @property senderId id of the user who sent it. Only the uid is stored: the screen shows the name
 *   through the public profiles, so a renamed user never shows up under an old name.
 * @property text what was sent, already trimmed, from 1 to [ChatRules.MAX_TEXT_LENGTH] characters.
 * @property sentAt when the backend received it, set by the backend rather than the sender. For a
 *   message not confirmed yet, typically sent offline, it is an estimate.
 * @property pending whether the message was sent from this device but not confirmed by the backend
 *   yet, typically offline. It becomes false once the backend has it, even after
 *   [ChatRepository.send] gave up waiting.
 * @property announcement whether it was sent as an announcement, which only the person heading the
 *   channel can do: the team's manager in a team channel (see [ChatChannelId]). Messages sent
 *   before announcements existed are normal messages.
 */
data class ChatMessage(
    val id: String,
    val senderId: String,
    val text: String,
    val sentAt: Instant,
    val pending: Boolean = false,
    val announcement: Boolean = false,
)

/** The limits of a channel, in one place so they are easy to change. */
object ChatRules {
  /** The longest text a message can have, once trimmed. */
  const val MAX_TEXT_LENGTH = 1000

  /** How many messages of a channel are read: the most recent ones. */
  const val MAX_MESSAGES = 100
}

/**
 * Returns [text] trimmed, as it is sent and stored.
 *
 * @throws IllegalArgumentException if the trimmed text is empty or longer than
 *   [ChatRules.MAX_TEXT_LENGTH].
 */
fun chatMessageText(text: String): String {
  val trimmed = text.trim()
  require(trimmed.isNotEmpty()) { "A message cannot be empty" }
  require(trimmed.length <= ChatRules.MAX_TEXT_LENGTH) {
    "A message cannot be longer than ${ChatRules.MAX_TEXT_LENGTH} characters"
  }
  return trimmed
}
