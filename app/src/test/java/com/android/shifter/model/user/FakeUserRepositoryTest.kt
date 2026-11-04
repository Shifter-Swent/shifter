// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeUserRepositoryTest {

  private val ada = User(uid = "uid-1", displayName = "Ada", email = "ada@example.com")
  private val repository = FakeUserRepository(listOf(ada))

  @Test
  fun getOrCreateUser_newProfile_hasNoMode() = runTest {
    assertNull(repository.getOrCreateUser("uid-2", "Bob", "bob@example.com").mode)
  }

  @Test
  fun getOrCreateUser_withMode_savesItOnAProfileWithout() = runTest {
    val bob = repository.getOrCreateUser("uid-2", "Bob", "bob@example.com", UserMode.ORGANIZER)
    val signedInAgain =
        repository.getOrCreateUser(ada.uid, "Ada", "ada@example.com", UserMode.ORGANIZER)

    assertEquals(UserMode.ORGANIZER, bob.mode)
    assertEquals(UserMode.ORGANIZER, signedInAgain.mode)
    assertEquals(UserMode.ORGANIZER, repository.getUser(ada.uid)?.mode)
  }

  @Test
  fun getOrCreateUser_withModeOnAProfileWithOne_keepsTheStoredMode() = runTest {
    repository.setMode(ada.uid, UserMode.VOLUNTEER)

    val user = repository.getOrCreateUser(ada.uid, "Ada", "ada@example.com", UserMode.ORGANIZER)

    assertEquals(UserMode.VOLUNTEER, user.mode)
    assertEquals(UserMode.VOLUNTEER, repository.getUser(ada.uid)?.mode)
  }

  @Test
  fun setMode_changesTheMode() = runTest {
    repository.setMode(ada.uid, UserMode.VOLUNTEER)

    repository.setMode(ada.uid, UserMode.ORGANIZER)

    assertEquals(UserMode.ORGANIZER, repository.getUser(ada.uid)?.mode)
  }

  @Test
  fun setMode_withoutProfile_throwsPermissionDeniedAndCreatesNothing() = runTest {
    val thrown = runCatching { repository.setMode("uid-2", UserMode.VOLUNTEER) }.exceptionOrNull()

    assertTrue(thrown is UserRepositoryException.PermissionDenied)
    assertNull(repository.getUser("uid-2"))
  }

  @Test
  fun setMode_withFailure_throwsIt() = runTest {
    val failure = UserRepositoryException.Unavailable()
    repository.failure = failure

    val thrown = runCatching { repository.setMode(ada.uid, UserMode.VOLUNTEER) }.exceptionOrNull()

    assertEquals(failure, thrown)
    repository.failure = null
    assertNull(repository.getUser(ada.uid)?.mode)
  }
}
