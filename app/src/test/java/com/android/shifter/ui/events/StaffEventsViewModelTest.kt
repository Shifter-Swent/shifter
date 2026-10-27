// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

import androidx.lifecycle.viewmodel.CreationExtras
import com.swent.shifter.utils.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StaffEventsViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

  private val upcoming = SampleMyEvents.staff.filter { it.badge != EventBadge.ENDED }
  private val past = SampleMyEvents.staff.filter { it.badge == EventBadge.ENDED }

  /** Every request sent to the backend, including the ones it refuses. */
  private val requests = mutableListOf<String>()
  private val withdrawn = mutableListOf<String>()
  private var failure: Exception? = null
  /** When set, each request waits for it, as a slow backend would. */
  private var answer: CompletableDeferred<Unit>? = null
  private val withdrawer = EventWithdrawer { eventId ->
    requests += eventId
    answer?.await()
    failure?.let { throw it }
    // Like the backend, someone who already withdrew is no longer a participant.
    check(eventId !in withdrawn) { "Not a participant of $eventId" }
    withdrawn += eventId
  }

  private fun viewModel() = StaffEventsViewModel(SampleMyEvents.staffLoader, withdrawer)

  @Test
  fun withdraw_removesTheCardOnceTheBackendConfirmed() = runTest {
    val viewModel = viewModel()
    advanceUntilIdle()

    viewModel.withdraw("city-marathon")
    advanceUntilIdle()

    assertEquals(listOf("city-marathon"), withdrawn)
    assertEquals(
        upcoming.map { it.id } - "city-marathon",
        viewModel.uiState.value.upcoming.map { it.id },
    )
    assertEquals(past, viewModel.uiState.value.past)
    assertFalse(viewModel.uiState.value.withdrawFailed)
  }

  @Test
  fun withdrawFailure_keepsTheCardAndReportsIt() = runTest {
    val viewModel = viewModel()
    advanceUntilIdle()
    failure = IllegalStateException("offline")

    viewModel.withdraw("city-marathon")
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.withdrawFailed)
    assertEquals(upcoming, viewModel.uiState.value.upcoming)
  }

  @Test
  fun withdraw_afterAFailure_canBeRetried() = runTest {
    val viewModel = viewModel()
    advanceUntilIdle()
    failure = IllegalStateException("offline")
    viewModel.withdraw("city-marathon")
    advanceUntilIdle()

    failure = null
    // The failed withdrawal released the event: otherwise this second call would be ignored.
    viewModel.withdraw("city-marathon")
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.withdrawFailed)
    assertEquals(listOf("city-marathon"), withdrawn)
  }

  @Test
  fun withdraw_twiceWhilePending_sendsOneRequest() = runTest {
    val viewModel = viewModel()
    advanceUntilIdle()
    answer = CompletableDeferred()

    viewModel.withdraw("city-marathon")
    advanceUntilIdle()
    viewModel.withdraw("city-marathon")
    advanceUntilIdle()
    answer!!.complete(Unit)
    advanceUntilIdle()

    assertEquals(listOf("city-marathon"), requests)
    assertFalse(viewModel.uiState.value.upcoming.any { it.id == "city-marathon" })
    assertFalse(viewModel.uiState.value.withdrawFailed)
  }

  @Test
  fun withdraw_ofAnotherEventWhilePending_isSent() = runTest {
    val viewModel = viewModel()
    advanceUntilIdle()
    answer = CompletableDeferred()

    viewModel.withdraw("city-marathon")
    viewModel.withdraw("tech-summit")
    advanceUntilIdle()
    answer!!.complete(Unit)
    advanceUntilIdle()

    assertEquals(listOf("city-marathon", "tech-summit"), requests)
    assertEquals(listOf("city-marathon", "tech-summit"), withdrawn)
  }

  @Test
  fun factory_createsAViewModelUsingTheLoaderAndTheWithdrawer() = runTest {
    val viewModel =
        StaffEventsViewModel.factory(SampleMyEvents.staffLoader, withdrawer)
            .create(StaffEventsViewModel::class.java, CreationExtras.Empty)
    advanceUntilIdle()

    viewModel.withdraw("tech-summit")
    advanceUntilIdle()

    assertEquals(listOf("tech-summit"), withdrawn)
    assertFalse(viewModel.uiState.value.upcoming.any { it.id == "tech-summit" })
  }
}
