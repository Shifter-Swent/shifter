// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.firebase.emulatorTest
import com.swent.shifter.firebase.signInForFirestore
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mapping tests for [toInboxNotification] and the writing side [InboxDocs], run against the
 * Firestore emulator with the real rules.
 *
 * The rules only accept well-formed notifications, so the documents a newer app or a malformed
 * write would leave are written with [FirestoreEmulatorAdmin]; the recipient then reads them, as
 * only they may.
 */
@RunWith(AndroidJUnit4::class)
class InboxNotificationFirestoreMapperTest {
  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore
  private val paths = mutableSetOf<String>()
  private lateinit var sender: Account
  private lateinit var recipient: Account

  @Before
  fun setUp() = emulatorTest {
    sender = account()
    recipient = account()
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
  fun writtenWithInboxDocs_isReadBackByTheRecipientOnly() = emulatorTest {
    val data =
        mapOf(
            NotificationData.Key.CHANGE to NotificationData.Value.JOINED,
            NotificationData.Key.USER_NAME to "Alice",
            NotificationData.Key.TEAM_ID to "team-bar",
            NotificationData.Key.TEAM_NAME to "Bar",
        )
    signIn(sender)
    val before = Instant.now()
    val batch = db.batch()
    // The sender and a duplicate among the recipients, as a writer may pass them: the batch would
    // be refused if either were written.
    InboxDocs.documents(
            db,
            listOf(recipient.uid, sender.uid, recipient.uid),
            NotificationKind.TEAM_CHANGED,
            "event-1",
            "Festival",
            sender.uid,
            data,
        )
        .forEach { (doc, fields) ->
          paths += doc.path
          batch.set(doc, fields)
        }
    batch.commit().await()

    assertTrue(InboxDocs.inbox(db, sender.uid).get(Source.SERVER).await().isEmpty)
    signIn(recipient)
    val inbox = InboxDocs.inbox(db, recipient.uid).get(Source.SERVER).await().documents
    val read = inbox.single().read()

    assertEquals(
        InboxNotification(
            inbox.single().id,
            NotificationKind.TEAM_CHANGED,
            "event-1",
            "Festival",
            sender.uid,
            data,
            read.createdAt,
        ),
        read,
    )
    // Set by the server: never before the write started, give or take the clocks' skew.
    assertTrue(read.createdAt.isAfter(before.minusSeconds(60)))
  }

  @Test
  fun anUnknownKind_isSkipped() = emulatorTest {
    assertNull(storeAndRead(valid() + ("kind" to "SHIFT_SWAPPED")).toInboxNotification())
  }

  @Test
  fun aKindComputedOnThePhone_isSkipped() = emulatorTest {
    for (kind in NotificationKind.entries.filterNot { it.inInbox }) {
      assertNull(storeAndRead(valid() + ("kind" to kind.name)).toInboxNotification())
    }
  }

  @Test
  fun anUnknownDataKey_isLeftOut() = emulatorTest {
    val stored =
        storeAndRead(valid() + ("data" to mapOf("userName" to "Alice", "avatar" to "a.png")))

    assertEquals(mapOf("userName" to "Alice"), stored.read().data)
  }

  @Test
  fun aDataValueThatIsNotAString_fails() = emulatorTest {
    assertFailsNaming(
        storeAndRead(valid() + ("data" to mapOf("userName" to 1L))),
        "'data.userName'",
    )
  }

  @Test
  fun aDataThatIsNotAMap_fails() = emulatorTest {
    assertFailsNaming(storeAndRead(valid() + ("data" to "Alice")), "'data'")
  }

  @Test
  fun aMissingField_fails() = emulatorTest {
    for (field in listOf("kind", "eventId", "eventTitle", "senderId", "data", "createdAt")) {
      assertFailsNaming(storeAndRead(valid() - field), "'$field'")
    }
  }

  private fun DocumentSnapshot.read(): InboxNotification = checkNotNull(toInboxNotification())

  /**
   * Asserts that mapping [snapshot] fails with an [IllegalStateException] whose message contains
   * [reason], so a test cannot pass because of another malformed field than the one it breaks.
   */
  private fun assertFailsNaming(snapshot: DocumentSnapshot, reason: String) {
    val failure = runCatching { snapshot.toInboxNotification() }.exceptionOrNull()

    assertTrue(
        "a malformed document must fail with IllegalStateException, but was: $failure",
        failure is IllegalStateException,
    )
    assertTrue(
        "the failure must say $reason, but said: ${failure?.message}",
        failure?.message?.contains(reason) == true,
    )
  }

  private fun valid(): Map<String, Any> =
      mapOf(
          "kind" to NotificationKind.NEW_APPLICATION.name,
          "eventId" to "event-1",
          "eventTitle" to "Festival",
          "senderId" to sender.uid,
          "data" to mapOf("userName" to "Alice"),
          "createdAt" to CREATED_AT,
      )

  /** Writes [fields] as a new document of the recipient's inbox, and reads it as the recipient. */
  private suspend fun storeAndRead(fields: Map<String, Any>): DocumentSnapshot {
    val path = "users/${recipient.uid}/notifications/${UUID.randomUUID()}".also { paths += it }
    FirestoreEmulatorAdmin.createDocument(path, fields)
    signIn(recipient)
    return db.document(path).get(Source.SERVER).await()
  }

  private suspend fun account(): Account {
    val email = "inbox-mapper-${UUID.randomUUID()}@example.test"
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
    val CREATED_AT: Instant = Instant.parse("2026-11-21T18:30:00Z")
  }
}
