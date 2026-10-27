// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.Source
import com.swent.shifter.model.event.EventSchema
import java.util.UUID
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks that a write made right after [signInForFirestore] is sent as the new user. Without it,
 * about one write in thirteen hung until the next sign-out, so the test repeats the switch.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreSignInTest {

  private val auth = FirestoreEmulator.auth
  private val events = FirestoreEmulator.firestore.collection(EventSchema.COLLECTION)
  private val createdPaths = mutableListOf<String>()

  @After
  fun tearDown() = emulatorTest {
    step("cleanup") { createdPaths.forEach { FirestoreEmulatorAdmin.deleteDocument(it) } }
    auth.signOut()
  }

  @Test
  fun signInForFirestore_sendsTheNextWriteAsTheNewUser() =
      emulatorTest(timeoutMillis = 60_000) {
        repeat(SWITCHES) {
          val uid = auth.signInForFirestore { auth.signInAnonymously().await() }.user!!.uid
          // `firestore.rules` only lets the organizer create an event, so a write queued under
          // the previous user could never succeed.
          val document = events.document(SCRATCH_ID_PREFIX + UUID.randomUUID())
          createdPaths += document.path
          step("write") { document.set(mapOf("organizerId" to uid)).await() }

          val stored = step("read") { document.get(Source.SERVER).await() }
          assertEquals(uid, stored.getString("organizerId"))
          auth.signOut()
        }
      }

  @Test
  fun signInForFirestore_returnsTheResultOfTheSignIn() = emulatorTest {
    val result = auth.signInForFirestore { auth.signInAnonymously().await() }

    assertEquals(auth.currentUser?.uid, result.user?.uid)
  }

  @Test
  fun signInForFirestore_asTheSignedInUser_returns() = emulatorTest {
    val email = "sign-in-${UUID.randomUUID()}@example.test"
    val uid =
        auth
            .signInForFirestore { auth.createUserWithEmailAndPassword(email, PASSWORD).await() }
            .user!!
            .uid

    // The user does not change, so no notification follows this sign-in.
    val again = auth.signInForFirestore { auth.signInWithEmailAndPassword(email, PASSWORD).await() }

    assertEquals(uid, again.user?.uid)
  }

  private companion object {
    const val SWITCHES = 30
    const val PASSWORD = "test-password-123"

    /** Marks the scratch documents these tests write into the events collection. */
    const val SCRATCH_ID_PREFIX = "sign-in-scratch-"
  }
}
