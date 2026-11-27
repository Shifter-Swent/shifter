// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.swent.shifter.model.event.EventSchema
import com.swent.shifter.model.firestore.requireString
import com.swent.shifter.model.firestore.requireStringList
import com.swent.shifter.model.membership.MembershipRequestRepositoryFirestore.Companion.PARTICIPANTS_COLLECTION
import com.swent.shifter.model.membership.MembershipRequestRepositoryFirestore.Companion.PARTICIPANT_IDS
import com.swent.shifter.model.notification.InboxDocs
import com.swent.shifter.model.notification.NotificationKind
import com.swent.shifter.model.team.TeamSchema
import com.swent.shifter.model.team.teamManagerId
import com.swent.shifter.model.team.teamMemberIds
import com.swent.shifter.model.user.PUBLIC_PROFILES_COLLECTION
import com.swent.shifter.model.user.UserRepositoryFirestore.Companion.DISPLAY_NAME
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [ChatRepository] backed by Cloud Firestore, storing each message under its channel: a team's
 * under the team, `/events/{eventId}/teams/{teamId}/messages/{messageId}`, and the event's general
 * channel under the event, `/events/{eventId}/channels/general/messages/{messageId}`.
 *
 * The rules of [ChatRepository] are enforced by `firestore.rules`, not here: this class only
 * translates what Firestore refuses into [ChatRepositoryException]. [firestore] and [auth] are
 * injected so tests can point them at the emulators; [auth] gives the sender, never an argument.
 */
class FirestoreChatRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : ChatRepository {

  /** The document of the event [eventId]. */
  private fun eventOf(eventId: String): DocumentReference =
      firestore.collection(EventSchema.COLLECTION).document(eventId)

  /** The collection holding the messages of [channel]. */
  private fun messagesOf(channel: ChatChannelId): CollectionReference {
    val event = eventOf(channel.eventId)
    val parent =
        when (channel) {
          is ChatChannelId.Team -> event.collection(TeamSchema.COLLECTION).document(channel.teamId)
          is ChatChannelId.General ->
              event.collection(ChatMessageSchema.CHANNELS).document(ChatMessageSchema.GENERAL)
          is ChatChannelId.Admin ->
              event.collection(ChatMessageSchema.CHANNELS).document(ChatMessageSchema.ADMIN)
        }
    return parent.collection(ChatMessageSchema.COLLECTION)
  }

