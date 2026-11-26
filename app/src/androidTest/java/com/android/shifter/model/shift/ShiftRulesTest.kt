// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.firebase.ScratchEvents
import com.swent.shifter.firebase.emulatorTest
import com.swent.shifter.firebase.signInForFirestore
import com.swent.shifter.firebase.step
import com.swent.shifter.model.event.EventSchema
import com.swent.shifter.model.firestore.toFirestoreTimestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Security rules of the `shifts` subcollection, run against the Firestore emulator: any signed-in
 * user reads them, since a volunteer chooses shifts before applying; only the organizer opens a
 * slot, of the right shape and during the event, and closes it while nobody chose it; nobody edits
 * the slot itself. Who may change its `volunteerIds` is [ShiftVolunteerRulesTest]'s concern.
 *
 * The scratch event runs from 09:00 to 23:00 UTC. A slot volunteers chose is written as an admin.
 */
@RunWith(AndroidJUnit4::class)
class ShiftRulesTest {

  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore

  /** Paths deleted in [cleanUp] through the emulator's admin API, whatever the rules allow. */
  private val paths = mutableListOf<String>()

  /** Auth accounts deleted in [cleanUp], so the emulator does not keep them between tests. */
  private val accounts = mutableListOf<Account>()

  private lateinit var organizer: Account
  private lateinit var volunteer: Account
  private lateinit var manager: Account
  private lateinit var stranger: Account
  private lateinit var event: DocumentReference

