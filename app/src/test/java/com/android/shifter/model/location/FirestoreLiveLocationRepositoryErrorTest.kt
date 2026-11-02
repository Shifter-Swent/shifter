// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.test.runTest
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

  private companion object {
    const val EVENT_ID = "event-1"
    const val TEAM_ID = "team-1"
    val GENEVA = GeoPosition(46.2044, 6.1432)
  }
}
