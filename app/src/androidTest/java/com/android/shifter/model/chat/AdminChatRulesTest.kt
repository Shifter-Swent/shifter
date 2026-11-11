// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
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
 * The `firestore.rules` of `events/{eventId}/channels/admin/messages/{messageId}`, written with the
 * raw SDK so they are checked on their own, whatever the repository does. The checks of the message
 * itself are shared with the other channels and covered by `TeamChatRulesTest`; only a few are
 * repeated here, to show this channel applies them too.
 *
 * A manager is set up as the app leaves them: their team's `managerId`, and their entry in the
 * managers index, both written by the admin API. Every denial has an allowed counterpart, so a test
 * cannot pass because its setup is broken.
 */
@RunWith(AndroidJUnit4::class)
class AdminChatRulesTest {
  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore
  private val paths = mutableSetOf<String>()
  private lateinit var organizer: Account
  private lateinit var manager: Account
  private lateinit var member: Account
  private lateinit var applicant: Account
  private lateinit var outsider: Account
  private lateinit var eventId: String
  private lateinit var otherEventId: String

  @Before
  fun setUp() =
      emulatorTest(SETUP_TIMEOUT_MILLIS) {
        organizer = account()
        manager = account()
        member = account()
        applicant = account()
        outsider = account()
        eventId = event(participants = listOf(manager, member))
        // The manager leads the team of the member, as appointed among its members, and is indexed.
        createAsAdmin(
            teamRef().path,
            mapOf("managerId" to manager.uid, "memberIds" to listOf(member.uid)),
        )
        createAsAdmin(entryRef(manager).path, mapOf("teamIds" to listOf(TEAM_ID)))
        request(eventId, manager, "ACCEPTED")
        request(eventId, member, "ACCEPTED")
        request(eventId, applicant, "PENDING")
        // The outsider manages a team of another event only.
        otherEventId = event(participants = listOf(outsider))
        createAsAdmin(entryRef(outsider, otherEventId).path, mapOf("teamIds" to listOf(TEAM_ID)))
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
  fun send_byAManager_storesTheMessage() = emulatorTest {
    signIn(manager)
    val ref = newMessage()

    ref.set(message(manager, "The bar needs two more people")).await()

    val stored = ref.get(Source.SERVER).await()
    assertEquals(manager.uid, stored.getString("senderId"))
    assertEquals("The bar needs two more people", stored.getString("text"))
    assertTrue(stored.get("sentAt") is Timestamp)
  }

  @Test
  fun send_byTheOrganizer_isAllowed() = emulatorTest {
    signIn(organizer)

    newMessage().set(message(organizer, "Briefing of the managers at 17:00")).await()
  }

  @Test
  fun read_byAManagerAndTheOrganizer_listsTheChannel() = emulatorTest {
    val sent = sentByManager()

    for (user in listOf(manager, organizer)) {
      signIn(user)
      assertEquals(
          listOf(sent.id),
          channelQuery().get(Source.SERVER).await().documents.map { it.id },
      )
      assertTrue(sent.get(Source.SERVER).await().exists())
    }
  }

  @Test
  fun access_byAMemberWhoManagesNoTeam_isDeniedUntilAppointed() = emulatorTest {
    val sent = sentByManager()

    // A participant of the event, in the team, but managing none.
    assertNoAccess(member, sent)

    appoint(member)
    assertAccess(member, sent)
  }

  @Test
  fun access_afterBeingReplaced_isDenied() = emulatorTest {
    val sent = sentByManager()

    appoint(member)

    // Their team moved to the new manager in the index, in the same commit as the team.
    assertNoAccess(manager, sent)
    assertAccess(member, sent)
  }

  @Test
  fun access_withAnEntryListingNoTeam_isDenied() = emulatorTest {
    val sent = sentByManager()
    // As the index leaves someone who no longer manages anything: the document stays, empty.
    createAsAdmin(entryRef(member).path, mapOf("teamIds" to emptyList<String>()))

    assertNoAccess(member, sent)

    appoint(member)
    assertAccess(member, sent)
  }

  @Test
  fun access_byAPendingApplicant_isDeniedEvenIfIndexed() = emulatorTest {
    val sent = sentByManager()
    // Listed by the index, as if it had gone stale, but not taking part in the event.
    createAsAdmin(entryRef(applicant).path, mapOf("teamIds" to listOf(TEAM_ID)))

    assertNoAccess(applicant, sent)

    // Through the admin API, as the acceptance itself is covered by the membership rules.
    setParticipantsAsAdmin(listOf(manager, member, applicant))
    assertAccess(applicant, sent)
  }

  @Test
  fun access_bySomeoneOutsideTheEvent_isDenied() = emulatorTest {
    val sent = sentByManager()
    signIn(outsider)
    // The admin channel of the event where they manage a team is open to them.
    newMessage(eventId = otherEventId).set(message(outsider, "Hello")).await()

    assertNoAccess(outsider, sent)
  }

  @Test
  fun access_afterWithdrawing_isDenied() = emulatorTest {
    val sent = sentByManager()

    // As the app withdraws a manager: out of the participants, the management of their team and
    // the index, and the request deleted, in one commit.
    signIn(manager)
    db.batch()
        .update(participantsRef(), "participantIds", FieldValue.arrayRemove(manager.uid))
        .update(teamRef(), "managerId", null)
        .set(entryRef(manager), removing(TEAM_ID), SetOptions.merge())
        .delete(requestRef(eventId, manager))
        .commit()
        .await()

    assertNoAccess(manager, sent)
  }

  @Test
  fun access_whenStillIndexedAfterLeavingTheEvent_isDenied() = emulatorTest {
    val sent = sentByManager()

    // The index is not trusted alone: someone who left the event is denied, though their entry
    // still lists the team. A deleted team's manager stays a participant, so #398 must clean it.
    setParticipantsAsAdmin(listOf(member))

    assertNoAccess(manager, sent)
  }

  @Test
  fun send_asAnAnnouncementByTheOrganizer_isReadAsAnAnnouncement() = emulatorTest {
    signIn(organizer)
    val ref = newMessage()

    ref.set(message(organizer, "Doors open at 18:00") + ("announcement" to true)).await()

    assertEquals(true, ref.get(Source.SERVER).await().getBoolean("announcement"))
  }

  @Test
  fun send_asAnAnnouncementByAManager_isDenied() = emulatorTest {
    // A normal message of the same manager goes through.
    sentByManager()

    // Though they announce in their team's channel.
    assertDenied {
      newMessage().set(message(manager, "Briefing at 18:00") + ("announcement" to true)).await()
    }
  }

  @Test
  fun send_withAnotherSenderId_isDenied() = emulatorTest {
    signIn(manager)

    assertDenied { newMessage().set(message(organizer, "Hello managers")).await() }
  }

  @Test
  fun updateAndDelete_bySenderOrOrganizer_areDenied() = emulatorTest {
    val sent = sentByManager()

    for (user in listOf(manager, organizer)) {
      signIn(user)
      assertDenied { sent.update("text", "Edited").await() }
      assertDenied { sent.delete().await() }
    }
  }

  /**
   * Appoints [newManager] as the app does, as the organizer: the team's manager and members, and
   * the team moved from the previous manager's entry to theirs, in one commit.
   */
  private suspend fun appoint(newManager: Account) {
    signIn(organizer)
    db.batch()
        .update(
            teamRef(),
            mapOf("managerId" to newManager.uid, "memberIds" to listOf(manager.uid)),
        )
        .set(entryRef(manager), removing(TEAM_ID), SetOptions.merge())
        .set(entryRef(newManager), adding(TEAM_ID), SetOptions.merge())
        .commit()
        .await()
  }

  /** Sends a message as [manager], which also shows their access works. */
  private suspend fun sentByManager(): DocumentReference {
    signIn(manager)
    return newMessage().also { it.set(message(manager, "Hello managers")).await() }
  }

  /** [user] can read [sent], list its channel and send to it. */
  private suspend fun assertAccess(user: Account, sent: DocumentReference) {
    signIn(user)
    assertTrue(sent.get(Source.SERVER).await().exists())
    channelQuery().get(Source.SERVER).await()
    newMessage().set(message(user, "Hello managers")).await()
  }

  /** [user] can neither read [sent], list its channel nor send to it. */
  private suspend fun assertNoAccess(user: Account, sent: DocumentReference) {
    signIn(user)
    assertDenied { sent.get(Source.SERVER).await() }
    assertDenied { channelQuery().get(Source.SERVER).await() }
    assertDenied { newMessage().set(message(user, "Hello managers")).await() }
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

  /** Creates the document at [path] with [fields], whatever the rules say about it. */
  private suspend fun createAsAdmin(path: String, fields: Map<String, Any>) {
    paths += path
    FirestoreEmulatorAdmin.createDocument(path, fields)
  }

  private fun participantsRef() = db.collection("eventParticipants").document(eventId)

  private fun teamRef() =
      db.collection("events").document(eventId).collection("teams").document(TEAM_ID)

  /** The entry of [user] in the managers index of [eventId], remembered for cleanup. */
  private fun entryRef(user: Account, eventId: String = this.eventId): DocumentReference =
      db.collection("events").document(eventId).collection("managers").document(user.uid).also {
        paths += it.path
      }

  private fun requestRef(eventId: String, user: Account) =
      db.collection("events").document(eventId).collection("membershipRequests").document(user.uid)

  private fun channelRef(eventId: String = this.eventId) =
      db.collection("events").document(eventId).collection("channels").document("admin")

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

  private fun adding(teamId: String) = mapOf("teamIds" to FieldValue.arrayUnion(teamId))

  private fun removing(teamId: String) = mapOf("teamIds" to FieldValue.arrayRemove(teamId))

  private suspend fun account(): Account {
    val email = "admin-chat-${UUID.randomUUID()}@example.test"
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

    /**
     * Room for the five accounts, two events and six documents of [setUp], which a slow emulator
     * cannot always create within the default budget of an emulator test.
     */
    const val SETUP_TIMEOUT_MILLIS = 60_000L
  }
}
