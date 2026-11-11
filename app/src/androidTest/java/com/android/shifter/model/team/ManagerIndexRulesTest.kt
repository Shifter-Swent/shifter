// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.team

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.firebase.emulatorTest
import com.swent.shifter.firebase.signInForFirestore
import java.util.UUID
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The `firestore.rules` of `events/{eventId}/managers/{uid}`, the index of the teams each user
 * manages, written with the raw SDK so they are checked on their own, whatever the repositories do.
 *
 * Every denial has an allowed counterpart, so a test cannot pass because its setup is broken.
 */
@RunWith(AndroidJUnit4::class)
class ManagerIndexRulesTest {
  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore
  private val paths = mutableSetOf<String>()
  private lateinit var organizer: Account
  private lateinit var manager: Account
  private lateinit var volunteer: Account
  private lateinit var eventId: String

  @Before
  fun setUp() = emulatorTest {
    organizer = account()
    manager = account()
    volunteer = account()
    signIn(organizer)
    val event = db.collection("events").document()
    paths += event.path
    event.set(mapOf("organizerId" to organizer.uid)).await()
    eventId = event.id
    // Both volunteers are members of the team, which has no manager yet.
    teamAsAdmin(TEAM_ID, managerId = null, members = listOf(manager.uid, volunteer.uid))
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
  fun appoint_withTheIndexInTheSameCommit_listsTheTeam() = emulatorTest {
    signIn(organizer)

    db.batch()
        .update(
            team(TEAM_ID),
            mapOf("managerId" to manager.uid, "memberIds" to listOf(volunteer.uid)),
        )
        .set(entry(manager), adding(TEAM_ID), SetOptions.merge())
        .commit()
        .await()

    assertEquals(listOf(TEAM_ID), teamsOf(manager))
  }

  @Test
  fun index_ofSomeoneWhoDoesNotManageTheTeam_isDenied() = emulatorTest {
    signIn(organizer)

    assertDenied { entry(volunteer).set(adding(TEAM_ID), SetOptions.merge()).await() }

    // Once they manage it, the same write goes through.
    teamAsAdmin(TEAM_ID, managerId = volunteer.uid, members = listOf(manager.uid))
    entry(volunteer).set(adding(TEAM_ID), SetOptions.merge()).await()
  }

  @Test
  fun index_ofTwoTeamsAtOnce_isDenied() = emulatorTest {
    teamAsAdmin(TEAM_ID, managerId = manager.uid, members = listOf(volunteer.uid))
    teamAsAdmin(OTHER_TEAM_ID, managerId = manager.uid, members = emptyList())
    signIn(organizer)

    assertDenied { entry(manager).set(mapOf("teamIds" to listOf(TEAM_ID, OTHER_TEAM_ID))).await() }

    // One team at a time, as the repositories write it.
    entry(manager).set(adding(TEAM_ID), SetOptions.merge()).await()
    entry(manager).set(adding(OTHER_TEAM_ID), SetOptions.merge()).await()
    assertEquals(listOf(TEAM_ID, OTHER_TEAM_ID), teamsOf(manager))
  }

  @Test
  fun index_byTheManagerThemself_isDenied() = emulatorTest {
    teamAsAdmin(TEAM_ID, managerId = manager.uid, members = listOf(volunteer.uid))
    signIn(manager)

    // Only the organizer appoints, so only they add a team, even one the user does manage.
    assertDenied { entry(manager).set(adding(TEAM_ID), SetOptions.merge()).await() }

    signIn(organizer)
    entry(manager).set(adding(TEAM_ID), SetOptions.merge()).await()
  }

  @Test
  fun removing_aTeamStillManaged_isDenied() = emulatorTest {
    managing(TEAM_ID)
    signIn(organizer)

    assertDenied { entry(manager).set(removing(TEAM_ID), SetOptions.merge()).await() }

    // Taking the manager out of the team in the same commit lets it go.
    db.batch()
        .update(
            team(TEAM_ID),
            mapOf("managerId" to null, "memberIds" to listOf(volunteer.uid, manager.uid)),
        )
        .set(entry(manager), removing(TEAM_ID), SetOptions.merge())
        .commit()
        .await()
    assertEquals(emptyList<String>(), teamsOf(manager))
  }

  @Test
  fun leaving_byTheManagerThemself_isAllowedWithTheTeamOnly() = emulatorTest {
    managing(TEAM_ID)
    signIn(manager)

    // Still leading the team, they cannot drop it from the index alone.
    assertDenied { entry(manager).set(removing(TEAM_ID), SetOptions.merge()).await() }

    // As they withdraw: out of the management and out of the index, in one commit.
    db.batch()
        .update(team(TEAM_ID), "managerId", null)
        .set(entry(manager), removing(TEAM_ID), SetOptions.merge())
        .commit()
        .await()
    assertEquals(emptyList<String>(), teamsOf(manager))
  }

  @Test
  fun leaving_byAManagerAppointedBeforeTheIndex_isAllowed() = emulatorTest {
    // They lead the team, but have no entry: the withdrawal still writes the removal to it.
    teamAsAdmin(TEAM_ID, managerId = manager.uid, members = listOf(volunteer.uid))
    signIn(manager)

    db.batch()
        .update(team(TEAM_ID), "managerId", null)
        .set(entry(manager), removing(TEAM_ID), SetOptions.merge())
        .commit()
        .await()
    assertEquals(emptyList<String>(), teamsOf(manager))
  }

  @Test
  fun index_byTheUserUnderAnEventThatDoesNotExist_isDenied() = emulatorTest {
    signIn(manager)
    val missing =
        db.collection("events")
            .document("missing-${UUID.randomUUID()}")
            .collection("managers")
            .document(manager.uid)

    assertDenied { missing.set(removing(TEAM_ID), SetOptions.merge()).await() }

    // The same write under an event that exists goes through.
    entry(manager).set(removing(TEAM_ID), SetOptions.merge()).await()
  }

  @Test
  fun index_withADuplicate_isDenied() = emulatorTest {
    managing(TEAM_ID)
    signIn(organizer)

    // Only a raw write can list a team twice: the repositories add with arrayUnion.
    assertDenied { entry(manager).set(mapOf("teamIds" to listOf(TEAM_ID, TEAM_ID))).await() }

    entry(manager).set(mapOf("teamIds" to listOf(TEAM_ID))).await()
  }

  @Test
  fun delete_byTheOrganizerOnly() = emulatorTest {
    managing(TEAM_ID)
    signIn(manager)

    assertDenied { entry(manager).delete().await() }

    signIn(organizer)
    entry(manager).delete().await()
  }

  @Test
  fun index_ofSomeoneElse_isDeniedToAVolunteer() = emulatorTest {
    managing(TEAM_ID)
    teamAsAdmin(TEAM_ID, managerId = null, members = listOf(manager.uid, volunteer.uid))
    signIn(volunteer)

    // The team no longer has a manager, but only the organizer or the user takes it out.
    assertDenied { entry(manager).set(removing(TEAM_ID), SetOptions.merge()).await() }

    signIn(manager)
    entry(manager).set(removing(TEAM_ID), SetOptions.merge()).await()
  }

  @Test
  fun index_withAnotherField_isDenied() = emulatorTest {
    teamAsAdmin(TEAM_ID, managerId = manager.uid, members = listOf(volunteer.uid))
    signIn(organizer)

    assertDenied {
      entry(manager).set(adding(TEAM_ID) + ("role" to "admin"), SetOptions.merge()).await()
    }

    entry(manager).set(adding(TEAM_ID), SetOptions.merge()).await()
  }

  @Test
  fun read_byTheUserAndTheOrganizerOnly() = emulatorTest {
    managing(TEAM_ID)

    signIn(volunteer)
    assertDenied { entry(manager).get(Source.SERVER).await() }

    for (user in listOf(manager, organizer)) {
      signIn(user)
      assertEquals(listOf(TEAM_ID), teamsOf(manager))
    }
  }

  /** Makes [manager] the manager of [teamId], indexed, through the admin API. */
  private suspend fun managing(teamId: String) {
    teamAsAdmin(teamId, managerId = manager.uid, members = listOf(volunteer.uid))
    paths += entry(manager).path
    FirestoreEmulatorAdmin.deleteDocument(entry(manager).path)
    FirestoreEmulatorAdmin.createDocument(entry(manager).path, mapOf("teamIds" to listOf(teamId)))
  }

  /** Replaces the team [teamId] through the admin API, with [managerId] and [members]. */
  private suspend fun teamAsAdmin(teamId: String, managerId: String?, members: List<String>) {
    val path = team(teamId).path
    paths += path
    FirestoreEmulatorAdmin.deleteDocument(path)
    val fields = mutableMapOf<String, Any>("name" to teamId, "memberIds" to members)
    managerId?.let { fields["managerId"] = it }
    FirestoreEmulatorAdmin.createDocument(path, fields)
  }

  private suspend fun teamsOf(user: Account): Any? =
      entry(user).get(Source.SERVER).await().get("teamIds")

  private fun team(teamId: String): DocumentReference =
      db.collection("events").document(eventId).collection("teams").document(teamId)

  private fun entry(user: Account): DocumentReference =
      db.collection("events").document(eventId).collection("managers").document(user.uid).also {
        paths += it.path
      }

  private fun adding(teamId: String) = mapOf("teamIds" to FieldValue.arrayUnion(teamId))

  private fun removing(teamId: String) = mapOf("teamIds" to FieldValue.arrayRemove(teamId))

  private suspend fun account(): Account {
    val email = "manager-index-${UUID.randomUUID()}@example.test"
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

  private suspend fun assertDenied(block: suspend () -> Unit) {
    val failure = runCatching { block() }.exceptionOrNull()
    assertTrue(
        "Expected permission denial, got $failure",
        failure is FirebaseFirestoreException &&
            failure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
  }

  private data class Account(val uid: String, val credential: AuthCredential)

  private companion object {
    const val TEAM_ID = "team-bar"
    const val OTHER_TEAM_ID = "team-kitchen"
  }
}
