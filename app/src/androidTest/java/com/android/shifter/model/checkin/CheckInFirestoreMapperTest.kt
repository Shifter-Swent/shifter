// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.checkin

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.firebase.emulatorTest
import com.swent.shifter.firebase.signInForFirestore
import com.swent.shifter.firebase.step
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mapping tests for [toCheckIn], run against the Firestore emulator.
 *
 * The mapper reads the event and mission ids from the path, so the documents must sit at the real
 * check-in path, whose rules reject the malformed bodies these tests need: they are written with
 * [FirestoreEmulatorAdmin]. The signed-in user is the volunteer of every document, which the rules
 * let read it.
 */
@RunWith(AndroidJUnit4::class)
class CheckInFirestoreMapperTest {

  private val auth = FirestoreEmulator.auth
  private val firestore = FirestoreEmulator.firestore
  private val eventId = "event-" + UUID.randomUUID()
  private lateinit var uid: String

  private val path
    get() = "events/$eventId/missions/$MISSION_ID/checkIns/$uid"

  @Before
  fun signIn() = emulatorTest {
    uid = checkNotNull(auth.signInForFirestore { auth.signInAnonymously().await() }.user).uid
  }

  @After
  fun tearDown() = emulatorTest {
    try {
      step("cleanup") { FirestoreEmulatorAdmin.deleteDocument(path) }
    } finally {
      auth.signOut()
    }
  }

  @Test
  fun toCheckIn_mapsTheFieldsAndTheIdsOfThePath() = emulatorTest {
    val mapped = storeAndRead(valid()).toCheckIn()

    assertEquals(
        CheckIn(eventId, MISSION_ID, uid, CHECKED_IN_AT, CheckInMethod.MANAGER, MANAGER_ID),
        mapped,
    )
  }

  @Test
  fun toCheckIn_rejectsAnUnknownMethod() = emulatorTest {
    val stored = storeAndRead(valid() + ("method" to "GPS"))

    assertThrows(IllegalStateException::class.java) { stored.toCheckIn() }
  }

  @Test
  fun toCheckIn_rejectsACheckedInAtThatIsNotATimestamp() = emulatorTest {
    val stored = storeAndRead(valid() + ("checkedInAt" to "yesterday"))

    assertThrows(IllegalStateException::class.java) { stored.toCheckIn() }
  }

  @Test
  fun toCheckIn_rejectsAMissingField() = emulatorTest {
    for (field in listOf("method", "checkedInAt", "checkedInBy")) {
      FirestoreEmulatorAdmin.deleteDocument(path)
      val stored = storeAndRead(valid() - field)

      assertThrows("without $field", IllegalStateException::class.java) { stored.toCheckIn() }
    }
  }

  @Test
  fun toCheckIn_rejectsADocumentNotNestedUnderAMissionOfAnEvent() = emulatorTest {
    // Neither needs to exist: the path is checked before any field is read.
    val topLevel = firestore.collection("publicProfiles").document(uid).get(Source.SERVER).await()
    val underAnEventOnly =
        firestore
            .collection("events")
            .document(eventId)
            .collection("teams")
            .document("team")
            .get(Source.SERVER)
            .await()

    assertThrows(IllegalStateException::class.java) { topLevel.toCheckIn() }
    assertThrows(IllegalStateException::class.java) { underAnEventOnly.toCheckIn() }
  }

  private fun valid(): Map<String, Any> =
      mapOf(
          "userId" to uid,
          "method" to "MANAGER",
          "checkedInAt" to CHECKED_IN_AT,
          "checkedInBy" to MANAGER_ID,
      )

  private suspend fun storeAndRead(fields: Map<String, Any>): DocumentSnapshot {
    FirestoreEmulatorAdmin.createDocument(path, fields)
    return firestore.document(path).get(Source.SERVER).await()
  }

  private companion object {
    const val MISSION_ID = "mission-bar"
    const val MANAGER_ID = "manager-1"
    val CHECKED_IN_AT: Instant = Instant.parse("2026-07-04T18:30:00Z")
  }
}
