// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.shifter.R
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventType
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventCreatedScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val loaded =
      EventCreatedUiState(eventTitle = "Paléo Festival", joinCode = "ABC234", isLoading = false)

  /** Returns [event] for its id, like a backend that already stored it. */
  private class StoredEventRepository(private val event: Event) : EventRepository {
    /** When set, [getEvent] throws it, to simulate a backend that cannot be reached. */
    @Volatile var failure: Exception? = null

    override suspend fun createEvent(event: Event): Event = event

    override suspend fun getEvent(eventId: String): Event? {
      failure?.let { throw it }
      return event.takeIf { it.id == eventId }
    }

    override suspend fun getEventByJoinCode(joinCode: String): Event? = null

    override suspend fun getEventsByOrganizer(organizerId: String): List<Event> = emptyList()

    override suspend fun getEventsByMember(userId: String): List<Event> = emptyList()
  }

  private fun setContent(
      state: EventCreatedUiState,
      onRetry: () -> Unit = {},
      onDone: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      ShifterTheme { EventCreatedContent(state, onRetry = onRetry, onDone = onDone) }
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  private fun string(id: Int) =
      InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

  private val storedEvent =
      Event(
          id = "event-1",
          organizerId = "organizer-1",
          title = "Paléo Festival",
          description = "Open-air music festival",
          type = EventType.MUSIC,
          startAt = Instant.parse("2027-07-21T12:00:00Z"),
          endAt = Instant.parse("2027-07-26T21:30:00Z"),
          location = EventLocation("Nyon"),
          joinCode = "XK7P2M",
          createdAt = Instant.parse("2027-06-01T10:00:00Z"),
      )

  private fun waitFor(tag: String) = composeTestRule.waitUntil {
    composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
  }

  @Test
  fun screen_showsTheJoinCodeStoredForTheEvent() {
    val viewModel = EventCreatedViewModel(StoredEventRepository(storedEvent), "event-1")
    composeTestRule.setContent { ShifterTheme { EventCreatedScreen(viewModel, onDone = {}) } }

    waitFor(EventCreatedScreenTestTags.JOIN_CODE)
    node(EventCreatedScreenTestTags.JOIN_CODE).assertTextEquals("XK7P2M")
    node(EventCreatedScreenTestTags.EVENT_TITLE).assertTextEquals("Paléo Festival")
  }

  @Test
  fun displaysTheJoinCodeWithItsLabel() {
    setContent(loaded)

    node(EventCreatedScreenTestTags.JOIN_CODE).assertIsDisplayed().assertTextEquals("ABC234")
    composeTestRule.onNodeWithText(string(R.string.event_created_code_label)).assertIsDisplayed()
  }

  @Test
  fun displaysTheEventTitle() {
    setContent(loaded)

    node(EventCreatedScreenTestTags.EVENT_TITLE).assertTextEquals("Paléo Festival")
  }

  @Test
  fun whileLoading_showsProgressAndNoCode() {
    setContent(EventCreatedUiState())

    node(EventCreatedScreenTestTags.LOADING_INDICATOR).assertIsDisplayed()
    node(EventCreatedScreenTestTags.JOIN_CODE).assertDoesNotExist()
  }

  @Test
  fun loadFailure_showsErrorAndRetryCallsOnRetry() {
    var retries = 0
    setContent(EventCreatedUiState(isLoading = false, loadFailed = true), onRetry = { retries++ })

    node(EventCreatedScreenTestTags.LOAD_ERROR)
        .assertTextEquals(string(R.string.event_created_load_failed))
    node(EventCreatedScreenTestTags.JOIN_CODE).assertDoesNotExist()
    node(EventCreatedScreenTestTags.RETRY_BUTTON).performScrollTo().performClick()

    assertEquals(1, retries)
  }

  @Test
  fun clickingDone_callsOnDoneOnce() {
    var done = 0
    setContent(loaded, onDone = { done++ })

    node(EventCreatedScreenTestTags.DONE_BUTTON).performScrollTo().performClick()

    assertEquals(1, done)
  }

  @Test
  fun clickingBackLink_callsOnDone() {
    var done = 0
    setContent(loaded, onDone = { done++ })

    node(EventCreatedScreenTestTags.BACK_BUTTON).performClick()

    assertEquals(1, done)
  }

  @Test
  fun screen_backendUnreachable_retryShowsTheCodeOnceItIsBack() {
    val repository = StoredEventRepository(storedEvent)
    repository.failure = IllegalStateException("offline")
    val viewModel = EventCreatedViewModel(repository, "event-1")
    composeTestRule.setContent { ShifterTheme { EventCreatedScreen(viewModel, onDone = {}) } }

    waitFor(EventCreatedScreenTestTags.RETRY_BUTTON)
    repository.failure = null
    node(EventCreatedScreenTestTags.RETRY_BUTTON).performScrollTo().performClick()

    waitFor(EventCreatedScreenTestTags.JOIN_CODE)
    node(EventCreatedScreenTestTags.JOIN_CODE).assertTextEquals("XK7P2M")
    node(EventCreatedScreenTestTags.LOAD_ERROR).assertDoesNotExist()
  }

  @Test
  fun loadedState_showsScreenTitleAndShareHint() {
    setContent(loaded)

    composeTestRule.onNodeWithText(string(R.string.event_created_title)).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.event_created_code_hint)).assertExists()
  }

  @Test
  fun organizerCanLeave_evenWhileLoadingOrAfterAFailure() {
    var done = 0
    var state by mutableStateOf(EventCreatedUiState())
    composeTestRule.setContent {
      ShifterTheme { EventCreatedContent(state, onRetry = {}, onDone = { done++ }) }
    }

    node(EventCreatedScreenTestTags.DONE_BUTTON).performScrollTo().performClick()
    state = EventCreatedUiState(isLoading = false, loadFailed = true)
    composeTestRule.waitForIdle()
    node(EventCreatedScreenTestTags.DONE_BUTTON).performScrollTo().performClick()

    assertEquals(2, done)
  }
}
