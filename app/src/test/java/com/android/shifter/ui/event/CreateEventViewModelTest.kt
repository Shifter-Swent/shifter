// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventStatus
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FakeEventRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
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

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    repository = FakeEventRepository()
    viewModel = CreateEventViewModel(repository, organizerId = "organizer-1", clock = clock)
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
  fun createEvent_whenRepositoryFails_reportsFailureAndAllowsRetry() = runTest {
    fillValidForm()
    repository.failure = IllegalStateException("offline")

    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(state.saveFailed)
    assertFalse(state.isSaving)
    assertNull(state.createdEvent)

    repository.failure = null
    viewModel.createEvent()
    dispatcher.scheduler.advanceUntilIdle()

    assertFalse(state.saveFailed)
    assertEquals(repository.events.single(), state.createdEvent)
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
}
