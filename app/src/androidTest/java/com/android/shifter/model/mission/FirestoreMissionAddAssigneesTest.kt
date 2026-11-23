// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.firebase.emulatorTest
import com.swent.shifter.firebase.signInForFirestore
import com.swent.shifter.firebase.step
import com.swent.shifter.model.event.EventSchema
import com.swent.shifter.model.notification.InboxDocs
import com.swent.shifter.model.notification.InboxNotification
import com.swent.shifter.model.notification.MissionLine
import com.swent.shifter.model.notification.NotificationData
import com.swent.shifter.model.notification.NotificationKind
import com.swent.shifter.model.notification.toInboxNotification
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [FirestoreMissionRepository.addAssignees], running the real Firestore SDK
 * against the emulators through [FirestoreEmulator].
 *
 * The organizer and the volunteers are real accounts, since only the owner of an inbox may read it:
 * each inbox is read signed in as its owner, then the organizer signs in again. Kept apart from
 * [FirestoreMissionAssigneesTest], which covers [FirestoreMissionRepository.updateAssignees]. Every
 * document created is deleted in [cleanUp], whatever the rules say.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreMissionAddAssigneesTest {

  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore
  private val repository = FirestoreMissionRepository(db, auth)

  /** The documents this test created, deleted in [cleanUp]. */
  private val paths = mutableSetOf<String>()

  private lateinit var organizer: Account
  private lateinit var alice: Account
  private lateinit var bob: Account
  private lateinit var eventId: String

  @Before
  fun setUp() = emulatorTest {
    alice = account()
    bob = account()
    organizer = account()
    // The event holds its title, which the notifications carry.
    eventId = "event-" + UUID.randomUUID()
    val event = db.collection(EventSchema.COLLECTION).document(eventId)
    step("event") {
      event
          .set(mapOf(EventSchema.ORGANIZER_ID to organizer.uid, EventSchema.TITLE to TITLE))
          .await()
    }
    paths += event.path
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
  fun addAssignees_notifiesEachPersonOnceWithAllTheirNewMissions() = emulatorTest {
    val sunday = create(day = 21, volunteersNeeded = 3, "kept")
    val saturday = create(day = 20, volunteersNeeded = 2)

    repository.addAssignees(
        eventId,
        mapOf(
            sunday.id to listOf(alice.uid, bob.uid),
            saturday.id to listOf(alice.uid, organizer.uid),
        ),
    )

    assertEquals(listOf("kept", alice.uid, bob.uid), stored(sunday).assigneeIds)
    assertEquals(listOf(alice.uid, organizer.uid), stored(saturday).assigneeIds)
    val toAlice = inboxOf(alice).single()
    assertEquals(NotificationKind.MISSION_ASSIGNED, toAlice.kind)
    assertEquals(eventId, toAlice.eventId)
    assertEquals(TITLE, toAlice.eventTitle)
    assertEquals(organizer.uid, toAlice.senderId)
    assertEquals(NotificationData.Value.ADDED, toAlice.data[NotificationData.Key.CHANGE])
    // Earliest first, with the stored titles and times.
    assertEquals(listOf(lineOf(saturday), lineOf(sunday)), linesOf(toAlice))
    assertEquals(listOf(lineOf(sunday)), linesOf(inboxOf(bob).single()))
    // The organizer is assigned, but not told about their own action.
    assertEquals(emptyList<InboxNotification>(), inboxOf(organizer))
  }

  @Test
  fun addAssignees_repeated_changesAndNotifiesNothingMore() = emulatorTest {
    val mission = create(day = 20, volunteersNeeded = 1)
    val additions = mapOf(mission.id to listOf(alice.uid))
    repository.addAssignees(eventId, additions)

    // As after an answer lost on the way back, once the mission is full and over.
    val afterItsEnd = FirestoreMissionRepository(db, auth, clockAt(mission.endAt))
    afterItsEnd.addAssignees(eventId, additions)

    assertEquals(listOf(alice.uid), stored(mission).assigneeIds)
    assertEquals(1, inboxOf(alice).size)
  }

  @Test
  fun addAssignees_toAFullMission_writesNothing() = emulatorTest {
    val open = create(day = 20, volunteersNeeded = 2)
    val full = create(day = 21, volunteersNeeded = 1, "kept")

    val failure = failureOf(mapOf(open.id to listOf(alice.uid), full.id to listOf(alice.uid)))

    assertTrue(
        "expected AssigneesAboveVolunteersException, but was: $failure",
        failure is AssigneesAboveVolunteersException,
    )
    assertNothingWritten(open, full)
  }

  @Test
  fun addAssignees_toAMissionClosedByHand_writesNothing() = emulatorTest {
    val open = create(day = 20, volunteersNeeded = 2)
    val closed = create(day = 21, volunteersNeeded = 2)
    repository.markCompleted(eventId, closed.id)

    val failure = failureOf(mapOf(open.id to listOf(alice.uid), closed.id to listOf(alice.uid)))

    assertTrue(
        "expected MissionCompletedException, but was: $failure",
        failure is MissionCompletedException,
    )
    assertNothingWritten(open, closed)
  }

  @Test
  fun addAssignees_toAMissionPastItsEnd_writesNothing() = emulatorTest {
    val later = create(day = 21, volunteersNeeded = 2)
    val ended = create(day = 20, volunteersNeeded = 2)
    val afterItsEnd = FirestoreMissionRepository(db, auth, clockAt(ended.endAt))

    val failure = runCatching {
      afterItsEnd.addAssignees(
          eventId,
          mapOf(later.id to listOf(alice.uid), ended.id to listOf(alice.uid)),
      )
    }
        .exceptionOrNull()

    assertTrue(
        "expected MissionCompletedException, but was: $failure",
        failure is MissionCompletedException,
    )
    assertNothingWritten(later, ended)
  }

  @Test
  fun addAssignees_toAMissingMission_failsWithNotFoundAndWritesNothing() = emulatorTest {
    val mission = create(day = 20, volunteersNeeded = 2)

    val failure = failureOf(mapOf(mission.id to listOf(alice.uid), "missing" to listOf(alice.uid)))

    assertTrue(
        "expected NotFound, but was: $failure",
        failure is MissionRepositoryException.NotFound,
    )
    assertNothingWritten(mission)
  }

  @Test
  fun addAssignees_toAMissingEvent_failsWithNotFound() = emulatorTest {
    val failure = runCatching {
      repository.addAssignees(
          "event-missing-" + UUID.randomUUID(),
          mapOf("m1" to listOf(alice.uid)),
      )
    }
        .exceptionOrNull()

    assertTrue(
        "expected NotFound, but was: $failure",
        failure is MissionRepositoryException.NotFound,
    )
  }

  @Test
  fun addAssignees_signedOut_isRefused() = emulatorTest {
    val mission = create(day = 20, volunteersNeeded = 2)
    auth.signOut()

    val failure = failureOf(mapOf(mission.id to listOf(alice.uid)))

    assertTrue(
        "expected PermissionDenied, but was: $failure",
        failure is MissionRepositoryException.PermissionDenied,
    )
  }

  /**
   * More missions than the 20 document reads the rules allow one transaction: each write checks the
   * organizer on the event, which the rules must not count once per mission. With a longer timeout
   * than the default, for its some 50 round trips to the emulator.
   */
  @Test
  fun addAssignees_toTheMissionsOfAWholeEvent_isOneWrite() =
      emulatorTest(timeoutMillis = 60_000) {
        val missions = (1..21).map { create(day = it, volunteersNeeded = 1) }

        repository.addAssignees(eventId, missions.associate { it.id to listOf(alice.uid) })

        val stored = step("read") { repository.getMissionsByEvent(eventId) }
        assertEquals(
            missions.map { listOf(alice.uid) },
            stored.sortedBy { it.startAt }.map { it.assigneeIds },
        )
        assertEquals(21, linesOf(inboxOf(alice).single()).size)
      }

  private suspend fun failureOf(additions: Map<String, List<String>>): Throwable? = runCatching {
    repository.addAssignees(eventId, additions)
  }
      .exceptionOrNull()

  /** The [missions] are as created, and nobody was notified. */
  private suspend fun assertNothingWritten(vararg missions: Mission) {
    missions.forEach { assertEquals(it.assigneeIds, stored(it).assigneeIds) }
    assertEquals(emptyList<InboxNotification>(), inboxOf(alice))
  }

  /** A mission of the event on day [day] of July 2030, needing [volunteersNeeded] people. */
  private suspend fun create(day: Int, volunteersNeeded: Int, vararg assigneeIds: String): Mission {
    val start = Instant.parse("2030-07-01T10:00:00Z").plusSeconds((day - 1) * 86_400L)
    val mission =
        Mission(
            eventId = eventId,
            title = "Mission of day $day",
            description = "",
            teamId = "team-logistics",
            volunteersNeeded = volunteersNeeded,
            startAt = start,
            endAt = start.plusSeconds(5_400),
            assigneeIds = assigneeIds.toList(),
            createdAt = Instant.parse("2030-05-02T08:15:00Z"),
        )
    return step("create") { repository.createMission(mission) }
        .also { paths += "${EventSchema.COLLECTION}/$eventId/${MissionSchema.COLLECTION}/${it.id}" }
  }

  private suspend fun stored(mission: Mission): Mission =
      checkNotNull(step("read") { repository.getMission(eventId, mission.id) })

  /** The inbox of [user], read signed in as them; the organizer is signed in again after. */
  private suspend fun inboxOf(user: Account): List<InboxNotification> {
    signIn(user)
    val documents = InboxDocs.inbox(db, user.uid).get(Source.SERVER).await().documents
    paths += documents.map { it.reference.path }
    signIn(organizer)
    return documents.map { checkNotNull(it.toInboxNotification()) }
  }

  private fun lineOf(mission: Mission) =
      MissionLine(mission.id, mission.title, mission.startAt, mission.endAt)

  private fun linesOf(notification: InboxNotification): List<MissionLine> =
      MissionLine.decodeAll(notification.data.getValue(NotificationData.Key.MISSIONS))

  private fun clockAt(instant: Instant): Clock = Clock.fixed(instant, ZoneOffset.UTC)

  /** A new account, left signed in, as [setUp] creates the organizer last. */
  private suspend fun account(): Account {
    val email = "add-assignees-${UUID.randomUUID()}@example.test"
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
    const val TITLE = "Summer Festival"
  }
}
