// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.DocumentReference
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.model.event.EventSchema
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
 * Integration tests for [FirestoreMissionRepository.updateAssignees], running the real Firestore
 * SDK against the Firestore emulator through [FirestoreEmulator].
 *
 * Kept apart from [FirestoreMissionRepositoryTest] so assignment has its own suite. Every test
 * builds its mission under a fresh random event id, and every document created is deleted in
 * [tearDown].
 */
@RunWith(AndroidJUnit4::class)
class FirestoreMissionAssigneesTest {

  private val auth = FirestoreEmulator.auth
  private val firestore = FirestoreEmulator.firestore
  private val repository = FirestoreMissionRepository(firestore)

  /** The documents this test created, deleted in [tearDown]. */
  private val createdDocuments = mutableListOf<DocumentReference>()

  /** `firestore.rules` only lets signed-in users touch missions. */
  @Before fun signIn() = emulatorTest { auth.signInAnonymously().await() }

  @After
  fun tearDown() = emulatorTest {
    createdDocuments.forEach { it.delete().await() }
    createdDocuments.clear()
    auth.signOut()
  }

  @Test
  fun updateAssignees_replacesTheAssigneesInTheGivenOrder() = emulatorTest {
    val created = create(mission(volunteersNeeded = 3, "user-1"))

    repository.updateAssignees(created.eventId, created.id, listOf("user-3", "user-2"))

    assertEquals(listOf("user-3", "user-2"), stored(created).assigneeIds)
  }

  @Test
  fun updateAssignees_leavesTheOtherFieldsUntouched() = emulatorTest {
    val created = create(mission(volunteersNeeded = 2, "user-1"))

    repository.updateAssignees(created.eventId, created.id, listOf("user-2"))

    assertEquals(created.copy(assigneeIds = listOf("user-2")), stored(created))
  }

  @Test
  fun updateAssignees_withAnEmptyList_leavesNobodyAssigned() = emulatorTest {
    val created = create(mission(volunteersNeeded = 2, "user-1", "user-2"))

    repository.updateAssignees(created.eventId, created.id, emptyList())

    assertEquals(emptyList<String>(), stored(created).assigneeIds)
  }

  @Test
  fun updateAssignees_acceptsExactlyAsManyPeopleAsNeeded() = emulatorTest {
    val created = create(mission(volunteersNeeded = 2))

    repository.updateAssignees(created.eventId, created.id, listOf("user-1", "user-2"))

    assertEquals(listOf("user-1", "user-2"), stored(created).assigneeIds)
  }

  @Test
  fun updateAssignees_refusesMorePeopleThanNeededAndWritesNothing() = emulatorTest {
    val created = create(mission(volunteersNeeded = 1, "user-1"))

    val failure = runCatching {
      repository.updateAssignees(created.eventId, created.id, listOf("user-1", "user-2"))
    }
        .exceptionOrNull()

    assertTrue(
        "expected IllegalArgumentException, but was: $failure",
        failure is IllegalArgumentException,
    )
    assertEquals(listOf("user-1"), stored(created).assigneeIds)
  }

  @Test
  fun updateAssignees_refusesAnAssigneeListedTwiceAndWritesNothing() = emulatorTest {
    val created = create(mission(volunteersNeeded = 3))

    val failure = runCatching {
      repository.updateAssignees(created.eventId, created.id, listOf("user-1", "user-1"))
    }
        .exceptionOrNull()

    assertTrue(
        "expected IllegalArgumentException, but was: $failure",
        failure is IllegalArgumentException,
    )
    assertEquals(emptyList<String>(), stored(created).assigneeIds)
  }

  @Test
  fun updateAssignees_failsOnAMissingMission() = emulatorTest {
    val failure = runCatching {
      repository.updateAssignees(uniqueEventId(), "missing", listOf("user-1"))
    }
        .exceptionOrNull()

    assertTrue(
        "a missing mission must fail with NotFound, but was: $failure",
        failure is MissionRepositoryException.NotFound,
    )
  }

  private suspend fun create(mission: Mission): Mission =
      repository.createMission(mission).also {
        createdDocuments +=
            firestore
                .collection(EventSchema.COLLECTION)
                .document(it.eventId)
                .collection(MissionSchema.COLLECTION)
                .document(it.id)
      }

  private suspend fun stored(mission: Mission): Mission =
      checkNotNull(repository.getMission(mission.eventId, mission.id))

  private fun uniqueEventId(): String = "event-" + UUID.randomUUID()

  /**
   * A mission of a fresh event, needing [volunteersNeeded] people and assigned to [assigneeIds].
   */
  private fun mission(volunteersNeeded: Int, vararg assigneeIds: String) =
      Mission(
          eventId = uniqueEventId(),
          title = "Trier les dons alimentaires",
          description = "Trier les produits collectés.",
          teamId = "team-logistics",
          volunteersNeeded = volunteersNeeded,
          startAt = Instant.parse("2026-06-20T12:00:00Z"),
          endAt = Instant.parse("2026-06-20T13:30:00Z"),
          assigneeIds = assigneeIds.toList(),
          createdAt = Instant.parse("2026-05-02T08:15:00Z"),
      )

  /** Fails the test instead of hanging when the emulator cannot be reached. */
  private fun emulatorTest(block: suspend () -> Unit) = runBlocking {
    withTimeout(TIMEOUT_MILLIS) { block() }
  }

  private companion object {
    const val TIMEOUT_MILLIS = 20_000L
  }
}
