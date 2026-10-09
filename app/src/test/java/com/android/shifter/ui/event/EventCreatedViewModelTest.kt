// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.lifecycle.viewmodel.CreationExtras
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FakeEventRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventCreatedViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private lateinit var repository: FakeEventRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    repository = FakeEventRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** Creates an event through the fake repository, which assigns its id and join code. */
  private suspend fun createdEvent(): Event =
      repository.createEvent(
          Event(
              organizerId = "organizer-1",
              title = "Paléo Festival",
              description = "Open-air music festival",
              type = EventType.MUSIC,
              startAt = Instant.parse("2027-07-21T12:00:00Z"),
              endAt = Instant.parse("2027-07-26T21:30:00Z"),
              location = EventLocation("Nyon"),
              createdAt = Instant.parse("2027-06-01T10:00:00Z"),
          )
      )

  @Test
  fun isLoadingUntilTheEventArrives() = runTest {
    val event = createdEvent()

    val viewModel = EventCreatedViewModel(repository, event.id)

    assertTrue(viewModel.uiState.value.isLoading)
  }

  @Test
  fun showsTheStoredJoinCodeAndTitle() = runTest {
    val event = createdEvent()

    val viewModel = EventCreatedViewModel(repository, event.id)
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(
        EventCreatedUiState(
            eventTitle = "Paléo Festival",
            joinCode = event.joinCode,
            isLoading = false,
        ),
        viewModel.uiState.value,
    )
  }

  @Test
  fun unknownEvent_reportsFailure() = runTest {
    val viewModel = EventCreatedViewModel(repository, "missing")
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(EventCreatedError.NOT_FOUND, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals("", viewModel.uiState.value.joinCode)
  }

  @Test
  fun eventWithoutJoinCode_reportsNotFoundInsteadOfAnEmptyCode() = runTest {
    val event = createdEvent()
    repository.events[0] = event.copy(joinCode = "")

    val viewModel = EventCreatedViewModel(repository, event.id)
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(EventCreatedError.NOT_FOUND, viewModel.uiState.value.error)
  }

  @Test
  fun failuresOtherThanOffline_areNotReportedAsOffline() = runTest {
    val event = createdEvent()
    val errors =
        listOf(
            EventRepositoryException.PermissionDenied(),
            EventRepositoryException.Unknown(),
            IllegalStateException("bug"),
        )

    for (error in errors) {
      repository.failure = error
      val viewModel = EventCreatedViewModel(repository, event.id)
      dispatcher.scheduler.advanceUntilIdle()

      assertEquals(error.toString(), EventCreatedError.UNEXPECTED, viewModel.uiState.value.error)
    }
  }

  @Test
  fun createdEvent_isShownRightAwayWithoutFetchingIt() = runTest {
    val event = createdEvent()
    repository.failure = EventRepositoryException.Unavailable()

    val viewModel = EventCreatedViewModel(repository, event.id, createdEvent = event)
    val shownBeforeAnyLoad = viewModel.uiState.value
    dispatcher.scheduler.advanceUntilIdle()

    val expected =
        EventCreatedUiState(eventTitle = event.title, joinCode = event.joinCode, isLoading = false)
    assertEquals(expected, shownBeforeAnyLoad)
    assertEquals(expected, viewModel.uiState.value)
  }

  @Test
  fun unusableCreatedEvent_loadsTheStoredOneInstead() = runTest {
    val stored = createdEvent()
    val other = createdEvent()

    for (given in listOf(other, stored.copy(joinCode = ""))) {
      val viewModel = EventCreatedViewModel(repository, stored.id, createdEvent = given)
      dispatcher.scheduler.advanceUntilIdle()

      assertEquals(stored.joinCode, viewModel.uiState.value.joinCode)
    }
  }

  @Test
  fun offline_reportsOfflineAndRetryLoadsTheEvent() = runTest {
    val event = createdEvent()
    repository.failure = EventRepositoryException.Unavailable()
    val viewModel = EventCreatedViewModel(repository, event.id)
    dispatcher.scheduler.advanceUntilIdle()
    assertEquals(EventCreatedError.OFFLINE, viewModel.uiState.value.error)

    repository.failure = null
    viewModel.retry()
    assertTrue(viewModel.uiState.value.isLoading)
    dispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.uiState.value.error)
    assertEquals(event.joinCode, viewModel.uiState.value.joinCode)
  }

  @Test
  fun retryWhileLoading_isIgnored() = runTest {
    val event = createdEvent()
    val viewModel = EventCreatedViewModel(repository, event.id)

    viewModel.retry()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(event.joinCode, viewModel.uiState.value.joinCode)
  }

  @Test
  fun showsTheRequestedEvent_notAnotherOneOfTheOrganizer() = runTest {
    createdEvent()
    val second = createdEvent()

    val viewModel = EventCreatedViewModel(repository, second.id)
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(second.joinCode, viewModel.uiState.value.joinCode)
  }

  @Test
  fun failedRetry_staysFailedAndCanBeRetriedAgain() = runTest {
    val event = createdEvent()
    repository.failure = EventRepositoryException.Unavailable()
    val viewModel = EventCreatedViewModel(repository, event.id)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.retry()
    dispatcher.scheduler.advanceUntilIdle()
    assertEquals(EventCreatedError.OFFLINE, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isLoading)

    repository.failure = null
    viewModel.retry()
    dispatcher.scheduler.advanceUntilIdle()
    assertEquals(event.joinCode, viewModel.uiState.value.joinCode)
  }

  @Test
  fun factory_createsViewModelForTheGivenEvent() = runTest {
    val event = createdEvent()
    val factory = EventCreatedViewModel.factory(repository, event.id, createdEvent = event)
    repository.failure = EventRepositoryException.Unavailable()

    val viewModel = factory.create(EventCreatedViewModel::class.java, CreationExtras.Empty)
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(event.joinCode, viewModel.uiState.value.joinCode)
  }
}
