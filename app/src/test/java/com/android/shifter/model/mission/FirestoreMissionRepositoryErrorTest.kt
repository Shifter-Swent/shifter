// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.swent.shifter.model.event.EventSchema
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Error translation with Firestore mocked; the emulator tests cover the real SDK. */
class FirestoreMissionRepositoryErrorTest {

  @Test
  fun toRepositoryException_translatesFirestoreErrors() {
    fun map(code: Code) =
        FirestoreMissionRepository.toRepositoryException(FirebaseFirestoreException("", code))
    assertTrue(map(Code.NOT_FOUND) is MissionRepositoryException.NotFound)
    assertTrue(map(Code.PERMISSION_DENIED) is MissionRepositoryException.PermissionDenied)
    assertTrue(map(Code.UNAUTHENTICATED) is MissionRepositoryException.PermissionDenied)
    assertTrue(map(Code.UNAVAILABLE) is MissionRepositoryException.Unavailable)
    assertTrue(map(Code.DEADLINE_EXCEEDED) is MissionRepositoryException.Unavailable)
    assertTrue(map(Code.INTERNAL) is MissionRepositoryException.Unknown)
  }

  @Test
  fun getMissionsByEvent_onFirestoreError_throwsTheDomainError() = runTest {
    val error = FirebaseFirestoreException("offline", Code.UNAVAILABLE)
    val firestore = mockk<FirebaseFirestore>()
    every {
      firestore
          .collection(EventSchema.COLLECTION)
          .document("event-1")
          .collection(MissionSchema.COLLECTION)
          .get()
    } returns Tasks.forException(error)

    val thrown = runCatching {
      FirestoreMissionRepository(firestore).getMissionsByEvent("event-1")
    }
        .exceptionOrNull()

    assertTrue(thrown is MissionRepositoryException.Unavailable)
    assertEquals(error, thrown!!.cause)
  }
}
