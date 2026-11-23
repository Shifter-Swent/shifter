// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

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
 * The `firestore.rules` of `users/{uid}/notifications/{id}`, written with the raw SDK so they are
 * checked on their own, whatever `InboxDocs` writes.
 *
 * Every denial has an allowed counterpart with the same user, so a test cannot pass because its
 * setup is broken.
 */
@RunWith(AndroidJUnit4::class)
class NotificationRulesTest {
  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore
  private val paths = mutableSetOf<String>()
  private lateinit var sender: Account
  private lateinit var recipient: Account
  private lateinit var outsider: Account

  @Before
  fun setUp() = emulatorTest {
    sender = account()
    recipient = account()
    outsider = account()
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
  fun create_inSomeoneElsesInboxInMyName_isStoredForEveryInboxKind() = emulatorTest {
    for (kind in NotificationKind.entries.filter { it.inInbox }) {
      signIn(sender)
      val ref = newNotification(recipient)
      ref.set(notification(kind = kind.name)).await()

      signIn(recipient)
      val stored = ref.get(Source.SERVER).await()
      assertEquals(kind.name, stored.getString("kind"))
      assertEquals(sender.uid, stored.getString("senderId"))
      assertTrue(stored.get("createdAt") is Timestamp)
    }
  }

  @Test
  fun create_withAKindComputedOnThePhoneOrUnknown_isDenied() = emulatorTest {
    signIn(sender)
    val refused = NotificationKind.entries.filterNot { it.inInbox }.map { it.name } + "UNKNOWN"

    for (kind in refused) {
      assertDenied { newNotification(recipient).set(notification(kind = kind)).await() }
    }
  }

  @Test
  fun create_inAnotherUsersName_isDenied() = emulatorTest {
    signIn(sender)

    assertDenied { newNotification(recipient).set(notification(senderId = outsider.uid)).await() }
  }

  @Test
  fun create_withACreationTimeOtherThanTheServers_isDenied() = emulatorTest {
    signIn(sender)

    assertDenied {
      newNotification(recipient).set(notification() + ("createdAt" to Timestamp.now())).await()
    }
  }

  @Test
  fun create_inMyOwnInbox_isDenied() = emulatorTest {
    signIn(recipient)

    assertDenied { newNotification(recipient).set(notification(senderId = recipient.uid)).await() }
  }

  @Test
  fun create_withAFieldOutsideTheSchema_isDenied() = emulatorTest {
    signIn(sender)

    assertDenied { newNotification(recipient).set(notification() + ("text" to "Hi")).await() }
  }

  @Test
  fun create_withAMissingField_isDenied() = emulatorTest {
    signIn(sender)

    for (field in listOf("kind", "eventId", "eventTitle", "senderId", "data", "createdAt")) {
      assertDenied { newNotification(recipient).set(notification() - field).await() }
    }
  }

  @Test
  fun create_withAFieldOfTheWrongType_isDenied() = emulatorTest {
    signIn(sender)

    for (wrong in listOf("eventId" to 1, "eventTitle" to 1, "data" to "Alice")) {
      assertDenied { newNotification(recipient).set(notification() + wrong).await() }
    }
  }

  @Test
  fun create_withAnUnknownDataKey_isDenied() = emulatorTest {
    signIn(sender)

    assertDenied {
      newNotification(recipient).set(notification(data = mapOf("title" to "Hi"))).await()
    }
  }

  @Test
  fun create_withADataValueThatIsNotAString_isDenied() = emulatorTest {
    signIn(sender)

    for (key in NotificationData.Key.ALL) {
      // Allowed as a string, refused as a number, key by key, as the rules name each key.
      newNotification(recipient).set(notification(data = mapOf(key to "value"))).await()
      assertDenied { newNotification(recipient).set(notification(data = mapOf(key to 1))).await() }
    }
  }

  @Test
  fun read_byTheOwnerOnly() = emulatorTest {
    val sent = sentToRecipient()

    signIn(recipient)
    assertEquals(
        listOf(sent.id),
        inbox(recipient).get(Source.SERVER).await().documents.map { it.id },
    )

    for (user in listOf(sender, outsider)) {
      signIn(user)
      assertDenied { sent.get(Source.SERVER).await() }
      assertDenied { inbox(recipient).get(Source.SERVER).await() }
    }
  }

  @Test
  fun delete_byTheOwnerOnly() = emulatorTest {
    val sent = sentToRecipient()

    for (user in listOf(sender, outsider)) {
      signIn(user)
      assertDenied { sent.delete().await() }
    }

    signIn(recipient)
    sent.delete().await()
    assertTrue(inbox(recipient).get(Source.SERVER).await().isEmpty)
  }

  @Test
  fun update_isDeniedToEveryone() = emulatorTest {
    val sent = sentToRecipient()

    for (user in listOf(sender, recipient)) {
      signIn(user)
      assertDenied { sent.update("eventTitle", "Changed").await() }
    }
  }

  private suspend fun sentToRecipient(): DocumentReference {
    signIn(sender)
    return newNotification(recipient).also { it.set(notification()).await() }
  }

  private fun inbox(user: Account) =
      db.collection("users").document(user.uid).collection("notifications")

  private fun newNotification(user: Account): DocumentReference =
      inbox(user).document().also { paths += it.path }

  private fun notification(
      kind: String = NotificationKind.NEW_APPLICATION.name,
      senderId: String = sender.uid,
      data: Map<String, Any> = mapOf(NotificationData.Key.USER_NAME to "Alice"),
  ): Map<String, Any> =
      mapOf(
          "kind" to kind,
          "eventId" to "event-1",
          "eventTitle" to "Festival",
          "senderId" to senderId,
          "data" to data,
          "createdAt" to FieldValue.serverTimestamp(),
      )

  private suspend fun account(): Account {
    val email = "notifications-${UUID.randomUUID()}@example.test"
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
}
