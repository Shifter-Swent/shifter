// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import android.content.res.Configuration
import android.icu.text.DateFormat
import android.icu.text.DisplayContext
import android.icu.util.TimeZone
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.ui.map.LocationPickerTestTags
import com.swent.shifter.ui.navigation.NavigationTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
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
  }

  private fun setScreen(
      repository: EventRepository,
      onEventCreated: (Event) -> Unit = {},
      onBack: () -> Unit = {},
      locale: Locale? = null,
  ) {
    val viewModel =
        CreateEventViewModel(repository, currentUserId = { "organizer-1" }, clock = clock)
    composeTestRule.setContent {
      InLocale(locale) {
        ShifterTheme {
          CreateEventScreen(viewModel, onEventCreated = onEventCreated, onBack = onBack)
        }
      }
    }
  }

  /** The day picked in place of the typed 21st, in the month the pickers open on. */
  private val JULY_22: LocalDate = LocalDate.of(2027, 7, 22)

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  private fun cancelLabel(): String =
      InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dialog_cancel)

  /**
   * The clickable cell of [day] in the open date picker. Material3 exposes each cell as its date
   * written in the language of [locale], and nothing else (the number shown is cleared from the
   * semantics), so the label is built here as the picker builds it: through ICU, for its
   * [DatePickerDefaults.YearMonthWeekdayDaySkeleton], at UTC midnight. Today's cell prefixes it
   * with "Today", hence the substring match.
   */
  @OptIn(ExperimentalMaterial3Api::class)
  private fun dayCell(day: LocalDate, locale: Locale = Locale.getDefault()): SemanticsMatcher {
    val format =
        DateFormat.getInstanceForSkeleton(DatePickerDefaults.YearMonthWeekdayDaySkeleton, locale)
            .apply {
              setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE)
              timeZone = TimeZone.GMT_ZONE
            }
    val label = format.format(Date(day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()))
    return hasText(label, substring = true) and hasClickAction()
  }

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
  fun opening_displaysEveryFormField() {
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

    listOf(
            EventFormField.TITLE,
            EventFormField.DESCRIPTION,
            EventFormField.ADDRESS,
            EventFormField.START,
            EventFormField.END,
        )
        .forEach { errorNode(it).performScrollTo().assertIsDisplayed() }
    errorNode(EventFormField.TYPE).performScrollTo().assertIsDisplayed()
    // The coordinates are optional, so an empty submit shows no error under them.
    errorNode(EventFormField.LATITUDE).assertDoesNotExist()
    errorNode(EventFormField.LONGITUDE).assertDoesNotExist()
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
  fun createdEvent_isNotReportedAgainAfterRecreation() {
    var reports = 0
    val viewModel = CreateEventViewModel(RecordingRepository(), { "organizer-1" }, clock)
    val restorationTester = StateRestorationTester(composeTestRule)
    restorationTester.setContent {
      ShifterTheme { CreateEventScreen(viewModel, onEventCreated = { reports++ }, onBack = {}) }
    }

    fillValidForm()
    node(CreateEventScreenTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
    composeTestRule.waitUntil { reports > 0 }
    restorationTester.emulateSavedInstanceStateRestore()
    composeTestRule.waitForIdle()

    assertEquals(1, reports)
  }

  @Test
  fun dateFields_exposeTheirLabelAndErrorToAccessibility() {
    setScreen(RecordingRepository())
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val start = context.getString(R.string.create_event_field_start)
    val end = context.getString(R.string.create_event_field_end)

    node(CreateEventScreenTestTags.START_FIELD).assert(hasContentDescription(start))
    node(CreateEventScreenTestTags.END_FIELD).assert(hasContentDescription(end))

    node(CreateEventScreenTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

    val dateError = context.getString(R.string.create_event_error_date_invalid)
    listOf(CreateEventScreenTestTags.START_FIELD, CreateEventScreenTestTags.END_FIELD).forEach {
      node(it).assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, dateError))
    }
  }

  @Test
  fun saveFailure_isShown() {
    composeTestRule.setContent {
      ShifterTheme {
        CreateEventContent(
            state = CreateEventUiState(saveFailed = true),
            actions = CreateEventActions(),
        )
      }
    }

    node(CreateEventScreenTestTags.SAVE_ERROR).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun notSignedIn_isShown() {
    composeTestRule.setContent {
      ShifterTheme {
        CreateEventContent(
            state = CreateEventUiState(notSignedIn = true),
            actions = CreateEventActions(),
        )
      }
    }

    node(CreateEventScreenTestTags.NOT_SIGNED_IN_ERROR).performScrollTo().assertIsDisplayed()
    node(CreateEventScreenTestTags.SAVE_ERROR).assertDoesNotExist()
  }

  @Test
  fun unconfirmedSave_isShownInsteadOfTheFailure() {
    composeTestRule.setContent {
      ShifterTheme {
        CreateEventContent(
            state = CreateEventUiState(saveUnconfirmed = true),
            actions = CreateEventActions(),
        )
      }
    }

    node(CreateEventScreenTestTags.SAVE_UNCONFIRMED).performScrollTo().assertIsDisplayed()
    // Shown as a warning rather than as a muted line, so it is not missed.
    node(CreateEventScreenTestTags.SAVE_UNCONFIRMED_ICON).assertIsDisplayed()
    node(CreateEventScreenTestTags.SAVE_ERROR).assertDoesNotExist()
  }

  @Test
  fun pickerButton_opensTheDatePicker() {
    setScreen(RecordingRepository())
    val cancel = cancelLabel()

    node(CreateEventScreenTestTags.pickerButton(EventFormField.START))
        .performScrollTo()
        .performClick()
    node(CreateEventScreenTestTags.PICKER_CONFIRM).assertIsDisplayed()

    // Close the Start dialog before touching the End card: the dialog is modal, so the End picker
    // cannot be scrolled to or clicked while it is open.
    composeTestRule.onNodeWithText(cancel).performClick()
    node(CreateEventScreenTestTags.PICKER_CONFIRM).assertDoesNotExist()

    node(CreateEventScreenTestTags.pickerButton(EventFormField.END))
        .performScrollTo()
        .performClick()
    node(CreateEventScreenTestTags.PICKER_CONFIRM).assertIsDisplayed()
  }

  @Test
  fun pickingANewDate_writesTheSelectedValueInTheExpectedFormat() {
    setScreen(RecordingRepository())
    // Start from a known value so the DatePicker opens on July 2027 with the 21st preselected.
    node(CreateEventScreenTestTags.START_FIELD)
        .performScrollTo()
        .performTextInput("21/07/2027 14:00")

    node(CreateEventScreenTestTags.pickerButton(EventFormField.START))
        .performScrollTo()
        .performClick()
    // Actually select a different day (the 22nd) by clicking its day cell, not by coordinates, so
    // the test fails if the picker does not write the newly selected date back to the field.
    composeTestRule.onNode(dayCell(JULY_22)).performClick()
    node(CreateEventScreenTestTags.PICKER_CONFIRM).performClick() // confirm the date
    node(CreateEventScreenTestTags.PICKER_CONFIRM).performClick() // confirm the time

    // The picked 22nd replaced the 21st, in the exact dd/MM/yyyy HH:mm format, time carried.
    node(CreateEventScreenTestTags.START_FIELD).assertTextEquals("22/07/2027 14:00")
  }

  @Test
  fun cancellingTheDatePicker_leavesTheValueUnchanged() {
    setScreen(RecordingRepository())
    node(CreateEventScreenTestTags.START_FIELD)
        .performScrollTo()
        .performTextInput("21/07/2027 14:00")

    node(CreateEventScreenTestTags.pickerButton(EventFormField.START))
        .performScrollTo()
        .performClick()
    val cancel = cancelLabel()
    composeTestRule.onNodeWithText(cancel).performClick()

    node(CreateEventScreenTestTags.PICKER_CONFIRM).assertDoesNotExist()
    node(CreateEventScreenTestTags.START_FIELD).assertTextEquals("21/07/2027 14:00")
  }

  /**
   * Material3 labels each day in the language of the configuration ("jeudi 22 juillet 2027" in
   * French), so a test naming the day in English only passes on an English device: the cell is
   * found by the label the picker itself gives it in that language.
   */
  @Test
  fun pickingANewDate_inFrench_writesTheSelectedValueInTheExpectedFormat() {
    val defaultLocale = Locale.getDefault()
    Locale.setDefault(Locale.FRENCH)
    try {
      setScreen(RecordingRepository(), locale = Locale.FRENCH)
      node(CreateEventScreenTestTags.START_FIELD)
          .performScrollTo()
          .performTextInput("21/07/2027 14:00")

      node(CreateEventScreenTestTags.pickerButton(EventFormField.START))
          .performScrollTo()
          .performClick()
      // The English label is not there to be found: the cell is reached by its French one.
      composeTestRule.onNode(hasText("Thursday, July 22, 2027")).assertDoesNotExist()
      composeTestRule.onNode(dayCell(JULY_22, Locale.FRENCH)).performClick()
      node(CreateEventScreenTestTags.PICKER_CONFIRM).performClick()
      node(CreateEventScreenTestTags.PICKER_CONFIRM).performClick()

      node(CreateEventScreenTestTags.START_FIELD).assertTextEquals("22/07/2027 14:00")
    } finally {
      Locale.setDefault(defaultLocale)
    }
  }

  /**
   * The picker reads the field as strictly as the ViewModel does. Read leniently, 31/02 would
   * preselect the 28th, and two OKs would write a day the organizer never typed.
   */
  @Test
  fun pickingFromAnImpossibleTypedDate_doesNotWriteTheAdjustedDate() {
    setScreen(RecordingRepository())
    node(CreateEventScreenTestTags.START_FIELD)
        .performScrollTo()
        .performTextInput("31/02/2027 10:00")

    node(CreateEventScreenTestTags.pickerButton(EventFormField.START))
        .performScrollTo()
        .performClick()
    node(CreateEventScreenTestTags.PICKER_CONFIRM).performClick()
    node(CreateEventScreenTestTags.PICKER_CONFIRM).performClick()

    // Nothing to preselect, so the picker started from today at noon, as it does for an empty
    // field.
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))
    node(CreateEventScreenTestTags.START_FIELD).assertTextEquals("$today 12:00")
  }

  /**
   * A typed year outside the picker's 1900–2100 range parses but cannot be preselected, so the date
   * dialog opens with no day: its OK must not lead to a time dialog with nothing to write.
   */
  @Test
  fun datePicker_withoutASelectedDay_disablesTheConfirmUntilADayIsPicked() {
    setScreen(RecordingRepository())
    node(CreateEventScreenTestTags.START_FIELD)
        .performScrollTo()
        .performTextInput("01/01/2200 10:00")

    node(CreateEventScreenTestTags.pickerButton(EventFormField.START))
        .performScrollTo()
        .performClick()
    node(CreateEventScreenTestTags.DATE_PICKER).assertIsDisplayed()
    node(CreateEventScreenTestTags.PICKER_CONFIRM).assertIsNotEnabled().performClick()
    node(CreateEventScreenTestTags.DATE_PICKER).assertIsDisplayed()
    node(CreateEventScreenTestTags.TIME_PICKER).assertDoesNotExist()

    // Picking a day (the dialog shows the current month) enables the OK and leads to the time.
    // Material3 falls back to the current month when the selected date is out of range.
    composeTestRule.onNode(dayCell(LocalDate.now().withDayOfMonth(15))).performClick()
    node(CreateEventScreenTestTags.PICKER_CONFIRM).assertIsEnabled().performClick()
    node(CreateEventScreenTestTags.TIME_PICKER).assertIsDisplayed()

    composeTestRule.onNodeWithText(cancelLabel()).performClick()
    node(CreateEventScreenTestTags.START_FIELD).assertTextEquals("01/01/2200 10:00")
  }

  @Test
  fun cancellingTheTimePicker_leavesTheValueUnchangedAndStartsOverFromTheDate() {
    setScreen(RecordingRepository())
    node(CreateEventScreenTestTags.START_FIELD)
        .performScrollTo()
        .performTextInput("21/07/2027 14:00")
    node(CreateEventScreenTestTags.pickerButton(EventFormField.START))
        .performScrollTo()
        .performClick()
    composeTestRule.onNode(dayCell(JULY_22)).performClick()
    node(CreateEventScreenTestTags.PICKER_CONFIRM).performClick()
    node(CreateEventScreenTestTags.TIME_PICKER).assertIsDisplayed()

    composeTestRule.onNodeWithText(cancelLabel()).performClick()

    node(CreateEventScreenTestTags.TIME_PICKER).assertDoesNotExist()
    node(CreateEventScreenTestTags.START_FIELD).assertTextEquals("21/07/2027 14:00")
    // The 22nd went with the cancelled dialog: picking again starts from the date, and confirming
    // the preselected 21st twice writes the 21st.
    node(CreateEventScreenTestTags.pickerButton(EventFormField.START))
        .performScrollTo()
        .performClick()
    node(CreateEventScreenTestTags.DATE_PICKER).assertIsDisplayed()
    node(CreateEventScreenTestTags.TIME_PICKER).assertDoesNotExist()
    node(CreateEventScreenTestTags.PICKER_CONFIRM).performClick()
    node(CreateEventScreenTestTags.PICKER_CONFIRM).performClick()
    node(CreateEventScreenTestTags.START_FIELD).assertTextEquals("21/07/2027 14:00")
  }

  @Test
  fun confirmingBothDialogsWithoutChanging_keepsTheEndValue() {
    setScreen(RecordingRepository())
    node(CreateEventScreenTestTags.END_FIELD).performScrollTo().performTextInput("26/07/2027 23:30")

    node(CreateEventScreenTestTags.pickerButton(EventFormField.END))
        .performScrollTo()
        .performClick()
    node(CreateEventScreenTestTags.PICKER_CONFIRM).performClick()
    node(CreateEventScreenTestTags.PICKER_CONFIRM).performClick()

    node(CreateEventScreenTestTags.END_FIELD).assertTextEquals("26/07/2027 23:30")
  }

  @Test
  fun restoringWhileTheTimePickerIsOpen_keepsThePickedDay() {
    val viewModel =
        CreateEventViewModel(
            RecordingRepository(),
            currentUserId = { "organizer-1" },
            clock = clock,
        )
    val restorationTester = StateRestorationTester(composeTestRule)
    restorationTester.setContent {
      ShifterTheme { CreateEventScreen(viewModel, onEventCreated = {}, onBack = {}) }
    }
    node(CreateEventScreenTestTags.START_FIELD)
        .performScrollTo()
        .performTextInput("21/07/2027 14:00")
    node(CreateEventScreenTestTags.pickerButton(EventFormField.START))
        .performScrollTo()
        .performClick()
    composeTestRule.onNode(dayCell(JULY_22)).performClick()
    node(CreateEventScreenTestTags.PICKER_CONFIRM).performClick()
    node(CreateEventScreenTestTags.TIME_PICKER).assertIsDisplayed()

    restorationTester.emulateSavedInstanceStateRestore()
    composeTestRule.waitForIdle()

    // The time dialog is back with the day picked before it: confirming writes the 22nd.
    node(CreateEventScreenTestTags.TIME_PICKER).assertIsDisplayed()
    node(CreateEventScreenTestTags.PICKER_CONFIRM).performClick()
    node(CreateEventScreenTestTags.START_FIELD).assertTextEquals("22/07/2027 14:00")
  }

  @Test
  fun whileSaving_disablesThePicker() {
    val repository = RecordingRepository().apply { gate = CompletableDeferred() }
    setScreen(repository)

    fillValidForm()
    node(CreateEventScreenTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

    node(CreateEventScreenTestTags.pickerButton(EventFormField.START)).assertIsNotEnabled()
    repository.gate?.complete(Unit)
  }

  @Test
  fun backButton_callsOnBack() {
    var backCalled = false
    setScreen(RecordingRepository(), onBack = { backCalled = true })

    node(NavigationTestTags.GO_BACK_BUTTON).performClick()

    assertTrue(backCalled)
  }

  /** Renders the stateless form with the given [state] and [actions], as the picker tests need. */
  private fun setContent(
      state: CreateEventUiState,
      actions: CreateEventActions = CreateEventActions(),
  ) {
    composeTestRule.setContent { ShifterTheme { CreateEventContent(state, actions) } }
  }

  @Test
  fun locationFields_areShown() {
    setContent(CreateEventUiState())

    node(CreateEventScreenTestTags.LATITUDE_FIELD).performScrollTo().assertIsDisplayed()
    node(CreateEventScreenTestTags.LONGITUDE_FIELD).performScrollTo().assertIsDisplayed()
    node(CreateEventScreenTestTags.PICK_ON_MAP_BUTTON).performScrollTo().assertIsDisplayed()
    node(CreateEventScreenTestTags.COORDINATES_RULE).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun pickOnMap_opensThePicker() {
    var opened = false
    setContent(CreateEventUiState(), CreateEventActions(onPickLocation = { opened = true }))

    node(CreateEventScreenTestTags.PICK_ON_MAP_BUTTON).performScrollTo().performClick()

    assertTrue(opened)
  }

  @Test
  fun picking_withAPoint_showsItAndUsesIt() {
    var confirmed = false
    setContent(
        CreateEventUiState(isPickingLocation = true, locationDraft = GeoPosition(46.5069, 6.6266)),
        CreateEventActions(onConfirmLocation = { confirmed = true }),
    )

    node(CreateEventScreenTestTags.LOCATION_PICKER_DIALOG).assertIsDisplayed()
    node(LocationPickerTestTags.SELECTED).assertTextEquals(selectedLabel("46.50690", "6.62660"))
    node(CreateEventScreenTestTags.LOCATION_PICKER_CONFIRM).assertIsEnabled().performClick()

    assertTrue(confirmed)
  }

  @Test
  fun picking_withoutAPoint_cannotBeConfirmed() {
    setContent(CreateEventUiState(isPickingLocation = true))

    node(LocationPickerTestTags.HINT).assertIsDisplayed()
    node(CreateEventScreenTestTags.LOCATION_PICKER_CONFIRM).assertIsNotEnabled()
  }

  @Test
  fun closingThePicker_cancelsIt() {
    var cancelled = false
    setContent(
        CreateEventUiState(isPickingLocation = true, locationDraft = GeoPosition(46.5069, 6.6266)),
        CreateEventActions(onCancelLocationPicker = { cancelled = true }),
    )

    node(CreateEventScreenTestTags.LOCATION_PICKER_CLOSE).performClick()

    assertTrue(cancelled)
  }

  @Test
  fun clearCoordinates_isOfferedOnlyWithCoordinates() {
    var hasCoordinates by mutableStateOf(true)
    var cleared = false
    composeTestRule.setContent {
      ShifterTheme {
        CreateEventContent(
            if (hasCoordinates) CreateEventUiState(latitude = "46.5069", longitude = "6.6266")
            else CreateEventUiState(),
            CreateEventActions(onClearLocation = { cleared = true }),
        )
      }
    }

    node(CreateEventScreenTestTags.CLEAR_LOCATION_BUTTON).performScrollTo().performClick()
    assertTrue(cleared)

    hasCoordinates = false
    node(CreateEventScreenTestTags.CLEAR_LOCATION_BUTTON).assertDoesNotExist()
  }

  /**
   * Through the real ViewModel: the map opens on the typed point, closing it keeps the point as
   * typed, and clearing the coordinates keeps the address, which stays required on its own.
   */
  @Test
  fun typedCoordinates_surviveAClosedMapAndClearingKeepsTheAddress() {
    setScreen(RecordingRepository())
    node(CreateEventScreenTestTags.ADDRESS_FIELD).performScrollTo().performTextInput("Nyon")
    node(CreateEventScreenTestTags.LATITUDE_FIELD).performScrollTo().performTextInput("46.5069")
    node(CreateEventScreenTestTags.LONGITUDE_FIELD).performScrollTo().performTextInput("6.6266")

    node(CreateEventScreenTestTags.PICK_ON_MAP_BUTTON).performScrollTo().performClick()
    node(CreateEventScreenTestTags.LOCATION_PICKER_DIALOG).assertIsDisplayed()
    node(LocationPickerTestTags.SELECTED).assertTextEquals(selectedLabel("46.50690", "6.62660"))
    node(CreateEventScreenTestTags.LOCATION_PICKER_CLOSE).performClick()

    node(CreateEventScreenTestTags.LOCATION_PICKER_DIALOG).assertDoesNotExist()
    assertEquals("46.5069", editableText(CreateEventScreenTestTags.LATITUDE_FIELD))
    assertEquals("6.6266", editableText(CreateEventScreenTestTags.LONGITUDE_FIELD))

    node(CreateEventScreenTestTags.CLEAR_LOCATION_BUTTON).performScrollTo().performClick()

    assertEquals("", editableText(CreateEventScreenTestTags.LATITUDE_FIELD))
    assertEquals("", editableText(CreateEventScreenTestTags.LONGITUDE_FIELD))
    assertEquals("Nyon", editableText(CreateEventScreenTestTags.ADDRESS_FIELD))
    node(CreateEventScreenTestTags.CLEAR_LOCATION_BUTTON).assertDoesNotExist()
  }

  /** The line the map shows over its selected point, as the app words it. */
  private fun selectedLabel(latitude: String, longitude: String): String =
      InstrumentationRegistry.getInstrumentation()
          .targetContext
          .getString(R.string.map_picker_selected, latitude, longitude)

  /** What is typed in the field [tag], without its label. */
  private fun editableText(tag: String): String =
      node(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

  @Test
  fun locationEditing_isDisabledWhileSaving() {
    setContent(CreateEventUiState(address = "Nyon", isSaving = true))

    node(CreateEventScreenTestTags.LATITUDE_FIELD).performScrollTo().assertIsNotEnabled()
    node(CreateEventScreenTestTags.LONGITUDE_FIELD).performScrollTo().assertIsNotEnabled()
    node(CreateEventScreenTestTags.PICK_ON_MAP_BUTTON).performScrollTo().assertIsNotEnabled()
  }
}

/**
 * [content] under a configuration in [locale], the language the Material3 pickers label days in.
 */
@Composable
private fun InLocale(locale: Locale?, content: @Composable () -> Unit) {
  if (locale == null) {
    content()
  } else {
    val configuration = Configuration(LocalConfiguration.current).apply { setLocale(locale) }
    CompositionLocalProvider(LocalConfiguration provides configuration, content = content)
  }
}
