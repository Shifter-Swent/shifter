// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.swent.shifter.model.firestore.invalidField
import com.swent.shifter.model.firestore.requireInstant
import com.swent.shifter.model.firestore.requireString
import com.swent.shifter.model.user.USERS_COLLECTION

/** Names this entity in the failure a malformed document raises. */
private const val ENTITY = "InboxNotification"

/**
 * Names of the inbox subcollection and of every field of a notification document. The recipient is
 * carried by the path, `users/{uid}/notifications/{id}`.
 */
internal object InboxSchema {

  /** The `notifications` subcollection of each `users/{uid}` document. */
  const val COLLECTION = "notifications"

  const val KIND = "kind"
  const val EVENT_ID = "eventId"
  const val EVENT_TITLE = "eventTitle"
  const val SENDER_ID = "senderId"
  const val DATA = "data"
  const val CREATED_AT = "createdAt"
}

/**
 * The writing side of the inbox. Each action sets the [documents] of its notifications in its own
 * batch or transaction, so the notifications and the action are stored together or not at all:
 * ```
 * InboxDocs.documents(db, team.memberIds, NotificationKind.TEAM_CHANGED, …)
 *     .forEach { (doc, fields) -> batch.set(doc, fields) }
 * ```
 */
internal object InboxDocs {

  /**
   * One new document and its fields for each of [recipientIds], leaving out [senderId] and the
   * duplicates: the writer passes everyone the action concerns, the sender included or not, and
   * never makes its own action fail on a notification the rules refuse.
   *
   * Throws [IllegalArgumentException] for a [kind] or [data] the rules refuse, see
   * [requireWritable].
   */
  fun documents(
      db: FirebaseFirestore,
      recipientIds: Iterable<String>,
      kind: NotificationKind,
      eventId: String,
      eventTitle: String,
      senderId: String,
      data: Map<String, String>,
  ): List<Pair<DocumentReference, Map<String, Any>>> =
      recipients(senderId, recipientIds).map { recipientId ->
        newDocument(db, recipientId) to
            fields(recipientId, kind, eventId, eventTitle, senderId, data)
      }

  /**
   * Who [documents] notifies: [recipientIds] in order, without [senderId] and without duplicates.
   */
  fun recipients(senderId: String, recipientIds: Iterable<String>): Set<String> =
      recipientIds.toCollection(LinkedHashSet()) - senderId

  /**
   * The inbox of [recipientId]: only they read it, `firestore.rules` let others create in it. The
   * reading side (#616) queries it too.
   */
  fun inbox(db: FirebaseFirestore, recipientId: String) =
      db.collection(USERS_COLLECTION).document(recipientId).collection(InboxSchema.COLLECTION)

  /** A new document in the inbox of [recipientId], with an id chosen by Firestore. */
  fun newDocument(db: FirebaseFirestore, recipientId: String): DocumentReference =
      inbox(db, recipientId).document()

  /**
   * The body of a notification of [senderId] for [recipientId]. `createdAt` is a server timestamp,
   * which the rules require to be the request time. Writers go through [documents]; this one is for
   * the tests that check what is stored.
   *
   * Throws [IllegalArgumentException] for what the rules refuse, with [requireWritable], including
   * a [recipientId] equal to [senderId].
   */
  fun fields(
      recipientId: String,
      kind: NotificationKind,
      eventId: String,
      eventTitle: String,
      senderId: String,
      data: Map<String, String>,
  ): Map<String, Any> {
    requireWritable(recipientId, senderId, kind, data)
    return mapOf(
        InboxSchema.KIND to kind.name,
        InboxSchema.EVENT_ID to eventId,
        InboxSchema.EVENT_TITLE to eventTitle,
        InboxSchema.SENDER_ID to senderId,
        InboxSchema.DATA to data,
        InboxSchema.CREATED_AT to FieldValue.serverTimestamp(),
    )
  }
}

/**
 * Rebuilds the [InboxNotification] stored in this document, taking its id from the document id.
 *
 * Returns null for a kind this app does not know, or one computed on the phone: a newer version of
 * the app may write a kind this one cannot show, and its user should still get the others. For the
 * same reason, a key of `data` this app does not know is left out.
 *
 * Throws [IllegalStateException] when a field is missing or has an unexpected type, including a
 * `data` that is not a map or holds a value that is not a string. The rules refuse such a document,
 * so it can only come from outside the app.
 */
internal fun DocumentSnapshot.toInboxNotification(): InboxNotification? {
  val name = requireString(ENTITY, InboxSchema.KIND)
  val kind = NotificationKind.entries.firstOrNull { it.name == name }
  if (kind == null || !kind.inInbox) return null
  return InboxNotification(
      id = id,
      kind = kind,
      eventId = requireString(ENTITY, InboxSchema.EVENT_ID),
      eventTitle = requireString(ENTITY, InboxSchema.EVENT_TITLE),
      senderId = requireString(ENTITY, InboxSchema.SENDER_ID),
      data = knownData(),
      createdAt = requireInstant(ENTITY, InboxSchema.CREATED_AT),
  )
}

/** The entries of `data` under a known key, each value required to be a string. */
private fun DocumentSnapshot.knownData(): Map<String, String> {
  val entries = get(InboxSchema.DATA) as? Map<*, *> ?: invalidField(ENTITY, InboxSchema.DATA)
  return entries
      .filterKeys { it in NotificationData.Key.ALL }
      .map { (key, value) ->
        key as String to (value as? String ?: invalidField(ENTITY, "${InboxSchema.DATA}.$key"))
      }
      .toMap()
}
