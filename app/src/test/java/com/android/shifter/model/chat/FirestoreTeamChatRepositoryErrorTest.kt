// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Error translation with Firestore mocked; the emulator tests cover the real SDK.
 *
 * The mocked Firestore answers no call, so reaching it would throw a `MockKException` instead of
 * the expected error: the `send` tests fail if it writes before checking.
 */
class FirestoreTeamChatRepositoryErrorTest {

  @Test
  fun toRepositoryException_translatesFirestoreErrors() {
    fun map(code: Code) =
        FirestoreTeamChatRepository.toRepositoryException(FirebaseFirestoreException("", code))
    assertTrue(map(Code.PERMISSION_DENIED) is TeamChatRepositoryException.NotAllowed)
    assertTrue(map(Code.UNAUTHENTICATED) is TeamChatRepositoryException.NotAllowed)
    assertTrue(map(Code.UNAVAILABLE) is TeamChatRepositoryException.Unavailable)
    assertTrue(map(Code.DEADLINE_EXCEEDED) is TeamChatRepositoryException.Unavailable)
    assertTrue(map(Code.INTERNAL) is TeamChatRepositoryException.Unknown)
  }

  @Test
  fun send_withABlankText_throwsWithoutWriting() = runTest {
    val auth = mockk<FirebaseAuth> { every { currentUser?.uid } returns "user-1" }
    val repository = FirestoreTeamChatRepository(mockk<FirebaseFirestore>(), auth)

    val thrown = runCatching { repository.send("event-1", "team-1", "   ") }.exceptionOrNull()

    assertTrue("Expected IllegalArgumentException, got $thrown", thrown is IllegalArgumentException)
  }

  @Test
  fun send_whenSignedOut_throwsNotAllowedWithoutWriting() = runTest {
    val auth = mockk<FirebaseAuth> { every { currentUser } returns null }
    val repository = FirestoreTeamChatRepository(mockk<FirebaseFirestore>(), auth)

    val thrown = runCatching { repository.send("event-1", "team-1", "Hello") }.exceptionOrNull()

    assertTrue("Expected NotAllowed, got $thrown", thrown is TeamChatRepositoryException.NotAllowed)
  }
}
