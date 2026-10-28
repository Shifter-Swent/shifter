// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.event

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
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
 * read them, and nobody else does either, except the teams, which any signed-in user reads to pick
 * one before applying. Only the organizer reads the event's participants, and a volunteer leaves
 * their team only by withdrawing.
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

  /** Auth accounts deleted in [cleanUp], so the emulator does not keep them between tests. */
  private val accounts = mutableListOf<Account>()

  private lateinit var organizer: Account
  private lateinit var participant: Account
  private lateinit var outsider: Account
  private lateinit var eventId: String
  private lateinit var teamId: String

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
      try {
        accounts.forEach { account ->
          signIn(account)
          checkNotNull(auth.currentUser).delete().await()
        }
      } finally {
        auth.signOut()
      }
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
      assertTrue(document.id in ids(subcollection))
      document.delete().await()
    }
  }

  @Test
  fun aParticipantReadsEverySubcollectionButCannotWrite() = emulatorTest {
    val documents = SUBCOLLECTIONS.associateWith { writeAsOrganizer(it) }
    signIn(participant)

    documents.forEach { (subcollection, document) ->
      assertTrue(document.get(Source.SERVER).await().exists())
      assertTrue(document.id in ids(subcollection))
      assertDenied { document.update("name", "changed").await() }
      assertDenied { document.delete().await() }
      assertDenied { newDocument(subcollection).set(mapOf("name" to "mine")).await() }
    }
  }

  @Test
  fun anOutsiderCanNeitherReadNorWriteShiftsAndMissions() = emulatorTest {
    val documents = PRIVATE_SUBCOLLECTIONS.associateWith { writeAsOrganizer(it) }
    signIn(outsider)

    documents.forEach { (subcollection, document) ->
      assertDenied { document.get(Source.SERVER).await() }
      assertDenied { collection(subcollection).get(Source.SERVER).await() }
      assertDenied { document.update("name", "changed").await() }
      assertDenied { newDocument(subcollection).set(mapOf("name" to "mine")).await() }
    }
  }

  @Test
  fun anOutsiderReadsTheTeamsButCannotWriteThem() = emulatorTest {
    val team = writeAsOrganizer("teams")
    signIn(outsider)

    assertTrue(team.get(Source.SERVER).await().exists())
    assertTrue(team.id in ids("teams"))
    assertDenied { team.update("name", "changed").await() }
    assertDenied { team.delete().await() }
    assertDenied { newDocument("teams").set(mapOf("name" to "mine")).await() }
  }

  @Test
  fun onlyTheOrganizerReadsTheParticipants() = emulatorTest {
    val participants = db.collection("eventParticipants").document(eventId)

    signIn(organizer)
    assertEquals(
        listOf(participant.uid),
        participants.get(Source.SERVER).await().get("participantIds"),
    )

    signIn(participant)
    assertDenied { participants.get(Source.SERVER).await() }

    signIn(outsider)
    assertDenied { participants.get(Source.SERVER).await() }
  }

  @Test
  fun aParticipantLeavesTheirTeamOnlyByWithdrawing() = emulatorTest {
    val team = collection("teams").document(teamId)

    signIn(outsider)
    assertDenied { team.update("memberIds", FieldValue.arrayRemove(participant.uid)).await() }

    signIn(participant)
    // Their request still exists, and they may not change anyone else's membership.
    assertDenied { team.update("memberIds", FieldValue.arrayRemove(participant.uid)).await() }
    assertDenied { team.update("memberIds", FieldValue.arrayUnion(outsider.uid)).await() }
    MembershipRequestRepositoryFirestore(db).withdraw(eventId, participant.uid)

    signIn(organizer)
    assertEquals(emptyList<String>(), team.get(Source.SERVER).await().get("memberIds"))
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
    }
    PRIVATE_SUBCOLLECTIONS.forEach { subcollection ->
      assertDenied { collection(subcollection, missingEventId).get(Source.SERVER).await() }
    }
  }

  /** Creates the event, owned by [organizer], with the team its volunteers apply to. */
  private suspend fun event(): String {
    signIn(organizer)
    val event = db.collection(EventSchema.COLLECTION).document()
    paths += event.path
    paths += "eventParticipants/${event.id}"
    event.set(mapOf(EventSchema.ORGANIZER_ID to organizer.uid)).await()
    val team = event.collection("teams").document()
    paths += team.path
    team.set(mapOf("memberIds" to emptyList<String>())).await()
    teamId = team.id
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
            teamId = teamId,
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

  private suspend fun ids(subcollection: String): List<String> =
      collection(subcollection).get(Source.SERVER).await().documents.map { it.id }

  private fun newDocument(subcollection: String): DocumentReference =
      collection(subcollection).document().also { paths += it.path }

  private suspend fun account(): Account {
    val email = "rules-${UUID.randomUUID()}@example.test"
    val password = "test-password-123"
    val user = checkNotNull(auth.createUserWithEmailAndPassword(email, password).await().user)
    return Account(user.uid, EmailAuthProvider.getCredential(email, password)).also {
      accounts += it
    }
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

    /** The subcollections only the organizer and the participants read. */
    val PRIVATE_SUBCOLLECTIONS = listOf("shifts", "missions")
  }
}
