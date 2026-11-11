// Co-authored-by: Claude Opus 4.8 <noreply@anthropic.com>
package com.swent.shifter.ui.location

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import com.swent.shifter.utils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [publishWhileForeground] against a [TestLifecycleOwner], so the foreground and sign-in boundaries
 * are driven with virtual time and no device.
 *
 * [publish] here only records that it ran and whether it is still active, so the test asserts when
 * the publishing starts and stops rather than what a coordinator does with it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveLocationForegroundTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  /** Every user a run was started for, in order, restarts included. */
  private val started = mutableListOf<String>()

  /** Whether a run for the user is active right now. */
  private val active = mutableMapOf<String, Boolean>()

  private suspend fun record(userId: String) {
    started += userId
    active[userId] = true
    try {
      awaitCancellation()
    } finally {
      active[userId] = false
    }
  }

  @Test
  fun publishWhileForeground_startsOnlyWhileStarted() =
      runTest(mainDispatcherRule.dispatcher) {
        val owner = lifecycleOwner(Lifecycle.State.CREATED)
        launchForeground(owner, MutableStateFlow("user-1"))
        runCurrent()

        // Below STARTED: nothing is published yet.
        assertTrue(started.isEmpty())

        owner.setCurrentState(Lifecycle.State.STARTED)
        runCurrent()

        assertEquals(listOf("user-1"), started)
        assertEquals(true, active["user-1"])
      }

  @Test
  fun publishWhileForeground_backgrounding_cancelsTheRun() =
      runTest(mainDispatcherRule.dispatcher) {
        val owner = lifecycleOwner(Lifecycle.State.STARTED)
        launchForeground(owner, MutableStateFlow("user-1"))
        runCurrent()
        assertEquals(true, active["user-1"])

        owner.setCurrentState(Lifecycle.State.CREATED)
        runCurrent()

        assertEquals(false, active["user-1"])
      }

  @Test
  fun publishWhileForeground_returningToForeground_startsAFreshRun() =
      runTest(mainDispatcherRule.dispatcher) {
        val owner = lifecycleOwner(Lifecycle.State.STARTED)
        launchForeground(owner, MutableStateFlow("user-1"))
        runCurrent()
        owner.setCurrentState(Lifecycle.State.CREATED)
        runCurrent()

        owner.setCurrentState(Lifecycle.State.STARTED)
        runCurrent()

        // Started again, so this is a new run, a fresh eligibility check, not a resumed one.
        assertEquals(listOf("user-1", "user-1"), started)
        assertEquals(true, active["user-1"])
      }

  @Test
  fun publishWhileForeground_signingOut_cancelsTheRunAtOnce() =
      runTest(mainDispatcherRule.dispatcher) {
        val owner = lifecycleOwner(Lifecycle.State.STARTED)
        val userIds = MutableStateFlow<String?>("user-1")
        launchForeground(owner, userIds)
        runCurrent()
        assertEquals(true, active["user-1"])

        // Signed out while still in the foreground: the run stops immediately.
        userIds.value = null
        runCurrent()

        assertEquals(false, active["user-1"])
      }

  @Test
  fun publishWhileForeground_switchingAccounts_cancelsThePreviousUser() =
      runTest(mainDispatcherRule.dispatcher) {
        val owner = lifecycleOwner(Lifecycle.State.STARTED)
        val userIds = MutableStateFlow<String?>("user-1")
        launchForeground(owner, userIds)
        runCurrent()

        userIds.value = "user-2"
        runCurrent()

        // The previous user's run is cancelled, the new user's started.
        assertEquals(false, active["user-1"])
        assertEquals(true, active["user-2"])
        assertEquals(listOf("user-1", "user-2"), started)
      }

  @Test
  fun publishWhileForeground_whenSignedOut_startsNothing() =
      runTest(mainDispatcherRule.dispatcher) {
        val owner = lifecycleOwner(Lifecycle.State.STARTED)
        launchForeground(owner, MutableStateFlow(null))
        runCurrent()

        assertTrue(started.isEmpty())
        assertFalse(active.values.any { it })
      }

  private fun lifecycleOwner(state: Lifecycle.State) =
      TestLifecycleOwner(state, mainDispatcherRule.dispatcher)

  private fun kotlinx.coroutines.test.TestScope.launchForeground(
      owner: TestLifecycleOwner,
      userIds: MutableStateFlow<String?>,
  ) = backgroundScope.launch { publishWhileForeground(owner.lifecycle, userIds, ::record) }
}
