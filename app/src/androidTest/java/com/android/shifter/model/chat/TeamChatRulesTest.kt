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
 * The `firestore.rules` of `events/{eventId}/teams/{teamId}/messages/{messageId}`, written with the
 * raw SDK so they are checked on their own, whatever the repository does.
 *
 * Every denial has an allowed counterpart with the same user and channel, so a test cannot pass
 * because its setup is broken.
 */
@RunWith(AndroidJUnit4::class)
class TeamChatRulesTest {
  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore
  private val paths = mutableSetOf<String>()
  private lateinit var organizer: Account
  private lateinit var manager: Account
  private lateinit var member: Account
  private lateinit var otherTeamMember: Account
  private lateinit var otherEventMember: Account
  private lateinit var eventId: String
  private lateinit var otherEventId: String

  @Before
  fun setUp() = emulatorTest {
    organizer = account()
    manager = account()
    member = account()
    otherTeamMember = account()
    otherEventMember = account()
    eventId = event()
    createTeam(eventId, TEAM_ID, manager, member)
    createTeam(eventId, OTHER_TEAM_ID, manager = null, otherTeamMember)
    otherEventId = event()
    createTeam(otherEventId, TEAM_ID, manager = null, otherEventMember)
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
  fun send_byAMember_storesTheMessage() = emulatorTest {
    signIn(member)
    val ref = newMessage()

    ref.set(message(member, "Hello team")).await()

    val stored = ref.get(Source.SERVER).await()
    assertEquals(member.uid, stored.getString("senderId"))
    assertEquals("Hello team", stored.getString("text"))
    assertTrue(stored.get("sentAt") is Timestamp)
  }

  @Test
  fun send_byTheManager_isAllowed() = emulatorTest {
    signIn(manager)

    newMessage().set(message(manager, "Briefing at 18:00")).await()
  }

  @Test
  fun send_byTheOrganizer_isAllowed() = emulatorTest {
    signIn(organizer)

    newMessage().set(message(organizer, "Doors open soon")).await()
  }

  @Test
  fun read_byAMemberTheManagerAndTheOrganizer_listsTheChannel() = emulatorTest {
    signIn(member)
    val sent = newMessage()
    sent.set(message(member, "Hello team")).await()

    for (user in listOf(member, manager, organizer)) {
      signIn(user)
      assertEquals(
          listOf(sent.id),
          channelQuery().get(Source.SERVER).await().documents.map { it.id },
      )
      assertTrue(sent.get(Source.SERVER).await().exists())
    }
  }

  @Test
  fun access_byAMemberOfAnotherTeam_isDenied() = emulatorTest {
    val sent = sentByMember()
    signIn(otherTeamMember)
    // Their own team's channel is open to them.
    newMessage(teamId = OTHER_TEAM_ID).set(message(otherTeamMember, "Hello")).await()

    assertNoAccess(otherTeamMember, sent)
  }

  @Test
  fun access_byAMemberOfAnotherEvent_isDenied() = emulatorTest {
    val sent = sentByMember()
    signIn(otherEventMember)
    // The team with the same id in their own event is open to them.
    newMessage(eventId = otherEventId).set(message(otherEventMember, "Hello")).await()

    assertNoAccess(otherEventMember, sent)
  }

  @Test
  fun access_afterBeingRemovedFromTheTeam_isDenied() = emulatorTest {
    val sent = sentByMember()

    signIn(organizer)
    teamRef(eventId, TEAM_ID).update("memberIds", emptyList<String>()).await()

    assertNoAccess(member, sent)
  }

  @Test
  fun send_withAnotherSenderId_isDenied() = emulatorTest {
    signIn(member)

    assertDenied { newMessage().set(message(manager, "Hello team")).await() }
  }

  @Test
  fun send_withAClientChosenSentAt_isDenied() = emulatorTest {
    signIn(member)

    assertDenied {
      newMessage().set(message(member, "Hello team") + ("sentAt" to Timestamp.now())).await()
    }
  }

  @Test
  fun send_withAnEmptyText_isDenied() = emulatorTest {
    signIn(member)

    assertDenied { newMessage().set(message(member, "")).await() }
  }

  @Test
  fun send_withTheMaximumLength_isAllowed() = emulatorTest {
    signIn(member)

    newMessage().set(message(member, "a".repeat(MAX_TEXT_LENGTH))).await()
  }

  @Test
  fun send_withATooLongText_isDenied() = emulatorTest {
    signIn(member)

    assertDenied { newMessage().set(message(member, "a".repeat(MAX_TEXT_LENGTH + 1))).await() }
  }

  @Test
  fun send_withAnUntrimmedText_isDenied() = emulatorTest {
    signIn(member)

    assertDenied { newMessage().set(message(member, " Hello team ")).await() }
    assertDenied { newMessage().set(message(member, "   ")).await() }
  }

  @Test
  fun send_withANonStringText_isDenied() = emulatorTest {
    signIn(member)

    assertDenied { newMessage().set(message(member, "Hello team") + ("text" to 42)).await() }
  }

  @Test
  fun send_withAnExtraField_isDenied() = emulatorTest {
    signIn(member)

    assertDenied { newMessage().set(message(member, "Hello team") + ("pinned" to true)).await() }
  }

  @Test
  fun update_bySenderOrOrganizer_isDenied() = emulatorTest {
    val sent = sentByMember()

    for (user in listOf(member, organizer)) {
      signIn(user)
      assertDenied { sent.update("text", "Edited").await() }
    }
  }

  @Test
  fun delete_bySenderOrOrganizer_isDenied() = emulatorTest {
    val sent = sentByMember()

    for (user in listOf(member, organizer)) {
      signIn(user)
      assertDenied { sent.delete().await() }
    }
  }

  /** Sends a message as [member] to [TEAM_ID], which also shows their access works. */
  private suspend fun sentByMember(): DocumentReference {
    signIn(member)
    return newMessage().also { it.set(message(member, "Hello team")).await() }
  }

  /** [user] can neither read [sent], list its channel nor send to it. */
  private suspend fun assertNoAccess(user: Account, sent: DocumentReference) {
    signIn(user)
    assertDenied { sent.get(Source.SERVER).await() }
    assertDenied { channelQuery().get(Source.SERVER).await() }
    assertDenied { newMessage().set(message(user, "Hello team")).await() }
  }

  /** Creates an event of [organizer] and returns its id. */
  private suspend fun event(): String {
    signIn(organizer)
    val ref = db.collection("events").document()
    paths += ref.path
    ref.set(mapOf("organizerId" to organizer.uid)).await()
    return ref.id
  }

  private suspend fun createTeam(
      eventId: String,
      teamId: String,
      manager: Account?,
      vararg members: Account,
  ) {
    signIn(organizer)
    val ref = teamRef(eventId, teamId)
    paths += ref.path
    ref.set(mapOf("managerId" to manager?.uid, "memberIds" to members.map { it.uid })).await()
  }

  private fun teamRef(eventId: String, teamId: String) =
      db.collection("events").document(eventId).collection("teams").document(teamId)

  /** The query the channel is read with: the last messages, oldest first. */
  private fun channelQuery() =
      teamRef(eventId, TEAM_ID).collection(MESSAGES).orderBy("sentAt").limitToLast(100)

  private fun newMessage(
      eventId: String = this.eventId,
      teamId: String = TEAM_ID,
  ): DocumentReference =
      teamRef(eventId, teamId).collection(MESSAGES).document().also { paths += it.path }

  private fun message(sender: Account, text: String): Map<String, Any> =
      mapOf("senderId" to sender.uid, "text" to text, "sentAt" to FieldValue.serverTimestamp())

  private suspend fun account(): Account {
    val email = "team-chat-${UUID.randomUUID()}@example.test"
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
    const val OTHER_TEAM_ID = "team-logistics"
    const val MAX_TEXT_LENGTH = 1000
  }
}
