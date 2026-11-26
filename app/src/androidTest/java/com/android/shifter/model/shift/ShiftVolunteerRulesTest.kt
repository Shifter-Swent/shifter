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
 * lists before they start, and out of those it no longer lists or once withdrawn, leaving the
 * others in their order; the organizer takes volunteers out but never puts one in. An application
 * carries no shifts yet.
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
      step("cleanup") { FirestoreEmulatorAdmin.deleteDocuments((paths + event.path).distinct()) }
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

  /**
   * Until the app puts the volunteer in their shifts in the same commit (#594), an application with
   * shifts would leave them out of those shifts: it is refused, the same one without them is not.
   */
  @Test
  fun apply_withShiftIds_isDenied() = emulatorTest {
    openShifts(10)
    signIn(volunteer)
    paths.add(0, request(volunteer).path)
    val application = application(volunteer, 10) + (CREATED_AT to FieldValue.serverTimestamp())

    assertDenied { request(volunteer).set(application).await() }
    request(volunteer).set(application - SHIFT_IDS).await()
  }

  /**
   * A volunteer chooses as many shifts as they like, here every 2 h slot of a week, although each
   * shift reads the request after the commit. As every shift reads the same request, this shows
   * that the emulator does not stop it, not that production counts that read once against its limit
   * on reads.
   */
  @Test
  fun changeShifts_toEveryShiftOfAWeek_isAllowed() = emulatorTest {
    val hours = (0 until 7 * 12).map { it * 2 }
    openShifts(*hours.toIntArray())
    storeRequest(volunteer)
    signIn(volunteer)

    commit { changeShifts(it, from = emptyList(), to = hours) }

    assertEquals(listOf(volunteer.uid), volunteersOf(hours.first()))
    assertEquals(listOf(volunteer.uid), volunteersOf(hours.last()))
  }

  /**
   * The known gap: a pending request listing a shift lets the volunteer put themself in it without
   * writing the request, e.g. back in once the organizer took them out of it.
   */
  @Test
  fun join_aShiftThePendingRequestLists_putsTheVolunteerIn() = emulatorTest {
    openShifts(10)
    storeRequest(volunteer, 10)
    signIn(volunteer)

    shift(10).update(VOLUNTEER_IDS, FieldValue.arrayUnion(volunteer.uid)).await()

    assertEquals(listOf(volunteer.uid), volunteersOf(10))
  }

  @Test
  fun join_aShiftTheRequestDoesNotList_isDenied() = emulatorTest {
    openShifts(10, 12)
    storeRequest(volunteer, 10)
    signIn(volunteer)

    assertDenied { shift(12).update(VOLUNTEER_IDS, FieldValue.arrayUnion(volunteer.uid)).await() }
  }

  @Test
  fun join_aShiftThatStarted_isDenied() = emulatorTest {
    openShifts(LONG_AGO)
    storeRequest(volunteer, LONG_AGO)
    signIn(volunteer)

    assertDenied {
      shift(LONG_AGO).update(VOLUNTEER_IDS, FieldValue.arrayUnion(volunteer.uid)).await()
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
    openShifts(12, volunteers = listOf(other.uid))
    storeRequest(volunteer, 10, 12)
    signIn(volunteer)

    assertDenied { shift(10).update(VOLUNTEER_IDS, listOf(other.uid)).await() }
    assertDenied { shift(10).update(VOLUNTEER_IDS, listOf(volunteer.uid, other.uid)).await() }
    // In place of someone else.
    assertDenied { shift(12).update(VOLUNTEER_IDS, listOf(volunteer.uid)).await() }
  }

  @Test
  fun join_twice_isDenied() = emulatorTest {
    openShifts(10)
    storeRequest(volunteer, 10)
    signIn(volunteer)

    assertDenied { shift(10).update(VOLUNTEER_IDS, listOf(volunteer.uid, volunteer.uid)).await() }
  }

  @Test
  fun join_reorderingTheOthers_isDenied() = emulatorTest {
    openShifts(10, volunteers = OTHERS)
    storeRequest(volunteer, 10)
    signIn(volunteer)

    assertDenied { shift(10).update(VOLUNTEER_IDS, OTHERS.reversed() + volunteer.uid).await() }
  }

  @Test
  fun changeShifts_whilePending_movesTheVolunteer() = emulatorTest {
    openShifts(12)
    sendRequest(volunteer, 10)
    signIn(volunteer)

    commit { changeShifts(it, from = listOf(10), to = listOf(12)) }

    assertEquals(emptyList<String>(), volunteersOf(10))
    assertEquals(listOf(volunteer.uid), volunteersOf(12))
  }

  @Test
  fun changeShifts_onceDecided_isDenied() = emulatorTest {
    openShifts(12)
    sendRequest(volunteer, 10, status = "ACCEPTED")
    sendRequest(other, 14, status = "REJECTED")
    signIn(volunteer)
    assertDenied { commit { changeShifts(it, from = listOf(10), to = listOf(12)) } }

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

  /** An absent shiftIds reads as empty, so only availability may not be deleted. */
  @Test
  fun changeShifts_withShiftsOrSlotsNotAList_isDenied() = emulatorTest {
    sendRequest(volunteer, 10)
    signIn(volunteer)

    assertDenied { request(volunteer).update(SHIFT_IDS, shiftId(10)).await() }
    for (slots in listOf(slots(10).first(), FieldValue.delete())) {
      assertDenied {
        request(volunteer).update(MembershipRequestSchema.AVAILABILITY, slots).await()
      }
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
  fun leave_withSomeoneElse_isDenied() = emulatorTest {
    openShifts(10, volunteers = listOf(volunteer.uid))
    storeRequest(volunteer, 12)
    signIn(volunteer)

    assertDenied { shift(10).update(VOLUNTEER_IDS, listOf(other.uid)).await() }
  }

  @Test
  fun leave_reorderingTheOthers_isDenied() = emulatorTest {
    openShifts(10, volunteers = OTHERS + volunteer.uid)
    storeRequest(volunteer, 12)
    signIn(volunteer)

    assertDenied { shift(10).update(VOLUNTEER_IDS, OTHERS.reversed()).await() }
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
  fun organizer_reorderingTheVolunteers_isAllowed() = emulatorTest {
    openShifts(10, volunteers = OTHERS)
    signIn(organizer)

    shift(10).update(VOLUNTEER_IDS, OTHERS.reversed()).await()

    assertEquals(OTHERS.reversed(), volunteersOf(10))
  }

  @Test
  fun organizer_listingAVolunteerTwice_isDenied() = emulatorTest {
    openShifts(10, volunteers = OTHERS)
    signIn(organizer)

    assertDenied { shift(10).update(VOLUNTEER_IDS, listOf(OTHERS[0], OTHERS[0])).await() }
  }

  /** Neither the volunteer leaving it nor the organizer makes volunteerIds anything but a list. */
  @Test
  fun volunteers_notAList_isDenied() = emulatorTest {
    openShifts(10, volunteers = listOf(volunteer.uid))
    storeRequest(volunteer, 12)

    for (user in listOf(volunteer, organizer)) {
      signIn(user)
      for (volunteers in listOf(null, volunteer.uid, FieldValue.delete())) {
        assertDenied { shift(10).update(VOLUNTEER_IDS, volunteers).await() }
      }
    }
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

  /** Opens the shifts starting at [hours], listing [volunteers], as an admin, in one commit. */
  private suspend fun openShifts(vararg hours: Int, volunteers: List<String> = emptyList()) {
    val shifts = hours.associate { hour ->
      shift(hour).path to
          mapOf(
              VOLUNTEER_IDS to volunteers,
              ShiftSchema.START_AT to at(hour),
              ShiftSchema.END_AT to at(hour + 2),
              ShiftSchema.CREATED_AT to EVENT_START,
          )
    }
    admin("open shifts") { createDocuments(shifts) }
    paths.addAll(0, shifts.keys)
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
                  CREATED_AT to EVENT_START,
              ),
      )
      paths.add(0, path)
    }
  }

  /**
   * Adds to [batch] the move of [volunteer]'s pending request from the shifts at [from] to [to].
   */
  private fun changeShifts(batch: WriteBatch, from: List<Int>, to: List<Int>) {
    batch.update(
        request(volunteer),
        mapOf(
            SHIFT_IDS to to.map { shiftId(it) },
            MembershipRequestSchema.AVAILABILITY to slots(*to.toIntArray()),
        ),
    )
    from.forEach { batch.update(shift(it), VOLUNTEER_IDS, FieldValue.arrayRemove(volunteer.uid)) }
    to.forEach { batch.update(shift(it), VOLUNTEER_IDS, FieldValue.arrayUnion(volunteer.uid)) }
  }

  /** The fields of a pending request from [user] choosing the shifts at [hours], but its date. */
  private fun application(user: Account, vararg hours: Int): Map<String, Any> =
      mapOf(
          MembershipRequestSchema.USER_ID to user.uid,
          MembershipRequestSchema.TEAM_ID to TEAM,
          MembershipRequestSchema.AVAILABILITY to slots(*hours),
          SHIFT_IDS to hours.map { shiftId(it) },
          STATUS to "PENDING",
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
    const val CREATED_AT = MembershipRequestSchema.CREATED_AT

    /** An hour some twenty years before the event's start: a shift then has long started. */
    const val LONG_AGO = -24 * 365 * 20

    /** Two volunteers already in a shift, in this order. */
    val OTHERS = listOf("volunteer-a", "volunteer-b")

    val EVENT_START: Instant = Instant.parse("2036-07-18T00:00:00Z")
    val ENDED_EVENT_START: Instant = Instant.parse("2020-07-18T00:00:00Z")

    /** [hour] o'clock UTC from the event's start; 24 and beyond run into the next days. */
    fun at(hour: Int): Instant = EVENT_START.plus(Duration.ofHours(hour.toLong()))

    fun shiftId(hour: Int): String = shiftIdOf(at(hour))
  }
}
