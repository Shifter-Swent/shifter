// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.team

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
class FirestoreTeamRepositoryErrorTest {

  @Test
  fun toRepositoryException_translatesFirestoreErrors() {
    fun map(code: Code) =
        FirestoreTeamRepository.toRepositoryException(FirebaseFirestoreException("", code))
    assertTrue(map(Code.NOT_FOUND) is TeamRepositoryException.NotFound)
    assertTrue(map(Code.PERMISSION_DENIED) is TeamRepositoryException.PermissionDenied)
    assertTrue(map(Code.UNAUTHENTICATED) is TeamRepositoryException.PermissionDenied)
    assertTrue(map(Code.UNAVAILABLE) is TeamRepositoryException.Unavailable)
    assertTrue(map(Code.DEADLINE_EXCEEDED) is TeamRepositoryException.Unavailable)
    assertTrue(map(Code.INTERNAL) is TeamRepositoryException.Unknown)
  }

  @Test
  fun getTeamsByEvent_onFirestoreError_throwsTheDomainError() = runTest {
    val error = FirebaseFirestoreException("offline", Code.UNAVAILABLE)
    val firestore = mockk<FirebaseFirestore>()
    every {
      firestore
          .collection(EventSchema.COLLECTION)
          .document("event-1")
          .collection(TeamSchema.COLLECTION)
          .get()
    } returns Tasks.forException(error)

    val thrown = runCatching {
      FirestoreTeamRepository(firestore).getTeamsByEvent("event-1")
    }
        .exceptionOrNull()

    assertTrue(thrown is TeamRepositoryException.Unavailable)
    assertEquals(error, thrown!!.cause)
  }
}
