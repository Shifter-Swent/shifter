// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.membership

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.Transaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Repository logic with Firestore mocked; the emulator test covers the real SDK and the rules. */
class MembershipRequestRepositoryFirestoreTest {

  private val db = mockk<FirebaseFirestore>()
  private val ref = mockk<DocumentReference>()
  private val snapshot = mockk<DocumentSnapshot>()
  private val transaction = mockk<Transaction>(relaxed = true)
  private val repository = MembershipRequestRepositoryFirestore(db)

  @Before
  fun setUp() {
    every {
      db.collection("events").document(EVENT_ID).collection("membershipRequests").document(UID)
    } returns ref
    every { ref.get() } returns Tasks.forResult(snapshot)
    every { transaction.get(ref) } returns snapshot
    every { snapshot.id } returns UID
    // Runs the transaction body synchronously against the mocked transaction.
    every { db.runTransaction(any<Transaction.Function<MembershipRequest>>()) } answers
        {
          Tasks.forResult(firstArg<Transaction.Function<MembershipRequest>>().apply(transaction))
        }
  }

  @Test
  fun requestToJoin_storesPendingRequestUnderUserIdWhenAbsent() = runTest {
    every { snapshot.exists() } returns false

    val sent =
        repository.requestToJoin(EVENT_ID, REQUEST.copy(status = MembershipRequestStatus.ACCEPTED))

    assertEquals(REQUEST.copy(id = UID), sent)
    verify { transaction.set(ref, REQUEST.copy(id = UID).toFirestoreMap()) }
  }

  @Test
  fun requestToJoin_returnsExistingRequestWithoutOverwritingIt() = runTest {
    val accepted = REQUEST.copy(id = UID, status = MembershipRequestStatus.ACCEPTED)
    val data = accepted.toFirestoreMap()
    every { snapshot.exists() } returns true
    every { snapshot.contains(any<String>()) } answers { firstArg<String>() in data }
    every { snapshot.get(any<String>()) } answers { data[firstArg()] }

    assertEquals(accepted, repository.requestToJoin(EVENT_ID, REQUEST))
    verify(exactly = 0) { transaction.set(any<DocumentReference>(), any()) }
  }

  @Test
  fun getRequest_reportsMissingAndMalformedDocuments() = runTest {
    every { snapshot.exists() } returns false
    assertEquals(null, repository.getRequest(EVENT_ID, UID))

    every { snapshot.exists() } returns true
    every { snapshot.get(any<String>()) } returns null
    val thrown = runCatching { repository.getRequest(EVENT_ID, UID) }.exceptionOrNull()
    assertTrue(thrown is MembershipRequestRepositoryException.Unknown)
  }

  @Test
  fun firestoreErrorsAreTranslated() = runTest {
    fun map(code: Code) =
        MembershipRequestRepositoryFirestore.toRepositoryException(
            FirebaseFirestoreException("", code)
        )
    assertTrue(map(Code.PERMISSION_DENIED) is MembershipRequestRepositoryException.PermissionDenied)
    assertTrue(map(Code.UNAUTHENTICATED) is MembershipRequestRepositoryException.PermissionDenied)
    assertTrue(map(Code.UNAVAILABLE) is MembershipRequestRepositoryException.Unavailable)
    assertTrue(map(Code.DEADLINE_EXCEEDED) is MembershipRequestRepositoryException.Unavailable)
    assertTrue(map(Code.INTERNAL) is MembershipRequestRepositoryException.Unknown)

    val error = FirebaseFirestoreException("offline", Code.UNAVAILABLE)
    every { ref.get() } returns Tasks.forException(error)
    val thrown = runCatching { repository.getRequest(EVENT_ID, UID) }.exceptionOrNull()
    assertTrue(thrown is MembershipRequestRepositoryException.Unavailable)
    assertEquals(error, thrown!!.cause)
  }

  private companion object {
    const val EVENT_ID = "event-1"
    const val UID = "uid-1"
    val REQUEST =
        MembershipRequest(
            userId = UID,
            preferredTeamIds = listOf("team-1"),
            availability =
                listOf(
                    AvailabilitySlot(Instant.ofEpochSecond(1_000), Instant.ofEpochSecond(2_000))
                ),
            createdAt = Instant.ofEpochSecond(500),
        )
  }
}
