// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.checkin

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.firebase.emulatorTest
import com.swent.shifter.firebase.signInForFirestore
import com.swent.shifter.firebase.step
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [FirestoreCheckInRepository] against the emulators, with the real `firestore.rules`.
 * `CheckInRulesTest` covers each rule on its own; these tests check what the repository makes of
 * them: the fields it writes, the errors it throws, and what its flows emit.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreCheckInRepositoryTest {
  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore
  private val repository = FirestoreCheckInRepository(db, auth)
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
      step("cleanup") { paths.forEach { FirestoreEmulatorAdmin.deleteDocument(it) } }
    } finally {
      auth.signOut()
    }
  }

  @Test
  fun checkIn_bySelf_recordsTheVolunteerAndTheServerTime() = emulatorTest {
    signIn(volunteer)
    val before = Instant.now()

    checkIn(volunteer, CheckInMethod.SELF)

    val stored = repository.observeMyCheckIns(eventId, volunteer.uid).first().single()
    assertEquals(eventId, stored.eventId)
    assertEquals(MISSION_ID, stored.missionId)
    assertEquals(volunteer.uid, stored.userId)
    assertEquals(CheckInMethod.SELF, stored.method)
    assertEquals(volunteer.uid, stored.checkedInBy)
    assertNear(before, stored.checkedInAt)
  }

  @Test
  fun checkIn_byManager_recordsTheManagerAsAuthor() = emulatorTest {
    signIn(manager)

    checkIn(volunteer, CheckInMethod.MANAGER)

    val stored = repository.observeCheckIns(eventId, MISSION_ID).first().single()
    assertEquals(volunteer.uid, stored.userId)
    assertEquals(CheckInMethod.MANAGER, stored.method)
    assertEquals(manager.uid, stored.checkedInBy)
  }

  @Test
  fun checkIn_byOrganizer_toAMissionOfNoTeam_isRecorded() = emulatorTest {
    signIn(organizer)

    checkIn(volunteer, CheckInMethod.MANAGER, GENERAL_MISSION_ID)

    val stored = repository.observeCheckIns(eventId, GENERAL_MISSION_ID).first().single()
    assertEquals(organizer.uid, stored.checkedInBy)
  }

  @Test
  fun checkIn_ofAnUnassignedVolunteer_throwsNotAllowed() = emulatorTest {
    signIn(outsider)
    assertFailsWith<CheckInRepositoryException.NotAllowed> { checkIn(outsider, CheckInMethod.SELF) }

    signIn(manager)
    assertFailsWith<CheckInRepositoryException.NotAllowed> {
      checkIn(outsider, CheckInMethod.MANAGER)
    }
  }

  @Test
  fun checkIn_bySelfForSomeoneElse_throwsNotAllowed() = emulatorTest {
    signIn(otherVolunteer)

    assertFailsWith<CheckInRepositoryException.NotAllowed> {
      checkIn(volunteer, CheckInMethod.SELF)
    }
  }

  @Test
  fun checkIn_asManagerByAnOutsider_throwsNotAllowed() = emulatorTest {
    signIn(outsider)

    assertFailsWith<CheckInRepositoryException.NotAllowed> {
      checkIn(volunteer, CheckInMethod.MANAGER)
    }
  }

  @Test
  fun checkIn_whenSignedOut_throwsNotAllowed() = emulatorTest {
    auth.signOut()

    assertFailsWith<CheckInRepositoryException.NotAllowed> {
      checkIn(volunteer, CheckInMethod.SELF)
    }
  }

  @Test
  fun checkIn_twice_throwsAlreadyCheckedInAndKeepsTheFirst() = emulatorTest {
    signIn(volunteer)
    checkIn(volunteer, CheckInMethod.SELF)
    val first = repository.observeMyCheckIns(eventId, volunteer.uid).first().single()

    assertFailsWith<CheckInRepositoryException.AlreadyCheckedIn> {
      checkIn(volunteer, CheckInMethod.SELF)
    }
    // The manager may read the check-in, so they get the same answer.
    signIn(manager)
    assertFailsWith<CheckInRepositoryException.AlreadyCheckedIn> {
      checkIn(volunteer, CheckInMethod.MANAGER)
    }

    assertEquals(listOf(first), repository.observeCheckIns(eventId, MISSION_ID).first())
  }

  @Test
  fun observeCheckIns_emitsEmptyThenEachNewCheckIn() = emulatorTest {
    signIn(manager)
    coroutineScope {
      val emissions = repository.observeCheckIns(eventId, MISSION_ID).produceIn(this)
      try {
        assertEquals(emptyList<CheckIn>(), emissions.receive())

        checkIn(volunteer, CheckInMethod.MANAGER)
        assertEquals(setOf(volunteer.uid), emissions.receiveUntil { it.size == 1 }.userIds())

        checkIn(otherVolunteer, CheckInMethod.MANAGER)
        assertEquals(
            setOf(volunteer.uid, otherVolunteer.uid),
            emissions.receiveUntil { it.size == 2 }.userIds(),
        )
      } finally {
        emissions.cancel()
      }
    }
  }

  @Test
  fun observeCheckIns_byAVolunteer_failsWithNotAllowed() = emulatorTest {
    signIn(volunteer)

    assertFailsWith<CheckInRepositoryException.NotAllowed> {
      repository.observeCheckIns(eventId, MISSION_ID).first()
    }
  }

  @Test
  fun observeMyCheckIns_keepsOnlyTheirOwnCheckInsOfThisEvent() = emulatorTest {
    val otherEventId = event()
    signIn(volunteer)
    checkIn(volunteer, CheckInMethod.SELF)
    checkIn(volunteer, CheckInMethod.SELF, GENERAL_MISSION_ID)
    repository.checkIn(otherEventId, MISSION_ID, volunteer.uid, CheckInMethod.SELF)
    paths += checkInPath(volunteer, MISSION_ID, otherEventId)
    signIn(otherVolunteer)
    checkIn(otherVolunteer, CheckInMethod.SELF)

    signIn(volunteer)
    val mine = repository.observeMyCheckIns(eventId, volunteer.uid).first()

    assertEquals(setOf(MISSION_ID, GENERAL_MISSION_ID), mine.map { it.missionId }.toSet())
    assertTrue(mine.all { it.eventId == eventId && it.userId == volunteer.uid })
  }

  @Test
  fun observeMyCheckIns_ofSomeoneElse_failsWithNotAllowed() = emulatorTest {
    signIn(otherVolunteer)

    assertFailsWith<CheckInRepositoryException.NotAllowed> {
      repository.observeMyCheckIns(eventId, volunteer.uid).first()
    }
  }

  @Test
  fun observeMyCheckIns_withAMalformedCheckIn_failsWithUnknown() = emulatorTest {
    // No client may write it: the rules only accept a well-formed check-in.
    val path = checkInPath(volunteer, MISSION_ID, eventId)
    paths += path
    FirestoreEmulatorAdmin.createDocument(
        path,
        mapOf(
            "userId" to volunteer.uid,
            "method" to "GPS",
            "checkedInAt" to Instant.now(),
            "checkedInBy" to volunteer.uid,
        ),
    )
    signIn(volunteer)

    assertFailsWith<CheckInRepositoryException.Unknown> {
      repository.observeMyCheckIns(eventId, volunteer.uid).first()
    }
  }

  /**
   * The promise of [CheckInRepository.observeMyCheckIns]: a check-in made offline is emitted at
   * once, with an estimated time, while [CheckInRepository.checkIn] waits for the connection.
   */
  @Test
  fun observeMyCheckIns_emitsAnOfflineCheckInBeforeTheServerHasIt() = emulatorTest {
    signIn(volunteer)
    coroutineScope {
      val emissions = repository.observeMyCheckIns(eventId, volunteer.uid).produceIn(this)
      try {
        assertEquals(emptyList<CheckIn>(), emissions.receive())
        db.disableNetwork().await()
        val before = Instant.now()
        val pendingCheckIn =
            async(start = CoroutineStart.UNDISPATCHED) { checkIn(volunteer, CheckInMethod.SELF) }

        val pending = emissions.receiveUntil { it.isNotEmpty() }.single()
        assertEquals(CheckInMethod.SELF, pending.method)
        assertNear(before, pending.checkedInAt)
        assertTrue("checkIn returned while offline", pendingCheckIn.isActive)

        db.enableNetwork().await()
        step("check-in sent once online") { pendingCheckIn.await() }
      } finally {
        db.enableNetwork().await()
        emissions.cancel()
      }
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
    val team = ref.collection("teams").document(TEAM_ID)
    paths += team.path
    team.set(mapOf("managerId" to manager.uid, "memberIds" to emptyList<String>())).await()
    for ((missionId, teamId) in listOf(MISSION_ID to TEAM_ID, GENERAL_MISSION_ID to null)) {
      val mission = ref.collection("missions").document(missionId)
      paths += mission.path
      mission
          .set(
              mapOf(
                  "teamId" to teamId,
                  "assigneeIds" to listOf(volunteer.uid, otherVolunteer.uid),
              )
          )
          .await()
    }
    return ref.id
  }

  /** Checks [user] in to [missionId] of [eventId], and remembers the document for the cleanup. */
  private suspend fun checkIn(
      user: Account,
      method: CheckInMethod,
      missionId: String = MISSION_ID,
  ) {
    paths += checkInPath(user, missionId, eventId)
    repository.checkIn(eventId, missionId, user.uid, method)
  }

  private fun checkInPath(user: Account, missionId: String, eventId: String) =
      "events/$eventId/missions/$missionId/checkIns/${user.uid}"

  private suspend fun account(): Account {
    val email = "check-in-repo-${UUID.randomUUID()}@example.test"
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
    const val TEAM_ID = "team-bar"
    const val MISSION_ID = "mission-bar"
    const val GENERAL_MISSION_ID = "mission-general"

    /** The device and the emulator host keep their own clocks, so a little drift is allowed. */
    val CLOCK_TOLERANCE: Duration = Duration.ofMinutes(2)
  }

  private fun List<CheckIn>.userIds() = map { it.userId }.toSet()

  /** Skips the emissions before the first one [predicate] accepts. */
  private suspend fun ReceiveChannel<List<CheckIn>>.receiveUntil(
      predicate: (List<CheckIn>) -> Boolean
  ): List<CheckIn> {
    while (true) {
      val emission = receive()
      if (predicate(emission)) return emission
    }
  }

  private fun assertNear(expected: Instant, actual: Instant) {
    assertTrue(
        "Expected about $expected, got $actual",
        Duration.between(expected, actual).abs() <= CLOCK_TOLERANCE,
    )
  }

  private inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit) {
    val failure = runCatching { block() }.exceptionOrNull()
    assertTrue("Expected ${T::class.simpleName}, got $failure", failure is T)
  }
}
