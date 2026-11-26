// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.firebase.ScratchEvents
import com.swent.shifter.firebase.emulatorTest
import com.swent.shifter.firebase.signInForFirestore
import com.swent.shifter.firebase.step
import com.swent.shifter.model.event.EventSchema
import com.swent.shifter.model.firestore.toFirestoreTimestamp
import com.swent.shifter.model.membership.MembershipRequestSchema
import com.swent.shifter.model.team.TeamSchema
import java.time.Duration
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
 * Security rules that keep the `volunteerIds` of the shifts in step with the requests (#594), run
 * against the Firestore emulator: a volunteer puts themself in the shifts their pending request
 * lists, in the same commit, and out of those it no longer lists or once withdrawn; the organizer
 * takes volunteers out but never puts one in.
 *
 * The scratch event runs for a week from midnight UTC. Its shifts, and the requests a test starts
 * from, are written as an admin.
 */
@RunWith(AndroidJUnit4::class)
class ShiftVolunteerRulesTest {

  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore

  /** Paths deleted in [cleanUp] through the emulator's admin API, whatever the rules allow. */
  private val paths = mutableListOf<String>()

  /** Auth accounts deleted in [cleanUp], so the emulator does not keep them between tests. */
  private val accounts = mutableListOf<Account>()

  private lateinit var organizer: Account
  private lateinit var volunteer: Account
  private lateinit var other: Account
  private lateinit var event: DocumentReference

  @Before
  fun setUp() = emulatorTest {
    volunteer = account()
    other = account()
    // Signed in last, so the scratch event is theirs.
    organizer = account()
    event =
        ScratchEvents.create(
            mapOf(
                EventSchema.START_AT to EVENT_START.ts(),
                EventSchema.END_AT to EVENT_START.plus(Duration.ofDays(7)).ts(),
            )
        )
    createTeam(TEAM, mapOf(TeamSchema.NAME to "Bar"))
  }

  @After
  fun cleanUp() = emulatorTest {
    try {
      step("cleanup") { (paths + event.path).forEach { FirestoreEmulatorAdmin.deleteDocument(it) } }
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
  fun apply_withShifts_listsTheVolunteerInThem() = emulatorTest {
    openShifts(10, 12)
    signIn(volunteer)

    commit { applyTo(it, 10, 12) }

    assertEquals(listOf(volunteer.uid), volunteersOf(10))
    assertEquals(listOf(volunteer.uid), volunteersOf(12))
  }

  @Test
  fun apply_withEveryShiftOfAWeek_isAllowed() = emulatorTest {
    // A volunteer chooses as many shifts as they like, here every 2 h slot of a week, although
    // each shift reads the request after the commit: the rules' limit on reads must not stop them.
    val hours = (0 until 7 * 12).map { it * 2 }
    openShifts(*hours.toIntArray())
    signIn(volunteer)

    commit { applyTo(it, *hours.toIntArray()) }

    assertEquals(listOf(volunteer.uid), volunteersOf(hours.last()))
  }

  @Test
  fun apply_withShiftIdsNotAList_isDenied() = emulatorTest {
    signIn(volunteer)

    assertDenied {
      request(volunteer).set(application(volunteer, 10) + (SHIFT_IDS to shiftId(10))).await()
    }
  }

  @Test
  fun join_aShiftTheRequestDoesNotList_isDenied() = emulatorTest {
    openShifts(10, 12)
    signIn(volunteer)

    assertDenied {
      commit {
        applyTo(it, 10)
        it.update(shift(12), VOLUNTEER_IDS, FieldValue.arrayUnion(volunteer.uid))
      }
    }
  }

  @Test
  fun join_withoutAPendingRequest_isDenied() = emulatorTest {
    openShifts(10, 12)
    storeRequest(volunteer, 12, status = "ACCEPTED")
    signIn(volunteer)

    // An accepted request, whether or not it lists the shift: the shifts no longer change.
    assertDenied { shift(10).update(VOLUNTEER_IDS, FieldValue.arrayUnion(volunteer.uid)).await() }
    assertDenied { shift(12).update(VOLUNTEER_IDS, FieldValue.arrayUnion(volunteer.uid)).await() }

    // No request at all.
    signIn(other)
    assertDenied { shift(10).update(VOLUNTEER_IDS, FieldValue.arrayUnion(other.uid)).await() }
  }

  @Test
  fun join_withSomeoneElse_isDenied() = emulatorTest {
    openShifts(10)
    signIn(volunteer)

    assertDenied {
      commit {
        applyTo(it, 10, join = false)
        it.update(shift(10), VOLUNTEER_IDS, listOf(other.uid))
      }
    }
    assertDenied {
      commit {
        applyTo(it, 10, join = false)
        it.update(shift(10), VOLUNTEER_IDS, listOf(volunteer.uid, other.uid))
      }
    }
    assertDenied {
      commit {
        applyTo(it, 10, join = false)
        it.update(shift(10), VOLUNTEER_IDS, listOf(volunteer.uid, volunteer.uid))
      }
    }
  }

  @Test
  fun changeShifts_whilePending_movesTheVolunteer() = emulatorTest {
    openShifts(12)
    sendRequest(volunteer, 10)
    signIn(volunteer)

    commit { changeShifts(it, from = 10, to = 12) }

    assertEquals(emptyList<String>(), volunteersOf(10))
    assertEquals(listOf(volunteer.uid), volunteersOf(12))
  }

  @Test
  fun changeShifts_onceDecided_isDenied() = emulatorTest {
    openShifts(12)
    sendRequest(volunteer, 10, status = "ACCEPTED")
    sendRequest(other, 14, status = "REJECTED")
    signIn(volunteer)
    assertDenied { commit { changeShifts(it, from = 10, to = 12) } }

    signIn(other)
    assertDenied { request(other).update(SHIFT_IDS, listOf(shiftId(12))).await() }
  }

  @Test
  fun changeShifts_withAnythingElse_isDenied() = emulatorTest {
    sendRequest(volunteer, 10)
    signIn(volunteer)

    assertDenied {
      request(volunteer)
          .update(mapOf(SHIFT_IDS to emptyList<String>(), STATUS to "ACCEPTED"))
          .await()
    }
    assertDenied {
      request(volunteer).update(MembershipRequestSchema.AVAILABILITY, emptyList<Any>()).await()
    }
  }

  @Test
  fun changeShifts_bySomeoneElse_isDenied() = emulatorTest {
    sendRequest(volunteer, 10)

    // Neither another volunteer nor the organizer chooses the shifts of a volunteer.
    for (user in listOf(other, organizer)) {
      signIn(user)
      assertDenied { request(volunteer).update(SHIFT_IDS, emptyList<String>()).await() }
    }
  }

  @Test
  fun changeShifts_onceTheEventEnded_isDenied() = emulatorTest {
    paths.add(event.path)
    signIn(organizer)
    event =
        ScratchEvents.create(
            mapOf(
                EventSchema.START_AT to ENDED_EVENT_START.ts(),
                EventSchema.END_AT to ENDED_EVENT_START.plus(Duration.ofDays(1)).ts(),
            )
        )
    createTeam(TEAM, mapOf(TeamSchema.NAME to "Bar"))
    storeRequest(volunteer, 10)
    signIn(volunteer)

    assertDenied { request(volunteer).update(SHIFT_IDS, emptyList<String>()).await() }
  }

  @Test
  fun changeShifts_whileTheTeamIsBeingDeleted_isDenied() = emulatorTest {
    createTeam(CLOSING_TEAM, mapOf(TeamSchema.DELETING to true))
    sendRequest(volunteer, 10, team = CLOSING_TEAM)
    signIn(volunteer)

    assertDenied { request(volunteer).update(SHIFT_IDS, emptyList<String>()).await() }
  }

  @Test
  fun leave_aShiftTheRequestStillLists_isDenied() = emulatorTest {
    sendRequest(volunteer, 10)
    signIn(volunteer)

    assertDenied { shift(10).update(VOLUNTEER_IDS, FieldValue.arrayRemove(volunteer.uid)).await() }
  }

  @Test
  fun withdraw_fromAShiftTheOrganizerTookThemOutOf_isAllowed() = emulatorTest {
    sendRequest(volunteer, 10)
    signIn(organizer)
    shift(10).update(VOLUNTEER_IDS, emptyList<String>()).await()
    signIn(volunteer)

    // Their request still lists the shift: taking them out of it again changes nothing.
    commit { batch ->
      batch.delete(request(volunteer))
      batch.update(shift(10), VOLUNTEER_IDS, FieldValue.arrayRemove(volunteer.uid))
    }

    assertEquals(emptyList<String>(), volunteersOf(10))
  }

  @Test
  fun withdraw_takesTheVolunteerOutOfTheirShifts() = emulatorTest {
    sendRequest(volunteer, 10, 12)
    signIn(volunteer)

    commit { batch ->
      batch.delete(request(volunteer))
      listOf(10, 12).forEach {
        batch.update(shift(it), VOLUNTEER_IDS, FieldValue.arrayRemove(volunteer.uid))
      }
    }

    assertEquals(emptyList<String>(), volunteersOf(10))
    assertEquals(emptyList<String>(), volunteersOf(12))
  }

  @Test
  fun organizer_takesVolunteersOutButNeverPutsOneIn() = emulatorTest {
    openShifts(10, volunteers = listOf(volunteer.uid, other.uid))
    openShifts(12)
    signIn(organizer)

    // Two at once, as deleting a team does, whatever their requests say.
    shift(10).update(VOLUNTEER_IDS, emptyList<String>()).await()

    assertEquals(emptyList<String>(), volunteersOf(10))
    assertDenied { shift(12).update(VOLUNTEER_IDS, listOf(volunteer.uid)).await() }
  }

  @Test
  fun someoneElse_takingAVolunteerOut_isDenied() = emulatorTest {
    sendRequest(volunteer, 10)
    signIn(other)

    assertDenied { shift(10).update(VOLUNTEER_IDS, FieldValue.arrayRemove(volunteer.uid)).await() }
  }

  @Test
  fun someoneElse_reorderingTheVolunteers_isDenied() = emulatorTest {
    openShifts(10, volunteers = listOf(volunteer.uid, other.uid))
    signIn(other)

    // Only a write that changes nothing passes: a new order is a change.
    assertDenied { shift(10).update(VOLUNTEER_IDS, listOf(other.uid, volunteer.uid)).await() }
  }

  /** Opens the shifts starting at [hours], listing [volunteers], as an admin. */
  private suspend fun openShifts(vararg hours: Int, volunteers: List<String> = emptyList()) {
    admin("open shifts") {
      hours.forEach { hour ->
        val path = shift(hour).path
        createDocument(
            path,
            mapOf(
                VOLUNTEER_IDS to volunteers,
                ShiftSchema.START_AT to at(hour),
                ShiftSchema.END_AT to at(hour + 2),
                ShiftSchema.CREATED_AT to EVENT_START,
            ),
        )
        paths.add(0, path)
      }
    }
  }

  /**
   * Stores the request of [user] choosing the shifts at [hours] with [status], and opens those
   * shifts listing them, as an admin: the state a test starts from.
   */
  private suspend fun sendRequest(
      user: Account,
      vararg hours: Int,
      status: String = "PENDING",
      team: String = TEAM,
  ) {
    openShifts(*hours, volunteers = listOf(user.uid))
    storeRequest(user, *hours, status = status, team = team)
  }

  /** Stores the request of [user] choosing the shifts at [hours], as an admin, leaving them be. */
  private suspend fun storeRequest(
      user: Account,
      vararg hours: Int,
      status: String = "PENDING",
      team: String = TEAM,
  ) {
    admin("request") {
      val path = request(user).path
      createDocument(
          path,
          application(user, *hours) +
              mapOf(
                  STATUS to status,
                  MembershipRequestSchema.TEAM_ID to team,
                  MembershipRequestSchema.CREATED_AT to EVENT_START,
              ),
      )
      paths.add(0, path)
    }
  }

  /** Adds to [batch] the application of [volunteer] to the shifts at [hours], putting them in. */
  private fun applyTo(batch: WriteBatch, vararg hours: Int, join: Boolean = true) {
    paths.add(0, request(volunteer).path)
    batch.set(request(volunteer), application(volunteer, *hours))
    if (join) {
      hours.forEach { batch.update(shift(it), VOLUNTEER_IDS, FieldValue.arrayUnion(volunteer.uid)) }
    }
  }

  /** Adds to [batch] the move of [volunteer]'s pending request from the shift at [from] to [to]. */
  private fun changeShifts(batch: WriteBatch, from: Int, to: Int) {
    batch.update(
        request(volunteer),
        mapOf(SHIFT_IDS to listOf(shiftId(to)), MembershipRequestSchema.AVAILABILITY to slots(to)),
    )
    batch.update(shift(from), VOLUNTEER_IDS, FieldValue.arrayRemove(volunteer.uid))
    batch.update(shift(to), VOLUNTEER_IDS, FieldValue.arrayUnion(volunteer.uid))
  }

  /** The fields of a new request from [user] choosing the shifts at [hours]. */
  private fun application(user: Account, vararg hours: Int): Map<String, Any> =
      mapOf(
          MembershipRequestSchema.USER_ID to user.uid,
          MembershipRequestSchema.TEAM_ID to TEAM,
          MembershipRequestSchema.AVAILABILITY to slots(*hours),
          SHIFT_IDS to hours.map { shiftId(it) },
          STATUS to "PENDING",
          MembershipRequestSchema.CREATED_AT to FieldValue.serverTimestamp(),
      )

  private fun slots(vararg hours: Int): List<Map<String, Timestamp>> = hours.map {
    mapOf(
        MembershipRequestSchema.SlotFields.START_AT to at(it).ts(),
        MembershipRequestSchema.SlotFields.END_AT to at(it + 2).ts(),
    )
  }

  private suspend fun commit(writes: (WriteBatch) -> Unit) {
    db.batch().also(writes).commit().await()
  }

  private suspend fun volunteersOf(hour: Int): List<*>? =
      shift(hour).get(Source.SERVER).await().get(VOLUNTEER_IDS) as List<*>?

  private suspend fun admin(name: String, block: suspend FirestoreEmulatorAdmin.() -> Unit) {
    step(name) { FirestoreEmulatorAdmin.block() }
  }

  private fun request(user: Account): DocumentReference =
      event.collection(MembershipRequestSchema.COLLECTION).document(user.uid)

  private fun shift(hour: Int): DocumentReference =
      event.collection(ShiftSchema.COLLECTION).document(shiftId(hour))

  private suspend fun createTeam(teamId: String, fields: Map<String, Any>) {
    val path = "${event.path}/${TeamSchema.COLLECTION}/$teamId"
    admin("team") { createDocument(path, fields) }
    paths.add(path)
  }

  private suspend fun account(): Account {
    val email = "shift-volunteer-rules-${UUID.randomUUID()}@example.test"
    val password = "test-password-123"
    // Creating the account signs it in, so Firestore switches to it too.
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

  private suspend fun assertDenied(block: suspend () -> Unit) {
    val failure = runCatching { block() }.exceptionOrNull()
    assertTrue(
        "expected a permission denial, but was: $failure",
        (failure as? FirebaseFirestoreException)?.code ==
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
  }

  private data class Account(val uid: String, val credential: AuthCredential)

  private fun Instant.ts(): Timestamp = toFirestoreTimestamp()

  private companion object {
    const val TEAM = "team-bar"
    const val CLOSING_TEAM = "team-closing"
    const val VOLUNTEER_IDS = ShiftSchema.VOLUNTEER_IDS
    const val SHIFT_IDS = MembershipRequestSchema.SHIFT_IDS
    const val STATUS = MembershipRequestSchema.STATUS

    val EVENT_START: Instant = Instant.parse("2036-07-18T00:00:00Z")
    val ENDED_EVENT_START: Instant = Instant.parse("2020-07-18T00:00:00Z")

    /** [hour] o'clock UTC from the event's start; 24 and beyond run into the next days. */
    fun at(hour: Int): Instant = EVENT_START.plus(Duration.ofHours(hour.toLong()))

    fun shiftId(hour: Int): String = shiftIdOf(at(hour))
  }
}
