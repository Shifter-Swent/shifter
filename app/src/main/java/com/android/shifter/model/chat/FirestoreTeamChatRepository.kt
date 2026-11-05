// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.MetadataChanges
import com.swent.shifter.model.event.EventSchema
import com.swent.shifter.model.team.TeamSchema
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [TeamChatRepository] backed by Cloud Firestore, storing each message under its team:
 * `/events/{eventId}/teams/{teamId}/messages/{messageId}`.
 *
 * The rules of [TeamChatRepository] are enforced by `firestore.rules`, not here: this class only
 * translates what Firestore refuses into [TeamChatRepositoryException]. [firestore] and [auth] are
 * injected so tests can point them at the emulators; [auth] gives the sender, never an argument.
 */
class FirestoreTeamChatRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : TeamChatRepository {

  private fun messagesOf(eventId: String, teamId: String) =
      firestore
          .collection(EventSchema.COLLECTION)
          .document(eventId)
          .collection(TeamSchema.COLLECTION)
          .document(teamId)
          .collection(ChatMessageSchema.COLLECTION)

  /**
   * Listens to the last [ChatRules.MAX_MESSAGES] messages by [ChatMessageSchema.SENT_AT]. A
   * listener error or a malformed message ends the flow with a [TeamChatRepositoryException].
   *
   * Metadata changes are listened to as well: the backend confirming a pending message changes
   * nothing but its metadata, so it would not be emitted otherwise and would stay
   * [ChatMessage.pending]. The other metadata changes give the same messages again, which are not
   * emitted twice. Each snapshot is the whole list, so only the latest one is kept for a slow
   * collector.
   */
  override fun messages(eventId: String, teamId: String): Flow<List<ChatMessage>> = callbackFlow {
    val registration =
        messagesOf(eventId, teamId)
            .orderBy(ChatMessageSchema.SENT_AT)
            .limitToLast(ChatRules.MAX_MESSAGES.toLong())
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
              when {
                error != null -> close(toRepositoryException(error))
                snapshot != null ->
                    try {
                      trySend(snapshot.documents.map { it.toChatMessage() })
                    } catch (e: IllegalStateException) {
                      close(TeamChatRepositoryException.Unknown(e))
                    }
              }
            }
    // Keeps the flow open while collected, then removes the listener so Firestore stops it.
    awaitClose { registration.remove() }
  }
      .conflate()
      .distinctUntilChanged()

  /**
   * Writes the message, then waits for the backend to accept it, for at most [SEND_TIMEOUT_MILLIS].
   * Offline, Firestore answers a write only once the server has it (#181), so giving up is reported
   * as [TeamChatRepositoryException.Unavailable]: the write stays queued and goes out once the
   * connection is back, which is why it must not be sent again. Nothing listens to the write after
   * that: if the rules refuse it on reconnection, Firestore only rolls it back from [messages].
   */
  override suspend fun send(eventId: String, teamId: String, text: String) {
    val trimmed = chatMessageText(text)
    val senderId = auth.currentUser?.uid ?: throw TeamChatRepositoryException.NotAllowed()
    val document = messagesOf(eventId, teamId).document()
    val confirmed =
        try {
          // `Task<Void>.await()` answers null when it succeeds, hence the explicit marker.
          withTimeoutOrNull(SEND_TIMEOUT_MILLIS.milliseconds) {
            document.set(newMessageFields(senderId, trimmed)).await()
            true
          }
        } catch (e: FirebaseFirestoreException) {
          throw toRepositoryException(e)
        }
    if (confirmed == null) throw TeamChatRepositoryException.Unavailable()
  }

  companion object {
    /**
     * How long [send] waits for the backend before reporting the message unconfirmed: the ten
     * seconds of the automatic check-in and of the live location, which bound the same offline
     * write.
     */
    const val SEND_TIMEOUT_MILLIS = 10_000L

    /** Translates a Firestore error into the [TeamChatRepositoryException] callers handle. */
    fun toRepositoryException(e: FirebaseFirestoreException): TeamChatRepositoryException =
        when (e.code) {
          Code.PERMISSION_DENIED,
          Code.UNAUTHENTICATED -> TeamChatRepositoryException.NotAllowed(e)
          Code.UNAVAILABLE,
          Code.DEADLINE_EXCEEDED -> TeamChatRepositoryException.Unavailable(e)
          else -> TeamChatRepositoryException.Unknown(e)
        }
  }
}
