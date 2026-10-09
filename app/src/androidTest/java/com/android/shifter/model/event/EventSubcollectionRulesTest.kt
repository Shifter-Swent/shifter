// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.event

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestRepositoryFirestore
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Security rules of the `teams`, `shifts` and `missions` subcollections of an event, run against
 * the Firestore emulator: the organizer writes them, the organizer and the accepted participants
 * read them, and nobody else does either.
 *
 * Documents are written as raw maps: the rules do not look at their content, and the mappers are
 * covered by their own tests.
 */
@RunWith(AndroidJUnit4::class)
class EventSubcollectionRulesTest {

  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore

  /** Paths deleted in [cleanUp] through the emulator's admin API, whatever the rules allow. */
  private val paths = mutableSetOf<String>()

  private lateinit var organizer: Account
  private lateinit var participant: Account
  private lateinit var outsider: Account
  private lateinit var eventId: String

  @Before
  fun setUp() = emulatorTest {
    organizer = account()
    participant = account()
    outsider = account()
    eventId = event()
    acceptAsParticipant(participant)
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
  fun theOrganizerWritesAndReadsEverySubcollection() = emulatorTest {
    signIn(organizer)

    SUBCOLLECTIONS.forEach { subcollection ->
      val document = newDocument(subcollection)
      document.set(mapOf("name" to "created")).await()
      document.update("name", "updated").await()

      assertEquals("updated", document.get(Source.SERVER).await().getString("name"))
      assertEquals(1, collection(subcollection).get(Source.SERVER).await().size())
      document.delete().await()
    }
  }

  @Test
  fun aParticipantReadsEverySubcollectionButCannotWrite() = emulatorTest {
    val documents = SUBCOLLECTIONS.associateWith { writeAsOrganizer(it) }
    signIn(participant)

    documents.forEach { (subcollection, document) ->
      assertTrue(document.get(Source.SERVER).await().exists())
      assertEquals(1, collection(subcollection).get(Source.SERVER).await().size())
      assertDenied { document.update("name", "changed").await() }
      assertDenied { document.delete().await() }
      assertDenied { newDocument(subcollection).set(mapOf("name" to "mine")).await() }
    }
  }

  @Test
  fun anOutsiderCanNeitherReadNorWrite() = emulatorTest {
    val documents = SUBCOLLECTIONS.associateWith { writeAsOrganizer(it) }
    signIn(outsider)

    documents.forEach { (subcollection, document) ->
      assertDenied { document.get(Source.SERVER).await() }
      assertDenied { collection(subcollection).get(Source.SERVER).await() }
      assertDenied { document.update("name", "changed").await() }
      assertDenied { newDocument(subcollection).set(mapOf("name" to "mine")).await() }
    }
  }

  @Test
  fun nobodyWritesOrReadsUnderAMissingEvent() = emulatorTest {
    // Without an event there is no organizer, so not even the user writing first may claim it.
    val missingEventId = "missing-" + UUID.randomUUID()
    signIn(organizer)

    SUBCOLLECTIONS.forEach { subcollection ->
      val document = collection(subcollection, missingEventId).document()
      paths += document.path
      assertDenied { document.set(mapOf("name" to "orphan")).await() }
      assertDenied { collection(subcollection, missingEventId).get(Source.SERVER).await() }
    }
  }

  /** Creates the event, owned by [organizer]. */
  private suspend fun event(): String {
    signIn(organizer)
    val event = db.collection(EventSchema.COLLECTION).document()
    paths += event.path
    paths += "eventParticipants/${event.id}"
    event.set(mapOf(EventSchema.ORGANIZER_ID to organizer.uid)).await()
    return event.id
  }

  /** Makes [user] a participant through the real flow: they apply, the organizer accepts. */
  private suspend fun acceptAsParticipant(user: Account) {
    val requests = MembershipRequestRepositoryFirestore(db)
    signIn(user)
    paths += "${EventSchema.COLLECTION}/$eventId/membershipRequests/${user.uid}"
    requests.apply(
        eventId,
        MembershipRequest(
            userId = user.uid,
            availability = listOf(AvailabilitySlot(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600))),
            createdAt = Instant.EPOCH,
        ),
    )
    signIn(organizer)
    requests.accept(eventId, user.uid)
  }

  private suspend fun writeAsOrganizer(subcollection: String): DocumentReference {
    signIn(organizer)
    return newDocument(subcollection).also { it.set(mapOf("name" to "original")).await() }
  }

  private fun collection(subcollection: String, eventId: String = this.eventId) =
      db.collection(EventSchema.COLLECTION).document(eventId).collection(subcollection)

  private fun newDocument(subcollection: String): DocumentReference =
      collection(subcollection).document().also { paths += it.path }

  private suspend fun account(): Account {
    val email = "rules-${UUID.randomUUID()}@example.test"
    val password = "test-password-123"
    val user = checkNotNull(auth.createUserWithEmailAndPassword(email, password).await().user)
    return Account(user.uid, EmailAuthProvider.getCredential(email, password))
  }

  private suspend fun signIn(user: Account) {
    auth.signInWithCredential(user.credential).await()
  }

  private suspend fun assertDenied(block: suspend () -> Unit) {
    val failure = runCatching { block() }.exceptionOrNull()
    assertTrue(
        "expected a permission denial, but was: $failure",
        (failure as? FirebaseFirestoreException)?.code ==
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
  }

  private data class Account(val uid: String, val credential: AuthCredential)

  /** Fails the test instead of hanging when the emulator cannot be reached. */
  private fun emulatorTest(block: suspend () -> Unit) = runBlocking {
    withTimeout(TIMEOUT_MILLIS) { block() }
  }

  private companion object {
    const val TIMEOUT_MILLIS = 30_000L

    val SUBCOLLECTIONS = listOf("teams", "shifts", "missions")
  }
}
