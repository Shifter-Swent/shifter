// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.authentication

import androidx.credentials.Credential
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeAuthRepositoryTest {

  private val user = AuthUser(uid = "uid-1", email = "ada@example.com", displayName = "Ada")

  @Test
  fun session_startsWithTheRestoredSession() {
    FakeAuthRepository(user).assertSession(user)
    FakeAuthRepository().assertSession(null)
  }

  @Test
  fun session_followsSignInAndSignOut() = runBlocking {
    val repository = FakeAuthRepository()
    repository.signInResult = Result.success(user)

    repository.signInWithGoogle(mockk<Credential>())
    repository.assertSession(user)

    repository.signOut()
    repository.assertSession(null)
  }

  @Test
  fun endSession_signsOutWithoutCallingSignOut() {
    val repository = FakeAuthRepository(user)

    repository.endSession()

    repository.assertSession(null)
    assertEquals(0, repository.signOutCalls)
  }

  /** Both ways of reading the session must agree, like in the real repository. */
  private fun FakeAuthRepository.assertSession(expected: AuthUser?) {
    assertEquals(expected, currentUser())
    assertEquals(expected, authState.value)
  }
}
