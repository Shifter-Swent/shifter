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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyEventsViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

  private fun card(id: String, badge: EventBadge) =
      EventCardUi(
          id = id,
          title = "Event $id",
          dateLabel = "Sat 14 Jun 2025",
          locationLabel = "Lyon",
          timeLabel = "08:00 – 14:00",
          badge = badge,
          footerLabel = "Role: Volunteer",
      )

  private val confirmed = card("confirmed", EventBadge.CONFIRMED)
  private val pending = card("pending", EventBadge.PENDING_APPROVAL)
  private val preparation = card("preparation", EventBadge.IN_PREPARATION)
  private val ongoing = card("ongoing", EventBadge.ONGOING)
  private val ended = card("ended", EventBadge.ENDED)

  @Test
  fun init_showsLoadingUntilTheLoaderAnswers() = runTest {
    val answer = CompletableDeferred<List<EventCardUi>>()
    val viewModel = MyEventsViewModel { answer.await() }
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.isLoading)

    answer.complete(listOf(confirmed))
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(listOf(confirmed), viewModel.uiState.value.upcoming)
  }

  @Test
  fun load_putsEndedEventsInPastAndTheOthersInUpcoming() = runTest {
    val viewModel = MyEventsViewModel { listOf(confirmed, ended, pending, preparation, ongoing) }
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(listOf(confirmed, pending, preparation, ongoing), state.upcoming)
    assertEquals(listOf(ended), state.past)
    assertNull(state.errorMessage)
  }

  @Test
  fun selectTab_changesTheVisibleEvents() = runTest {
    val viewModel = MyEventsViewModel { listOf(confirmed, ended) }
    advanceUntilIdle()

    assertEquals(EventTab.UPCOMING, viewModel.uiState.value.selectedTab)
    assertEquals(listOf(confirmed), viewModel.uiState.value.visibleEvents)

    viewModel.selectTab(EventTab.PAST)

    assertEquals(EventTab.PAST, viewModel.uiState.value.selectedTab)
    assertEquals(listOf(ended), viewModel.uiState.value.visibleEvents)
  }

  @Test
  fun loaderFailure_showsItsMessage() = runTest {
    val viewModel = MyEventsViewModel { throw IllegalStateException("Network down") }
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals("Network down", state.errorMessage)
    assertTrue(state.upcoming.isEmpty())
  }

  @Test
  fun loaderFailureWithoutMessage_showsAGenericMessage() = runTest {
    val viewModel = MyEventsViewModel { throw IllegalStateException() }
    advanceUntilIdle()

    assertEquals("Could not load your events", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun refresh_clearsTheErrorAndKeepsTheSelectedTab() = runTest {
    var fail = true
    val viewModel = MyEventsViewModel {
      if (fail) throw IllegalStateException("Network down") else listOf(ended)
    }
    advanceUntilIdle()
    viewModel.selectTab(EventTab.PAST)

    fail = false
    viewModel.refresh()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertNull(state.errorMessage)
    assertEquals(EventTab.PAST, state.selectedTab)
    assertEquals(listOf(ended), state.visibleEvents)
  }

  @Test
  fun factory_createsAViewModelUsingTheLoader() = runTest {
    val viewModel =
        MyEventsViewModel.factory { listOf(ongoing) }
            .create(MyEventsViewModel::class.java, CreationExtras.Empty)
    advanceUntilIdle()

    assertEquals(listOf(ongoing), viewModel.uiState.value.upcoming)
  }
}
