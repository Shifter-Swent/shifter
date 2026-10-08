// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.event

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Error translation with Firestore mocked; the emulator test covers the real SDK. */
class FirestoreEventRepositoryErrorTest {

  @Test
  fun firestoreErrorsAreTranslated() = runTest {
    fun map(code: Code) =
        FirestoreEventRepository.toRepositoryException(FirebaseFirestoreException("", code))
    assertTrue(map(Code.PERMISSION_DENIED) is EventRepositoryException.PermissionDenied)
    assertTrue(map(Code.UNAUTHENTICATED) is EventRepositoryException.PermissionDenied)
    assertTrue(map(Code.UNAVAILABLE) is EventRepositoryException.Unavailable)
    assertTrue(map(Code.DEADLINE_EXCEEDED) is EventRepositoryException.Unavailable)
    assertTrue(map(Code.INTERNAL) is EventRepositoryException.Unknown)

    val error = FirebaseFirestoreException("offline", Code.UNAVAILABLE)
    val firestore = mockk<FirebaseFirestore>()
    every {
      firestore.collection(EventSchema.COLLECTION).whereEqualTo(any<String>(), any()).limit(1).get()
    } returns Tasks.forException(error)
    val repository = FirestoreEventRepository(firestore)

    val thrown = runCatching { repository.getEventByJoinCode("ABC123") }.exceptionOrNull()

    assertTrue(thrown is EventRepositoryException.Unavailable)
    assertEquals(error, thrown!!.cause)
  }
}
