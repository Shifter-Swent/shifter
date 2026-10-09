// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventRepositoryException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the join code cannot be shown. The screen turns each into a translated message. */
enum class EventCreatedError {
  /** The backend could not be reached: retrying may help. */
  OFFLINE,
  /** The event does not exist or has no join code yet. */
  NOT_FOUND,
  /** Any other failure, such as denied access, which retrying cannot fix. */
  UNEXPECTED,
}

/**
 * State of the screen shown after an event was created.
 *
 * @property joinCode the code volunteers enter to join, as stored by the backend.
 * @property error why the code cannot be shown, or null when it can.
 */
data class EventCreatedUiState(
    val eventTitle: String = "",
    val joinCode: String = "",
    val isLoading: Boolean = true,
    val error: EventCreatedError? = null,
)

/**
 * Shows the join code of the event identified by [eventId].
 *
 * @param createdEvent the event as returned when it was created. When it matches [eventId] and has
 *   a join code, it is shown right away; otherwise, e.g. after process death, the event is loaded
 *   from [repository].
 */
class EventCreatedViewModel(
    private val repository: EventRepository,
    private val eventId: String,
    createdEvent: Event? = null,
) : ViewModel() {

  private val _uiState =
      MutableStateFlow(
          createdEvent?.takeIf { it.id == eventId }?.toUiState() ?: EventCreatedUiState()
      )
  val uiState: StateFlow<EventCreatedUiState> = _uiState.asStateFlow()

  init {
    if (_uiState.value.isLoading) load()
  }

  /** Loads the event again, e.g. after a failure. Ignored while loading. */
  fun retry() {
    if (_uiState.value.isLoading) return
    load()
  }

  private fun load() {
    _uiState.update { it.copy(isLoading = true, error = null) }
    viewModelScope.launch {
      val state =
          try {
            repository.getEvent(eventId)?.toUiState() ?: failed(EventCreatedError.NOT_FOUND)
          } catch (e: CancellationException) {
            throw e
          } catch (_: EventRepositoryException.Unavailable) {
            failed(EventCreatedError.OFFLINE)
          } catch (_: Exception) {
            failed(EventCreatedError.UNEXPECTED)
          }
      _uiState.value = state
    }
  }

  /** The loaded state for this event, or a failure when it has no join code yet. */
  private fun Event.toUiState(): EventCreatedUiState? =
      if (joinCode.isBlank()) null
      else EventCreatedUiState(eventTitle = title, joinCode = joinCode, isLoading = false)

  private fun failed(error: EventCreatedError) =
      EventCreatedUiState(isLoading = false, error = error)

  companion object {
    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(
        repository: EventRepository,
        eventId: String,
        createdEvent: Event? = null,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { EventCreatedViewModel(repository, eventId, createdEvent) }
    }
  }
}
