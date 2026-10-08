// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.event.EventRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the screen shown after an event was created.
 *
 * @property joinCode the code volunteers enter to join, as stored by the backend.
 * @property loadFailed true when the event could not be loaded or does not exist.
 */
data class EventCreatedUiState(
    val eventTitle: String = "",
    val joinCode: String = "",
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
)

/**
 * Loads the event identified by [eventId] from [repository], so the screen shows the join code
 * actually stored for it.
 */
class EventCreatedViewModel(
    private val repository: EventRepository,
    private val eventId: String,
) : ViewModel() {

  private val _uiState = MutableStateFlow(EventCreatedUiState())
  val uiState: StateFlow<EventCreatedUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  /** Loads the event again, e.g. after a failure. Ignored while loading. */
  fun retry() {
    if (_uiState.value.isLoading) return
    load()
  }

  private fun load() {
    _uiState.update { it.copy(isLoading = true, loadFailed = false) }
    viewModelScope.launch {
      try {
        val event = repository.getEvent(eventId)
        _uiState.update {
          if (event == null) EventCreatedUiState(isLoading = false, loadFailed = true)
          else
              EventCreatedUiState(
                  eventTitle = event.title,
                  joinCode = event.joinCode,
                  isLoading = false,
              )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _uiState.update { it.copy(isLoading = false, loadFailed = true) }
      }
    }
  }

  companion object {
    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(repository: EventRepository, eventId: String): ViewModelProvider.Factory =
        viewModelFactory {
          initializer { EventCreatedViewModel(repository, eventId) }
        }
  }
}
