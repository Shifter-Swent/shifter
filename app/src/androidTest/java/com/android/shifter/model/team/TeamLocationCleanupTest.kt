// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
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
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Taking a volunteer out of a team deletes the position they shared with its manager, in the same
 * commit as the membership change (#305), against the emulators and the real `firestore.rules`.
 *
 * `LiveLocationRulesTest` covers who may delete a position; these tests cover the four paths that
 * remove a volunteer from `memberIds`, and that each one really takes the position with it. The
 * positions are written through the raw SDK rather than a live location repository, which this PR
 * does not depend on: only the documented path matters here.
 */
@RunWith(AndroidJUnit4::class)
class TeamLocationCleanupTest {
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
  private lateinit var eventId: String

  @Before
  fun setUp() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        organizer = account()
        manager = account()
        volunteer = account()
        teammate = account()
        eventId = event()
        // Through the real flow, so the request, the participants document and memberIds are all in
        // the state a withdrawal or a rejection expects to find.
        join(volunteer)
        join(teammate)
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
  fun withdraw_byTheVolunteer_deletesTheirPositionAndKeepsTheirTeammates() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        shareBothPositions()

        signIn(volunteer)
        requests.withdraw(eventId, volunteer.uid)

        assertEquals(listOf(teammate.uid), memberIds())
        // Read back from the server as the manager: what they still see is the point of the
        // cleanup.
        assertEquals(setOf(teammate.uid), positionsSeenBy(manager))
      }

  @Test
  fun reject_ofAnAcceptedVolunteer_deletesTheirPosition() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        shareBothPositions()

        signIn(organizer)
        requests.reject(eventId, volunteer.uid)

        assertEquals(listOf(teammate.uid), memberIds())
        assertEquals(setOf(teammate.uid), positionsSeenBy(manager))
      }

  @Test
  fun removeMember_byTheOrganizer_deletesThePositionWithoutTheVolunteersDevice() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        shareBothPositions()

        // The volunteer does nothing here, as an offline or uninstalled app would not: the
        // organizer's
        // own write is what takes the position out.
        signIn(organizer)
        teams.removeMember(eventId, TEAM_ID, volunteer.uid)

        assertEquals(listOf(teammate.uid), memberIds())
        assertEquals(setOf(teammate.uid), positionsSeenBy(manager))
      }

  @Test
  fun setManager_promotingAMember_deletesThePositionTheySharedAsAVolunteer() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        shareBothPositions()

        signIn(organizer)
        teams.setManager(eventId, TEAM_ID, volunteer.uid)
        // The index entries the change wrote, deleted with the rest.
        paths += managerEntry(manager).path
        paths += managerEntry(volunteer).path

        // Promoted out of the members, so nothing of theirs would replace or remove the position.
        assertEquals(listOf(teammate.uid, manager.uid), memberIds())
        // Read as the organizer: the previous manager no longer leads this team and may not list
        // it.
        assertEquals(setOf(teammate.uid), positionsSeenBy(organizer))
      }

  @Test
  fun removeMember_withNoPositionStored_stillRemovesTheMember() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        // Nothing shared at all, so the delete has no document to take out.
        signIn(organizer)
        teams.removeMember(eventId, TEAM_ID, volunteer.uid)

        assertEquals(listOf(teammate.uid), memberIds())
        assertEquals(emptySet<String>(), positionsSeenBy(manager))
      }

  @Test
  fun removeMember_byAUserWhoMayNotWriteTheTeam_deletesNothing() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        shareBothPositions()

        // The manager may read the positions but not write memberIds, so the whole batch is
        // refused:
        // an unauthorized membership change must not take a position out on its own.
        signIn(manager)
        val failure = runCatching { teams.removeMember(eventId, TEAM_ID, volunteer.uid) }
        assertTrue(
            "Expected PermissionDenied, got ${failure.exceptionOrNull()}",
            failure.exceptionOrNull() is TeamRepositoryException.PermissionDenied,
        )

        assertEquals(listOf(volunteer.uid, teammate.uid), memberIds())
        assertEquals(setOf(volunteer.uid, teammate.uid), positionsSeenBy(manager))
      }

  /**
   * The cleanup cannot be undone by a write that was already on its way: a position queued on the
   * volunteer's device before their removal, and sent once it is done, is refused because the rules
   * read `memberIds` at the time the write lands. Locked in here, so a later change to the `create,
   * update` rule cannot quietly let a removed volunteer back onto the manager's map.
   */
  @Test
  fun removedMember_cannotShareTheirPositionAgain() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        shareBothPositions()
        signIn(organizer)
        teams.removeMember(eventId, TEAM_ID, volunteer.uid)
        assertEquals(setOf(teammate.uid), positionsSeenBy(manager))

        // The write their device had queued, sent as themselves through the raw SDK.
        signIn(volunteer)
        val refused = runCatching { locationRef(volunteer).set(location()).await() }

        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            (refused.exceptionOrNull() as? FirebaseFirestoreException)?.code,
        )
        // And nothing is back on the server for the manager to read.
        assertEquals(setOf(teammate.uid), positionsSeenBy(manager))
      }

  /**
   * The position is keyed by team, so leaving one team must not touch the other: a volunteer in two
   * teams of the same event shares a position with each manager, and only the team they are removed
   * from loses it.
   */
  @Test
  fun removeMember_fromOneOfTwoTeams_leavesThePositionSharedWithTheOther() =
      emulatorTest(TEST_TIMEOUT_MILLIS) {
        // A second team of the same event, led by the same manager, with the volunteer added to it:
        // `addMember` is the only way in, since an accepted request names a single team.
        signIn(organizer)
        createTeam(OTHER_TEAM_ID)
        teams.addMember(eventId, OTHER_TEAM_ID, volunteer.uid)

        // One position per team: the document id is the uid, so the two never collide.
        signIn(volunteer)
        step("share in both teams") {
          locationRef(volunteer).set(location()).await()
          locationRef(volunteer, OTHER_TEAM_ID).set(location()).await()
        }

        signIn(organizer)
        teams.removeMember(eventId, TEAM_ID, volunteer.uid)

        // Out of the team they left, with its position gone, and untouched in the other one.
        assertEquals(listOf(teammate.uid), memberIds())
        assertEquals(emptySet<String>(), positionsSeenBy(manager))
        assertEquals(listOf(volunteer.uid), memberIds(OTHER_TEAM_ID))
        assertEquals(setOf(volunteer.uid), positionsSeenBy(manager, OTHER_TEAM_ID))
      }

  /** An ongoing event of [organizer] with [TEAM_ID], led by [manager] and still without members. */
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
    createTeam(TEAM_ID, ref.id)
    return ref.id
  }

  /**
   * Creates [teamId] of [eventId] without members, led by [manager], as its organizer, who must be
   * signed in. Complete, so the team repository can read it too.
   */
  private suspend fun createTeam(teamId: String, eventId: String = this.eventId) {
    val team = db.collection(EVENTS).document(eventId).collection(TEAMS).document(teamId)
    paths += team.path
    step("write team") {
      team
          .set(
              Team(
                      eventId = eventId,
                      name = teamId,
                      description = "The $teamId team",
                      icon = "wine",
                      managerId = manager.uid,
                      createdAt = Instant.EPOCH,
                  )
                  .toFirestoreMap()
          )
          .await()
    }
  }

  /** Puts [user] in [TEAM_ID] the way the app does: they apply, the organizer accepts. */
  private suspend fun join(user: Account) {
    signIn(user)
    paths += requestRef(user).path
    step("apply") {
      requests.apply(
          eventId,
          MembershipRequest(
              userId = user.uid,
              teamId = TEAM_ID,
              availability = listOf(AvailabilitySlot(Instant.EPOCH, Instant.EPOCH.plusSeconds(60))),
              createdAt = Instant.EPOCH,
          ),
      )
    }
    signIn(organizer)
    step("accept") { requests.accept(eventId, user.uid) }
  }

  /** Both volunteers share a position, as their app would while they are in the team. */
  private suspend fun shareBothPositions() {
    for (user in listOf(volunteer, teammate)) {
      signIn(user)
      step("share") { locationRef(user).set(location()).await() }
    }
  }

  /** The uids [reader] still finds in the positions of [teamId], read from the server. */
  private suspend fun positionsSeenBy(reader: Account, teamId: String = TEAM_ID): Set<String> {
    signIn(reader)
    return liveLocations(teamId).get(Source.SERVER).await().documents.map { it.id }.toSet()
  }

  private suspend fun memberIds(teamId: String = TEAM_ID): List<*> {
    signIn(organizer)
    return teamRef(teamId).get(Source.SERVER).await().get(TeamSchema.MEMBER_IDS) as List<*>
  }

  private fun minutesFromNow(minutes: Long) = Timestamp(Instant.now().epochSecond + minutes * 60, 0)

  private fun teamRef(teamId: String = TEAM_ID) =
      db.collection(EVENTS).document(eventId).collection(TEAMS).document(teamId)

  private fun liveLocations(teamId: String = TEAM_ID) = teamRef(teamId).collection(LIVE_LOCATIONS)

  private fun locationRef(user: Account, teamId: String = TEAM_ID): DocumentReference =
      liveLocations(teamId).document(user.uid).also { paths += it.path }

  private fun managerEntry(user: Account) =
      db.collection(EVENTS)
          .document(eventId)
          .collection(ManagerIndexSchema.COLLECTION)
          .document(user.uid)

  private fun requestRef(user: Account) =
      db.collection(EVENTS).document(eventId).collection(REQUESTS).document(user.uid)

  private fun location(): Map<String, Any> =
      mapOf(
          "position" to mapOf("latitude" to GENEVA_LATITUDE, "longitude" to GENEVA_LONGITUDE),
          "measuredAt" to MEASURED_AT,
      )

  /** A new account, signed in, with a profile that shares its location. */
  private suspend fun account(): Account {
    val email = "team-cleanup-${UUID.randomUUID()}@example.test"
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

  private data class Account(val uid: String, val credential: AuthCredential)

  private companion object {
    /**
     * Room for a class whose every test signs a handful of accounts in and out: under the load of
     * the whole class the emulators answer more slowly than the default budget of an emulator test
     * allows, as `FirestoreSignInTest` found before it.
     */
    const val TEST_TIMEOUT_MILLIS = 60_000L
    const val EVENTS = "events"
    const val TEAMS = "teams"
    const val REQUESTS = "membershipRequests"
    const val PARTICIPANTS = "eventParticipants"
    const val LIVE_LOCATIONS = "liveLocations"
    const val USERS = "users"
    const val LOCATION_SHARING_ENABLED = "locationSharingEnabled"
    const val TEAM_ID = "team-bar"
    const val OTHER_TEAM_ID = "team-logistics"
    const val GENEVA_LATITUDE = 46.2044
    const val GENEVA_LONGITUDE = 6.1432
    val MEASURED_AT = Timestamp(1_762_000_000, 0)
  }
}
