// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.checkin

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.swent.shifter.model.event.EventSchema
import com.swent.shifter.model.mission.MissionSchema
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Error translation with Firestore mocked; the emulator tests cover the real SDK. */
class FirestoreCheckInRepositoryErrorTest {

  @Test
  fun toRepositoryException_translatesFirestoreErrors() {
    fun map(code: Code) =
        FirestoreCheckInRepository.toRepositoryException(FirebaseFirestoreException("", code))
    assertTrue(map(Code.PERMISSION_DENIED) is CheckInRepositoryException.NotAllowed)
    assertTrue(map(Code.UNAUTHENTICATED) is CheckInRepositoryException.NotAllowed)
    assertTrue(map(Code.UNAVAILABLE) is CheckInRepositoryException.Unavailable)
    assertTrue(map(Code.DEADLINE_EXCEEDED) is CheckInRepositoryException.Unavailable)
    assertTrue(map(Code.INTERNAL) is CheckInRepositoryException.Unknown)
  }

  /** Only `PERMISSION_DENIED` is read back: any other write error is translated as is. */
  @Test
  fun checkIn_onAnotherWriteError_throwsTheDomainErrorWithoutReadingBack() = runTest {
    val error = FirebaseFirestoreException("offline", Code.UNAVAILABLE)
    val firestore = mockk<FirebaseFirestore>()
    every {
      firestore
          .collection(EventSchema.COLLECTION)
          .document("event-1")
          .collection(MissionSchema.COLLECTION)
          .document("mission-1")
          .collection(CheckInSchema.COLLECTION)
          .document("user-1")
          .set(any())
    } returns Tasks.forException(error)
    val auth = mockk<FirebaseAuth> { every { currentUser?.uid } returns "user-1" }

    val thrown = runCatching {
      FirestoreCheckInRepository(firestore, auth)
          .checkIn("event-1", "mission-1", "user-1", CheckInMethod.SELF)
    }
        .exceptionOrNull()

    assertTrue(thrown is CheckInRepositoryException.Unavailable)
    assertEquals(error, thrown!!.cause)
  }
}
