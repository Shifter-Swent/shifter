// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.notification

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import com.swent.shifter.model.notification.AppNotification
import com.swent.shifter.model.notification.FakeNotifier
import com.swent.shifter.model.notification.NotificationKind
import com.swent.shifter.model.notification.NotificationTarget
import com.swent.shifter.utils.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Covers [runAppNotifications] against a [TestLifecycleOwner]: who the notifications run for, when
 * they stop, and the notifications cleared on sign-out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppNotificationsTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val notifier = FakeNotifier()
  private val userIds = MutableStateFlow<String?>("u1")

  /** Who a run is active for right now. */
  private val running = mutableListOf<String>()

  @Test
  fun inTheForeground_runsForTheSignedInUser() =
      runTest(mainDispatcherRule.dispatcher) {
        start(Lifecycle.State.STARTED)

        assertEquals(listOf("u1"), running)
      }

  @Test
  fun inTheBackground_runsNothing() =
      runTest(mainDispatcherRule.dispatcher) {
        val owner = start(Lifecycle.State.STARTED)

        owner.setCurrentState(Lifecycle.State.CREATED)
        runCurrent()

        assertEquals(emptyList<String>(), running)
      }

  @Test
  fun signingOut_stopsTheRun_andClearsTheNotifications() =
      runTest(mainDispatcherRule.dispatcher) {
        start(Lifecycle.State.STARTED)
        notifier.show(listOf(NOTIFICATION))

        userIds.value = null
        runCurrent()

        assertEquals(emptyList<String>(), running)
        assertEquals(emptySet<String>(), notifier.activeIdentities())
      }

  @Test
  fun signingOut_duringAShow_clearsOnceTheRunHasStopped() =
      runTest(mainDispatcherRule.dispatcher) {
        val owner = TestLifecycleOwner(Lifecycle.State.STARTED, mainDispatcherRule.dispatcher)
        backgroundScope.launch {
          runAppNotifications(owner.lifecycle, userIds, notifier, mainDispatcherRule.dispatcher) {
            try {
              awaitCancellation()
            } finally {
              // A run cancelled in the middle of show still posts what it was showing.
              notifier.show(listOf(NOTIFICATION))
            }
          }
        }
        runCurrent()

        userIds.value = null
        runCurrent()

        assertEquals(emptySet<String>(), notifier.activeIdentities())
      }

  private fun TestScope.start(state: Lifecycle.State): TestLifecycleOwner {
    val owner = TestLifecycleOwner(state, mainDispatcherRule.dispatcher)
    backgroundScope.launch {
      runAppNotifications(owner.lifecycle, userIds, notifier, mainDispatcherRule.dispatcher) {
        running += it
        try {
          awaitCancellation()
        } finally {
          running -= it
        }
      }
    }
    runCurrent()
    return owner
  }

  private companion object {
    val NOTIFICATION =
        AppNotification(
            NotificationKind.OPEN_SPOTS,
            "m1",
            "Fest",
            "Text",
            Instant.EPOCH,
            NotificationTarget.Requests("e1", "Fest"),
        )
  }
}
