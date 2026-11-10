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
import com.swent.shifter.firebase.emulatorTest
import com.swent.shifter.firebase.signInForFirestore
import com.swent.shifter.firebase.step
import java.util.UUID
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Security rules of `events` and `joinCodes`, run against the Firestore emulator: any signed-in
 * user reads an event or a join code by id, only the organizer lists their events, nobody lists the
 * join codes, and only an event's organizer reserves its join code, which is never taken over.
 *
 * Documents are written as raw maps with only what the rules look at.
 */
@RunWith(AndroidJUnit4::class)
class JoinCodeRulesTest {

  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore

  /** Paths deleted in [cleanUp] through the emulator's admin API, whatever the rules allow. */
  private val paths = mutableSetOf<String>()

  /** Auth accounts deleted in [cleanUp], so the emulator does not keep them between tests. */
  private val accounts = mutableListOf<Account>()

  private lateinit var organizer: Account
  private lateinit var outsider: Account

  @Before
  fun setUp() = emulatorTest {
    organizer = account()
    outsider = account()
  }

  @After
  fun cleanUp() = emulatorTest {
    try {
      step("cleanup") { paths.forEach { FirestoreEmulatorAdmin.deleteDocument(it) } }
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
  fun listEvents_onlyReturnsTheOrganizersOwnEvents() = emulatorTest {
    val code = newCode()
    val event = eventWithCode(code, reserve = true)
    val events = db.collection(EventSchema.COLLECTION)
    val ownEvents = events.whereEqualTo("organizerId", organizer.uid)

    signIn(organizer)
    assertEquals(listOf(event.id), ownEvents.get(Source.SERVER).await().documents.map { it.id })

    signIn(outsider)
    assertDenied { ownEvents.get(Source.SERVER).await() }
    assertDenied { events.whereEqualTo("joinCode", code).get(Source.SERVER).await() }
    assertTrue(event.get(Source.SERVER).await().exists())
  }

  @Test
  fun getJoinCode_asAnOutsider_returnsTheEventButListingIsDenied() = emulatorTest {
    val code = newCode()
    val event = eventWithCode(code, reserve = true)

    signIn(outsider)
    assertEquals(
        event.id,
        joinCode(code).get(Source.SERVER).await().getString(JoinCodeSchema.EVENT_ID),
    )
    assertDenied { db.collection(JoinCodeSchema.COLLECTION).get(Source.SERVER).await() }
  }

  @Test
  fun createJoinCode_onlyByTheOrganizerAndForTheEventsOwnCode() = emulatorTest {
    val code = newCode()
    val event = eventWithCode(code, reserve = false)

    signIn(outsider)
    assertDenied { joinCode(code).set(mapOf(JoinCodeSchema.EVENT_ID to event.id)).await() }

    signIn(organizer)
    assertDenied { joinCode(newCode()).set(mapOf(JoinCodeSchema.EVENT_ID to event.id)).await() }
    assertDenied {
      joinCode(code).set(mapOf(JoinCodeSchema.EVENT_ID to event.id, "extra" to true)).await()
    }
    joinCode(code).set(mapOf(JoinCodeSchema.EVENT_ID to event.id)).await()
  }

  @Test
  fun createJoinCode_alreadyTaken_isDenied() = emulatorTest {
    val code = newCode()
    eventWithCode(code, reserve = true)

    signIn(organizer)
    val second = eventWithCode(code, reserve = false)
    assertDenied { joinCode(code).set(mapOf(JoinCodeSchema.EVENT_ID to second.id)).await() }

    signIn(outsider)
    assertDenied { joinCode(code).delete().await() }
  }

  /**
   * Creates an event of [organizer] using [code], and, when [reserve] is true, its `joinCodes`
   * document in the same commit, as [FirestoreEventRepository.createEvent] does.
   */
  private suspend fun eventWithCode(code: String, reserve: Boolean): DocumentReference {
    signIn(organizer)
    val event = db.collection(EventSchema.COLLECTION).document()
    paths += event.path
    val batch = db.batch()
    batch.set(event, mapOf("organizerId" to organizer.uid, "joinCode" to code))
    if (reserve) batch.set(joinCode(code), mapOf(JoinCodeSchema.EVENT_ID to event.id))
    batch.commit().await()
    return event
  }

  private fun joinCode(code: String): DocumentReference =
      db.collection(JoinCodeSchema.COLLECTION).document(code).also { paths += it.path }

  /** A code the generator never draws, so no other test can hold it. */
  private fun newCode(): String = "test-" + UUID.randomUUID()

  private suspend fun account(): Account {
    val email = "rules-${UUID.randomUUID()}@example.test"
    val password = "test-password-123"
    // Creating the account signs it in, so Firestore switches to it too.
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

  private suspend fun assertDenied(block: suspend () -> Unit) {
    val failure = runCatching { block() }.exceptionOrNull()
    assertTrue(
        "expected a permission denial, but was: $failure",
        (failure as? FirebaseFirestoreException)?.code ==
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
  }

  private data class Account(val uid: String, val credential: AuthCredential)
}