  /**
   * Listens to the last [ChatRules.MAX_MESSAGES] messages by [ChatMessageSchema.SENT_AT]. A
   * listener error or a malformed message ends the flow with a [ChatRepositoryException].
   *
   * Metadata changes are listened to as well: the backend confirming a pending message changes
   * nothing but its metadata, so it would not be emitted otherwise and would stay
   * [ChatMessage.pending]. The other metadata changes give the same messages again, which are not
   * emitted twice. Each snapshot is the whole list, so only the latest one is kept for a slow
   * collector.
   */
  override fun messages(channel: ChatChannelId): Flow<List<ChatMessage>> = callbackFlow {
    val registration =
        messagesOf(channel)
            .orderBy(ChatMessageSchema.SENT_AT)
            .limitToLast(ChatRules.MAX_MESSAGES.toLong())
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
              when {
                error != null -> close(toRepositoryException(error))
                snapshot != null ->
                    try {
                      trySend(snapshot.documents.map { it.toChatMessage() })
                    } catch (e: IllegalStateException) {
                      close(ChatRepositoryException.Unknown(e))
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
   * as [ChatRepositoryException.Unavailable]: the write stays queued and goes out once the
   * connection is back, which is why it must not be sent again. Nothing listens to the write after
   * that: if the rules refuse it on reconnection, Firestore only rolls it back from [messages].
   *
   * An [announcement] goes in one batch with its notifications (#618), so neither is stored without
   * the other: see [notificationsOf]. Reading its members takes from the same
   * [SEND_TIMEOUT_MILLIS].
   */
  override suspend fun send(channel: ChatChannelId, text: String, announcement: Boolean) {
    val trimmed = chatMessageText(text)
    val senderId = auth.currentUser?.uid ?: throw ChatRepositoryException.NotAllowed()
    val deadline = TimeSource.Monotonic.markNow() + SEND_TIMEOUT_MILLIS.milliseconds
    val batch = firestore.batch()
    batch.set(messagesOf(channel).document(), newMessageFields(senderId, trimmed, announcement))
    if (announcement) {
      notificationsOf(channel, senderId, trimmed, deadline).forEach { (document, fields) ->
        batch.set(document, fields)
      }
    }
    // Before waiting, so the write is queued even with no time left, as Unavailable then says.
    val committed = batch.commit()
    val confirmed =
        try {
          // `Task<Void>.await()` answers null when it succeeds, hence the explicit marker.
          withTimeoutOrNull(deadline.remaining()) {
            committed.await()
            true
          }
        } catch (e: FirebaseFirestoreException) {
          throw toRepositoryException(e)
        }
    if (confirmed == null) throw ChatRepositoryException.Unavailable()
  }

  /**
   * A notification of the announcement [text] for each member of [channel] but [senderId], read
   * from the server, so it needs a connection. Nothing is written yet: a failure is
   * [ChatRepositoryException.NotAllowed] when the sender may not read them or the event or the team
   * is gone, else [ChatRepositoryException.Unknown]: offline, past [deadline], or past
   * [MAX_BATCH_WRITES] writes with the message (#677). Never [ChatRepositoryException.Unavailable],
   * which would tell the caller the message is queued.
   */
  private suspend fun notificationsOf(
      channel: ChatChannelId,
      senderId: String,
      text: String,
      deadline: TimeMark,
  ): List<Pair<DocumentReference, Map<String, Any>>> =
      try {
        withTimeoutOrNull(deadline.remaining()) {
          coroutineScope {
            val event = async { eventOf(channel.eventId).get(Source.SERVER).await() }
            val sender = async {
              firestore
                  .collection(PUBLIC_PROFILES_COLLECTION)
                  .document(senderId)
                  .get(Source.SERVER)
                  .await()
            }
            val audience = audienceOf(channel)
            val senderName = sender.await().get(DISPLAY_NAME) as? String
            val notifications =
                InboxDocs.documents(
                    firestore,
                    audience.userIds,
                    NotificationKind.ANNOUNCEMENT,
                    channel.eventId,
                    event.await().eventTitle(),
                    senderId,
                    announcementData(channel, senderName, audience.teamName, text),
                )
            check(notifications.size < MAX_BATCH_WRITES) {
              "An announcement notifies at most ${MAX_BATCH_WRITES - 1} people"
            }
            notifications
          }
        } ?: throw ChatRepositoryException.Unknown()
      } catch (e: FirebaseFirestoreException) {
        throw when (e.code) {
          Code.PERMISSION_DENIED,
          Code.UNAUTHENTICATED,
          Code.NOT_FOUND -> ChatRepositoryException.NotAllowed(e)
          else -> ChatRepositoryException.Unknown(e)
        }
      } catch (e: CancellationException) {
        // A CancellationException is an IllegalStateException: the caller's cancellation goes on.
        throw e
      } catch (e: IllegalStateException) {
        // A malformed event, team or participants document, or too many people to notify.
        throw ChatRepositoryException.Unknown(e)
      }

  /**
   * Who reads [channel], the sender among them: the participants, the team's members and manager,
   * or the managers of the event's teams, read from the teams rather than `ManagerIndexSchema`.
   */
  private suspend fun audienceOf(channel: ChatChannelId): Audience {
    val event = eventOf(channel.eventId)
    return when (channel) {
      is ChatChannelId.Team -> {
        val team =
            event
                .collection(TeamSchema.COLLECTION)
                .document(channel.teamId)
                .get(Source.SERVER)
                .await()
        if (!team.exists()) {
          throw FirebaseFirestoreException(
              "Team '${team.reference.path}' does not exist",
              Code.NOT_FOUND,
          )
        }
        Audience(
            team.teamMemberIds() + listOfNotNull(team.teamManagerId()),
            team.requireString(TEAM, TeamSchema.NAME),
        )
      }
      is ChatChannelId.General -> {
        val participants =
            firestore
                .collection(PARTICIPANTS_COLLECTION)
                .document(channel.eventId)
                .get(Source.SERVER)
                .await()
        // An event nobody joined yet has no participants document: it reads as empty.
        Audience(participants.requireStringList(PARTICIPANTS, PARTICIPANT_IDS), teamName = null)
      }
      is ChatChannelId.Admin -> {
        val teams = event.collection(TeamSchema.COLLECTION).get(Source.SERVER).await()
        Audience(teams.documents.mapNotNull { it.teamManagerId() }, teamName = null)
      }
    }
  }

  /** The title of the event in this snapshot, failing with NOT_FOUND when the event is gone. */
  private fun DocumentSnapshot.eventTitle(): String {
    if (!exists()) {
      throw FirebaseFirestoreException("Event '${reference.path}' does not exist", Code.NOT_FOUND)
    }
    return requireString(EVENT, EventSchema.TITLE)
  }

  /** The time left until this deadline, negative once it passed. */
  private fun TimeMark.remaining(): Duration = -elapsedNow()

  /** Who [audienceOf] found, and the team's name for a team's channel. */
  private data class Audience(val userIds: List<String>, val teamName: String?)

  companion object {
    /**
     * How long [send] waits for the backend before reporting the message unconfirmed, the reads of
     * an announcement included: the ten seconds of the automatic check-in and of the live location,
     * which bound the same offline write.
     */
    const val SEND_TIMEOUT_MILLIS = 10_000L

    /** The most writes of an announcement's batch, its message included (#677). */
    private const val MAX_BATCH_WRITES = 500

    /** Name the documents read by [audienceOf] in the failure a malformed one raises. */
    private const val EVENT = "Event"
    private const val TEAM = "Team"
    private const val PARTICIPANTS = "EventParticipants"

    /** Translates a Firestore error into the [ChatRepositoryException] callers handle. */
    internal fun toRepositoryException(e: FirebaseFirestoreException): ChatRepositoryException =
        when (e.code) {
          Code.PERMISSION_DENIED,
          Code.UNAUTHENTICATED -> ChatRepositoryException.NotAllowed(e)
          Code.UNAVAILABLE,
          Code.DEADLINE_EXCEEDED -> ChatRepositoryException.Unavailable(e)
          else -> ChatRepositoryException.Unknown(e)
        }
  }
}
