// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.firebase.emulatorTest
import com.swent.shifter.firebase.signInForFirestore
import com.swent.shifter.firebase.step
import com.swent.shifter.model.notification.InboxDocs
import com.swent.shifter.model.notification.InboxNotification
import com.swent.shifter.model.notification.NotificationData.Key
import com.swent.shifter.model.notification.NotificationKind
import com.swent.shifter.model.notification.toInboxNotification
import java.util.UUID
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The notifications [FirestoreChatRepository.send] drops, with an announcement, in the inboxes of
 * the members of its channel (#618), against the Firestore and Auth emulators, under
 * `firestore.rules`.
 *
 * Each test has its own accounts, so an inbox holds only what that test sent, and reads it signed
 * in as its owner, the only one the rules let read it. The event, its two teams, its participants
 * and the senders' public profiles are set up whatever the rules say.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreAnnouncementNotificationsTest {

  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore
  private val repository = FirestoreChatRepository(db, auth)

  /** Every document this test created or read, deleted in [cleanUp]. */
  private val paths = mutableListOf<String>()

  /** Every account this test created, deleted in [cleanUp]. */
  private val accounts = mutableListOf<Account>()

  private lateinit var organizer: Account
  /** Manages the bar team, of [member]. */
  private lateinit var manager: Account
  private lateinit var member: Account
  /** Manages the gate team, of nobody. */
  private lateinit var otherManager: Account
  /** Takes part in the event, in no team. */
  private lateinit var participant: Account
  /** Takes no part in the event. */
  private lateinit var outsider: Account
  private lateinit var eventId: String

  @Before
  fun setUp() = emulatorTest {
    organizer = account()
    manager = account()
    member = account()
    otherManager = account()
    participant = account()
    outsider = account()
    eventId = "event-" + UUID.randomUUID()
    create("events/$eventId", mapOf("organizerId" to organizer.uid, "title" to EVENT_TITLE))
    create(
        "events/$eventId/teams/$BAR",
        mapOf("name" to "Bar", "managerId" to manager.uid, "memberIds" to listOf(member.uid)),
    )
    create("events/$eventId/teams/gate", mapOf("name" to "Gate", "managerId" to otherManager.uid))
    participants(listOf(manager.uid, member.uid, otherManager.uid, participant.uid))
    create("publicProfiles/${organizer.uid}", mapOf("displayName" to "Olivia"))
    create("publicProfiles/${manager.uid}", mapOf("displayName" to "Max"))
  }

  @After
  fun cleanUp() = emulatorTest {
    try {
      val messages = CHANNELS.flatMap { messagesIn(it) }
      (messages + paths).toSet().forEach { FirestoreEmulatorAdmin.deleteDocument(it) }
    } finally {
      try {
        for (account in accounts) {
          signIn(account)
          checkNotNull(auth.currentUser).delete().await()
        }
      } finally {
        auth.signOut()
      }
    }
  }

  @Test
  fun send_anAnnouncementInTheGeneralChannel_notifiesEachParticipant() = emulatorTest {
    signIn(organizer)

    repository.send(ChatChannelId.General(eventId), "Gates open at 18:00.", announcement = true)

    val received = inboxOf(participant).single()
    assertEquals(NotificationKind.ANNOUNCEMENT, received.kind)
    assertEquals(eventId, received.eventId)
    assertEquals(EVENT_TITLE, received.eventTitle)
    assertEquals(organizer.uid, received.senderId)
    assertEquals(
        mapOf(
            Key.CHANNEL to "GENERAL",
            Key.SENDER_NAME to "Olivia",
            Key.PREVIEW to "Gates open at 18:00.",
        ),
        received.data,
    )
    for (other in listOf(manager, member, otherManager)) {
      assertEquals(1, inboxOf(other).size)
    }
    assertTrue(inboxOf(organizer).isEmpty())
    assertTrue(inboxOf(outsider).isEmpty())
  }

  @Test
  fun send_anAnnouncementInATeamsChannel_notifiesOnlyTheTeam() = emulatorTest {
    signIn(manager)

    repository.send(ChatChannelId.Team(eventId, BAR), "Briefing at 17:00.", announcement = true)

    val received = inboxOf(member).single()
    assertEquals(manager.uid, received.senderId)
    assertEquals(
        mapOf(
            Key.CHANNEL to "TEAM",
            Key.TEAM_ID to BAR,
            Key.TEAM_NAME to "Bar",
            Key.SENDER_NAME to "Max",
            Key.PREVIEW to "Briefing at 17:00.",
        ),
        received.data,
    )
    for (other in listOf(manager, organizer, otherManager, participant)) {
      assertTrue(inboxOf(other).isEmpty())
    }
  }

  @Test
  fun send_anAnnouncementInTheAdminChannel_notifiesTheManagers() = emulatorTest {
    signIn(organizer)

    repository.send(ChatChannelId.Admin(eventId), "Meeting at 16:00.", announcement = true)

    for (each in listOf(manager, otherManager)) {
      assertEquals("ADMIN", inboxOf(each).single().data[Key.CHANNEL])
    }
    assertTrue(inboxOf(member).isEmpty())
    assertTrue(inboxOf(participant).isEmpty())
  }

  @Test
  fun send_aMessageThatIsNoAnnouncement_notifiesNobody() = emulatorTest {
    signIn(organizer)

    repository.send(ChatChannelId.General(eventId), "Hello everyone")

    for (other in listOf(manager, member, otherManager, participant)) {
      assertTrue(inboxOf(other).isEmpty())
    }
  }

  @Test
  fun send_anAnnouncementWhileOffline_failsWithNothingSent() = emulatorTest {
    signIn(organizer)

    db.disableNetwork().await()
    val failure =
        try {
          step("offline send") { runCatching { announceInGeneral() } }
        } finally {
          db.enableNetwork().await()
        }

    assertRefusedWithNothingSent(failure)
  }

  /** One person more than a batch of 500 writes can tell along with the message (#677). */
  @Test
  fun send_anAnnouncementToMorePeopleThanABatchTakes_failsWithNothingSent() = emulatorTest {
    participants(List(MAX_RECIPIENTS) { "crowd-${UUID.randomUUID()}" } + participant.uid)
    signIn(organizer)

    assertRefusedWithNothingSent(runCatching { announceInGeneral() })
  }

  private suspend fun announceInGeneral() =
      repository.send(ChatChannelId.General(eventId), "Gates open", announcement = true)

  /** Checks that [failure] is the one of an announcement refused before anything was written. */
  private suspend fun assertRefusedWithNothingSent(failure: Result<Unit>) {
    assertTrue(
        "Expected Unknown, got ${failure.exceptionOrNull()}",
        failure.exceptionOrNull() is ChatRepositoryException.Unknown,
    )
    assertTrue(messagesIn(GENERAL).isEmpty())
    assertTrue(inboxOf(participant).isEmpty())
  }

  /** Makes [userIds] the participants of the event, replacing those set up before. */
  private suspend fun participants(userIds: List<String>) {
    FirestoreEmulatorAdmin.deleteDocument("eventParticipants/$eventId")
    paths += FirestoreEmulatorAdmin.createParticipants(eventId, userIds)
  }

  /** Creates the document at [path] whatever the rules say; it is cleaned up. */
  private suspend fun create(path: String, fields: Map<String, Any?>) {
    FirestoreEmulatorAdmin.createDocument(path, fields)
    paths += path
  }

  /** The paths of the messages of [channel], read as the organizer, who may read every channel. */
  private suspend fun messagesIn(channel: String): List<String> {
    signIn(organizer)
    return db.collection("events/$eventId/$channel").get(Source.SERVER).await().documents.map {
      it.reference.path
    }
  }

  /** The inbox of [owner], read from the server signed in as them; its documents are cleaned up. */
  private suspend fun inboxOf(owner: Account): List<InboxNotification> {
    signIn(owner)
    val documents = InboxDocs.inbox(db, owner.uid).get(Source.SERVER).await().documents
    paths += documents.map { it.reference.path }
    return documents.mapNotNull { it.toInboxNotification() }
  }

  private suspend fun account(): Account {
    val email = "announcement-notifications-${UUID.randomUUID()}@example.test"
    val password = "test-password-123"
    val user =
        checkNotNull(
            auth
                .signInForFirestore { auth.createUserWithEmailAndPassword(email, password).await() }
                .user
        )
    return Account(user.uid, EmailAuthProvider.getCredential(email, password)).also {
      accounts += it
    }
  }

  private suspend fun signIn(user: Account) {
    auth.signInForFirestore { auth.signInWithCredential(user.credential).await() }
  }

  private data class Account(val uid: String, val credential: AuthCredential)

  private companion object {
    const val EVENT_TITLE = "Summer Festival"
    const val BAR = "team-bar"
    const val GENERAL = "channels/general/messages"

    /** The channels a test sends in, whose messages are cleaned up. */
    val CHANNELS = listOf("teams/$BAR/messages", GENERAL, "channels/admin/messages")

    /** The most people an action notifies (#677): a batch of 500 writes, its own included. */
    const val MAX_RECIPIENTS = 499
  }
}