  @Before
  fun setUp() = emulatorTest {
    volunteer = account()
    manager = account()
    stranger = account()
    organizer = account()
    event =
        ScratchEvents.create(
            mapOf(
                EventSchema.START_AT to EVENT_START.toFirestoreTimestamp(),
                EventSchema.END_AT to EVENT_END.toFirestoreTimestamp(),
            )
        )
    step("participants") {
      paths +=
          FirestoreEmulatorAdmin.createParticipants(event.id, listOf(volunteer.uid, manager.uid))
      val team = "${event.path}/teams/team-bar"
      FirestoreEmulatorAdmin.createDocument(
          team,
          mapOf("name" to "Bar", "managerId" to manager.uid, "memberIds" to listOf(volunteer.uid)),
      )
      paths += team
    }
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
  fun read_byAnySignedInUser_isAllowed() = emulatorTest {
    val shift = open(slot(10))

    // A candidate is not a participant yet: they still see the slots they may choose.
    signIn(stranger)

    assertTrue(shift.get(Source.SERVER).await().exists())
    assertTrue(shift.id in shifts().get(Source.SERVER).await().documents.map { it.id })
  }

  @Test
  fun read_bySomeoneSignedOut_isDenied() = emulatorTest {
    val shift = open(slot(10))

    signOut()

    assertDenied { shift.get(Source.SERVER).await() }
    assertDenied { shifts().get(Source.SERVER).await() }
  }

  @Test
  fun openAndClose_byTheOrganizer_areAllowed() = emulatorTest {
    val shift = open(slot(10))

    shift.delete().await()

    assertFalse(shift.get(Source.SERVER).await().exists())
    // Closing again deletes nothing and succeeds, as after a lost answer.
    shift.delete().await()
  }

  @Test
  fun openAndClose_bySomeoneElse_areDenied() = emulatorTest {
    val shift = open(slot(10))

    for (user in listOf(volunteer, manager, stranger)) {
      signIn(user)
      assertDenied { document(slot(12)).set(fields(slot(12))).await() }
      assertDenied { shift.delete().await() }
    }
  }

  @Test
  fun update_ofTheSlot_isDenied() = emulatorTest {
    val shift = open(slot(10))
    assertDenied { shift.update(ShiftSchema.END_AT, at(13).ts()).await() }

    // Not even by the organizer taking a volunteer out at the same time: a slot is never edited.
    paths.add(0, shift.path)
    step("write a chosen slot") {
      FirestoreEmulatorAdmin.deleteDocument(shift.path)
      FirestoreEmulatorAdmin.createDocument(
          shift.path,
          mapOf(
              ShiftSchema.VOLUNTEER_IDS to listOf(volunteer.uid),
              ShiftSchema.START_AT to slot(10).startAt,
              ShiftSchema.END_AT to slot(10).endAt,
              ShiftSchema.CREATED_AT to EVENT_START,
          ),
      )
    }
    assertDenied {
      shift
          .update(
              mapOf(
                  ShiftSchema.VOLUNTEER_IDS to emptyList<String>(),
                  ShiftSchema.END_AT to at(13).ts(),
              )
          )
          .await()
    }
  }

  @Test
  fun close_ofASlotVolunteersChose_isDenied() = emulatorTest {
    val slot = slot(10)
    signIn(organizer)
    paths.add(0, document(slot).path)
    step("write a chosen slot") {
      FirestoreEmulatorAdmin.createDocument(
          document(slot).path,
          mapOf(
              ShiftSchema.VOLUNTEER_IDS to listOf(volunteer.uid),
              ShiftSchema.START_AT to slot.startAt,
              ShiftSchema.END_AT to slot.endAt,
              ShiftSchema.CREATED_AT to EVENT_START,
          ),
      )
    }

    assertDenied { document(slot).delete().await() }
  }

  @Test
  fun open_withEveryRightShape_isAllowed() = emulatorTest {
    open(slot(10))
    // 1 h and 3 h, as on the nights the clocks change.
    open(ShiftSlot(at(14), at(15)))
    open(ShiftSlot(at(16), at(19)))
    // A start on the half hour: an even hour in India, 5 h 30 ahead of UTC.
    open(ShiftSlot(at(20).plusSeconds(1800), at(22).plusSeconds(1800)))
  }

  @Test
  fun open_ofASlotThatRunsDuringTheEventInPart_isAllowed() = emulatorTest {
    // The event runs from 09:00 to 23:00: 08–10 and 22–00 touch it, so it is covered entirely.
    open(slot(8))
    open(slot(22))
  }

  @Test
  fun open_ofASlotOutsideTheEvent_isDenied() = emulatorTest {
    // 06–08 ends before the event starts, and 00–02 the next day starts after it ends.
    assertDenied { document(slot(6)).set(fields(slot(6))).await() }
    // 07–09 ends just as it starts, so it does not run during it: `overlaps` refuses it too.
    val touching = ShiftSlot(at(7), at(9))
    assertDenied { document(touching).set(fields(touching)).await() }
    // 23–01 starts just as it ends, the same edge on the other side.
    val afterTheEnd = ShiftSlot(at(23), at(25))
    assertDenied { document(afterTheEnd).set(fields(afterTheEnd)).await() }
    val nextDay = ShiftSlot(at(24), at(26))
    assertDenied { document(nextDay).set(fields(nextDay)).await() }
  }

  @Test
  fun open_withAWrongLength_isDenied() = emulatorTest {
    val fourHours = slot(10) to at(14)
    val twoAndAHalf = slot(10) to at(12).plusSeconds(1800)
    val noLength = slot(10) to at(10)
    val endingBeforeItStarts = slot(10) to at(8)

    for ((slot, endAt) in listOf(fourHours, twoAndAHalf, noLength, endingBeforeItStarts)) {
      assertDenied { document(slot).set(fields(slot) + (ShiftSchema.END_AT to endAt.ts())).await() }
    }
  }

  @Test
  fun open_withAStartOffTheQuarterHour_isDenied() = emulatorTest {
    // Both last 2 whole hours and are keyed by their start, but neither starts on a quarter hour:
    // one is 7 minutes off, the other a fraction of a millisecond.
    val offTheMinute = at(10).plusSeconds(7 * 60)
    val withNanos = Timestamp(at(10).epochSecond, 400_000)

    assertDenied {
      db.document(shiftPath(shiftIdOf(offTheMinute)))
          .set(
              fields(slot(10)) +
                  mapOf(
                      ShiftSchema.START_AT to offTheMinute.ts(),
                      ShiftSchema.END_AT to offTheMinute.plus(TWO_HOURS).ts(),
                  )
          )
          .await()
    }
    assertDenied {
      document(slot(10))
          .set(
              fields(slot(10)) +
                  mapOf(
                      ShiftSchema.START_AT to withNanos,
                      ShiftSchema.END_AT to Timestamp(at(12).epochSecond, 400_000),
                  )
          )
          .await()
    }
  }

  @Test
  fun open_withAWrongIdOrWrongFields_isDenied() = emulatorTest {
    val slot = slot(10)

    // An id that is not the start would let the same slot exist twice.
    assertDenied { db.document(shiftPath("shift-1")).set(fields(slot)).await() }
    assertDenied {
      document(slot)
          .set(fields(slot) + (ShiftSchema.VOLUNTEER_IDS to listOf(volunteer.uid)))
          .await()
    }
    assertDenied {
      document(slot).set(fields(slot) + (ShiftSchema.CREATED_AT to EVENT_START.ts())).await()
    }
    assertDenied { document(slot).set(fields(slot) + ("teamId" to "team-bar")).await() }
    assertDenied {
      document(slot).set(fields(slot) + (ShiftSchema.START_AT to "2036-07-18T10:00:00Z")).await()
    }
    assertDenied {
      document(slot).set(fields(slot) + (ShiftSchema.END_AT to "2036-07-18T12:00:00Z")).await()
    }
    assertDenied { document(slot).set(fields(slot) + (ShiftSchema.VOLUNTEER_IDS to "")).await() }
    assertDenied { document(slot).set(fields(slot) - ShiftSchema.VOLUNTEER_IDS).await() }
  }

  @Test
  fun open_underAMissingEvent_isDenied() = emulatorTest {
    // Without an event there is no organizer, so not even the user writing first may claim it.
    signIn(organizer)
    val orphan =
        db.collection(EventSchema.COLLECTION)
            .document("missing-" + UUID.randomUUID())
            .collection(ShiftSchema.COLLECTION)
            .document(slot(10).shiftId)

    assertDenied { orphan.set(fields(slot(10))).await() }
  }

  /** Opens [slot] as the organizer, who stays signed in, and remembers it for cleanup. */
  private suspend fun open(slot: ShiftSlot): DocumentReference {
    signIn(organizer)
    return document(slot).also {
      paths.add(0, it.path)
      it.set(fields(slot)).await()
    }
  }

  /** The fields of a new slot, as `FirestoreShiftRepository.openSlot` writes them. */
  private fun fields(slot: ShiftSlot): Map<String, Any> =
      mapOf(
          ShiftSchema.VOLUNTEER_IDS to emptyList<String>(),
          ShiftSchema.START_AT to slot.startAt.ts(),
          ShiftSchema.END_AT to slot.endAt.ts(),
          ShiftSchema.CREATED_AT to FieldValue.serverTimestamp(),
      )

  private fun shifts() = event.collection(ShiftSchema.COLLECTION)

  private fun shiftPath(shiftId: String) = "${event.path}/${ShiftSchema.COLLECTION}/$shiftId"

  private fun document(slot: ShiftSlot): DocumentReference = db.document(shiftPath(slot.shiftId))

  private suspend fun account(): Account {
    val email = "shift-rules-${UUID.randomUUID()}@example.test"
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

  /**
   * Signs out and waits until Firestore knows it, which it learns asynchronously, as
   * [signInForFirestore] explains: a read sent right after could still go as the previous user.
   */
  private suspend fun signOut() {
    val signedOut = CompletableDeferred<Unit>()
    // Called at once with the user still signed in, then again once they are signed out.
    val listener = FirebaseAuth.IdTokenListener {
      if (it.currentUser == null) signedOut.complete(Unit)
    }
    auth.addIdTokenListener(listener)
    try {
      auth.signOut()
      step("sign-out") { signedOut.await() }
    } finally {
      auth.removeIdTokenListener(listener)
    }
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
    val EVENT_START: Instant = Instant.parse("2036-07-18T09:00:00Z")
    val EVENT_END: Instant = Instant.parse("2036-07-18T23:00:00Z")
    val TWO_HOURS: Duration = Duration.ofHours(2)

    /** [hour] o'clock UTC on the event's day; 24 and beyond run into the next day. */
    fun at(hour: Int): Instant =
        Instant.parse("2036-07-18T00:00:00Z").plus(Duration.ofHours(hour.toLong()))

    /** The 2 h slot starting at [hour] o'clock UTC. */
    fun slot(hour: Int) = ShiftSlot(at(hour), at(hour + 2))
  }
}
