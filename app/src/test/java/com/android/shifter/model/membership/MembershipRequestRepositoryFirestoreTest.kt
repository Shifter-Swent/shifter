// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.membership

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.WriteBatch
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Repository logic with Firestore mocked; the emulator test covers the real SDK and the rules. */
class MembershipRequestRepositoryFirestoreTest {
  private val db = mockk<FirebaseFirestore>()
  private val requests = mockk<CollectionReference>()
  private val ref = mockk<DocumentReference>()
  private val query = mockk<Query>()
  private val result = mockk<QuerySnapshot>()
  private val transaction = mockk<Transaction>(relaxed = true)
  private val batch = mockk<WriteBatch>(relaxed = true)
  private val participants = mockk<DocumentReference>()
  private val repository = MembershipRequestRepositoryFirestore(db)
  private val request =
      MembershipRequest(
          userId = "volunteer",
          preferredTeamIds = listOf("welcome", "catering"),
          availability = listOf(AvailabilitySlot(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600))),
          createdAt = Instant.EPOCH,
      )

  @Before
  fun setUp() {
    val events = mockk<CollectionReference>()
    val event = mockk<DocumentReference>()
    val group = mockk<Query>()
    val participantCollection = mockk<CollectionReference>()
    every { db.collection("eventParticipants") } returns participantCollection
    every { participantCollection.document("event") } returns participants
    every { db.batch() } returns batch
    every { batch.commit() } returns Tasks.forResult(null)
    every { db.collection("events") } returns events
    every { events.document("event") } returns event
    every { event.collection("membershipRequests") } returns requests
    every { requests.document(request.userId) } returns ref
    every { requests.get() } returns Tasks.forResult(result)
    every { db.collectionGroup("membershipRequests") } returns group
    every { group.whereEqualTo("userId", request.userId) } returns query
    every { query.get() } returns Tasks.forResult(result)
    every { ref.update("status", any<String>()) } returns Tasks.forResult(null)
    every { db.runTransaction(any<Transaction.Function<DocumentSnapshot?>>()) } answers
        {
          Tasks.forResult(firstArg<Transaction.Function<DocumentSnapshot?>>().apply(transaction))
        }
  }

  @Test
  fun applyCreatesPendingRequestWithUserId() = runTest {
    val missing = mockk<DocumentSnapshot>()
    every { missing.exists() } returns false
    every { transaction.get(ref) } returns missing
    val actual =
        repository.apply(
            "event",
            request.copy(id = "ignored", status = MembershipRequestStatus.ACCEPTED),
        )
    assertEquals(request.copy(id = request.userId), actual)
    verify { transaction.set(ref, actual.toFirestoreMap()) }
  }

  @Test
  fun applyPreservesExistingDecisions() = runTest {
    for (status in MembershipRequestStatus.entries) {
      val existing = request.copy(id = request.userId, status = status)
      every { transaction.get(ref) } returns snapshot(existing)
      assertEquals(
          existing,
          repository.apply("event", request.copy(preferredTeamIds = emptyList())),
      )
    }
    verify(exactly = 0) { transaction.set(any<DocumentReference>(), any()) }
  }

  @Test
  fun acceptAtomicallyUpdatesStatusAndUnionsParticipantsWithoutReplacingDocument() = runTest {
    // Firestore FieldValue uses identity equality, so verify the factory call and its result.
    val union = FieldValue.arrayUnion(request.userId)
    mockkStatic(FieldValue::class) {
      every { FieldValue.arrayUnion(request.userId) } returns union
      repository.accept("event", request.userId)
      repository.accept("event", request.userId)
      verify(exactly = 2) { batch.update(ref, "status", "ACCEPTED") }
      verify(exactly = 2) {
        batch.set(
            participants,
            match<Map<String, Any>> {
              it.keys == setOf("participantIds") && it["participantIds"] === union
            },
            SetOptions.merge(),
        )
      }
      verify(exactly = 2) { batch.commit() }
      verify(exactly = 0) { ref.update(any<String>(), any()) }
      verify(exactly = 2) { FieldValue.arrayUnion(request.userId) }
    }
  }

  @Test
  fun rejectAtomicallyUpdatesStatusAndRemovesOnlyApplicantWithoutDeletingRequest() = runTest {
    val removal = FieldValue.arrayRemove(request.userId)
    mockkStatic(FieldValue::class) {
      every { FieldValue.arrayRemove(request.userId) } returns removal
      repository.reject("event", request.userId)
      repository.reject("event", request.userId)
      verify(exactly = 2) { batch.update(ref, "status", "REJECTED") }
      verify(exactly = 2) {
        batch.set(
            participants,
            match<Map<String, Any>> {
              it.keys == setOf("participantIds") && it["participantIds"] === removal
            },
            SetOptions.merge(),
        )
      }
      verify(exactly = 2) { batch.commit() }
      verify(exactly = 2) { FieldValue.arrayRemove(request.userId) }
      verify(exactly = 0) { ref.delete() }
      verify(exactly = 0) { ref.update(any<String>(), any()) }
    }
  }

  @Test
  fun withdrawRemovesAParticipantAndDeletesTheRequestInOneTransaction() = runTest {
    val removal = FieldValue.arrayRemove(request.userId)
    every { transaction.get(ref) } returns statusSnapshot("ACCEPTED")
    mockkStatic(FieldValue::class) {
      every { FieldValue.arrayRemove(request.userId) } returns removal
      repository.withdraw("event", request.userId)
      verify { transaction.update(participants, "participantIds", removal) }
      verify { transaction.delete(ref) }
    }
    // The volunteer may not be allowed to read the participants document.
    verify(exactly = 0) { transaction.get(participants) }
  }

  @Test
  fun withdrawOnlyDeletesTheRequestOfANonParticipant() = runTest {
    for (status in listOf(null, "PENDING", "REJECTED")) {
      every { transaction.get(ref) } returns statusSnapshot(status)
      repository.withdraw("event", request.userId)
    }
    verify(exactly = 3) { transaction.delete(ref) }
    verify(exactly = 0) { transaction.update(any<DocumentReference>(), any<String>(), any()) }
  }

  @Test
  fun withdrawTranslatesARefusal() = runTest {
    val error = FirebaseFirestoreException("denied", Code.PERMISSION_DENIED)
    every { db.runTransaction(any<Transaction.Function<DocumentSnapshot?>>()) } returns
        Tasks.forException(error)
    val failure = runCatching { repository.withdraw("event", request.userId) }.exceptionOrNull()
    assertTrue(failure is MembershipRequestRepositoryException.PermissionDenied)
    assertSame(error, failure?.cause)
  }

  @Test
  fun decisionsTranslateMissingRequestFailure() = runTest {
    val error = FirebaseFirestoreException("missing", Code.NOT_FOUND)
    every { batch.commit() } returns Tasks.forException(error)
    every { ref.update("status", any<String>()) } returns Tasks.forException(error)
    for (operation in
        listOf<suspend () -> Unit>(
            { repository.accept("event", request.userId) },
            { repository.reject("event", request.userId) },
        )) {
      val failure = runCatching { operation() }.exceptionOrNull()
      assertTrue(failure is MembershipRequestRepositoryException.Unknown)
      assertSame(error, failure?.cause)
    }
  }

  @Test
  fun eventQueryMapsAllFieldsAndHandlesEmptyResults() = runTest {
    val stored =
        listOf(
            request.copy(id = request.userId),
            request.copy(
                id = "second",
                userId = "second",
                status = MembershipRequestStatus.REJECTED,
            ),
        )
    every { result.documents } returns stored.map { snapshot(it) }
    assertEquals(stored, repository.getMembershipRequestsByEId("event"))
    every { result.documents } returns emptyList()
    assertTrue(repository.getMembershipRequestsByEId("event").isEmpty())
  }

  @Test
  fun userQueryKeepsRequestsWithTheSameUidDistinctAcrossEvents() = runTest {
    val pending = request.copy(id = request.userId)
    val rejected = pending.copy(status = MembershipRequestStatus.REJECTED)
    every { result.documents } returns
        listOf(snapshot(pending, "event-a"), snapshot(rejected, "event-b"))

    assertEquals(
        mapOf("event-a" to pending, "event-b" to rejected),
        repository.getMembershipRequestsByUId(request.userId),
    )
    verify { db.collectionGroup("membershipRequests") }
    verify { query.get() }
    every { result.documents } returns emptyList()
    assertTrue(repository.getMembershipRequestsByUId(request.userId).isEmpty())
  }

  @Test
  fun userQueryReportsMissingParentEventAsUnknown() = runTest {
    every { result.documents } returns listOf(snapshot(request, null))
    val failure = runCatching {
      repository.getMembershipRequestsByUId(request.userId)
    }
        .exceptionOrNull()
    assertTrue(failure is MembershipRequestRepositoryException.Unknown)
    assertTrue(failure?.cause is IllegalStateException)
  }

  @Test
  fun queriesRejectMalformedDocuments() = runTest {
    val malformed = snapshot(request)
    every { malformed.get("status") } returns "invalid"
    every { result.documents } returns listOf(malformed)
    assertTrue(
        runCatching { repository.getMembershipRequestsByEId("event") }.exceptionOrNull()
            is MembershipRequestRepositoryException.Unknown
    )
    assertTrue(
        runCatching { repository.getMembershipRequestsByUId(request.userId) }.exceptionOrNull()
            is MembershipRequestRepositoryException.Unknown
    )
  }

  @Test
  fun everyOperationTranslatesBackendFailures() = runTest {
    val error = FirebaseFirestoreException("denied", Code.PERMISSION_DENIED)
    every { batch.commit() } returns Tasks.forException(error)
    every { requests.get() } returns Tasks.forException(error)
    every { query.get() } returns Tasks.forException(error)
    every { ref.update("status", any<String>()) } returns Tasks.forException(error)
    every { db.runTransaction(any<Transaction.Function<DocumentSnapshot?>>()) } returns
        Tasks.forException(error)
    val operations =
        listOf<suspend () -> Any>(
            { repository.apply("event", request) },
            { repository.accept("event", request.userId) },
            { repository.reject("event", request.userId) },
            { repository.getMembershipRequestsByEId("event") },
            { repository.getMembershipRequestsByUId(request.userId) },
        )
    operations.forEach {
      val failure = runCatching { it() }.exceptionOrNull()
      assertTrue(failure is MembershipRequestRepositoryException.PermissionDenied)
      assertSame(error, failure?.cause)
    }
  }

  @Test
  fun queryPreservesCancellation() = runTest {
    val cancellation = CancellationException("cancelled")
    every { requests.get() } throws cancellation
    every { query.get() } throws cancellation
    assertSame(
        cancellation,
        runCatching { repository.getMembershipRequestsByEId("event") }.exceptionOrNull(),
    )
    assertSame(
        cancellation,
        runCatching { repository.getMembershipRequestsByUId(request.userId) }.exceptionOrNull(),
    )
  }

  @Test
  fun applyPreservesTaskCancellation() = runTest {
    every { db.runTransaction(any<Transaction.Function<DocumentSnapshot?>>()) } returns
        Tasks.forCanceled()
    val thrown = runCatching { repository.apply("event", request) }.exceptionOrNull()
    assertTrue(thrown is CancellationException)
  }

  @Test
  fun applyReportsMalformedExistingDocumentAsUnknown() = runTest {
    val malformed = snapshot(request)
    every { malformed.get(any<String>()) } returns null
    every { transaction.get(ref) } returns malformed
    val thrown = runCatching { repository.apply("event", request) }.exceptionOrNull()
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
    every { db.runTransaction(any<Transaction.Function<DocumentSnapshot?>>()) } returns
        Tasks.forException(error)
    val thrown = runCatching { repository.apply("event", request) }.exceptionOrNull()
    assertTrue(thrown is MembershipRequestRepositoryException.Unavailable)
    assertEquals(error, thrown!!.cause)
  }

  /** The volunteer's request, missing when [status] is null. */
  private fun statusSnapshot(status: String?): DocumentSnapshot =
      mockk<DocumentSnapshot>().also { every { it.getString("status") } returns status }

  private fun snapshot(value: MembershipRequest, eventId: String? = "event"): DocumentSnapshot {
    val snapshot = mockk<DocumentSnapshot>()
    val document = mockk<DocumentReference>()
    val collection = mockk<CollectionReference>()
    val event = eventId?.let { id ->
      mockk<DocumentReference>().also { every { it.id } returns id }
    }
    every { snapshot.reference } returns document
    every { document.parent } returns collection
    every { collection.parent } returns event
    val fields = value.toFirestoreMap()
    every { snapshot.id } returns value.id
    every { snapshot.exists() } returns true
    every { snapshot.get(any<String>()) } answers { fields[firstArg<String>()] }
    every { snapshot.contains(any<String>()) } answers { fields.containsKey(firstArg<String>()) }
    return snapshot
  }
}
