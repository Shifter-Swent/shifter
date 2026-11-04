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
  fun chooseMode_savesTheModeOnTheProfile() = runTest {
    repository.chooseMode(ada.uid, UserMode.ORGANIZER)

    assertEquals(UserMode.ORGANIZER, repository.getUser(ada.uid)?.mode)
  }

  @Test
  fun chooseMode_whenAlreadyChosen_throwsPermissionDeniedAndKeepsTheFirstChoice() = runTest {
    repository.chooseMode(ada.uid, UserMode.VOLUNTEER)

    val thrown = runCatching {
      repository.chooseMode(ada.uid, UserMode.ORGANIZER)
    }
        .exceptionOrNull()

    assertTrue(thrown is UserRepositoryException.PermissionDenied)
    assertEquals(UserMode.VOLUNTEER, repository.getUser(ada.uid)?.mode)
  }

  @Test
  fun chooseMode_withFailure_throwsIt() = runTest {
    val failure = UserRepositoryException.Unavailable()
    repository.failure = failure

    val thrown = runCatching {
      repository.chooseMode(ada.uid, UserMode.VOLUNTEER)
    }
        .exceptionOrNull()

    assertEquals(failure, thrown)
    repository.failure = null
    assertNull(repository.getUser(ada.uid)?.mode)
  }
}
