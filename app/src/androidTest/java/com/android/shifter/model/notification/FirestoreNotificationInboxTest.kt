// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.firebase.emulatorTest
import com.swent.shifter.firebase.signInForFirestore
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [FirestoreNotificationInbox] against the Firestore emulator and the real rules: the owner reads,
 * follows and empties their inbox, and nobody else's. The notifications are written with
 * [FirestoreEmulatorAdmin], each with its own creation time, so their order is known and nobody has
 * to change accounts while a listener runs. What the rules let writers store is checked by
 * `NotificationRulesTest`.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreNotificationInboxTest {
  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore
  private val inbox = FirestoreNotificationInbox(db)
  private val paths = mutableSetOf<String>()
  private lateinit var owner: Account
  private lateinit var other: Account

  @Before
  fun setUp() = emulatorTest {
    owner = account()
    other = account()
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
  fun pending_returnsMyNotificationsOldestFirst() = emulatorTest {
    val second = store(at = T0.plusSeconds(60))
    val first = store(at = T0)
    val third = store(at = T0.plusSeconds(120))
    signIn(owner)

    assertEquals(listOf(first, second, third), inbox.pending(owner.uid).map { it.id })
  }

  @Test
  fun pending_skipsWhatThisAppCannotRead_andLeavesIt() = emulatorTest {
    val known = store(at = T0)
    val unknown = store(at = T0.plusSeconds(60), kind = "SHIFT_SWAPPED")
    // Without its sender: the rules refuse it, so only a write outside the app could leave it.
    val malformed = store(at = T0.plusSeconds(120), withSender = false)
    signIn(owner)

    val pending = inbox.pending(owner.uid)

    assertEquals(
        InboxNotification(
            known,
            NotificationKind.NEW_APPLICATION,
            "event-1",
            "Festival",
            other.uid,
            mapOf(NotificationData.Key.USER_NAME to "Alice"),
            T0,
        ),
        pending.single(),
    )
    assertTrue(FirestoreEmulatorAdmin.documentExists(pathOf(unknown)))
    assertTrue(FirestoreEmulatorAdmin.documentExists(pathOf(malformed)))
  }

  @Test
  fun observePending_emitsTheListThenEachChange() = emulatorTest {
    val first = store(at = T0)
    signIn(owner)
    coroutineScope {
      val seen = Channel<List<String>>(Channel.UNLIMITED)
      val job = launch {
        inbox.observePending(owner.uid).collect { seen.send(it.map { n -> n.id }) }
      }

      seen.awaitList(listOf(first))
      val second = store(at = T0.plusSeconds(60))
      seen.awaitList(listOf(first, second))
      inbox.remove(owner.uid, listOf(first))
      seen.awaitList(listOf(second))

      job.cancel()
    }
  }

  @Test
  fun remove_emptiesMyInbox_andIgnoresAnIdAlreadyGone() = emulatorTest {
    val first = store(at = T0)
    val second = store(at = T0.plusSeconds(60))
    signIn(owner)

    inbox.remove(owner.uid, listOf(first, second, "already-gone"))

    assertEquals(emptyList<InboxNotification>(), inbox.pending(owner.uid))
    awaitDeletedOnTheServer(first, second)
  }

  @Test
  fun remove_offline_returnsAtOnceAndReachesTheServerOnceOnline() = emulatorTest {
    val id = store(at = T0)
    signIn(owner)
    inbox.pending(owner.uid)
    db.disableNetwork().await()
    try {
      withTimeout(OFFLINE_TIMEOUT_MILLIS) { inbox.remove(owner.uid, listOf(id)) }

      assertEquals(emptyList<InboxNotification>(), inbox.pending(owner.uid))
      assertTrue(FirestoreEmulatorAdmin.documentExists(pathOf(id)))
    } finally {
      db.enableNetwork().await()
    }
    awaitDeletedOnTheServer(id)
  }

  @Test
  fun pending_ofSomeoneElsesInbox_isNotAllowed() = emulatorTest {
    store(at = T0)
    signIn(other)

    val thrown = runCatching { inbox.pending(owner.uid) }.exceptionOrNull()

    assertTrue("Got $thrown", thrown is NotificationInboxException.NotAllowed)
  }

  @Test
  fun observePending_ofSomeoneElsesInbox_endsNotAllowed() = emulatorTest {
    store(at = T0)
    signIn(other)

    val thrown = runCatching { inbox.observePending(owner.uid).first() }.exceptionOrNull()

    assertTrue("Got $thrown", thrown is NotificationInboxException.NotAllowed)
  }

  @Test
  fun remove_ofSomeoneElsesInbox_doesNotThrow() = emulatorTest {
    val id = store(at = T0)
    signIn(other)

    // Recorded on the phone without asking the server, so it does not throw.
    inbox.remove(owner.uid, listOf(id))
    // Done once the server has refused the delete, which it reports as a failure.
    runCatching { db.waitForPendingWrites().await() }

    // The refusal itself is checked by NotificationRulesTest.
    assertTrue(FirestoreEmulatorAdmin.documentExists(pathOf(id)))
  }

  /**
   * Stores a notification of [other] in the owner's inbox, created at [at], as the rules would
   * store it, or without its sender unless [withSender], and returns its id.
   */
  private suspend fun store(
      at: Instant,
      kind: String = NotificationKind.NEW_APPLICATION.name,
      withSender: Boolean = true,
  ): String {
    val id = UUID.randomUUID().toString()
    val fields =
        mapOf(
            InboxSchema.KIND to kind,
            InboxSchema.EVENT_ID to "event-1",
            InboxSchema.EVENT_TITLE to "Festival",
            InboxSchema.SENDER_ID to other.uid,
            InboxSchema.DATA to mapOf(NotificationData.Key.USER_NAME to "Alice"),
            InboxSchema.CREATED_AT to at,
        )
    FirestoreEmulatorAdmin.createDocument(
        pathOf(id).also { paths += it },
        if (withSender) fields else fields - InboxSchema.SENDER_ID,
    )
    return id
  }

  private fun pathOf(id: String) = "users/${owner.uid}/notifications/$id"

  /** Receives lists until [expected] comes, as the first ones may predate the last change. */
  private suspend fun Channel<List<String>>.awaitList(expected: List<String>) {
    withTimeout(LISTENER_TIMEOUT_MILLIS) {
      var last = receive()
      while (last != expected) last = receive()
    }
  }

  /** Waits for the server to apply the deletes [remove] sent without waiting, then checks them. */
  private suspend fun awaitDeletedOnTheServer(vararg ids: String) {
    db.waitForPendingWrites().await()
    ids.forEach { assertFalse(FirestoreEmulatorAdmin.documentExists(pathOf(it))) }
  }

  private suspend fun account(): Account {
    val email = "inbox-${UUID.randomUUID()}@example.test"
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

  private data class Account(val uid: String, val credential: AuthCredential)

  private companion object {
    val T0: Instant = Instant.parse("2026-11-23T09:00:00Z")
    const val LISTENER_TIMEOUT_MILLIS = 10_000L
    const val OFFLINE_TIMEOUT_MILLIS = 2_000L
  }
}
