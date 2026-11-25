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
import com.swent.shifter.model.event.EventSchema
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.notification.InboxDocs
import com.swent.shifter.model.notification.InboxNotification
import com.swent.shifter.model.notification.MissionLine
import com.swent.shifter.model.notification.NotificationData.Key
import com.swent.shifter.model.notification.NotificationData.Value
import com.swent.shifter.model.notification.NotificationKind
import com.swent.shifter.model.notification.toInboxNotification
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The notifications [FirestoreMissionRepository.updateAssignees] and
 * [FirestoreMissionRepository.updateMission] drop in the inboxes of the volunteers concerned
 * (#617), against the Firestore and Auth emulators, under `firestore.rules`.
 *
 * Each test has its own accounts, so an inbox holds only what that test sent, and reads it signed
 * in as its owner, the only one the rules let read it. The organizer acts on an event whose
 * document is set up whatever the rules say, so it can hold a title and nothing else.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreMissionNotificationsTest {

  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore
  private val repository = FirestoreMissionRepository(db, auth)

  /** Every document this test created or read, deleted in [cleanUp]. */
  private val paths = mutableListOf<String>()

  /** Every account this test created, deleted in [cleanUp]. */
  private val accounts = mutableListOf<Account>()

  private lateinit var organizer: Account
  private lateinit var alice: Account
  private lateinit var bob: Account
  private lateinit var eventId: String

  /** Tomorrow, so no mission of this test is past its end and refused a change. */
  private val start = Instant.now().truncatedTo(ChronoUnit.SECONDS) + Duration.ofDays(1)

  private val bar = MissionLocation("Rue de Genève 12", GeoPosition(46.5, 6.6))

  @Before
  fun setUp() = emulatorTest {
    alice = account()
    bob = account()
    organizer = account()
    eventId = "event-" + UUID.randomUUID()
    val event = "${EventSchema.COLLECTION}/$eventId"
    FirestoreEmulatorAdmin.createDocument(
        event,
        mapOf(EventSchema.ORGANIZER_ID to organizer.uid, EventSchema.TITLE to "Summer Festival"),
    )
    paths += event
  }

  @After
  fun cleanUp() = emulatorTest {
    try {
      // Children first, the event last.
      paths.asReversed().forEach { FirestoreEmulatorAdmin.deleteDocument(it) }
    } finally {
      try {
        for (account in accounts) {
          signIn(account)
          checkNotNull(auth.currentUser).delete().await()
        }
      } finally {
        auth.signOut()
      }
    }
  }

  @Test
  fun updateAssignees_tellsEachNewcomerTheyWereAdded() = emulatorTest {
    val mission = create()

    repository.updateAssignees(eventId, mission.id, listOf(alice.uid, bob.uid))

    for (volunteer in listOf(alice, bob)) {
      val received = inboxOf(volunteer).single()
      assertEquals(NotificationKind.MISSION_ASSIGNED, received.kind)
      assertEquals(eventId, received.eventId)
      assertEquals("Summer Festival", received.eventTitle)
      assertEquals(organizer.uid, received.senderId)
      assertEquals(Value.ADDED, received.data[Key.CHANGE])
      assertEquals(listOf(lineOf(mission)), missionsOf(received))
    }
  }

  @Test
  fun updateAssignees_tellsOnlyWhoWasRemoved() = emulatorTest {
    val mission = create(assigneeIds = listOf(alice.uid, bob.uid))

    repository.updateAssignees(eventId, mission.id, listOf(alice.uid))

    val received = inboxOf(bob).single()
    assertEquals(NotificationKind.MISSION_ASSIGNED, received.kind)
    assertEquals(Value.REMOVED, received.data[Key.CHANGE])
    assertEquals(listOf(lineOf(mission)), missionsOf(received))
    assertTrue(inboxOf(alice).isEmpty())
  }

  @Test
  fun updateAssignees_byOneAssigningThemself_tellsOnlyTheOthers() = emulatorTest {
    val mission = create()

    repository.updateAssignees(eventId, mission.id, listOf(organizer.uid, alice.uid))

    assertTrue(inboxOf(organizer).isEmpty())
    assertEquals(Value.ADDED, inboxOf(alice).single().data[Key.CHANGE])
  }

  @Test
  fun updateAssignees_ofACompletedMission_tellsNobody() = emulatorTest {
    val mission = create(assigneeIds = listOf(alice.uid, bob.uid))
    repository.markCompleted(eventId, mission.id)

    // The organizer takes off bob, who did not come.
    repository.updateAssignees(eventId, mission.id, listOf(alice.uid))

    assertEquals(listOf(alice.uid), repository.getMission(eventId, mission.id)?.assigneeIds)
    assertTrue(inboxOf(bob).isEmpty())
  }

  @Test
  fun updateMission_withANewTime_tellsEachAssigneeTheNewTime() = emulatorTest {
    val mission = create(assigneeIds = listOf(alice.uid, bob.uid))
    val later = mission.copy(startAt = start + Duration.ofHours(1))

    repository.updateMission(later)

    for (volunteer in listOf(alice, bob)) {
      val received = inboxOf(volunteer).single()
      assertEquals(NotificationKind.MISSION_CHANGED, received.kind)
      assertEquals(Value.TIME, received.data[Key.WHAT])
      assertEquals(listOf(lineOf(later)), missionsOf(received))
    }
  }

  @Test
  fun updateMission_withANewAddress_tellsThePlaceAndTheAddress() = emulatorTest {
    val mission = create(assigneeIds = listOf(alice.uid))

    repository.updateMission(
        mission.copy(location = MissionLocation("Place du Marché 1", bar.position))
    )

    val received = inboxOf(alice).single()
    assertEquals(Value.PLACE, received.data[Key.WHAT])
    assertEquals("Place du Marché 1", received.data[Key.ADDRESS])
  }

  @Test
  fun updateMission_withOnlyANewTitle_tellsNobody() = emulatorTest {
    val mission = create(assigneeIds = listOf(alice.uid))

    repository.updateMission(mission.copy(title = "Serve drinks"))

    assertEquals("Serve drinks", repository.getMission(eventId, mission.id)?.title)
    assertTrue(inboxOf(alice).isEmpty())
  }

  @Test
  fun updates_signedOut_areRefusedBeforeAnyRequest() = emulatorTest {
    val mission = create(assigneeIds = listOf(alice.uid))
    auth.signOut()

    val assigning = runCatching { repository.updateAssignees(eventId, mission.id, emptyList()) }
    val editing = runCatching { repository.updateMission(mission.copy(title = "Serve drinks")) }

    assertTrue(assigning.exceptionOrNull() is MissionRepositoryException.PermissionDenied)
    assertTrue(editing.exceptionOrNull() is MissionRepositoryException.PermissionDenied)
  }

  /** Creates, as the organizer, the bar mission of the event, and signs the organizer in. */
  private suspend fun create(assigneeIds: List<String> = emptyList()): Mission {
    signIn(organizer)
    val created =
        repository.createMission(
            Mission(
                eventId = eventId,
                title = "Serve at the bar",
                description = "",
                volunteersNeeded = 3,
                startAt = start,
                endAt = start + Duration.ofHours(2),
                location = bar,
                assigneeIds = assigneeIds,
                createdAt = start - Duration.ofDays(7),
            )
        )
    paths += "${EventSchema.COLLECTION}/$eventId/${MissionSchema.COLLECTION}/${created.id}"
    return created
  }

  /** The inbox of [owner], read from the server signed in as them; its documents are cleaned up. */
  private suspend fun inboxOf(owner: Account): List<InboxNotification> {
    signIn(owner)
    val documents = InboxDocs.inbox(db, owner.uid).get(Source.SERVER).await().documents
    paths += documents.map { it.reference.path }
    return documents.mapNotNull { it.toInboxNotification() }
  }

  private fun missionsOf(notification: InboxNotification): List<MissionLine> =
      MissionLine.decodeAll(notification.data.getValue(Key.MISSIONS))

  private fun lineOf(mission: Mission) =
      MissionLine(mission.id, mission.title, mission.startAt, mission.endAt)

  private suspend fun account(): Account {
    val email = "mission-notifications-${UUID.randomUUID()}@example.test"
    val password = "test-password-123"
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

  private data class Account(val uid: String, val credential: AuthCredential)
}
