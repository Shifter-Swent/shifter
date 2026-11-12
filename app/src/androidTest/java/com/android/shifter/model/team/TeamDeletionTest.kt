// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.team

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
import com.swent.shifter.firebase.step
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestRepositoryFirestore
import com.swent.shifter.model.membership.MembershipRequestStatus
import java.time.Instant
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
 * [FirestoreTeamRepository.deleteTeam] against the emulators and the real `firestore.rules` (#398):
 * nothing of the team is left, its members stay participants, an interrupted deletion is completed
 * by trying again, and only the organizer may delete a team.
 *
 * The data is built through the real flows where the rules depend on it (applying, accepting), and
 * with the raw SDK elsewhere. What is left is checked through [FirestoreEmulatorAdmin], since
 * nobody may read the messages of a team that is gone.
 */
@RunWith(AndroidJUnit4::class)
class TeamDeletionTest {
  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore
  private val teams = FirestoreTeamRepository(db)
  private val requests = MembershipRequestRepositoryFirestore(db)
  private val paths = mutableSetOf<String>()
  private val accounts = mutableListOf<Account>()
  private lateinit var organizer: Account
  private lateinit var manager: Account
  private lateinit var volunteer: Account
  private lateinit var teammate: Account
  private lateinit var applicant: Account
  private lateinit var eventId: String

  @Before
  fun setUp() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        organizer = account()
        manager = account()
        volunteer = account()
        teammate = account()
        applicant = account()
        eventId = event()
        // Appointed among the accepted members, as in the app, so they are a participant: the
        // admin channel requires it.
        join(manager)
        signIn(organizer)
        step("appoint") { teams.setManager(eventId, TEAM_ID, manager.uid) }
        join(volunteer)
        join(teammate)
        apply(applicant)
      }

  @After
  fun cleanUp() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
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
  fun deleteTeam_leavesNothingOfTheTeamBehind() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        val team = fillTeam(TEAM_ID)

        signIn(organizer)
        teams.deleteTeam(eventId, TEAM_ID)

        team.forEach { assertGone(it) }
        assertGone(requestRef(applicant))
        // The manager no longer manages it, from the same commit on.
        assertEquals(emptyList<String>(), managedTeams())
      }

  @Test
  fun deleteTeam_leavesTheOtherTeamsAndTheEventAlone() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        // Led by the same manager, so their entry lists both teams.
        signIn(organizer)
        createTeam(OTHER_TEAM_ID)
        val otherMember = account()
        join(otherMember, OTHER_TEAM_ID)
        val other = fillTeam(OTHER_TEAM_ID, otherMember)
        val general = mission(MISSION_ID + "-general", teamId = null, assignee = volunteer)
        val event = db.collection(EVENTS).document(eventId)

        teams.deleteTeam(eventId, TEAM_ID)

        (other + general + event).forEach { assertTrue("$it must stay", exists(it)) }
        assertEquals(listOf(OTHER_TEAM_ID), managedTeams())
        assertEquals(
            setOf(manager.uid, volunteer.uid, teammate.uid, otherMember.uid),
            participants(),
        )
      }

  @Test
  fun deleteTeam_keepsItsMembersAsParticipantsWhoCanStillWithdraw() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        signIn(organizer)
        teams.deleteTeam(eventId, TEAM_ID)

        // Still participants, with their accepted request, without a team.
        assertEquals(setOf(manager.uid, volunteer.uid, teammate.uid), participants())
        signIn(volunteer)
        assertEquals(
            MembershipRequestStatus.ACCEPTED,
            requests.getMembershipRequest(eventId, volunteer.uid)?.status,
        )

        requests.withdraw(eventId, volunteer.uid)

        assertEquals(setOf(manager.uid, teammate.uid), participants())
      }

  /**
   * Interrupted after the team was marked and its mission deleted, as a killed app would leave it:
   * trying again deletes the rest.
   */
  @Test
  fun deleteTeam_interruptedHalfway_isCompletedByTryingAgain() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        val team = fillTeam(TEAM_ID)
        signIn(organizer)
        teamRef(TEAM_ID).update(TeamSchema.DELETING, true).await()
        val mission = missionRef(TEAM_ID)
        db.batch()
            .delete(mission.collection(CHECK_INS).document(volunteer.uid))
            .delete(mission)
            .commit()
            .await()

        teams.deleteTeam(eventId, TEAM_ID)
        // Once more, as a retry whose first attempt had in fact gone through: nothing to do.
        teams.deleteTeam(eventId, TEAM_ID)

        team.forEach { assertGone(it) }
        assertEquals(emptyList<String>(), managedTeams())
      }

  /** The one team the app cannot show is the one the organizer most needs to remove. */
  @Test
  fun deleteTeam_ofATeamMalformedElsewhere_stillDeletesIt() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        val malformed = teamRef("team-malformed")
        paths += malformed.path
        // No name, description, icon nor createdAt: only what the deletion reads.
        FirestoreEmulatorAdmin.createDocument(
            malformed.path,
            mapOf("memberIds" to listOf(volunteer.uid)),
        )

        signIn(organizer)
        teams.deleteTeam(eventId, malformed.id)

        assertGone(malformed)
      }

  /** Interrupted right after its first write, the most likely place: nothing else is gone yet. */
  @Test
  fun deleteTeam_interruptedAfterTheMark_isCompletedByTryingAgain() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        val team = fillTeam(TEAM_ID)
        signIn(organizer)
        teamRef(TEAM_ID).update(TeamSchema.DELETING, true).await()

        teams.deleteTeam(eventId, TEAM_ID)

        team.forEach { assertGone(it) }
        assertGone(requestRef(applicant))
        assertEquals(emptyList<String>(), managedTeams())
      }

  @Test
  fun deleteTeam_byAnyoneButTheOrganizer_deletesNothing() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        val team = fillTeam(TEAM_ID)

        for (user in listOf(manager, volunteer)) {
          signIn(user)
          val failure = runCatching { teams.deleteTeam(eventId, TEAM_ID) }.exceptionOrNull()
          assertTrue(
              "Expected PermissionDenied, got $failure",
              failure is TeamRepositoryException.PermissionDenied,
          )
        }

        team.forEach { assertTrue("$it must stay", exists(it)) }
        assertTrue(exists(requestRef(applicant)))
      }

  /** The rules only let a pending application go with its team, never on its own. */
  @Test
  fun pendingApplication_whileItsTeamExists_cannotBeDeletedByTheOrganizer() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        signIn(organizer)

        assertDenied { requestRef(applicant).delete().await() }
      }

  /** An accepted request stays when its team goes: its volunteer is still a participant. */
  @Test
  fun acceptedRequest_cannotBeDeletedWithItsTeam() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        signIn(organizer)

        assertDenied {
          db.batch().delete(teamRef(TEAM_ID)).delete(requestRef(volunteer)).commit().await()
        }
      }

  /** A mission added once the team's missions were read would outlive the team. */
  @Test
  fun mission_inATeamBeingDeleted_cannotBeCreated() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        // Both may create one until the deletion starts.
        for (user in listOf(organizer, manager)) {
          signIn(user)
          newMission().set(missionOf(TEAM_ID)).await()
        }

        signIn(organizer)
        teamRef(TEAM_ID).update(TeamSchema.DELETING, true).await()

        for (user in listOf(organizer, manager)) {
          signIn(user)
          assertDenied { newMission().set(missionOf(TEAM_ID)).await() }
        }
      }

  @Test
  fun mission_cannotBeMovedToATeamBeingDeleted() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        signIn(organizer)
        val general = newMission().also { it.set(missionOf(teamId = null)).await() }
        teamRef(TEAM_ID).update(TeamSchema.DELETING, true).await()

        assertDenied { general.update("teamId", TEAM_ID).await() }
      }

  /** #426: the admin channel lets in whoever the index lists, so the deletion shuts them out. */
  @Test
  fun deleteTeam_shutsItsManagerOutOfTheAdminChannelAtOnce() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        signIn(manager)
        adminChannel().get(Source.SERVER).await()

        signIn(organizer)
        teams.deleteTeam(eventId, TEAM_ID)

        signIn(manager)
        assertDenied { adminChannel().get(Source.SERVER).await() }
      }

  /** A rejected request stays too, so its volunteer still cannot apply again. */
  @Test
  fun rejectedRequest_cannotBeDeletedWithItsTeam() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        val rejected = account()
        apply(rejected)
        signIn(organizer)
        step("reject") { requests.reject(eventId, rejected.uid) }

        assertDenied {
          db.batch().delete(teamRef(TEAM_ID)).delete(requestRef(rejected)).commit().await()
        }
      }

  /** The manager's entry only loses the team in the commit that deletes it. */
  @Test
  fun managerEntry_cannotLoseTheTeamWhileItExists() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        signIn(organizer)

        assertDenied {
          managerEntry()
              .update(ManagerIndexSchema.TEAM_IDS, FieldValue.arrayRemove(TEAM_ID))
              .await()
        }
      }

  /**
   * Gives [teamId], of which [member] is a member, a mission with a check-in, a message and a
   * shared position, and returns the documents that must go with the team, the team included.
   */
  private suspend fun fillTeam(
      teamId: String,
      member: Account = volunteer,
  ): List<DocumentReference> {
    signIn(member)
    val position = teamRef(teamId).collection(LIVE_LOCATIONS).document(member.uid)
    step("share") { position.set(location()).await() }
    val message = teamRef(teamId).collection(MESSAGES).document()
    step("send") { message.set(messageFrom(member)).await() }
    signIn(organizer)
    val mission = mission(MISSION_ID + "-" + teamId, teamId, assignee = member)
    val checkIn = mission.collection(CHECK_INS).document(member.uid)
    step("check in") { checkIn.set(checkInOf(member)).await() }
    return listOf(teamRef(teamId), mission, checkIn, message, position).onEach { paths += it.path }
  }

  /** A mission of [teamId], or of no team, with [assignee] assigned, written as the organizer. */
  private suspend fun mission(
      missionId: String,
      teamId: String?,
      assignee: Account,
  ): DocumentReference {
    signIn(organizer)
    val ref = db.collection(EVENTS).document(eventId).collection(MISSIONS).document(missionId)
    paths += ref.path
    step("write mission") {
      ref.set(
              mapOf(
                  "teamId" to teamId,
                  "assigneeIds" to listOf(assignee.uid),
                  "completed" to false,
              )
          )
          .await()
    }
    return ref
  }

  private fun newMission(): DocumentReference =
      db.collection(EVENTS).document(eventId).collection(MISSIONS).document().also {
        paths += it.path
      }

  /** A mission of [teamId] with nobody assigned, which its manager may write too. */
  private fun missionOf(teamId: String?): Map<String, Any?> =
      mapOf("teamId" to teamId, "assigneeIds" to emptyList<String>(), "completed" to false)

  /** An ongoing event of [organizer] with [TEAM_ID], still without a manager or members. */
  private suspend fun event(): String {
    signIn(organizer)
    val ref = db.collection(EVENTS).document()
    paths += ref.path
    paths += db.collection(PARTICIPANTS).document(ref.id).path
    step("write event") {
      ref.set(
              mapOf(
                  "organizerId" to organizer.uid,
                  "startAt" to minutesFromNow(-60),
                  "endAt" to minutesFromNow(60),
              )
          )
          .await()
    }
    eventId = ref.id
    createTeam(TEAM_ID, leader = null)
    return ref.id
  }

  /**
   * Creates [teamId] without members, led by [leader] and indexed as such if there is one, as the
   * organizer, who must be signed in.
   */
  private suspend fun createTeam(teamId: String, leader: Account? = manager) {
    val team = teamRef(teamId)
    paths += team.path
    paths += managerEntry().path
    step("write team") {
      val batch =
          db.batch()
              .set(
                  team,
                  Team(
                          eventId = eventId,
                          name = teamId,
                          description = "The $teamId team",
                          icon = "wine",
                          managerId = leader?.uid,
                          createdAt = Instant.EPOCH,
                      )
                      .toFirestoreMap(),
              )
      leader?.let { batch.indexManager(team, it.uid) }
      batch.commit().await()
    }
  }

  /** [user] applies to [teamId] and is accepted, the way the app does it. */
  private suspend fun join(user: Account, teamId: String = TEAM_ID) {
    apply(user, teamId)
    signIn(organizer)
    step("accept") { requests.accept(eventId, user.uid) }
  }

  /** [user] applies to [teamId], and stays pending. */
  private suspend fun apply(user: Account, teamId: String = TEAM_ID) {
    signIn(user)
    paths += requestRef(user).path
    step("apply") {
      requests.apply(
          eventId,
          MembershipRequest(
              userId = user.uid,
              teamId = teamId,
              availability = listOf(AvailabilitySlot(Instant.EPOCH, Instant.EPOCH.plusSeconds(60))),
              createdAt = Instant.EPOCH,
          ),
      )
    }
  }

  private suspend fun participants(): Set<String> {
    signIn(organizer)
    val document = db.collection(PARTICIPANTS).document(eventId).get(Source.SERVER).await()
    return (document.get("participantIds") as List<*>).map { it as String }.toSet()
  }

  private suspend fun managedTeams(): List<*> {
    signIn(organizer)
    return managerEntry().get(Source.SERVER).await().get(ManagerIndexSchema.TEAM_IDS) as List<*>
  }

  private suspend fun exists(document: DocumentReference) =
      FirestoreEmulatorAdmin.documentExists(document.path)

  private suspend fun assertGone(document: DocumentReference) {
    assertFalse("${document.path} must be deleted", exists(document))
  }

  private fun teamRef(teamId: String) =
      db.collection(EVENTS).document(eventId).collection(TEAMS).document(teamId)

  private fun missionRef(teamId: String) =
      db.collection(EVENTS).document(eventId).collection(MISSIONS).document("$MISSION_ID-$teamId")

  private fun adminChannel() =
      db.collection(EVENTS)
          .document(eventId)
          .collection("channels")
          .document("admin")
          .collection(MESSAGES)

  private fun managerEntry() =
      db.collection(EVENTS)
          .document(eventId)
          .collection(ManagerIndexSchema.COLLECTION)
          .document(manager.uid)

  private fun requestRef(user: Account) =
      db.collection(EVENTS).document(eventId).collection(REQUESTS).document(user.uid)

  private fun minutesFromNow(minutes: Long) = Timestamp(Instant.now().epochSecond + minutes * 60, 0)

  private fun location(): Map<String, Any> =
      mapOf(
          "position" to mapOf("latitude" to GENEVA_LATITUDE, "longitude" to GENEVA_LONGITUDE),
          "measuredAt" to MEASURED_AT,
      )

  private fun messageFrom(sender: Account): Map<String, Any> =
      mapOf(
          "senderId" to sender.uid,
          "text" to "Hello team",
          "sentAt" to FieldValue.serverTimestamp(),
      )

  private fun checkInOf(user: Account): Map<String, Any> =
      mapOf(
          "userId" to user.uid,
          "method" to "MANAGER",
          "checkedInAt" to FieldValue.serverTimestamp(),
          "checkedInBy" to organizer.uid,
      )

  /** A new account, signed in, with a profile that shares its location. */
  private suspend fun account(): Account {
    val email = "team-deletion-${UUID.randomUUID()}@example.test"
    val password = "test-password-123"
    val user =
        checkNotNull(
            auth
                .signInForFirestore { auth.createUserWithEmailAndPassword(email, password).await() }
                .user
        )
    val profile = db.collection(USERS).document(user.uid)
    paths += profile.path
    // The rules only let a profile start with the consent off, as the app's opt-in does.
    profile
        .set(
            mapOf(
                "displayName" to "Volunteer",
                "email" to email,
                LOCATION_SHARING_ENABLED to false,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )
        .await()
    profile.update(LOCATION_SHARING_ENABLED, true).await()
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
        "Expected permission denial, got $failure",
        failure is FirebaseFirestoreException &&
            failure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
  }

  private data class Account(val uid: String, val credential: AuthCredential)

  private companion object {
    /** Room for a class whose every test signs a handful of accounts in and out. */
    const val TEST_TIMEOUT_MILLIS = 60_000L
    const val EVENTS = "events"
    const val TEAMS = "teams"
    const val MISSIONS = "missions"
    const val CHECK_INS = "checkIns"
    const val MESSAGES = "messages"
    const val REQUESTS = "membershipRequests"
    const val PARTICIPANTS = "eventParticipants"
    const val LIVE_LOCATIONS = "liveLocations"
    const val USERS = "users"
    const val LOCATION_SHARING_ENABLED = "locationSharingEnabled"
    const val TEAM_ID = "team-bar"
    const val OTHER_TEAM_ID = "team-logistics"
    const val MISSION_ID = "mission"
    const val GENEVA_LATITUDE = 46.2044
    const val GENEVA_LONGITUDE = 6.1432
    val MEASURED_AT = Timestamp(1_762_000_000, 0)
  }
}
