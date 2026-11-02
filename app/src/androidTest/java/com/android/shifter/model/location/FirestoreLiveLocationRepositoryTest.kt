// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.firebase.emulatorTest
import com.swent.shifter.firebase.signInForFirestore
import com.swent.shifter.firebase.step
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [FirestoreLiveLocationRepository] against the emulators, with the real `firestore.rules`.
 * `LiveLocationRulesTest` covers each rule on its own; these tests check what the repository makes
 * of them: the document it writes, what its flow emits, and the errors it throws.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreLiveLocationRepositoryTest {
  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore
  private val repository = FirestoreLiveLocationRepository(db, auth)
  private val paths = mutableSetOf<String>()
  private lateinit var organizer: Account
  private lateinit var manager: Account
  private lateinit var volunteer: Account
  private lateinit var otherVolunteer: Account
  private lateinit var eventId: String

  @Before
  fun setUp() = emulatorTest {
    organizer = account()
    manager = account()
    volunteer = account()
    otherVolunteer = account()
    eventId = event()
  }

  @After
  fun cleanUp() = emulatorTest {
    try {
      step("cleanup") { paths.forEach { FirestoreEmulatorAdmin.deleteDocument(it) } }
    } finally {
      auth.signOut()
    }
  }

  @Test
  fun shareLocation_storesTheDocumentTheSchemaDescribes() = emulatorTest {
    signIn(volunteer)

    share(volunteer, GENEVA)

    // Read as the manager, the one allowed to: the stored fields are the ones the rules validate.
    signIn(manager)
    val stored = locationRef(volunteer).get(Source.SERVER).await()
    assertEquals(setOf("position", "measuredAt"), stored.data?.keys)
    assertEquals(GENEVA.latitude, stored.getDouble("position.latitude"))
    assertEquals(GENEVA.longitude, stored.getDouble("position.longitude"))
    assertEquals(
        Timestamp(MEASURED_AT.epochSecond, MEASURED_AT.nano),
        stored.getTimestamp("measuredAt"),
    )
  }

  /** The other half of the round trip: what the repository reads back out of its own document. */
  @Test
  fun observeTeamLocations_mapsThePositionTheTimeAndTheVolunteer() = emulatorTest {
    signIn(volunteer)
    share(volunteer, GENEVA)

    signIn(manager)
    val mapped = repository.observeTeamLocations(eventId, TEAM_ID).first().single()

    assertEquals(LiveLocation(volunteer.uid, GENEVA, MEASURED_AT), mapped)
  }

  @Test
  fun shareLocation_twice_replacesThePositionWithoutDuplicating() = emulatorTest {
    signIn(volunteer)
    share(volunteer, GENEVA)

    share(volunteer, LAUSANNE, LATER)

    signIn(manager)
    val shared = repository.observeTeamLocations(eventId, TEAM_ID).first()
    assertEquals(listOf(LiveLocation(volunteer.uid, LAUSANNE, LATER)), shared)
  }

  @Test
  fun shareLocation_withAnOlderMeasure_throwsPermissionDenied() = emulatorTest {
    signIn(volunteer)
    share(volunteer, LAUSANNE, LATER)

    // The rules refuse a measure older than the stored one, and it reaches the caller as any other
    // refusal does. See LiveLocationRepositoryException.PermissionDenied: a publisher must not read
    // a revoked consent into this, which is why the case is pinned here and not only in the rules.
    assertFailsWith<LiveLocationRepositoryException.PermissionDenied> {
      share(volunteer, GENEVA, MEASURED_AT)
    }
  }

  @Test
  fun removeLocation_deletesThePositionAndCanBeRepeated() = emulatorTest {
    signIn(volunteer)
    share(volunteer, GENEVA)

    repository.removeLocation(eventId, TEAM_ID)
    // Idempotent, as the contract says: neither a second removal nor one by a volunteer who never
    // shared a position fails.
    repository.removeLocation(eventId, TEAM_ID)
    signIn(otherVolunteer)
    repository.removeLocation(eventId, TEAM_ID)

    signIn(manager)
    assertFalse(locationRef(volunteer).get(Source.SERVER).await().exists())
    assertEquals(
        emptyList<LiveLocation>(),
        repository.observeTeamLocations(eventId, TEAM_ID).first(),
    )
  }

  @Test
  fun observeTeamLocations_emitsEachChangeToTheTeam() = emulatorTest {
    signIn(manager)
    coroutineScope {
      val emissions = repository.observeTeamLocations(eventId, TEAM_ID).produceIn(this)
      try {
        assertEquals(emptyList<LiveLocation>(), emissions.receive())

        // The positions are written through the emulator's admin API: only the volunteers may write
        // them, and signing one in would take the listener's permission to read the collection away
        // from the manager.
        shareAsAdmin(volunteer, GENEVA)
        assertEquals(
            LiveLocation(volunteer.uid, GENEVA, MEASURED_AT),
            emissions.receiveUntil { it.isNotEmpty() }.single(),
        )

        shareAsAdmin(volunteer, LAUSANNE, LATER)
        assertEquals(
            LiveLocation(volunteer.uid, LAUSANNE, LATER),
            emissions.receiveUntil { it.singleOrNull()?.position == LAUSANNE }.single(),
        )

        shareAsAdmin(otherVolunteer, GENEVA)
        assertEquals(
            setOf(volunteer.uid, otherVolunteer.uid),
            emissions.receiveUntil { it.size == 2 }.map { it.userId }.toSet(),
        )

        removeAsAdmin(volunteer)
        assertEquals(otherVolunteer.uid, emissions.receiveUntil { it.size == 1 }.single().userId)
      } finally {
        // Cancelling the collection removes the listener, which `awaitClose` is there for.
        emissions.cancel()
      }
    }
  }

  @Test
  fun shareLocation_withoutTheConsent_throwsPermissionDenied() = emulatorTest {
    setSharing(volunteer, false)

    assertFailsWith<LiveLocationRepositoryException.PermissionDenied> { share(volunteer, GENEVA) }
  }

  @Test
  fun observeTeamLocations_byAVolunteer_failsWithPermissionDenied() = emulatorTest {
    signIn(volunteer)

    assertFailsWith<LiveLocationRepositoryException.PermissionDenied> {
      repository.observeTeamLocations(eventId, TEAM_ID).first()
    }
  }

  @Test
  fun observeTeamLocations_withAMalformedPosition_failsWithUnknown() = emulatorTest {
    val path = locationPath(volunteer)
    paths += path
    // No client may write any of these: the rules only accept a well-formed position.
    val malformed =
        listOf(
            mapOf("position" to "46.2044,6.1432", "measuredAt" to MEASURED_AT),
            mapOf("position" to mapOf("latitude" to 46.2044), "measuredAt" to MEASURED_AT),
            mapOf(
                "position" to mapOf("latitude" to 91.0, "longitude" to 6.1432),
                "measuredAt" to MEASURED_AT,
            ),
            mapOf("position" to mapOf("latitude" to 46.2044, "longitude" to 6.1432)),
        )
    for (fields in malformed) {
      FirestoreEmulatorAdmin.deleteDocument(path)
      FirestoreEmulatorAdmin.createDocument(path, fields)
      signIn(manager)

      assertFailsWith<LiveLocationRepositoryException.Unknown> {
        repository.observeTeamLocations(eventId, TEAM_ID).first()
      }
    }
  }

  /**
   * Creates an ongoing event of [organizer] with the team [TEAM_ID], led by [manager] with
   * [volunteer] and [otherVolunteer] as members. The period matters: the rules only allow sharing
   * and reading a position while the event is running.
   */
  private suspend fun event(): String {
    signIn(organizer)
    val ref = db.collection("events").document()
    paths += ref.path
    ref.set(
            mapOf(
                "organizerId" to organizer.uid,
                "startAt" to minutesFromNow(-60),
                "endAt" to minutesFromNow(60),
            )
        )
        .await()
    val team = ref.collection("teams").document(TEAM_ID)
    paths += team.path
    team
        .set(
            mapOf(
                "managerId" to manager.uid,
                "memberIds" to listOf(volunteer.uid, otherVolunteer.uid),
            )
        )
        .await()
    return ref.id
  }

  /**
   * Shares [position] through the repository, which takes the volunteer from the signed-in user:
   * [user] only says whose document to clean up, and must be the one signed in.
   */
  private suspend fun share(
      user: Account,
      position: GeoPosition,
      measuredAt: Instant = MEASURED_AT,
  ) {
    paths += locationPath(user)
    repository.shareLocation(eventId, TEAM_ID, position, measuredAt)
  }

  /** A timestamp [minutes] from now, negative for the past, to place the event's period. */
  private fun minutesFromNow(minutes: Long) = Timestamp(Instant.now().epochSecond + minutes * 60, 0)

  /** Shares [position] for [user] whatever the rules say, replacing the one there, if any. */
  private suspend fun shareAsAdmin(
      user: Account,
      position: GeoPosition,
      measuredAt: Instant = MEASURED_AT,
  ) {
    val path = locationPath(user)
    paths += path
    // A missing document is accepted by the cleanup API, so this doubles as a replacement.
    FirestoreEmulatorAdmin.deleteDocument(path)
    FirestoreEmulatorAdmin.createDocument(
        path,
        mapOf(
            "position" to mapOf("latitude" to position.latitude, "longitude" to position.longitude),
            "measuredAt" to measuredAt,
        ),
    )
  }

  private suspend fun removeAsAdmin(user: Account) =
      FirestoreEmulatorAdmin.deleteDocument(locationPath(user))

  private fun locationRef(user: Account) =
      db.collection("events")
          .document(eventId)
          .collection("teams")
          .document(TEAM_ID)
          .collection("liveLocations")
          .document(user.uid)

  private fun locationPath(user: Account) =
      "events/$eventId/teams/$TEAM_ID/liveLocations/${user.uid}"

  /** Signs [user] in and sets the consent in their own profile, the way the settings would. */
  private suspend fun setSharing(user: Account, enabled: Boolean) {
    signIn(user)
    db.collection("users").document(user.uid).update("locationSharingEnabled", enabled).await()
  }

  /** A new account, signed in, with a profile that shares its location. */
  private suspend fun account(): Account {
    val email = "live-location-repo-${UUID.randomUUID()}@example.test"
    val password = "test-password-123"
    val user =
        checkNotNull(
            auth
                .signInForFirestore { auth.createUserWithEmailAndPassword(email, password).await() }
                .user
        )
    val profile = db.collection("users").document(user.uid)
    paths += profile.path
    // The rules only let a profile start with the consent off, as the app's opt-in does.
    profile
        .set(
            mapOf(
                "displayName" to "Volunteer",
                "email" to email,
                "locationSharingEnabled" to false,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )
        .await()
    profile.update("locationSharingEnabled", true).await()
    return Account(user.uid, EmailAuthProvider.getCredential(email, password))
  }

  private suspend fun signIn(user: Account) {
    auth.signInForFirestore { auth.signInWithCredential(user.credential).await() }
  }

  /** Skips the emissions before the first one [predicate] accepts. */
  private suspend fun ReceiveChannel<List<LiveLocation>>.receiveUntil(
      predicate: (List<LiveLocation>) -> Boolean
  ): List<LiveLocation> {
    while (true) {
      val emission = receive()
      if (predicate(emission)) return emission
    }
  }

  private inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit) {
    val failure = runCatching { block() }.exceptionOrNull()
    assertTrue("Expected ${T::class.simpleName}, got $failure", failure is T)
  }

  private data class Account(val uid: String, val credential: AuthCredential)

  private companion object {
    const val TEAM_ID = "team-bar"
    val GENEVA = GeoPosition(46.2044, 6.1432)
    val LAUSANNE = GeoPosition(46.5197, 6.6323)
    val MEASURED_AT: Instant = Instant.parse("2026-11-01T09:00:00Z")
    val LATER: Instant = MEASURED_AT.plusSeconds(60)
  }
}
