// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
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
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.ui.map.LocationPicking
import com.swent.shifter.ui.map.LocationPickingForm
import com.swent.shifter.ui.map.parseCoordinate
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A form field of the event creation screen. */
enum class EventFormField {
  TITLE,
  DESCRIPTION,
  TYPE,
  ADDRESS,
  LATITUDE,
  LONGITUDE,
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
  LATITUDE_INVALID(EventFormField.LATITUDE),
  LONGITUDE_INVALID(EventFormField.LONGITUDE),
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
 * @property saveFailed the backend refused or failed the last save, so the event was not created.
 * @property notSignedIn the last submit found nobody signed in, so nothing was saved.
 * @property saveUnconfirmed the last save timed out, typically offline: the backend has not
 *   confirmed it yet, but it may still create the event once the device is back online.
 * @property createdEvent the persisted event once creation succeeded, null before.
 * @property createdEventHandled whether the screen already reacted to [createdEvent], e.g. by
 *   opening it, so it does not react again after a recomposition or configuration change.
 * @property latitude the latitude of the optional point, as typed. The address stays mandatory on
 *   its own; the coordinates are optional, but then both are required together.
 * @property longitude the longitude of the optional point, as typed.
 * @property isPickingLocation whether the map to pick the point on is open.
 * @property locationDraft the point picked on the open map, not used until it is confirmed.
 */
data class CreateEventUiState(
    val title: String = "",
    val description: String = "",
    val type: EventType? = null,
    val address: String = "",
    override val latitude: String = "",
    override val longitude: String = "",
    override val isPickingLocation: Boolean = false,
    override val locationDraft: GeoPosition? = null,
    val startAt: String = "",
    val endAt: String = "",
    val errors: Set<EventFormError> = emptySet(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val notSignedIn: Boolean = false,
    val saveUnconfirmed: Boolean = false,
    val createdEvent: Event? = null,
    val createdEventHandled: Boolean = false,
) : LocationPickingForm<CreateEventUiState> {
  /** The error to show under [field], or null when it is valid. */
  fun errorFor(field: EventFormField): EventFormError? = errors.firstOrNull { it.field == field }

  /** Whether the organizer started to give coordinates, which then both have to be valid. */
  val hasCoordinates: Boolean
    get() = latitude.isNotBlank() || longitude.isNotBlank()

  override fun withPicker(isPickingLocation: Boolean, locationDraft: GeoPosition?) =
      copy(isPickingLocation = isPickingLocation, locationDraft = locationDraft)

  override fun withCoordinates(latitude: String, longitude: String) =
      copy(latitude = latitude, longitude = longitude)
}

/**
 * Holds the event creation form, validates it and creates the event through [repository].
 *
 * @param currentUserId returns the signed-in user's id, who becomes the organizer of the event, or
 *   null when nobody is signed in.
 * @param clock source of the current time, used to reject events starting in the past and to stamp
 *   [Event.createdAt]; inject a fixed clock in tests.
 */
class CreateEventViewModel(
    private val repository: EventRepository,
    private val currentUserId: () -> String?,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(CreateEventUiState())
  val uiState: StateFlow<CreateEventUiState> = _uiState.asStateFlow()

  /** Becomes true on the first submit, after which errors follow every edit. */
  private var submitAttempted = false

  /**
   * Id of the event this form creates, kept across retries: a save that timed out may still reach
   * the backend later, so a retry must overwrite that event instead of creating a duplicate.
   */
  private val eventId = UUID.randomUUID().toString()

  /** The map picker's lifecycle, shared with the mission form (see [LocationPicking]). */
  private val picking = LocationPicking(_uiState)

  fun onTitleChange(title: String) = edit { it.copy(title = title) }

  fun onDescriptionChange(description: String) = edit { it.copy(description = description) }

  fun onTypeChange(type: EventType) = edit { it.copy(type = type) }

  fun onAddressChange(address: String) = edit { it.copy(address = address) }

  fun onLatitudeChange(latitude: String) = edit { it.copy(latitude = latitude) }

  fun onLongitudeChange(longitude: String) = edit { it.copy(longitude = longitude) }

  /**
   * Opens the map on the point the coordinates give. With no complete point, e.g. a latitude typed
   * and not the longitude yet, it opens on the app's default place, and the point picked replaces
   * both coordinates.
   */
  fun onPickLocation() = picking.open()

  /** Moves the point picked on the open map, without touching the form yet. */
  fun onLocationDraftChange(position: GeoPosition) = picking.moveTo(position)

  /**
   * Closes the map and fills the coordinates with the point picked. A point left where the map
   * opened is not written again: the coordinates typed keep their precision, rather than being
   * rounded to the decimals the map gives.
   */
  fun onConfirmLocation() {
    val confirm = picking.confirmed() ?: return picking.close()
    edit(confirm)
  }

  /** Closes the map and leaves the coordinates as they were. */
  fun onCancelLocationPicker() = picking.close()

  /** Empties the coordinates, keeping the address: the event is then saved with no point. */
  fun onClearLocation() = edit { it.copy(latitude = "", longitude = "") }

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
      _uiState.update { it.copy(errors = errors, saveFailed = false, notSignedIn = false) }
      return
    }
    val organizerId = currentUserId()
    if (organizerId == null) {
      _uiState.update { it.copy(errors = emptySet(), saveFailed = false, notSignedIn = true) }
      return
    }
    // Validation guarantees the type and both dates are present and parseable.
    val event =
        Event(
            id = eventId,
            organizerId = organizerId,
            title = form.title.trim(),
            description = form.description.trim(),
            type = checkNotNull(form.type),
            startAt = checkNotNull(parseDateTime(form.startAt)),
            endAt = checkNotNull(parseDateTime(form.endAt)),
            // Validation guarantees the coordinates are either both valid or both empty, so
            // position is the typed point, or null for an address alone.
            location = EventLocation(address = form.address.trim(), position = form.position),
            createdAt = clock.instant(),
        )
    _uiState.update {
      it.copy(
          errors = emptySet(),
          isSaving = true,
          saveFailed = false,
          notSignedIn = false,
          saveUnconfirmed = false,
      )
    }
    viewModelScope.launch {
      try {
        // Offline, the backend may neither confirm nor reject the write, so stop waiting at some
        // point instead of leaving the screen saving forever. The write is not refused, only
        // unconfirmed: it may still go through once the device is back online.
        val created = withTimeoutOrNull(SAVE_TIMEOUT_MILLIS) { repository.createEvent(event) }
        _uiState.update {
          it.copy(isSaving = false, saveUnconfirmed = created == null, createdEvent = created)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _uiState.update { it.copy(isSaving = false, saveFailed = true) }
      }
    }
  }

  /**
   * Marks [CreateEventUiState.createdEvent] as handled once the screen reacted to it. The event
   * itself is kept, so the form still cannot create it a second time.
   */
  fun onEventCreatedHandled() {
    _uiState.update { it.copy(createdEventHandled = true) }
  }

  private fun edit(change: (CreateEventUiState) -> CreateEventUiState) {
    _uiState.update { state ->
      val edited = change(state).copy(saveFailed = false, notSignedIn = false)
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
    // The address alone is enough; but once a coordinate is typed, both must be valid.
    if (form.hasCoordinates) {
      if (parseCoordinate(form.latitude, GeoPosition.LATITUDE_RANGE) == null) {
        add(EventFormError.LATITUDE_INVALID)
      }
      if (parseCoordinate(form.longitude, GeoPosition.LONGITUDE_RANGE) == null) {
        add(EventFormError.LONGITUDE_INVALID)
      }
    }

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
      parseEventDateTime(text)?.atZone(clock.zone)?.toInstant()

  companion object {
    /** Longest title accepted, so it fits on event cards and in lists. */
    const val TITLE_MAX_LENGTH = 80

    /** Longest description accepted. */
    const val DESCRIPTION_MAX_LENGTH = 2000

    /**
     * How long a save may take before it is reported as unconfirmed, so the organizer can retry.
     */
    const val SAVE_TIMEOUT_MILLIS = 15_000L

    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(
        repository: EventRepository,
        currentUserId: () -> String?,
        clock: Clock = Clock.systemDefaultZone(),
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { CreateEventViewModel(repository, currentUserId, clock) }
    }
  }
}
