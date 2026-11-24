// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import android.util.Log
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.WriteBatch
import com.swent.shifter.model.user.USERS_COLLECTION
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * What [FirestoreNotificationInbox] does with what Firestore answers, on mocks: the documents it
 * keeps, the errors it translates and the batches it writes. That the rules let the owner, and only
 * them, read and empty the inbox is checked on the emulator by `FirestoreNotificationInboxTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreNotificationInboxUnitTest {

  private val db = mockk<FirebaseFirestore>()
  private val users = mockk<CollectionReference>()
  private val inbox = mockk<CollectionReference>()
  private val query = mockk<Query>()
  private val repository = FirestoreNotificationInbox(db)

  @Before
  fun setUp() {
    val user = mockk<DocumentReference>()
    every { db.collection(USERS_COLLECTION) } returns users
    every { users.document(UID) } returns user
    // What Firestore throws for an id no document can have.
    every { users.document(INVALID_ID) } throws IllegalArgumentException("Invalid document")
    every { user.collection(InboxSchema.COLLECTION) } returns inbox
    every { inbox.orderBy(InboxSchema.CREATED_AT) } returns query
  }

  @Test
  fun pending_skipsWhatThisAppCannotRead_andKeepsTheOrderOfTheQuery() = runTest {
    stored(
        notification("n-2", createdAt = 20),
        unknownKind("n-3"),
        malformed("n-4"),
        notification("n-1", createdAt = 10),
    )

    val pending = repository.pending(UID)

    assertEquals(listOf("n-2", "n-1"), pending.map { it.id })
    verify { inbox.orderBy(InboxSchema.CREATED_AT) }
  }

  @Test
  fun pending_translatesFirestoreErrors() = runTest {
    val expected =
        mapOf(
            Code.PERMISSION_DENIED to NotificationInboxException.NotAllowed::class,
            Code.UNAUTHENTICATED to NotificationInboxException.NotAllowed::class,
            Code.UNAVAILABLE to NotificationInboxException.Unavailable::class,
            Code.DEADLINE_EXCEEDED to NotificationInboxException.Unavailable::class,
            Code.INTERNAL to NotificationInboxException.Unknown::class,
        )
    for ((code, type) in expected) {
      every { query.get() } returns Tasks.forException(FirebaseFirestoreException("error", code))

      val thrown = runCatching { repository.pending(UID) }.exceptionOrNull()

      assertTrue("$code gave $thrown", type.isInstance(thrown))
    }
  }

  @Test
  fun pending_ofAUserIdNoDocumentCanHave_failsWithUnknown() = runTest {
    val thrown = runCatching { repository.pending(INVALID_ID) }.exceptionOrNull()

    assertTrue("Got $thrown", thrown is NotificationInboxException.Unknown)
  }

  @Test
  fun observePending_emitsWhatItCanReadOfEachSnapshot() = runTest {
    val listener = captureListener()
    val seen = mutableListOf<List<String>>()
    val job = backgroundScope.launch {
      repository.observePending(UID).collect { list -> seen += list.map { it.id } }
    }
    runCurrent()

    listener.captured.onEvent(snapshotOf(notification("n-1")), null)
    runCurrent()
    listener.captured.onEvent(
        snapshotOf(notification("n-1"), unknownKind("n-2"), malformed("n-4")),
        null,
    )
    runCurrent()
    listener.captured.onEvent(snapshotOf(notification("n-1"), notification("n-3")), null)
    runCurrent()
    job.cancel()

    // The second snapshot only adds what this app skips, so the same list is not emitted again.
    assertEquals(listOf(listOf("n-1"), listOf("n-1", "n-3")), seen)
  }

  @Test
  fun observePending_whenTheListenerFails_failsWithTheMappedException() = runTest {
    val listener = captureListener()
    val thrown =
        failureOf(repository.observePending(UID)) {
          listener.captured.onEvent(null, FirebaseFirestoreException("offline", Code.UNAVAILABLE))
        }

    assertTrue("Got $thrown", thrown is NotificationInboxException.Unavailable)
  }

  @Test
  fun observePending_ofAUserIdNoDocumentCanHave_failsWithUnknown() = runTest {
    val thrown = failureOf(repository.observePending(INVALID_ID)) {}

    assertTrue("Got $thrown", thrown is NotificationInboxException.Unknown)
  }

  @Test
  fun remove_withNoId_writesNothing() = runTest {
    repository.remove(UID, emptyList())

    verify(exactly = 0) { db.batch() }
  }

  @Test
  fun remove_deletesEachNotificationInBatchesOfAtMost500() = runTest {
    val batches = mutableListOf<WriteBatch>()
    every { db.batch() } answers { mockk<WriteBatch>(relaxed = true).also { batches += it } }
    every { inbox.document(any()) } answers { mockk<DocumentReference>(name = firstArg()) }
    val ids = (1..BATCH_LIMIT + 1).map { "n-$it" }

    repository.remove(UID, ids)

    assertEquals(2, batches.size)
    verify(exactly = BATCH_LIMIT) { batches[0].delete(any()) }
    verify(exactly = 1) { batches[1].delete(any()) }
    batches.forEach { verify(exactly = 1) { it.commit() } }
    ids.forEach { verify { inbox.document(it) } }
  }

  @Test
  fun remove_withAnIdNoDocumentCanHave_deletesNothing() = runTest {
    every { inbox.document("n-1") } returns mockk()
    every { inbox.document(INVALID_ID) } throws IllegalArgumentException("Invalid document")

    val thrown = runCatching { repository.remove(UID, listOf("n-1", INVALID_ID)) }.exceptionOrNull()

    assertTrue("Got $thrown", thrown is NotificationInboxException.Unknown)
    verify(exactly = 0) { db.batch() }
  }

  @Test
  fun remove_whenTheServerRefusesTheDelete_logsItWithoutThrowing() = runTest {
    val batch = mockk<WriteBatch>(relaxed = true)
    val commit = mockk<Task<Void>>()
    val onFailure = slot<OnFailureListener>()
    every { db.batch() } returns batch
    every { inbox.document("n-1") } returns mockk()
    every { batch.commit() } returns commit
    every { commit.addOnFailureListener(capture(onFailure)) } returns commit
    mockkStatic(Log::class)
    try {
      every { Log.w(any(), any<String>(), any()) } returns 0

      repository.remove(UID, listOf("n-1"))
      onFailure.captured.onFailure(FirebaseFirestoreException("denied", Code.PERMISSION_DENIED))

      verify { Log.w(any(), match<String> { "refused" in it }, any()) }
    } finally {
      unmockkStatic(Log::class)
    }
  }

  /** Makes the query answer [documents]. */
  private fun stored(vararg documents: DocumentSnapshot) {
    every { query.get() } returns Tasks.forResult(snapshotOf(*documents))
  }

  private fun snapshotOf(vararg documents: DocumentSnapshot): QuerySnapshot =
      mockk<QuerySnapshot>().also { every { it.documents } returns documents.toList() }

  private fun captureListener() =
      slot<EventListener<QuerySnapshot>>().also {
        every { query.addSnapshotListener(capture(it)) } returns
            mockk<ListenerRegistration>(relaxed = true)
      }

  /** What [flow] fails with once [event] is delivered. */
  private fun TestScope.failureOf(flow: Flow<*>, event: () -> Unit): Throwable? {
    var thrown: Throwable? = null
    backgroundScope.launch {
      try {
        flow.collect {}
      } catch (e: Throwable) {
        thrown = e
      }
    }
    runCurrent()
    event()
    runCurrent()
    return thrown
  }

  /** A notification document as the rules store it, [createdAt] in seconds since the epoch. */
  private fun notification(id: String, createdAt: Long = 0) =
      document(
          id,
          mapOf(
              InboxSchema.KIND to NotificationKind.NEW_APPLICATION.name,
              InboxSchema.EVENT_ID to "event-1",
              InboxSchema.EVENT_TITLE to "Festival",
              InboxSchema.SENDER_ID to "sender-1",
              InboxSchema.DATA to mapOf(NotificationData.Key.USER_NAME to "Alice"),
              InboxSchema.CREATED_AT to Timestamp(createdAt, 0),
          ),
      )

  /** A notification of a kind a later version of the app added. */
  private fun unknownKind(id: String) =
      document(id, mapOf(InboxSchema.KIND to "SHIFT_SWAPPED", InboxSchema.EVENT_ID to "event-1"))

  /** A notification without its sender, which only a write outside the app could leave. */
  private fun malformed(id: String) =
      document(id, mapOf(InboxSchema.KIND to NotificationKind.NEW_APPLICATION.name))

  private fun document(id: String, fields: Map<String, Any>): DocumentSnapshot =
      mockk<DocumentSnapshot>().also {
        every { it.id } returns id
        every { it.get(any<String>()) } answers { fields[firstArg()] }
      }

  private companion object {
    const val UID = "user-1"

    /** An id no Firestore document can have. */
    const val INVALID_ID = ""

    /** The most writes a Firestore batch holds. */
    const val BATCH_LIMIT = 500
  }
}
