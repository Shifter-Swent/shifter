// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.location

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
 * The `firestore.rules` of `events/{eventId}/teams/{teamId}/liveLocations/{uid}`, written with the
 * raw SDK so they are checked on their own, whatever the repository does.
 *
 * Every account shares its location by default ([account] turns the consent on) and the event of
 * [event] is ongoing, so a test that ends in a denial shows the boundary it is about rather than a
 * missing consent or a period that has not come.
 */
@RunWith(AndroidJUnit4::class)
class LiveLocationRulesTest {
  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore
  private val paths = mutableSetOf<String>()

  /** Auth accounts deleted in [cleanUp], so the emulator does not keep them between tests. */
  private val accounts = mutableListOf<Account>()

  private lateinit var organizer: Account
  private lateinit var manager: Account
  private lateinit var volunteer: Account
  private lateinit var otherVolunteer: Account
  private lateinit var otherManager: Account
  private lateinit var outsider: Account
  private lateinit var eventId: String

  @Before
  fun setUp() = emulatorTest {
    organizer = account()
    manager = account()
    volunteer = account()
    otherVolunteer = account()
    otherManager = account()
    outsider = account()
    eventId = event()
  }

  @After
  fun cleanUp() = emulatorTest {
    try {
      paths.forEach { FirestoreEmulatorAdmin.deleteDocument(it) }
    } finally {
      try {
        // Only an account itself may delete it, so each one is signed in again first.
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
  fun createAndUpdate_byAMemberSharingTheirLocation_keepTheLastPosition() = emulatorTest {
    signIn(volunteer)

    locationRef(volunteer).set(location()).await()
    locationRef(volunteer).set(location(LAUSANNE_LATITUDE, LAUSANNE_LONGITUDE)).await()

    // Read back as the manager, the one the position is shared with.
    signIn(manager)
    val stored = locationRef(volunteer).get(Source.SERVER).await()
    assertEquals(LAUSANNE_LATITUDE, stored.getDouble("position.latitude"))
    assertEquals(LAUSANNE_LONGITUDE, stored.getDouble("position.longitude"))
    assertEquals(MEASURED_AT, stored.getTimestamp("measuredAt"))
  }

  @Test
  fun write_withoutTheConsent_isDenied() = emulatorTest {
    signIn(volunteer)
    locationRef(volunteer).set(location()).await()

    // Turning the consent off stops the updates,
    setSharing(volunteer, false)
    assertDenied {
      locationRef(volunteer).set(location(LAUSANNE_LATITUDE, LAUSANNE_LONGITUDE)).await()
    }

    // and a volunteer who never gave it cannot share a position at all.
    setSharing(otherVolunteer, false)
    assertDenied { locationRef(otherVolunteer).set(location()).await() }
  }

  @Test
  fun write_ofAnotherVolunteersPosition_isDenied() = emulatorTest {
    signIn(volunteer)
    locationRef(volunteer).set(location()).await()

    // Neither a teammate who shares a position of their own, nor the manager, nor the organizer.
    for (user in listOf(otherVolunteer, manager, organizer)) {
      signIn(user)
      assertDenied {
        locationRef(volunteer).set(location(LAUSANNE_LATITUDE, LAUSANNE_LONGITUDE)).await()
      }
      assertDenied { locationRef(volunteer).delete().await() }
    }
  }

  @Test
  fun create_underATeamOneIsNotAMemberOf_isDenied() = emulatorTest {
    // The outsider belongs to no team, and the manager leads this one without being a member.
    for (user in listOf(outsider, manager)) {
      signIn(user)
      assertDenied { locationRef(user).set(location()).await() }
    }

    // A member of this team is no member of the other one either.
    signIn(volunteer)
    assertDenied { locationRef(volunteer, OTHER_TEAM_ID).set(location()).await() }
  }

  @Test
  fun create_withAMeasureInTheFuture_isDenied() = emulatorTest {
    signIn(volunteer)

    // Staleness is read off measuredAt, so a device clock running days ahead would otherwise pin
    // the volunteer at a place every reader considers fresh for ever. A wrong clock is enough.
    assertDenied { locationRef(volunteer).set(location(measuredAt = minutesFromNow(60))).await() }

    // A clock a little ahead is still tolerated, so a slightly off device can share its position.
    locationRef(volunteer).set(location(measuredAt = minutesFromNow(1))).await()
  }

  @Test
  fun update_withAnOlderMeasure_isDeniedAndANewerOneIsAccepted() = emulatorTest {
    signIn(volunteer)
    locationRef(volunteer).set(location()).await()

    // Two devices, a replayed write or offline writes sent out of order must not move the position
    // back to where the volunteer was ten minutes ago.
    assertDenied {
      locationRef(volunteer)
          .set(location(LAUSANNE_LATITUDE, LAUSANNE_LONGITUDE, EARLIER_MEASURE))
          .await()
    }

    // The same measure again is a retry rather than a regression, and a newer one goes through.
    locationRef(volunteer).set(location()).await()
    locationRef(volunteer)
        .set(location(LAUSANNE_LATITUDE, LAUSANNE_LONGITUDE, LATER_MEASURE))
        .await()

    signIn(manager)
    val stored = locationRef(volunteer).get(Source.SERVER).await()
    assertEquals(LATER_MEASURE, stored.getTimestamp("measuredAt"))
    assertEquals(LAUSANNE_LATITUDE, stored.getDouble("position.latitude"))
  }

  @Test
  fun access_beforeTheEventStarts_isDenied() = emulatorTest {
    setEventPeriod(minutesFromNow(24 * 60), minutesFromNow(48 * 60))

    // A position is the event day's: nothing is published nor read the day before.
    signIn(volunteer)
    assertDenied { locationRef(volunteer).set(location()).await() }
    signIn(manager)
    assertDenied { locationRef(volunteer).get(Source.SERVER).await() }
    assertDenied { liveLocations().get(Source.SERVER).await() }
  }

  @Test
  fun access_afterTheEventEnds_isDeniedExceptTheVolunteersOwnDelete() = emulatorTest {
    signIn(volunteer)
    locationRef(volunteer).set(location()).await()

    setEventPeriod(minutesFromNow(-48 * 60), minutesFromNow(-24 * 60))

    // A volunteer who leaves sharing on does not keep publishing days later, and neither the
    // manager nor the organizer keeps reading what is already there.
    signIn(manager)
    assertDenied { locationRef(volunteer).get(Source.SERVER).await() }
    assertDenied { liveLocations().get(Source.SERVER).await() }
    signIn(organizer)
    assertDenied { liveLocations().get(Source.SERVER).await() }

    signIn(volunteer)
    assertDenied {
      locationRef(volunteer).set(location(LAUSANNE_LATITUDE, LAUSANNE_LONGITUDE)).await()
    }

    // The cleanup stays possible. Nobody may read it back now, so the call going through without a
    // denial is the whole assertion.
    locationRef(volunteer).delete().await()
  }

  @Test
  fun access_withinTheMarginAroundTheEvent_isAllowed() = emulatorTest {
    // The margin covers a volunteer arriving early; the ongoing case is the fixture of every other
    // test here.
    setEventPeriod(minutesFromNow(30), minutesFromNow(4 * 60))
    signIn(volunteer)
    locationRef(volunteer).set(location()).await()

    // And one leaving late, half an hour after the end being still inside the hour of margin.
    setEventPeriod(minutesFromNow(-4 * 60), minutesFromNow(-30))
    signIn(volunteer)
    locationRef(volunteer).set(location()).await()

    signIn(manager)
    assertEquals(setOf(volunteer.uid), sharedUids())
  }

  @Test
  fun read_byTheTeamsManagerOrTheOrganizer_isAllowedForOnePositionAndForTheQuery() = emulatorTest {
    shareTeamPositions()

    for (user in listOf(manager, organizer)) {
      signIn(user)
      assertTrue(locationRef(volunteer).get(Source.SERVER).await().exists())
      assertEquals(setOf(volunteer.uid, otherVolunteer.uid), sharedUids())
    }
  }

  @Test
  fun read_byTheVolunteer_isAllowedForTheirOwnPositionOnly() = emulatorTest {
    shareTeamPositions()

    signIn(volunteer)
    val own = locationRef(volunteer).get(Source.SERVER).await()
    assertEquals(GENEVA_LATITUDE, own.getDouble("position.latitude"))
    // A teammate's position is their manager's business, and so is the collection as a whole.
    assertDenied { locationRef(otherVolunteer).get(Source.SERVER).await() }
    assertDenied { liveLocations().get(Source.SERVER).await() }
  }

  @Test
  fun read_byAnotherTeamsManagerOrAnOutsider_isDenied() = emulatorTest {
    shareTeamPositions()

    for (user in listOf(otherManager, outsider)) {
      signIn(user)
      assertDenied { locationRef(volunteer).get(Source.SERVER).await() }
      assertDenied { liveLocations().get(Source.SERVER).await() }
    }
  }

  @Test
  fun read_afterATeammateTurnedTheConsentOff_stillAnswersTheQuery() = emulatorTest {
    shareTeamPositions()

    setSharing(otherVolunteer, false)

    // The rules read the path only, so one volunteer's consent cannot break the manager's query.
    // Their last position is still there: withdrawing the consent stops the updates, and it is the
    // volunteer's app that removes what was already shared.
    signIn(manager)
    assertEquals(setOf(volunteer.uid, otherVolunteer.uid), sharedUids())

    signIn(otherVolunteer)
    locationRef(otherVolunteer).delete().await()

    signIn(manager)
    assertEquals(setOf(volunteer.uid), sharedUids())
  }

  @Test
  fun delete_byTheVolunteer_isAllowedAfterLeavingTheTeam() = emulatorTest {
    signIn(volunteer)
    locationRef(volunteer).set(location()).await()

    signIn(organizer)
    teamRef(TEAM_ID).update("memberIds", listOf(otherVolunteer.uid)).await()

    signIn(volunteer)
    locationRef(volunteer).delete().await()

    signIn(manager)
    assertFalse(locationRef(volunteer).get(Source.SERVER).await().exists())
  }

  @Test
  fun access_afterTheEventIsDeleted_isDeniedExceptTheVolunteersOwnDelete() = emulatorTest {
    signIn(volunteer)
    locationRef(volunteer).set(location()).await()

    signIn(organizer)
    db.collection(EVENTS).document(eventId).delete().await()
    // Deleting a document leaves its subcollections behind, so the team and the position are both
    // still stored under an event that is gone.
    assertTrue(teamRef(TEAM_ID).get(Source.SERVER).await().exists())

    // The manager of that team reads neither the position nor the collection any more.
    signIn(manager)
    assertDenied { locationRef(volunteer).get(Source.SERVER).await() }
    assertDenied { liveLocations().get(Source.SERVER).await() }

    // Nor does a volunteer share a position under it, whether a first one or an update.
    signIn(otherVolunteer)
    assertDenied { locationRef(otherVolunteer).set(location()).await() }
    signIn(volunteer)
    assertDenied {
      locationRef(volunteer).set(location(LAUSANNE_LATITUDE, LAUSANNE_LONGITUDE)).await()
    }

    // The cleanup stays possible: the volunteer still deletes what they shared. Nobody may read it
    // back now, so the call going through without a denial is the whole assertion.
    locationRef(volunteer).delete().await()
  }

  @Test
  fun create_withMalformedOrUnexpectedFields_isDenied() = emulatorTest {
    signIn(volunteer)
    val valid = location()
    for (invalid in
        listOf(
            location(latitude = 90.1),
            location(longitude = -180.1),
            location(measuredAt = "2026-10-31T09:00:00Z"),
            // The uid is the document id: storing it again would be a second source of truth.
            valid + ("userId" to volunteer.uid),
            valid - "measuredAt",
            valid - "position",
            mapOf("position" to GENEVA_LATITUDE, "measuredAt" to MEASURED_AT),
            mapOf("position" to mapOf("latitude" to GENEVA_LATITUDE), "measuredAt" to MEASURED_AT),
            mapOf(
                "position" to
                    mapOf(
                        "latitude" to GENEVA_LATITUDE,
                        "longitude" to GENEVA_LONGITUDE,
                        "accuracyMeters" to 5.0,
                    ),
                "measuredAt" to MEASURED_AT,
            ),
        )) {
      assertDenied { locationRef(volunteer).set(invalid).await() }
    }

    // The valid position is accepted, so the rules do not simply refuse everything.
    locationRef(volunteer).set(valid).await()
  }

  @Test
  fun signedOutUser_cannotReadOrWrite() = emulatorTest {
    signIn(volunteer)
    locationRef(volunteer).set(location()).await()
    auth.signOut()

    assertDenied { locationRef(volunteer).get(Source.SERVER).await() }
    assertDenied { liveLocations().get(Source.SERVER).await() }
    assertDenied { locationRef(volunteer).set(location()).await() }
    assertDenied { locationRef(volunteer).delete().await() }
  }

  /**
   * Creates an event of [organizer], ongoing so its positions may be shared, with [TEAM_ID], led by
   * [manager] with [volunteer] and [otherVolunteer] as members, and [OTHER_TEAM_ID], led by
   * [otherManager] with no member.
   */
  private suspend fun event(): String {
    signIn(organizer)
    val ref = db.collection(EVENTS).document()
    paths += ref.path
    ref.set(
            mapOf(
                "organizerId" to organizer.uid,
                "startAt" to minutesFromNow(-60),
                "endAt" to minutesFromNow(60),
            )
        )
        .await()
    createTeam(TEAM_ID, manager, listOf(volunteer.uid, otherVolunteer.uid), ref.id)
    createTeam(OTHER_TEAM_ID, otherManager, emptyList(), ref.id)
    return ref.id
  }

  /** Moves the event's period, the way a test places itself before, during or after it. */
  private suspend fun setEventPeriod(startAt: Timestamp, endAt: Timestamp) {
    signIn(organizer)
    db.collection(EVENTS).document(eventId).update("startAt", startAt, "endAt", endAt).await()
  }

  /** A timestamp [minutes] from now, negative for the past, to place a period or a measure. */
  private fun minutesFromNow(minutes: Long) = Timestamp(Instant.now().epochSecond + minutes * 60, 0)

  private suspend fun createTeam(
      teamId: String,
      manager: Account,
      memberIds: List<String>,
      eventId: String,
  ) {
    val ref = teamRef(teamId, eventId)
    paths += ref.path
    ref.set(mapOf("managerId" to manager.uid, "memberIds" to memberIds)).await()
  }

  /** Shares a position for both members of [TEAM_ID], each signed in as themself. */
  private suspend fun shareTeamPositions() {
    signIn(volunteer)
    locationRef(volunteer).set(location()).await()
    signIn(otherVolunteer)
    locationRef(otherVolunteer).set(location(LAUSANNE_LATITUDE, LAUSANNE_LONGITUDE)).await()
  }

  /** The uids the signed-in user sees in the collection of [TEAM_ID]. */
  private suspend fun sharedUids(): Set<String> =
      liveLocations().get(Source.SERVER).await().documents.map { it.id }.toSet()

  private fun teamRef(teamId: String, eventId: String = this.eventId) =
      db.collection(EVENTS).document(eventId).collection(TEAMS).document(teamId)

  private fun liveLocations(teamId: String = TEAM_ID) = teamRef(teamId).collection(LIVE_LOCATIONS)

  private fun locationRef(user: Account, teamId: String = TEAM_ID): DocumentReference =
      liveLocations(teamId).document(user.uid).also { paths += it.path }

  private fun location(
      latitude: Double = GENEVA_LATITUDE,
      longitude: Double = GENEVA_LONGITUDE,
      measuredAt: Any = MEASURED_AT,
  ): Map<String, Any> =
      mapOf(
          "position" to mapOf("latitude" to latitude, "longitude" to longitude),
          "measuredAt" to measuredAt,
      )

  /** Signs [user] in and sets the consent in their own profile, the way the settings would. */
  private suspend fun setSharing(user: Account, enabled: Boolean) {
    signIn(user)
    db.collection(USERS).document(user.uid).update(LOCATION_SHARING_ENABLED, enabled).await()
  }

  /** A new account, signed in, with a profile that shares its location. */
  private suspend fun account(): Account {
    val email = "live-location-${UUID.randomUUID()}@example.test"
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
    const val EVENTS = "events"
    const val TEAMS = "teams"
    const val LIVE_LOCATIONS = "liveLocations"
    const val USERS = "users"
    const val LOCATION_SHARING_ENABLED = "locationSharingEnabled"
    const val TEAM_ID = "team-bar"
    const val OTHER_TEAM_ID = "team-logistics"
    const val GENEVA_LATITUDE = 46.2044
    const val GENEVA_LONGITUDE = 6.1432
    const val LAUSANNE_LATITUDE = 46.5197
    const val LAUSANNE_LONGITUDE = 6.6323
    val MEASURED_AT = Timestamp(1_762_000_000, 0)
    val EARLIER_MEASURE = Timestamp(MEASURED_AT.seconds - 600, 0)
    val LATER_MEASURE = Timestamp(MEASURED_AT.seconds + 600, 0)
  }
}
