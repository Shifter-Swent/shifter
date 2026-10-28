// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.checkin

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The `firestore.rules` of `events/{eventId}/missions/{missionId}/checkIns/{uid}`, written with the
 * raw SDK so they are checked on their own, whatever the repository does.
 */
@RunWith(AndroidJUnit4::class)
class CheckInRulesTest {
  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore
  private val paths = mutableSetOf<String>()
  private lateinit var organizer: Account
  private lateinit var manager: Account
  private lateinit var volunteer: Account
  private lateinit var otherVolunteer: Account
  private lateinit var outsider: Account
  private lateinit var eventId: String

  @Before
  fun setUp() = emulatorTest {
    organizer = account()
    manager = account()
    volunteer = account()
    otherVolunteer = account()
    outsider = account()
    eventId = event()
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
  fun create_bySelf_withAssignedVolunteer_storesTheCheckIn() = emulatorTest {
    signIn(volunteer)

    checkInRef(volunteer).set(checkIn(volunteer, SELF, by = volunteer)).await()

    val stored = checkInRef(volunteer).get(Source.SERVER).await()
    assertEquals(volunteer.uid, stored.getString("userId"))
    assertEquals(SELF, stored.getString("method"))
    assertEquals(volunteer.uid, stored.getString("checkedInBy"))
    assertTrue(stored.get("checkedInAt") is Timestamp)
  }

  @Test
  fun create_byManagerOfTheMissionsTeam_isAllowed() = emulatorTest {
    signIn(manager)

    checkInRef(volunteer).set(checkIn(volunteer, MANAGER, by = manager)).await()

    assertTrue(checkInRef(volunteer).get(Source.SERVER).await().exists())
  }

  @Test
  fun create_byOrganizer_isAllowed() = emulatorTest {
    signIn(organizer)

    checkInRef(volunteer).set(checkIn(volunteer, MANAGER, by = organizer)).await()
    checkInRef(otherVolunteer, GENERAL_MISSION_ID)
        .set(checkIn(otherVolunteer, MANAGER, by = organizer))
        .await()

    assertTrue(checkInRef(volunteer).get(Source.SERVER).await().exists())
    assertTrue(checkInRef(otherVolunteer, GENERAL_MISSION_ID).get(Source.SERVER).await().exists())
  }

  @Test
  fun create_withUnassignedVolunteer_isDenied() = emulatorTest {
    signIn(outsider)
    assertDenied { checkInRef(outsider).set(checkIn(outsider, SELF, by = outsider)).await() }

    signIn(manager)
    assertDenied { checkInRef(outsider).set(checkIn(outsider, MANAGER, by = manager)).await() }

    signIn(organizer)
    assertDenied { checkInRef(outsider).set(checkIn(outsider, MANAGER, by = organizer)).await() }
  }

  @Test
  fun create_bySelf_forSomeoneElse_isDenied() = emulatorTest {
    signIn(otherVolunteer)

    assertDenied {
      checkInRef(volunteer).set(checkIn(volunteer, SELF, by = otherVolunteer)).await()
    }
  }

  @Test
  fun create_asManager_byNeitherTheTeamsManagerNorTheOrganizer_isDenied() = emulatorTest {
    // A volunteer of the mission cannot check a teammate in either.
    signIn(otherVolunteer)
    assertDenied {
      checkInRef(volunteer).set(checkIn(volunteer, MANAGER, by = otherVolunteer)).await()
    }

    signIn(outsider)
    assertDenied { checkInRef(volunteer).set(checkIn(volunteer, MANAGER, by = outsider)).await() }
  }

  @Test
  fun create_byManagerOfAnotherTeam_isDenied() = emulatorTest {
    val otherManager = account()
    createTeam(OTHER_TEAM_ID, otherManager)
    signIn(otherManager)

    assertDenied {
      checkInRef(volunteer).set(checkIn(volunteer, MANAGER, by = otherManager)).await()
    }
    // A mission of no team has no manager, so no team's manager may check in to it.
    signIn(manager)
    assertDenied {
      checkInRef(otherVolunteer, GENERAL_MISSION_ID)
          .set(checkIn(otherVolunteer, MANAGER, by = manager))
          .await()
    }
  }

  @Test
  fun create_toMissingMission_isDenied() = emulatorTest {
    signIn(organizer)

    assertDenied {
      checkInRef(volunteer, "missing-${UUID.randomUUID()}")
          .set(checkIn(volunteer, MANAGER, by = organizer))
          .await()
    }
  }

  @Test
  fun create_withForgedOrMalformedFields_isDenied() = emulatorTest {
    signIn(volunteer)
    val valid = checkIn(volunteer, SELF, by = volunteer)
    for (invalid in
        listOf(
            valid + ("checkedInBy" to manager.uid),
            valid + ("checkedInAt" to Timestamp(1_000, 0)),
            valid + ("userId" to otherVolunteer.uid),
            valid + ("method" to "GPS"),
            valid + ("unexpected" to true),
            valid - "userId",
            valid - "method",
            valid - "checkedInAt",
            valid - "checkedInBy",
        )) {
      assertDenied { checkInRef(volunteer).set(invalid).await() }
    }
  }

  @Test
  fun create_twice_isDeniedAndKeepsTheFirstCheckIn() = emulatorTest {
    signIn(volunteer)
    checkInRef(volunteer).set(checkIn(volunteer, SELF, by = volunteer)).await()
    val first = checkInRef(volunteer).get(Source.SERVER).await()

    assertDenied { checkInRef(volunteer).set(checkIn(volunteer, SELF, by = volunteer)).await() }
    signIn(manager)
    assertDenied { checkInRef(volunteer).set(checkIn(volunteer, MANAGER, by = manager)).await() }

    assertEquals(first.data, checkInRef(volunteer).get(Source.SERVER).await().data)
  }

  @Test
  fun updateAndDelete_areDeniedToEveryone() = emulatorTest {
    signIn(volunteer)
    checkInRef(volunteer).set(checkIn(volunteer, SELF, by = volunteer)).await()

    for (user in listOf(volunteer, manager, organizer)) {
      signIn(user)
      assertDenied { checkInRef(volunteer).update("method", MANAGER).await() }
      assertDenied { checkInRef(volunteer).delete().await() }
    }
  }

  @Test
  fun get_byTheVolunteerTheManagerOrTheOrganizer_isAllowed() = emulatorTest {
    signIn(volunteer)
    // Reading a check-in that does not exist yet is allowed too: it tells "not checked in" apart
    // from "not allowed".
    assertFalse(checkInRef(volunteer).get(Source.SERVER).await().exists())
    checkInRef(volunteer).set(checkIn(volunteer, SELF, by = volunteer)).await()

    for (user in listOf(volunteer, manager, organizer)) {
      signIn(user)
      assertTrue(checkInRef(volunteer).get(Source.SERVER).await().exists())
    }
  }

  @Test
  fun get_byAnotherVolunteerOrAnOutsider_isDenied() = emulatorTest {
    signIn(volunteer)
    checkInRef(volunteer).set(checkIn(volunteer, SELF, by = volunteer)).await()

    for (user in listOf(otherVolunteer, outsider)) {
      signIn(user)
      assertDenied { checkInRef(volunteer).get(Source.SERVER).await() }
    }
  }

  @Test
  fun list_ofAMission_isAllowedToTheManagerAndTheOrganizerOnly() = emulatorTest {
    signIn(volunteer)
    checkInRef(volunteer).set(checkIn(volunteer, SELF, by = volunteer)).await()
    signIn(otherVolunteer)
    checkInRef(otherVolunteer).set(checkIn(otherVolunteer, SELF, by = otherVolunteer)).await()

    for (user in listOf(manager, organizer)) {
      signIn(user)
      assertEquals(
          setOf(volunteer.uid, otherVolunteer.uid),
          checkIns(MISSION_ID).get(Source.SERVER).await().documents.map { it.id }.toSet(),
      )
    }
    for (user in listOf(volunteer, outsider)) {
      signIn(user)
      assertDenied { checkIns(MISSION_ID).get(Source.SERVER).await() }
    }
  }

  @Test
  fun collectionGroupQuery_byUserId_returnsOnlyTheirOwnCheckIns() = emulatorTest {
    val otherEventId = event()
    signIn(volunteer)
    checkInRef(volunteer).set(checkIn(volunteer, SELF, by = volunteer)).await()
    checkInRef(volunteer, eventId = otherEventId)
        .set(checkIn(volunteer, SELF, by = volunteer))
        .await()
    signIn(otherVolunteer)
    checkInRef(otherVolunteer).set(checkIn(otherVolunteer, SELF, by = otherVolunteer)).await()

    signIn(volunteer)
    val mine =
        db.collectionGroup(CHECK_INS)
            .whereEqualTo("userId", volunteer.uid)
            .get(Source.SERVER)
            .await()
            .documents
            .map { it.reference.path }
            .toSet()

    assertEquals(
        setOf(checkInRef(volunteer).path, checkInRef(volunteer, eventId = otherEventId).path),
        mine,
    )
    assertDenied {
      db.collectionGroup(CHECK_INS)
          .whereEqualTo("userId", otherVolunteer.uid)
          .get(Source.SERVER)
          .await()
    }
  }

  @Test
  fun signedOutUser_cannotReadOrWrite() = emulatorTest {
    signIn(volunteer)
    checkInRef(volunteer).set(checkIn(volunteer, SELF, by = volunteer)).await()
    auth.signOut()

    assertDenied { checkInRef(volunteer).get(Source.SERVER).await() }
    assertDenied {
      checkInRef(otherVolunteer).set(checkIn(otherVolunteer, SELF, by = otherVolunteer)).await()
    }
  }

  /**
   * Creates an event of [organizer] with the team [TEAM_ID] led by [manager], a mission of that
   * team and a mission of no team, both with [volunteer] and [otherVolunteer] assigned.
   */
  private suspend fun event(): String {
    signIn(organizer)
    val ref = db.collection("events").document()
    paths += ref.path
    ref.set(mapOf("organizerId" to organizer.uid)).await()
    createTeam(TEAM_ID, manager, ref.id)
    createMission(ref.id, MISSION_ID, TEAM_ID)
    createMission(ref.id, GENERAL_MISSION_ID, teamId = null)
    return ref.id
  }

  private suspend fun createTeam(teamId: String, manager: Account, eventId: String = this.eventId) {
    signIn(organizer)
    val ref = db.collection("events").document(eventId).collection("teams").document(teamId)
    paths += ref.path
    ref.set(mapOf("managerId" to manager.uid, "memberIds" to emptyList<String>())).await()
  }

  private suspend fun createMission(eventId: String, missionId: String, teamId: String?) {
    val ref = db.collection("events").document(eventId).collection("missions").document(missionId)
    paths += ref.path
    ref.set(mapOf("teamId" to teamId, "assigneeIds" to listOf(volunteer.uid, otherVolunteer.uid)))
        .await()
  }

  private fun checkIns(missionId: String, eventId: String = this.eventId) =
      db.collection("events")
          .document(eventId)
          .collection("missions")
          .document(missionId)
          .collection(CHECK_INS)

  private fun checkInRef(
      user: Account,
      missionId: String = MISSION_ID,
      eventId: String = this.eventId,
  ): DocumentReference = checkIns(missionId, eventId).document(user.uid).also { paths += it.path }

  private fun checkIn(user: Account, method: String, by: Account): Map<String, Any> =
      mapOf(
          "userId" to user.uid,
          "method" to method,
          "checkedInAt" to FieldValue.serverTimestamp(),
          "checkedInBy" to by.uid,
      )

  private suspend fun account(): Account {
    val email = "check-in-${UUID.randomUUID()}@example.test"
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
    const val CHECK_INS = "checkIns"
    const val TEAM_ID = "team-bar"
    const val OTHER_TEAM_ID = "team-logistics"
    const val MISSION_ID = "mission-bar"
    const val GENERAL_MISSION_ID = "mission-general"
    const val SELF = "SELF"
    const val MANAGER = "MANAGER"
  }

  private suspend fun assertDenied(block: suspend () -> Unit) {
    val failure = runCatching { block() }.exceptionOrNull()
    assertTrue(
        "Expected permission denial, got $failure",
        failure is FirebaseFirestoreException &&
            failure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
  }
}
