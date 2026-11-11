// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.swent.shifter.model.firestore.invalidField
import com.swent.shifter.model.firestore.optionalBoolean
import com.swent.shifter.model.firestore.requireString

/** Names this entity in the failure a malformed document raises. */
private const val ENTITY = "ChatMessage"

/**
 * Names of the messages subcollection and of every field of a message document. The channel is
 * carried by the path, such as `/events/{eventId}/teams/{teamId}/messages/{messageId}`.
 */
internal object ChatMessageSchema {

  /** The `messages` subcollection of each channel, such as `/events/{eventId}/teams/{teamId}`. */
  const val COLLECTION = "messages"

  /**
   * The `channels` subcollection of each `/events/{eventId}` document, holding the channels of the
   * whole event, such as [GENERAL] and [ADMIN].
   */
  const val CHANNELS = "channels"

  /** The id of the event's general channel in [CHANNELS]. */
  const val GENERAL = "general"

  /** The id of the event's admin channel in [CHANNELS]. */
  const val ADMIN = "admin"

  const val SENDER_ID = "senderId"
  const val TEXT = "text"
  const val SENT_AT = "sentAt"
  const val ANNOUNCEMENT = "announcement"
}

/**
 * The document body of a new message of [senderId]. [ChatMessage.sentAt] is a server timestamp:
 * `firestore.rules` require it to be the request time. [ChatMessage.announcement] is always
 * written, so only the messages sent before it existed lack it.
 */
internal fun newMessageFields(
    senderId: String,
    text: String,
    announcement: Boolean,
): Map<String, Any> =
    mapOf(
        ChatMessageSchema.SENDER_ID to senderId,
        ChatMessageSchema.TEXT to text,
        ChatMessageSchema.SENT_AT to FieldValue.serverTimestamp(),
        ChatMessageSchema.ANNOUNCEMENT to announcement,
    )

/**
 * Rebuilds the [ChatMessage] stored in this document, taking [ChatMessage.id] from the document id.
 *
 * A message whose write is still pending has no server time yet: it is read with an estimate of it,
 * as [ChatRepository.messages] promises, and marked [ChatMessage.pending]. A message with no
 * [ChatMessageSchema.ANNOUNCEMENT] field, sent before announcements existed, is a normal message.
 *
 * Throws [IllegalStateException] when a field is missing or has an unexpected type.
 */
internal fun DocumentSnapshot.toChatMessage(): ChatMessage =
    ChatMessage(
        id = id,
        senderId = requireString(ENTITY, ChatMessageSchema.SENDER_ID),
        text = requireString(ENTITY, ChatMessageSchema.TEXT),
        sentAt =
            (get(ChatMessageSchema.SENT_AT, ServerTimestampBehavior.ESTIMATE) as? Timestamp)
                ?.toInstant() ?: invalidField(ENTITY, ChatMessageSchema.SENT_AT),
        pending = metadata.hasPendingWrites(),
        announcement = optionalBoolean(ENTITY, ChatMessageSchema.ANNOUNCEMENT) ?: false,
    )
