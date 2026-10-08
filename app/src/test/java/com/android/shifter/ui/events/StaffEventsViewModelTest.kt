// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

import androidx.lifecycle.viewmodel.CreationExtras
import com.swent.shifter.utils.MainDispatcherRule
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

  private val withdrawn = mutableListOf<String>()
  private var failure: Exception? = null
  private val withdrawer = EventWithdrawer { eventId ->
    failure?.let { throw it }
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
  fun withdrawAgain_clearsThePreviousFailure() = runTest {
    val viewModel = viewModel()
    advanceUntilIdle()
    failure = IllegalStateException("offline")
    viewModel.withdraw("city-marathon")
    advanceUntilIdle()

    failure = null
    viewModel.withdraw("city-marathon")
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.withdrawFailed)
    assertEquals(listOf("city-marathon"), withdrawn)
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
