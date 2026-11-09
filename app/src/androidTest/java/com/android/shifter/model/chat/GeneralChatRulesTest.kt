// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.firebase.emulatorTest
import com.swent.shifter.firebase.signInForFirestore
import java.util.UUID
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The `firestore.rules` of `events/{eventId}/channels/general/messages/{messageId}`, written with
 * the raw SDK so they are checked on their own, whatever the repository does. The checks of the
 * message itself are shared with the team channel and covered by `TeamChatRulesTest`; only a few
 * are repeated here, to show this channel applies them too.
 *
 * Every denial has an allowed counterpart with the same user, so a test cannot pass because its
 * setup is broken.
 */
@RunWith(AndroidJUnit4::class)
class GeneralChatRulesTest {
  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore
  private val paths = mutableSetOf<String>()
  private lateinit var organizer: Account
  private lateinit var manager: Account
  private lateinit var participant: Account
  private lateinit var applicant: Account
  private lateinit var outsider: Account
  private lateinit var eventId: String
  private lateinit var otherEventId: String

  @Before
  fun setUp() = emulatorTest {
    organizer = account()
    manager = account()
    participant = account()
    applicant = account()
    outsider = account()
    eventId = event(participants = listOf(manager, participant))
    // The manager leads the team of the participant, as appointed among its members.
    createAsAdmin(
        teamRef().path,
        mapOf("managerId" to manager.uid, "memberIds" to listOf(participant.uid)),
    )
    request(eventId, participant, "ACCEPTED")
    request(eventId, applicant, "PENDING")
    // The outsider takes part in another event only.
    otherEventId = event(participants = listOf(outsider))
  }

  @After
  fun cleanUp() = emulatorTest {
    try {
      paths.forEach { FirestoreEmulatorAdmin.deleteDocument(it) }
    } finally {
      auth.signOut()
    }
  }

  @Test
  fun send_byAParticipant_storesTheMessage() = emulatorTest {
    signIn(participant)
    val ref = newMessage()

    ref.set(message(participant, "Hello everyone")).await()

    val stored = ref.get(Source.SERVER).await()
    assertEquals(participant.uid, stored.getString("senderId"))
    assertEquals("Hello everyone", stored.getString("text"))
    assertTrue(stored.get("sentAt") is Timestamp)
  }

  @Test
  fun send_byAManager_isAllowed() = emulatorTest {
    signIn(manager)

    newMessage().set(message(manager, "The bar opens at 18:00")).await()
  }

  @Test
  fun send_byTheOrganizer_isAllowed() = emulatorTest {
    signIn(organizer)

    newMessage().set(message(organizer, "Welcome to the festival")).await()
  }

  @Test
  fun read_byAParticipantAManagerAndTheOrganizer_listsTheChannel() = emulatorTest {
    val sent = sentByParticipant()

    for (user in listOf(participant, manager, organizer)) {
      signIn(user)
      assertEquals(
          listOf(sent.id),
          channelQuery().get(Source.SERVER).await().documents.map { it.id },
      )
      assertTrue(sent.get(Source.SERVER).await().exists())
    }
  }

  @Test
  fun access_byAPendingApplicant_isDeniedUntilAccepted() = emulatorTest {
    val sent = sentByParticipant()

    assertNoAccess(applicant, sent)

    // Through the admin API, as the acceptance itself is covered by the membership rules.
    setParticipantsAsAdmin(listOf(manager, participant, applicant))
    signIn(applicant)
    newMessage().set(message(applicant, "Hello everyone")).await()
  }

  @Test
  fun access_bySomeoneOutsideTheEvent_isDenied() = emulatorTest {
    val sent = sentByParticipant()
    signIn(outsider)
    // The general channel of their own event is open to them.
    newMessage(eventId = otherEventId).set(message(outsider, "Hello")).await()

    assertNoAccess(outsider, sent)
  }

  @Test
  fun access_afterWithdrawing_isDenied() = emulatorTest {
    val sent = sentByParticipant()

    // As the app withdraws: leaving the participants and the team, and deleting the request, in
    // one commit.
    signIn(participant)
    db.batch()
        .update(participantsRef(), "participantIds", FieldValue.arrayRemove(participant.uid))
        .update(teamRef(), "memberIds", FieldValue.arrayRemove(participant.uid))
        .delete(requestRef(eventId, participant))
        .commit()
        .await()

    assertNoAccess(participant, sent)
  }

  @Test
  fun access_afterBeingRejected_isDenied() = emulatorTest {
    val sent = sentByParticipant()

    // As the app rejects an accepted volunteer: the request, the participants and the team, in one
    // commit by the organizer.
    signIn(organizer)
    db.batch()
        .update(requestRef(eventId, participant), "status", "REJECTED")
        .update(participantsRef(), "participantIds", FieldValue.arrayRemove(participant.uid))
        .update(teamRef(), "memberIds", FieldValue.arrayRemove(participant.uid))
        .commit()
        .await()

    assertNoAccess(participant, sent)
  }

