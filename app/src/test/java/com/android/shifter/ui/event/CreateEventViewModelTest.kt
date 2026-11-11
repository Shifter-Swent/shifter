// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.lifecycle.viewmodel.CreationExtras
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventStatus
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FakeEventRepository
import com.swent.shifter.model.location.GeoPosition
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
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
class CreateEventViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private val zone = ZoneId.of("Europe/Zurich")
  /** 1 June 2027, 12:00 in Zurich. */
  private val now = Instant.parse("2027-06-01T10:00:00Z")
  private val clock = Clock.fixed(now, zone)

  private lateinit var repository: FakeEventRepository
  private lateinit var viewModel: CreateEventViewModel

  /** A point whose five-decimal form is "46.50690, 6.62660", as the map would give it. */
  private val OUCHY = GeoPosition(46.5069, 6.6266)
  /** A second point, to move a draft to before cancelling. */
  private val LAUSANNE = GeoPosition(46.5197, 6.6323)

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    repository = FakeEventRepository()
    viewModel = CreateEventViewModel(repository, currentUserId = { "organizer-1" }, clock = clock)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun fillValidForm() {
    viewModel.onTitleChange("  Paléo Festival ")
    viewModel.onDescriptionChange("Open-air music festival")
    viewModel.onTypeChange(EventType.MUSIC)
    viewModel.onAddressChange("Route de Saint-Cergue 312, Nyon ")
    viewModel.onStartAtChange("21/07/2027 14:00")
    viewModel.onEndAtChange("26/07/2027 23:30")
  }

  private val state
    get() = viewModel.uiState.value

  @Test
  fun initialState_isEmptyAndShowsNoError() {
    assertEquals(CreateEventUiState(), state)
  }

  @Test
  fun editingFields_updatesStateWithoutValidating() {
    viewModel.onTitleChange("A")
    viewModel.onStartAtChange("not a date")

    assertEquals("A", state.title)
    assertEquals("not a date", state.startAt)
    assertTrue(state.errors.isEmpty())
  }

  @Test
  fun createEvent_withEmptyForm_reportsEveryMissingField() {
    viewModel.createEvent()

    assertEquals(
        setOf(
            EventFormError.TITLE_EMPTY,
            EventFormError.DESCRIPTION_EMPTY,
            EventFormError.TYPE_MISSING,
            EventFormError.ADDRESS_EMPTY,
            EventFormError.START_INVALID,
            EventFormError.END_INVALID,
        ),
        state.errors,
    )
    assertFalse(state.isSaving)
    assertTrue(repository.events.isEmpty())
  }

  @Test
  fun createEvent_withBlankText_reportsItAsEmpty() {
    fillValidForm()
    viewModel.onTitleChange("   ")
    viewModel.onAddressChange("\n")

    viewModel.createEvent()

    assertEquals(setOf(EventFormError.TITLE_EMPTY, EventFormError.ADDRESS_EMPTY), state.errors)
  }

  @Test
  fun createEvent_rejectsMalformedAndImpossibleDates() {
    fillValidForm()
    viewModel.onStartAtChange("2027-07-21 14:00")
    viewModel.onEndAtChange("31/02/2028 10:00")

    viewModel.createEvent()

    assertEquals(setOf(EventFormError.START_INVALID, EventFormError.END_INVALID), state.errors)
  }

  @Test
  fun createEvent_rejectsStartInThePastOrNow() {
    fillValidForm()
    viewModel.onStartAtChange("01/06/2027 12:00")

    viewModel.createEvent()

    assertEquals(setOf(EventFormError.START_IN_PAST), state.errors)
  }

  @Test
  fun createEvent_rejectsEndNotAfterStart() {
    fillValidForm()
    viewModel.onEndAtChange("21/07/2027 14:00")

    viewModel.createEvent()

    assertEquals(setOf(EventFormError.END_NOT_AFTER_START), state.errors)
    assertEquals(EventFormError.END_NOT_AFTER_START, state.errorFor(EventFormField.END))
    assertNull(state.errorFor(EventFormField.START))
  }

  @Test
  fun createEvent_acceptsStartOneMinuteFromNow() = runTest {
    fillValidForm()
    viewModel.onStartAtChange("01/06/2027 12:01")

    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(Instant.parse("2027-06-01T10:01:00Z"), repository.events.single().startAt)
  }

  @Test
  fun createEvent_convertsDatesWithTheOffsetInEffectOnThatDay() = runTest {
    fillValidForm()
    // Zurich is UTC+1 in winter, while the clock's current offset (June) is UTC+2.
    viewModel.onStartAtChange("21/12/2027 14:00")
    viewModel.onEndAtChange("21/12/2027 18:00")

    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(Instant.parse("2027-12-21T13:00:00Z"), repository.events.single().startAt)
  }

  @Test
  fun createEvent_acceptsFebruary29OnlyInLeapYears() {
    fillValidForm()
    viewModel.onStartAtChange("29/02/2027 10:00")
    viewModel.onEndAtChange("01/03/2028 10:00")

    viewModel.createEvent()
    assertEquals(setOf(EventFormError.START_INVALID), state.errors)

    viewModel.onStartAtChange("29/02/2028 10:00")
    assertTrue(state.errors.isEmpty())
  }

  @Test
  fun fixingEveryErrorAfterASubmit_clearsErrorsButSavesOnlyOnResubmit() = runTest {
    viewModel.createEvent()

    fillValidForm()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(state.errors.isEmpty())
    assertFalse(state.isSaving)
    assertTrue(repository.events.isEmpty())
  }

  @Test
  fun afterAFailedSubmit_errorsFollowEdits() {
    viewModel.createEvent()

    viewModel.onTitleChange("Paléo Festival")

    assertNull(state.errorFor(EventFormField.TITLE))
    assertEquals(EventFormError.DESCRIPTION_EMPTY, state.errorFor(EventFormField.DESCRIPTION))
  }

  @Test
  fun createEvent_withValidForm_persistsTrimmedEvent() = runTest {
    fillValidForm()

    viewModel.createEvent()
    assertTrue(state.isSaving)
    dispatcher.scheduler.advanceUntilIdle()

    val saved = repository.events.single()
    assertEquals("organizer-1", saved.organizerId)
    assertEquals("Paléo Festival", saved.title)
    assertEquals("Open-air music festival", saved.description)
    assertEquals(EventType.MUSIC, saved.type)
    assertEquals(EventLocation("Route de Saint-Cergue 312, Nyon"), saved.location)
    assertEquals(Instant.parse("2027-07-21T12:00:00Z"), saved.startAt)
    assertEquals(Instant.parse("2027-07-26T21:30:00Z"), saved.endAt)
    assertEquals(now, saved.createdAt)
    assertEquals(EventStatus.PREPARATION, saved.status)
    assertFalse(state.isSaving)
    assertTrue(state.errors.isEmpty())
    assertEquals(saved, state.createdEvent)
  }

  @Test
  fun createEvent_whileSaving_isIgnored() = runTest {
    fillValidForm()

    viewModel.createEvent()
    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repository.events.size)
  }

  @Test
  fun onEventCreatedHandled_marksItHandledAndKeepsTheEvent() = runTest {
    fillValidForm()
    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()
    assertFalse(state.createdEventHandled)
    val created = state.createdEvent

    viewModel.onEventCreatedHandled()
    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(state.createdEventHandled)
    assertEquals(created, state.createdEvent)
    assertEquals(1, repository.events.size)
  }

  @Test
  fun secondSubmitAfterSuccess_createsNothing() = runTest {
    fillValidForm()
    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()
    val created = state.createdEvent

    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repository.events.size)
    assertEquals(created, state.createdEvent)
    assertFalse(state.isSaving)
  }

  @Test
  fun createEvent_acceptsTitleAndDescriptionAtMaxLength() = runTest {
    fillValidForm()
    viewModel.onTitleChange("a".repeat(CreateEventViewModel.TITLE_MAX_LENGTH))
    viewModel.onDescriptionChange("b".repeat(CreateEventViewModel.DESCRIPTION_MAX_LENGTH))

    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(state.errors.isEmpty())
    assertEquals(1, repository.events.size)
  }

  @Test
  fun createEvent_rejectsTitleAndDescriptionOverMaxLength() {
    fillValidForm()
    viewModel.onTitleChange("a".repeat(CreateEventViewModel.TITLE_MAX_LENGTH + 1))
    viewModel.onDescriptionChange("b".repeat(CreateEventViewModel.DESCRIPTION_MAX_LENGTH + 1))

    viewModel.createEvent()

    assertEquals(
        setOf(EventFormError.TITLE_TOO_LONG, EventFormError.DESCRIPTION_TOO_LONG),
        state.errors,
    )
    assertTrue(repository.events.isEmpty())
  }

  @Test
  fun maxLength_ignoresSurroundingWhitespace() {
    fillValidForm()
    viewModel.onTitleChange("  " + "a".repeat(CreateEventViewModel.TITLE_MAX_LENGTH) + "  ")

    viewModel.createEvent()

    assertNull(state.errorFor(EventFormField.TITLE))
  }

  @Test
  fun factory_createsViewModelWithGivenDependencies() = runTest {
    val factory = CreateEventViewModel.factory(repository, currentUserId = { "organizer-2" }, clock)
    viewModel = factory.create(CreateEventViewModel::class.java, CreationExtras.Empty)

    fillValidForm()
    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals("organizer-2", repository.events.single().organizerId)
  }

  @Test
  fun createEvent_whenRepositoryFails_reportsFailureAndAllowsRetry() = runTest {
    fillValidForm()
    repository.failure = IllegalStateException("offline")

    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(state.saveFailed)
    assertFalse(state.saveUnconfirmed)
    assertFalse(state.isSaving)
    assertNull(state.createdEvent)

    repository.failure = null
    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()

    assertFalse(state.saveFailed)
    assertEquals(repository.events.single(), state.createdEvent)
  }

  @Test
  fun createEvent_whenSaveIsNeverConfirmed_reportsItUnconfirmedAfterTimeout() = runTest {
    fillValidForm()
    repository.unconfirmedWrites = true

    viewModel.createEvent()
    dispatcher.scheduler.advanceTimeBy(CreateEventViewModel.SAVE_TIMEOUT_MILLIS - 1)
    assertTrue(state.isSaving)
    dispatcher.scheduler.advanceUntilIdle()

    assertFalse(state.isSaving)
    assertTrue(state.saveUnconfirmed)
    assertFalse(state.saveFailed)
    assertNull(state.createdEvent)
  }

  @Test
  fun unconfirmedSave_staysReportedWhileEditingUntilTheNextSubmit() = runTest {
    fillValidForm()
    repository.unconfirmedWrites = true
    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()

    // The queued write may still create the event, whatever the organizer edits meanwhile.
    viewModel.onTitleChange("Paléo")
    assertTrue(state.saveUnconfirmed)

    viewModel.createEvent()
    assertFalse(state.saveUnconfirmed)
  }

  @Test
  fun retryAfterTimeout_overwritesTheSameEventInsteadOfDuplicatingIt() = runTest {
    fillValidForm()
    repository.unconfirmedWrites = true
    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()
    val firstAttempt = repository.events.single()

    repository.unconfirmedWrites = false
    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()

    val saved = repository.events.single()
    assertEquals(firstAttempt.id, saved.id)
    assertEquals(saved, state.createdEvent)
    assertFalse(state.saveUnconfirmed)
  }

  @Test
  fun createEvent_withoutSession_reportsItAndSavesNothing() = runTest {
    var userId: String? = null
    viewModel = CreateEventViewModel(repository, currentUserId = { userId }, clock = clock)
    fillValidForm()

    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(state.notSignedIn)
    assertFalse(state.saveFailed)
    assertFalse(state.isSaving)
    assertTrue(repository.events.isEmpty())

    // The id is read on each submit, so signing in afterwards is enough to retry.
    userId = "organizer-1"
    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()

    assertFalse(state.notSignedIn)
    assertEquals("organizer-1", repository.events.single().organizerId)
  }

  @Test
  fun createEvent_withInvalidFormAndNoSession_reportsOnlyTheForm() {
    viewModel = CreateEventViewModel(repository, currentUserId = { null }, clock = clock)

    viewModel.createEvent()

    assertFalse(state.notSignedIn)
    assertTrue(state.errors.isNotEmpty())
  }

  @Test
  fun editingAfterNoSession_clearsTheError() = runTest {
    viewModel = CreateEventViewModel(repository, currentUserId = { null }, clock = clock)
    fillValidForm()
    viewModel.createEvent()

    viewModel.onTitleChange("Paléo")

    assertFalse(state.notSignedIn)
  }

  @Test
  fun editingAfterAFailedSave_clearsTheFailure() = runTest {
    fillValidForm()
    repository.failure = IllegalStateException("offline")
    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onTitleChange("Paléo")

    assertFalse(state.saveFailed)
  }

  @Test
  fun createEvent_withValidCoordinates_savesThePoint() = runTest {
    fillValidForm()
    viewModel.onLatitudeChange("46.5069")
    // A decimal comma is accepted as well as a point, as typed on many keyboards.
    viewModel.onLongitudeChange(" 6,6266 ")

    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(
        EventLocation("Route de Saint-Cergue 312, Nyon", GeoPosition(46.5069, 6.6266)),
        repository.events.single().location,
    )
  }

  @Test
  fun createEvent_withOnlyOneCoordinate_reportsTheOtherInvalid() {
    fillValidForm()
    viewModel.onLatitudeChange("46.5069")

    viewModel.createEvent()

    assertEquals(setOf(EventFormError.LONGITUDE_INVALID), state.errors)
    assertTrue(repository.events.isEmpty())
  }

  @Test
  fun createEvent_withOutOfRangeCoordinates_reportsThem() {
    fillValidForm()
    viewModel.onLatitudeChange("91")
    viewModel.onLongitudeChange("200")

    viewModel.createEvent()

    assertEquals(
        setOf(EventFormError.LATITUDE_INVALID, EventFormError.LONGITUDE_INVALID),
        state.errors,
    )
    assertTrue(repository.events.isEmpty())
  }

  @Test
  fun pickLocation_opensOnTheTypedCoordinates() {
    viewModel.onLatitudeChange("46.5069")
    viewModel.onLongitudeChange("6.6266")

    viewModel.onPickLocation()

    assertTrue(state.isPickingLocation)
    assertEquals(OUCHY, state.locationDraft)
  }

  @Test
  fun pickLocation_withoutCoordinates_opensWithNoDraft() {
    viewModel.onPickLocation()

    assertTrue(state.isPickingLocation)
    assertNull(state.locationDraft)
  }

  @Test
  fun confirmLocation_fillsTheCoordinatesWithADecimalPoint() {
    val defaultLocale = Locale.getDefault()
    try {
      Locale.setDefault(Locale.FRANCE)

      viewModel.onPickLocation()
      viewModel.onLocationDraftChange(OUCHY)
      viewModel.onConfirmLocation()

      assertFalse(state.isPickingLocation)
      assertNull(state.locationDraft)
      assertEquals("46.50690", state.latitude)
      assertEquals("6.62660", state.longitude)
    } finally {
      Locale.setDefault(defaultLocale)
    }
  }

  @Test
  fun confirmLocation_withoutMoving_keepsTheTypedCoordinates() {
    viewModel.onLatitudeChange("46.5069123")
    viewModel.onLongitudeChange("6.6266456")

    viewModel.onPickLocation()
    viewModel.onConfirmLocation()

    // Not rounded to the five decimals the map gives.
    assertFalse(state.isPickingLocation)
    assertEquals("46.5069123", state.latitude)
    assertEquals("6.6266456", state.longitude)
  }

  @Test
  fun cancelLocationPicker_keepsTheCoordinates() {
    viewModel.onLatitudeChange("46.5069")
    viewModel.onLongitudeChange("6.6266")
    viewModel.onPickLocation()
    viewModel.onLocationDraftChange(LAUSANNE)

    viewModel.onCancelLocationPicker()

    assertFalse(state.isPickingLocation)
    assertNull(state.locationDraft)
    assertEquals("46.5069", state.latitude)
    assertEquals("6.6266", state.longitude)
  }

  @Test
  fun locationDraftChange_withTheMapClosed_isIgnored() {
    viewModel.onLocationDraftChange(OUCHY)

    assertNull(state.locationDraft)
  }

  @Test
  fun clearLocation_emptiesTheCoordinatesButKeepsTheAddress() = runTest {
    fillValidForm()
    viewModel.onLatitudeChange("91")
    viewModel.createEvent()
    assertTrue(EventFormError.LATITUDE_INVALID in state.errors)

    viewModel.onClearLocation()

    assertEquals("Route de Saint-Cergue 312, Nyon ", state.address)
    assertEquals("", state.latitude)
    assertEquals("", state.longitude)
    assertTrue(state.errors.isEmpty())
    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()
    assertNull(repository.events.single().location.position)
  }

  @Test
  fun createEvent_withACoordinateThatIsNotANumber_reportsItRatherThanReadingPartOfIt() {
    fillValidForm()
    viewModel.onLatitudeChange("46.5069 N")
    viewModel.onLongitudeChange("6.6266")

    viewModel.createEvent()

    assertEquals(setOf(EventFormError.LATITUDE_INVALID), state.errors)
    assertTrue(repository.events.isEmpty())
  }

  @Test
  fun pickLocation_afterACancelledPick_opensOnTheTypedCoordinatesAgain() {
    viewModel.onLatitudeChange("46.5069")
    viewModel.onLongitudeChange("6.6266")
    viewModel.onPickLocation()
    viewModel.onLocationDraftChange(LAUSANNE)
    viewModel.onCancelLocationPicker()

    viewModel.onPickLocation()

    // The point moved to before cancelling is forgotten: the map opens on the typed point again.
    assertEquals(OUCHY, state.locationDraft)
  }

  @Test
  fun confirmLocation_withoutAPoint_closesTheMapAndChangesNothing() {
    viewModel.onPickLocation()

    viewModel.onConfirmLocation()

    assertFalse(state.isPickingLocation)
    assertEquals("", state.latitude)
    assertEquals("", state.longitude)
  }

  @Test
  fun createEvent_afterPicking_savesThePickedPosition() = runTest {
    fillValidForm()
    viewModel.onPickLocation()
    viewModel.onLocationDraftChange(OUCHY)
    viewModel.onConfirmLocation()

    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(OUCHY, repository.events.single().location.position)
  }
}
