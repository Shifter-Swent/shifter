// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Loads the cards of a My Events screen. ViewModels depend on this, never on a backend. */
fun interface MyEventsLoader {
  suspend fun load(): List<EventCardUi>
}

/**
 * State holder of a My Events screen. The staff and the organizer views share it and only differ by
 * the [loader] they are given.
 *
 * Cards whose badge is [EventBadge.ENDED] go to the Past tab, every other card to Upcoming.
 */
class MyEventsViewModel(private val loader: MyEventsLoader) : ViewModel() {

  private val _uiState = MutableStateFlow(MyEventsUiState())
  val uiState: StateFlow<MyEventsUiState> = _uiState.asStateFlow()

  init {
    refresh()
  }

  fun selectTab(tab: EventTab) {
    _uiState.update { it.copy(selectedTab = tab) }
  }

  /** Reloads the cards, keeping the selected tab. */
  fun refresh() {
    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
    viewModelScope.launch {
      try {
        val (past, upcoming) = loader.load().partition { it.badge == EventBadge.ENDED }
        _uiState.update { it.copy(upcoming = upcoming, past = past, isLoading = false) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.update {
          it.copy(isLoading = false, errorMessage = e.message ?: "Could not load your events")
        }
      }
    }
  }

  companion object {
    fun factory(loader: MyEventsLoader): ViewModelProvider.Factory = viewModelFactory {
      initializer { MyEventsViewModel(loader) }
    }
  }
}
