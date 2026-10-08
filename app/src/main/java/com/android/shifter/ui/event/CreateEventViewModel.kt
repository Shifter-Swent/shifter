// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventType
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A form field of the event creation screen. */
enum class EventFormField {
  TITLE,
  DESCRIPTION,
  TYPE,
  ADDRESS,
  START,
  END,
}

/** Why a field of the event creation form is invalid. Each error belongs to exactly one [field]. */
enum class EventFormError(val field: EventFormField) {
  TITLE_EMPTY(EventFormField.TITLE),
  TITLE_TOO_LONG(EventFormField.TITLE),
  DESCRIPTION_EMPTY(EventFormField.DESCRIPTION),
  DESCRIPTION_TOO_LONG(EventFormField.DESCRIPTION),
  TYPE_MISSING(EventFormField.TYPE),
  ADDRESS_EMPTY(EventFormField.ADDRESS),
  START_INVALID(EventFormField.START),
  START_IN_PAST(EventFormField.START),
  END_INVALID(EventFormField.END),
  END_NOT_AFTER_START(EventFormField.END),
}

/**
 * State of the event creation screen.
 *
 * The dates are kept as the raw text the organizer typed, so a half-typed date is not lost; they
 * are only parsed when the form is validated.
 *
 * @property errors the current validation errors. Empty until the organizer first tries to submit,
 *   so an untouched form is not shown in red; from then on it is kept up to date on every edit.
 * @property createdEvent the persisted event once creation succeeded, null before.
 */
data class CreateEventUiState(
    val title: String = "",
    val description: String = "",
    val type: EventType? = null,
    val address: String = "",
    val startAt: String = "",
    val endAt: String = "",
    val errors: Set<EventFormError> = emptySet(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val createdEvent: Event? = null,
) {
  /** The error to show under [field], or null when it is valid. */
  fun errorFor(field: EventFormField): EventFormError? = errors.firstOrNull { it.field == field }
}

/**
 * Holds the event creation form, validates it and creates the event through [repository].
 *
 * @param organizerId id of the signed-in user, who becomes the organizer of the event.
 * @param clock source of the current time, used to reject events starting in the past and to stamp
 *   [Event.createdAt]; inject a fixed clock in tests.
 */
class CreateEventViewModel(
    private val repository: EventRepository,
    private val organizerId: String,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(CreateEventUiState())
  val uiState: StateFlow<CreateEventUiState> = _uiState.asStateFlow()

  /** Becomes true on the first submit, after which errors follow every edit. */
  private var submitAttempted = false

  fun onTitleChange(title: String) = edit { it.copy(title = title) }

  fun onDescriptionChange(description: String) = edit { it.copy(description = description) }

  fun onTypeChange(type: EventType) = edit { it.copy(type = type) }

  fun onAddressChange(address: String) = edit { it.copy(address = address) }

  fun onStartAtChange(startAt: String) = edit { it.copy(startAt = startAt) }

  fun onEndAtChange(endAt: String) = edit { it.copy(endAt = endAt) }

  /**
   * Validates the form and, when it is valid, creates the event. Ignored while saving and once the
   * event has been created, so a second tap cannot create it twice.
   */
  fun createEvent() {
    val form = _uiState.value
    if (form.isSaving || form.createdEvent != null) return
    submitAttempted = true
    val errors = validate(form)
    if (errors.isNotEmpty()) {
      _uiState.update { it.copy(errors = errors, saveFailed = false) }
      return
    }
    // Validation guarantees the type and both dates are present and parseable.
    val event =
        Event(
            organizerId = organizerId,
            title = form.title.trim(),
            description = form.description.trim(),
            type = checkNotNull(form.type),
            startAt = checkNotNull(parseDateTime(form.startAt)),
            endAt = checkNotNull(parseDateTime(form.endAt)),
            location = EventLocation(address = form.address.trim()),
            createdAt = clock.instant(),
        )
    _uiState.update { it.copy(errors = emptySet(), isSaving = true, saveFailed = false) }
    viewModelScope.launch {
      try {
        val created = repository.createEvent(event)
        _uiState.update { it.copy(isSaving = false, createdEvent = created) }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _uiState.update { it.copy(isSaving = false, saveFailed = true) }
      }
    }
  }

  private fun edit(change: (CreateEventUiState) -> CreateEventUiState) {
    _uiState.update { state ->
      val edited = change(state).copy(saveFailed = false)
      if (submitAttempted) edited.copy(errors = validate(edited)) else edited
    }
  }

  private fun validate(form: CreateEventUiState): Set<EventFormError> = buildSet {
    when {
      form.title.isBlank() -> add(EventFormError.TITLE_EMPTY)
      form.title.trim().length > TITLE_MAX_LENGTH -> add(EventFormError.TITLE_TOO_LONG)
    }
    when {
      form.description.isBlank() -> add(EventFormError.DESCRIPTION_EMPTY)
      form.description.trim().length > DESCRIPTION_MAX_LENGTH ->
          add(EventFormError.DESCRIPTION_TOO_LONG)
    }
    if (form.type == null) add(EventFormError.TYPE_MISSING)
    if (form.address.isBlank()) add(EventFormError.ADDRESS_EMPTY)

    val start = parseDateTime(form.startAt)
    val end = parseDateTime(form.endAt)
    when {
      start == null -> add(EventFormError.START_INVALID)
      !start.isAfter(clock.instant()) -> add(EventFormError.START_IN_PAST)
    }
    when {
      end == null -> add(EventFormError.END_INVALID)
      start != null && !end.isAfter(start) -> add(EventFormError.END_NOT_AFTER_START)
    }
  }

  /** Reads [text] as a local date and time in the clock's zone, or returns null if malformed. */
  private fun parseDateTime(text: String): Instant? =
      try {
        LocalDateTime.parse(text.trim(), DATE_TIME_FORMATTER).atZone(clock.zone).toInstant()
      } catch (_: DateTimeParseException) {
        null
      }

  companion object {
    /** Longest title accepted, so it fits on event cards and in lists. */
    const val TITLE_MAX_LENGTH = 80

    /** Longest description accepted. */
    const val DESCRIPTION_MAX_LENGTH = 2000

    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(
        repository: EventRepository,
        organizerId: String,
        clock: Clock = Clock.systemDefaultZone(),
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { CreateEventViewModel(repository, organizerId, clock) }
    }

    /**
     * How the organizer types a date and time, e.g. `24/12/2027 18:30`. Strict, so impossible dates
     * such as 31/02 are rejected instead of being adjusted.
     */
    private val DATE_TIME_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm").withResolverStyle(ResolverStyle.STRICT)
  }
}