  @Test
  fun send_asAnAnnouncementByAParticipant_isDenied() = emulatorTest {
    // A normal message of the same participant goes through.
    sentByParticipant()

    assertDenied {
      newMessage().set(message(participant, "Briefing at 18:00") + ("announcement" to true)).await()
    }
  }

  @Test
  fun send_withAnotherSenderId_isDenied() = emulatorTest {
    signIn(participant)

    assertDenied { newMessage().set(message(organizer, "Hello everyone")).await() }
  }

  @Test
  fun send_withATooLongText_isDenied() = emulatorTest {
    signIn(participant)

    assertDenied {
      newMessage().set(message(participant, "a".repeat(ChatRules.MAX_TEXT_LENGTH + 1))).await()
    }
  }

  @Test
  fun updateAndDelete_bySenderOrOrganizer_areDenied() = emulatorTest {
    val sent = sentByParticipant()

    for (user in listOf(participant, organizer)) {
      signIn(user)
      assertDenied { sent.update("text", "Edited").await() }
      assertDenied { sent.delete().await() }
    }
  }

  /** Sends a message as [participant], which also shows their access works. */
  private suspend fun sentByParticipant(): DocumentReference {
    signIn(participant)
    return newMessage().also { it.set(message(participant, "Hello everyone")).await() }
  }

  /** [user] can neither read [sent], list its channel nor send to it. */
  private suspend fun assertNoAccess(user: Account, sent: DocumentReference) {
    signIn(user)
    assertDenied { sent.get(Source.SERVER).await() }
    assertDenied { channelQuery().get(Source.SERVER).await() }
    assertDenied { newMessage().set(message(user, "Hello everyone")).await() }
  }

  /** Creates an event of [organizer], with [participants] accepted, and returns its id. */
  private suspend fun event(participants: List<Account>): String {
    signIn(organizer)
    val ref = db.collection("events").document()
    paths += ref.path
    ref.set(mapOf("organizerId" to organizer.uid)).await()
    paths += FirestoreEmulatorAdmin.createParticipants(ref.id, participants.map { it.uid })
    return ref.id
  }

  /** Replaces the participants of the event with [participants]. */
  private suspend fun setParticipantsAsAdmin(participants: List<Account>) {
    FirestoreEmulatorAdmin.deleteDocument(participantsRef().path)
    FirestoreEmulatorAdmin.createParticipants(eventId, participants.map { it.uid })
  }

  /** Stores the membership request of [user] to [TEAM_ID] in [eventId], with [status]. */
  private suspend fun request(eventId: String, user: Account, status: String) {
    createAsAdmin(
        requestRef(eventId, user).path,
        mapOf("userId" to user.uid, "teamId" to TEAM_ID, "status" to status),
    )
  }

  private suspend fun createAsAdmin(path: String, fields: Map<String, Any>) {
    paths += path
    FirestoreEmulatorAdmin.createDocument(path, fields)
  }

  private fun participantsRef() = db.collection("eventParticipants").document(eventId)

  private fun teamRef() =
      db.collection("events").document(eventId).collection("teams").document(TEAM_ID)

  private fun requestRef(eventId: String, user: Account) =
      db.collection("events").document(eventId).collection("membershipRequests").document(user.uid)

  private fun channelRef(eventId: String = this.eventId) =
      db.collection("events").document(eventId).collection("channels").document("general")

  /** The query the channel is read with: the last messages, oldest first. */
  private fun channelQuery() =
      channelRef()
          .collection(MESSAGES)
          .orderBy("sentAt")
          .limitToLast(ChatRules.MAX_MESSAGES.toLong())

  private fun newMessage(eventId: String = this.eventId): DocumentReference =
      channelRef(eventId).collection(MESSAGES).document().also { paths += it.path }

  private fun message(sender: Account, text: String): Map<String, Any> =
      mapOf("senderId" to sender.uid, "text" to text, "sentAt" to FieldValue.serverTimestamp())

  private suspend fun account(): Account {
    val email = "general-chat-${UUID.randomUUID()}@example.test"
    val password = "test-password-123"
    val user =
        checkNotNull(
            auth
                .signInForFirestore { auth.createUserWithEmailAndPassword(email, password).await() }
                .user
        )
    return Account(user.uid, EmailAuthProvider.getCredential(email, password))
  }

  private suspend fun signIn(user: Account) {
    auth.signInForFirestore { auth.signInWithCredential(user.credential).await() }
  }

  private suspend fun assertDenied(block: suspend () -> Unit) {
    val failure = runCatching { block() }.exceptionOrNull()
    assertTrue(
        "Expected permission denial, got $failure",
        failure is FirebaseFirestoreException &&
            failure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
  }

  private data class Account(val uid: String, val credential: AuthCredential)

  private companion object {
    const val MESSAGES = "messages"
    const val TEAM_ID = "team-bar"
  }
}
