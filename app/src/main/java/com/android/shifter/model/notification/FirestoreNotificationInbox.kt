// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import android.util.Log
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/**
 * [NotificationInbox] backed by Cloud Firestore, reading `users/{uid}/notifications`, which the
 * writers fill with [InboxDocs.documents].
 *
 * Who may read and empty an inbox is enforced by `firestore.rules`, not here: this class only
 * translates what Firestore refuses into [NotificationInboxException]. A notification this app
 * cannot read is skipped and left in place, so it never blocks the others: one of a kind it does
 * not know, which it shows once updated, or a malformed one, which the rules keep the app from
 * writing and which is logged. [firestore] is injected so tests can point it at the emulator.
 */
class FirestoreNotificationInbox(private val firestore: FirebaseFirestore) : NotificationInbox {

  /**
   * The inbox of [userId]. Throws [NotificationInboxException.Unknown] for an id no document can
   * have, such as an empty one or one holding a `/`.
   */
  private fun inboxOf(userId: String): CollectionReference =
      try {
        InboxDocs.inbox(firestore, userId)
      } catch (e: IllegalArgumentException) {
        throw NotificationInboxException.Unknown(e)
      }

  /** Reads the inbox from the server, or from the phone's cache when offline. */
  override suspend fun pending(userId: String): List<InboxNotification> {
    val oldestFirst = inboxOf(userId).orderBy(InboxSchema.CREATED_AT)
    val documents =
        try {
          oldestFirst.get().await().documents
        } catch (e: FirebaseFirestoreException) {
          throw toInboxException(e)
        }
    return readable(documents)
  }

  /**
   * Listens to the inbox. A listener error ends the flow with a [NotificationInboxException]. Each
   * snapshot is the whole list, so only the latest one is kept for a slow collector, and the same
   * list is not emitted twice.
   */
  override fun observePending(userId: String): Flow<List<InboxNotification>> = callbackFlow {
    val registration =
        inboxOf(userId).orderBy(InboxSchema.CREATED_AT).addSnapshotListener { snapshot, error ->
          when {
            error != null -> close(toInboxException(error))
            snapshot != null -> trySend(readable(snapshot.documents))
          }
        }
    // Keeps the flow open while collected, then removes the listener so Firestore stops it.
    awaitClose { registration.remove() }
  }
      .conflate()
      .distinctUntilChanged()

  /**
   * Deletes the notifications in batches of at most [MAX_BATCH_WRITES], without waiting for the
   * server: Firestore applies a delete to the phone's cache at once and sends it when it can, so
   * this never blocks offline. Deleting an id already gone is not an error.
   *
   * Every document is named before anything is sent, so an id no document can have fails the call
   * with [NotificationInboxException.Unknown] and deletes nothing. A delete the server refuses
   * later, such as one in someone else's inbox, cannot be reported to the caller, who has moved on:
   * it is logged, and Firestore puts the notification back.
   */
  override suspend fun remove(userId: String, ids: Collection<String>) {
    val inbox = inboxOf(userId)
    val documents = ids.map { id -> documentOf(inbox, id) }
    documents.chunked(MAX_BATCH_WRITES).forEach { chunk ->
      val batch = firestore.batch()
      chunk.forEach { batch.delete(it) }
      batch.commit().addOnFailureListener { e ->
        Log.w(TAG, "The server refused to delete ${chunk.size} notifications", e)
      }
    }
  }

  private fun documentOf(inbox: CollectionReference, id: String): DocumentReference =
      try {
        inbox.document(id)
      } catch (e: IllegalArgumentException) {
        throw NotificationInboxException.Unknown(e)
      }

  /** The notifications among [documents] this app can read, in their order. */
  private fun readable(documents: List<DocumentSnapshot>): List<InboxNotification> =
      documents.mapNotNull { document ->
        try {
          document.toInboxNotification()
        } catch (e: IllegalStateException) {
          Log.w(TAG, "Skipped the malformed notification ${document.id}", e)
          null
        }
      }

  /** Translates a Firestore error into the [NotificationInboxException] callers handle. */
  private fun toInboxException(e: FirebaseFirestoreException): NotificationInboxException =
      when (e.code) {
        Code.PERMISSION_DENIED,
        Code.UNAUTHENTICATED -> NotificationInboxException.NotAllowed(e)
        Code.UNAVAILABLE,
        Code.DEADLINE_EXCEEDED -> NotificationInboxException.Unavailable(e)
        else -> NotificationInboxException.Unknown(e)
      }

  private companion object {
    const val TAG = "FirestoreNotificationInbox"

    /** The most writes a Firestore batch holds. */
    const val MAX_BATCH_WRITES = 500
  }
}
