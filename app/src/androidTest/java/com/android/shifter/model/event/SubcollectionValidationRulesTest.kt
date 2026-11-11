// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.event

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestoreException
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.firebase.ScratchEvents
import com.swent.shifter.firebase.emulatorTest
import com.swent.shifter.firebase.signInForFirestore
import com.swent.shifter.firebase.step
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Field validation of the `teams`, `shifts` and `missions` rules, run against the Firestore
 * emulator as the event's organizer, who may write all three: a document with the schema's fields
 * and types is accepted, and an unknown field, a wrong type or a time slot ending before it starts
 * is refused.
 */
@RunWith(AndroidJUnit4::class)
class SubcollectionValidationRulesTest {

  private val auth = FirestoreEmulator.auth

  /** Paths deleted in [cleanUp] through the emulator's admin API, whatever the rules allow. */
  private val paths = mutableListOf<String>()

  private lateinit var event: DocumentReference

  @Before
  fun setUp() = emulatorTest {
    auth.signInForFirestore { auth.signInAnonymously().await() }
    event = ScratchEvents.create()
    paths += event.path
  }

  @After
  fun cleanUp() = emulatorTest {
    try {
      step("cleanup") { paths.asReversed().forEach { FirestoreEmulatorAdmin.deleteDocument(it) } }
    } finally {
      auth.signOut()
    }
  }

  @Test
  fun writeTeam_checksItsFields() = emulatorTest {
    newDocument("teams").set(TEAM).await()
    newDocument("teams").set(TEAM + ("managerId" to null) + ("volunteersNeeded" to null)).await()
    // Teams written before #164 still hold a check-in zone, and must stay writable.
    newDocument("teams").set(TEAM + ("checkInZone" to mapOf("radiusMeters" to 75.0))).await()

    assertDenied { newDocument("teams").set(TEAM + ("poles" to listOf("bar"))).await() }
    assertDenied { newDocument("teams").set(TEAM + ("name" to 42)).await() }
    assertDenied { newDocument("teams").set(TEAM + ("memberIds" to "user-1")).await() }
    assertDenied { newDocument("teams").set(TEAM + ("volunteersNeeded" to 2.5)).await() }
  }

  @Test
  fun writeShift_checksItsFieldsAndItsSlot() = emulatorTest {
    newDocument("shifts").set(SHIFT).await()

    assertDenied { newDocument("shifts").set(SHIFT + ("name" to "Morning")).await() }
    assertDenied { newDocument("shifts").set(SHIFT + ("startAt" to "2026-07-21")).await() }
    assertDenied { newDocument("shifts").set(SHIFT + ("endAt" to START)).await() }
  }

  @Test
  fun writeMission_checksItsFieldsItsSlotAndItsLocation() = emulatorTest {
    newDocument("missions").set(MISSION).await()
    newDocument("missions").set(MISSION + ("teamId" to null) + ("location" to null)).await()

    assertDenied { newDocument("missions").set(MISSION + ("name" to "Bar")).await() }
    assertDenied { newDocument("missions").set(MISSION + ("title" to 42)).await() }
    assertDenied { newDocument("missions").set(MISSION + ("volunteersNeeded" to 0)).await() }
    assertDenied { newDocument("missions").set(MISSION + ("endAt" to START)).await() }
    assertDenied {
      newDocument("missions").set(MISSION + ("location" to mapOf("address" to "Nyon"))).await()
    }
    assertDenied {
      newDocument("missions").set(MISSION + ("location" to LOCATION + ("latitude" to 91.0))).await()
    }
  }

  @Test
  fun updateMission_withAWrongType_isDenied() = emulatorTest {
    val mission = newDocument("missions").also { it.set(MISSION).await() }

    mission.update("title", "Welcome desk").await()
    assertDenied { mission.update("volunteersNeeded", "two").await() }
    assertDenied { mission.update("extra", true).await() }
  }

  private fun newDocument(subcollection: String): DocumentReference =
      event.collection(subcollection).document().also { paths += it.path }

  private suspend fun assertDenied(block: suspend () -> Unit) {
    val failure = runCatching { block() }.exceptionOrNull()
    assertTrue(
        "expected a permission denial, but was: $failure",
        (failure as? FirebaseFirestoreException)?.code ==
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
  }

  private companion object {
    val START = Timestamp(1_784_620_800, 0) // 2026-07-21T08:00:00Z
    val END = Timestamp(1_784_635_200, 0) // 2026-07-21T12:00:00Z

    val TEAM: Map<String, Any?> =
        mapOf(
            "name" to "Bar",
            "description" to "Serves the drinks",
            "icon" to "TRUCK",
            "managerId" to "user-manager",
            "memberIds" to listOf("user-1", "user-2"),
            "volunteersNeeded" to 4,
            "createdAt" to START,
        )

    val SHIFT: Map<String, Any?> =
        mapOf(
            "teamId" to "team-bar",
            "assigneeIds" to listOf("user-1"),
            "startAt" to START,
            "endAt" to END,
            "createdAt" to START,
        )

    val LOCATION: Map<String, Any?> =
        mapOf("address" to "Place du Château, Nyon", "latitude" to 46.382, "longitude" to 6.239)

    val MISSION: Map<String, Any?> =
        mapOf(
            "title" to "Bar",
            "description" to "Serve the drinks",
            "teamId" to "team-bar",
            "volunteersNeeded" to 2,
            "startAt" to START,
            "endAt" to END,
            "location" to LOCATION,
            "assigneeIds" to listOf("user-1"),
            "createdAt" to START,
            "completed" to false,
        )
  }
}
