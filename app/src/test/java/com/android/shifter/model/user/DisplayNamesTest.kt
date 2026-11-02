// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayNamesTest {
  private val repository = mockk<UserRepository>()

  @Test
  fun displayNames_mapsMissingOrBlankNamesToNullAndReadsEachUserOnce() = runTest {
    coEvery { repository.getPublicProfile("alice") } returns PublicProfile("alice", "Alice")
    coEvery { repository.getPublicProfile("bob") } returns PublicProfile("bob", "  ")
    coEvery { repository.getPublicProfile("carol") } returns null

    val names = repository.displayNames(listOf("alice", "bob", "carol", "alice"))

    assertEquals(mapOf("alice" to "Alice", "bob" to null, "carol" to null), names)
    coVerify(exactly = 1) { repository.getPublicProfile("alice") }
  }

  @Test
  fun displayNames_leavesOutOnlyTheUsersWhoseProfileCannotBeRead() = runTest {
    coEvery { repository.getPublicProfile("alice") } throws UserRepositoryException.Unavailable()
    coEvery { repository.getPublicProfile("bob") } returns PublicProfile("bob", "Bob")

    val names = repository.displayNames(listOf("alice", "bob"))

    assertEquals(mapOf("bob" to "Bob"), names)
    assertFalse("alice" in names)
  }

  @Test
  fun displayNames_readsTheProfilesInParallel() = runTest {
    val started = mutableListOf<String>()
    val gate = CompletableDeferred<Unit>()
    coEvery { repository.getPublicProfile(any()) } coAnswers
        {
          val uid = firstArg<String>()
          started += uid
          gate.await()
          PublicProfile(uid, uid)
        }

    val names = async { repository.displayNames(listOf("alice", "bob", "carol")) }
    runCurrent()

    // Every read started before the first one answered.
    assertEquals(listOf("alice", "bob", "carol"), started)
    gate.complete(Unit)
    assertEquals(setOf("alice", "bob", "carol"), names.await().keys)
  }

  @Test
  fun displayNames_letsAnUnexpectedErrorFailTheWholeRead() = runTest {
    coEvery { repository.getPublicProfile(any()) } returns PublicProfile("bob", "Bob")
    coEvery { repository.getPublicProfile("alice") } throws IllegalStateException("bug")

    val failure = runCatching { repository.displayNames(listOf("alice", "bob")) }.exceptionOrNull()

    assertTrue("Expected IllegalStateException, got $failure", failure is IllegalStateException)
  }
}
