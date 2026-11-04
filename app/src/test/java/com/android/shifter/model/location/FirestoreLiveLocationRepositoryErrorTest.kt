// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test

/** Error translation on its own; the emulator tests cover the real SDK. */
class FirestoreLiveLocationRepositoryErrorTest {

  @Test
  fun toRepositoryException_translatesFirestoreErrors() {
    fun map(code: Code) =
        FirestoreLiveLocationRepository.toRepositoryException(FirebaseFirestoreException("", code))
    assertTrue(map(Code.PERMISSION_DENIED) is LiveLocationRepositoryException.PermissionDenied)
    assertTrue(map(Code.UNAUTHENTICATED) is LiveLocationRepositoryException.PermissionDenied)
    assertTrue(map(Code.UNAVAILABLE) is LiveLocationRepositoryException.Unavailable)
    assertTrue(map(Code.DEADLINE_EXCEEDED) is LiveLocationRepositoryException.Unavailable)
    assertTrue(map(Code.INTERNAL) is LiveLocationRepositoryException.Unknown)
  }

  @Test
  fun writes_withNobodySignedIn_failWithPermissionDeniedWithoutReachingFirestore() = runTest {
    val auth = mockk<FirebaseAuth>()
    every { auth.currentUser } returns null
    // Firestore is left unstubbed on purpose: touching it would throw a MockK error instead, so
    // these assertions also prove the refusal costs no round trip.
    val repository = FirestoreLiveLocationRepository(mockk<FirebaseFirestore>(), auth)

    val shared = runCatching { repository.shareLocation(EVENT_ID, TEAM_ID, GENEVA, Instant.EPOCH) }
    val removed = runCatching { repository.removeLocation(EVENT_ID, TEAM_ID) }

    assertTrue(shared.exceptionOrNull() is LiveLocationRepositoryException.PermissionDenied)
    assertTrue(removed.exceptionOrNull() is LiveLocationRepositoryException.PermissionDenied)
  }

  /**
   * A write Firestore has already confirmed answers `null`, since its task carries no value, which
   * is exactly what a `withTimeoutOrNull` would read as "never answered". Both calls here are given
   * a task that is already complete, so nothing suspends and the bound of
   * [FirestoreLiveLocationRepository.OPERATION_TIMEOUT_MILLIS] cannot have expired: a confirmed
   * operation has to come back as a success and not as
   * [LiveLocationRepositoryException.Unavailable].
   */
  @Test
  fun writes_confirmedWithANullResult_succeedRatherThanLookLikeATimeout() = runTest {
    val document = mockk<DocumentReference>()
    val collection = mockk<CollectionReference>()
    every { collection.document(any<String>()) } returns document
    every { document.collection(any()) } returns collection
    every { document.set(any()) } returns Tasks.forResult(null)
    every { document.delete() } returns Tasks.forResult(null)
    val firestore = mockk<FirebaseFirestore>()
    every { firestore.collection(any()) } returns collection

    val repository = FirestoreLiveLocationRepository(firestore, signedIn())
    repository.shareLocation(EVENT_ID, TEAM_ID, GENEVA, Instant.EPOCH)
    repository.removeLocation(EVENT_ID, TEAM_ID)

    verify { document.set(any()) }
    verify { document.delete() }
  }

  /**
   * The repository's own bound is what becomes [LiveLocationRepositoryException.Unavailable]: a
   * write Firestore accepted but never confirmed, as it does while offline, must not wait for ever.
   */
  @Test
  fun writes_neverConfirmed_failWithUnavailable() = runTest {
    val repository = repositoryWhoseWritesNeverAnswer()

    val thrown = runCatching { repository.shareLocation(EVENT_ID, TEAM_ID, GENEVA, Instant.EPOCH) }

    assertTrue(
        "Expected Unavailable, got ${thrown.exceptionOrNull()}",
        thrown.exceptionOrNull() is LiveLocationRepositoryException.Unavailable,
    )
  }

  /**
   * A deadline the caller imposed is a cancellation of theirs, not a backend that cannot be
   * reached. It carries the very same [TimeoutCancellationException] as the repository's own bound,
   * so catching that type around a `withTimeout` cannot tell the two apart: the caller's deadline
   * would come back as a failed write and their cancelled coroutine would carry on as if it had
   * only lost a position.
   */
  @Test
  fun writes_underTheCallersOwnTimeout_stayCancelled() = runTest {
    val repository = repositoryWhoseWritesNeverAnswer()

    val thrown = runCatching {
      withTimeout(CALLER_TIMEOUT_MILLIS.milliseconds) {
        repository.shareLocation(EVENT_ID, TEAM_ID, GENEVA, Instant.EPOCH)
      }
    }

    assertTrue(
        "Expected the caller's timeout, got ${thrown.exceptionOrNull()}",
        thrown.exceptionOrNull() is TimeoutCancellationException,
    )
  }

  /** A repository whose writes are taken but never acknowledged, as Firestore does when offline. */
  private fun repositoryWhoseWritesNeverAnswer(): FirestoreLiveLocationRepository {
    val document = mockk<DocumentReference>()
    val collection = mockk<CollectionReference>()
    every { collection.document(any<String>()) } returns document
    every { document.collection(any()) } returns collection
    every { document.set(any()) } returns TaskCompletionSource<Void>().task
    every { document.delete() } returns TaskCompletionSource<Void>().task
    val firestore = mockk<FirebaseFirestore>()
    every { firestore.collection(any()) } returns collection
    return FirestoreLiveLocationRepository(firestore, signedIn())
  }

  /** A [FirebaseAuth] whose signed-in volunteer is [USER_ID]. */
  private fun signedIn(): FirebaseAuth {
    val user = mockk<FirebaseUser>()
    every { user.uid } returns USER_ID
    val auth = mockk<FirebaseAuth>()
    every { auth.currentUser } returns user
    return auth
  }

  private companion object {
    const val EVENT_ID = "event-1"
    const val TEAM_ID = "team-1"
    const val USER_ID = "volunteer-1"

    /**
     * Shorter than the repository's own bound, so the caller's deadline is the one that expires.
     */
    val CALLER_TIMEOUT_MILLIS = FirestoreLiveLocationRepository.OPERATION_TIMEOUT_MILLIS / 2
    val GENEVA = GeoPosition(46.2044, 6.1432)
  }
}
