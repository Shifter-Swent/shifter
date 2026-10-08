// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.shifter.R
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventType
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreateEventScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val clock = Clock.fixed(Instant.parse("2027-06-01T10:00:00Z"), ZoneId.of("UTC"))

  /** Records created events; [gate] lets a test hold the save in progress. */
  private class RecordingRepository : EventRepository {
    val created = mutableListOf<Event>()
    var gate: CompletableDeferred<Unit>? = null
    var failure: Exception? = null

    override suspend fun createEvent(event: Event): Event {
      gate?.await()
      failure?.let { throw it }
      return event.copy(id = "event-1", joinCode = "ABC234").also { created += it }
    }

    override suspend fun getEvent(eventId: String): Event? = null

    override suspend fun getEventByJoinCode(joinCode: String): Event? = null

    override suspend fun getEventsByOrganizer(organizerId: String): List<Event> = emptyList()

    override suspend fun getEventsByMember(userId: String): List<Event> = emptyList()
  }

  private fun setScreen(
      repository: EventRepository,
      onEventCreated: (Event) -> Unit = {},
      onBack: () -> Unit = {},
  ) {
    val viewModel = CreateEventViewModel(repository, organizerId = "organizer-1", clock = clock)
    composeTestRule.setContent {
      ShifterTheme {
        CreateEventScreen(viewModel, onEventCreated = onEventCreated, onBack = onBack)
      }
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  /** Error texts are merged into their text field, so they are only found in the unmerged tree. */
  private fun errorNode(field: EventFormField) =
      composeTestRule.onNodeWithTag(CreateEventScreenTestTags.error(field), useUnmergedTree = true)

  private fun fillValidForm() {
    node(CreateEventScreenTestTags.TITLE_FIELD).performTextInput("Paléo Festival")
    node(CreateEventScreenTestTags.DESCRIPTION_FIELD).performTextInput("Open-air festival")
    node(CreateEventScreenTestTags.typeChip(EventType.MUSIC)).performScrollTo().performClick()
    node(CreateEventScreenTestTags.ADDRESS_FIELD).performScrollTo().performTextInput("Nyon")
    node(CreateEventScreenTestTags.START_FIELD)
        .performScrollTo()
        .performTextInput("21/07/2027 14:00")
    node(CreateEventScreenTestTags.END_FIELD).performScrollTo().performTextInput("26/07/2027 23:30")
  }

  @Test
  fun displaysEveryFormField() {
    setScreen(RecordingRepository())

    listOf(
            CreateEventScreenTestTags.TITLE_FIELD,
            CreateEventScreenTestTags.DESCRIPTION_FIELD,
            CreateEventScreenTestTags.ADDRESS_FIELD,
            CreateEventScreenTestTags.START_FIELD,
            CreateEventScreenTestTags.END_FIELD,
            CreateEventScreenTestTags.SUBMIT_BUTTON,
        )
        .forEach { node(it).performScrollTo().assertIsDisplayed() }
    EventType.entries.forEach {
      node(CreateEventScreenTestTags.typeChip(it)).performScrollTo().assertIsDisplayed()
    }
  }

  @Test
  fun submittingEmptyForm_showsErrorsAndCreatesNothing() {
    val repository = RecordingRepository()
    setScreen(repository)

    node(CreateEventScreenTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

    EventFormField.entries
        .filter { it != EventFormField.TYPE }
        .forEach { errorNode(it).performScrollTo().assertIsDisplayed() }
    node(CreateEventScreenTestTags.TYPE_ERROR).performScrollTo().assertIsDisplayed()
    assertTrue(repository.created.isEmpty())
  }

  @Test
  fun selectingAType_marksItsChipSelected() {
    setScreen(RecordingRepository())

    node(CreateEventScreenTestTags.typeChip(EventType.SPORT)).performScrollTo().performClick()

    node(CreateEventScreenTestTags.typeChip(EventType.SPORT)).assertIsSelected()
  }

  @Test
  fun submittingValidForm_createsEventAndReportsIt() {
    val repository = RecordingRepository()
    var reported: Event? = null
    setScreen(repository, onEventCreated = { reported = it })

    fillValidForm()
    node(CreateEventScreenTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

    composeTestRule.waitUntil { reported != null }
    assertEquals(repository.created.single(), reported)
    assertEquals("Paléo Festival", reported?.title)
  }

  @Test
  fun whileSaving_showsProgressAndDisablesSubmit() {
    val repository = RecordingRepository().apply { gate = CompletableDeferred() }
    setScreen(repository)

    fillValidForm()
    node(CreateEventScreenTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

    composeTestRule
        .onNodeWithTag(CreateEventScreenTestTags.SAVING_INDICATOR, useUnmergedTree = true)
        .assertIsDisplayed()
    node(CreateEventScreenTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
    repository.gate?.complete(Unit)
  }

  @Test
  fun whileSaving_disablesTextFields() {
    val repository = RecordingRepository().apply { gate = CompletableDeferred() }
    setScreen(repository)

    fillValidForm()
    node(CreateEventScreenTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

    node(CreateEventScreenTestTags.TITLE_FIELD).assertIsNotEnabled()
    node(CreateEventScreenTestTags.END_FIELD).assertIsNotEnabled()
    repository.gate?.complete(Unit)
  }

  @Test
  fun fixingAFieldAfterSubmit_removesOnlyItsError() {
    setScreen(RecordingRepository())
    node(CreateEventScreenTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

    node(CreateEventScreenTestTags.TITLE_FIELD).performScrollTo().performTextInput("Paléo")

    errorNode(EventFormField.TITLE).assertDoesNotExist()
    errorNode(EventFormField.DESCRIPTION).assertExists()
  }

  @Test
  fun tooLongTitle_showsItsMessageAndCreatesNothing() {
    val repository = RecordingRepository()
    setScreen(repository)
    fillValidForm()
    node(CreateEventScreenTestTags.TITLE_FIELD)
        .performScrollTo()
        .performTextReplacement("a".repeat(CreateEventViewModel.TITLE_MAX_LENGTH + 1))

    node(CreateEventScreenTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

    val message =
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.create_event_error_title_too_long)
    errorNode(EventFormField.TITLE).assertTextEquals(message)
    assertTrue(repository.created.isEmpty())
  }

  @Test
  fun startInThePast_showsItsMessageUnderStart() {
    setScreen(RecordingRepository())
    fillValidForm()
    node(CreateEventScreenTestTags.START_FIELD)
        .performScrollTo()
        .performTextReplacement("01/06/2027 09:00")

    node(CreateEventScreenTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

    val message =
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.create_event_error_start_in_past)
    errorNode(EventFormField.START).assertTextEquals(message)
    errorNode(EventFormField.END).assertDoesNotExist()
  }

  @Test
  fun repositoryFailure_showsErrorAndLetsTheOrganizerRetry() {
    val repository = RecordingRepository().apply { failure = IllegalStateException("offline") }
    var reported: Event? = null
    setScreen(repository, onEventCreated = { reported = it })

    fillValidForm()
    node(CreateEventScreenTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

    composeTestRule.waitUntil {
      composeTestRule
          .onAllNodesWithTag(CreateEventScreenTestTags.SAVE_ERROR)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    node(CreateEventScreenTestTags.SUBMIT_BUTTON).performScrollTo().assertIsEnabled()
    assertNull(reported)
  }

  @Test
  fun createdEvent_isReportedOnlyOnce() {
    var reports = 0
    setScreen(RecordingRepository(), onEventCreated = { reports++ })

    fillValidForm()
    node(CreateEventScreenTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
    composeTestRule.waitUntil { reports > 0 }
    node(CreateEventScreenTestTags.TITLE_FIELD).performScrollTo().performTextInput(" 2027")
    composeTestRule.waitForIdle()

    assertEquals(1, reports)
  }

  @Test
  fun saveFailure_isShown() {
    composeTestRule.setContent {
      ShifterTheme {
        CreateEventContent(
            state = CreateEventUiState(saveFailed = true),
            onTitleChange = {},
            onDescriptionChange = {},
            onTypeChange = {},
            onAddressChange = {},
            onStartAtChange = {},
            onEndAtChange = {},
            onSubmit = {},
            onBack = {},
        )
      }
    }

    node(CreateEventScreenTestTags.SAVE_ERROR).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun backButton_callsOnBack() {
    var backCalled = false
    setScreen(RecordingRepository(), onBack = { backCalled = true })

    node(CreateEventScreenTestTags.BACK_BUTTON).performClick()

    assertTrue(backCalled)
  }
}
