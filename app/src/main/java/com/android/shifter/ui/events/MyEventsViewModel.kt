// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Loads the cards of a My Events screen. ViewModels depend on this, never on a backend. */
fun interface MyEventsLoader {
  suspend fun load(): List<EventCardUi>
}

/** Withdraws the user from an event they take part in. Throws when the backend refuses or fails. */
fun interface EventWithdrawer {
  suspend fun withdraw(eventId: String)
}

/**
 * State holder of a My Events screen. The staff and the organizer views share it and only differ by
 * the [loader] they are given; [StaffEventsViewModel] adds what only volunteers can do.
 *
 * Cards whose badge is [EventBadge.ENDED] go to the Past tab, every other card to Upcoming.
 */
open class MyEventsViewModel(private val loader: MyEventsLoader) : ViewModel() {

  protected val mutableUiState = MutableStateFlow(MyEventsUiState())
  val uiState: StateFlow<MyEventsUiState> = mutableUiState.asStateFlow()

  private var loadJob: Job? = null

  init {
    refresh()
  }

  fun selectTab(tab: EventTab) {
    mutableUiState.update { it.copy(selectedTab = tab) }
  }

  /**
   * Reloads the cards, keeping the selected tab. A reload cancels the one still running, so an
   * older answer can never overwrite a newer one.
   */
  fun refresh() {
    loadJob?.cancel()
    mutableUiState.update { it.copy(isLoading = true, loadFailed = false) }
    loadJob = viewModelScope.launch {
      try {
        val (past, upcoming) = loader.load().partition { it.badge == EventBadge.ENDED }
        mutableUiState.update { it.copy(upcoming = upcoming, past = past, isLoading = false) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // The screen shows a generic message: backend errors are logged, never displayed.
        Log.e(TAG, "Could not load the events", e)
        mutableUiState.update { it.copy(isLoading = false, loadFailed = true) }
      }
    }
  }

  companion object {
    private const val TAG = "MyEventsViewModel"

    fun factory(loader: MyEventsLoader): ViewModelProvider.Factory = viewModelFactory {
      initializer { MyEventsViewModel(loader) }
    }
  }
}

/** [MyEventsViewModel] of the staff view, which can also withdraw from an event. */
class StaffEventsViewModel(loader: MyEventsLoader, private val withdrawer: EventWithdrawer) :
    MyEventsViewModel(loader) {

  /** Withdraws from [eventId] and removes its card once the backend confirmed it. */
  fun withdraw(eventId: String) {
    mutableUiState.update { it.copy(withdrawFailed = false) }
    viewModelScope.launch {
      try {
        withdrawer.withdraw(eventId)
        mutableUiState.update { state ->
          state.copy(
              upcoming = state.upcoming.filterNot { it.id == eventId },
              past = state.past.filterNot { it.id == eventId },
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Could not withdraw from $eventId", e)
        mutableUiState.update { it.copy(withdrawFailed = true) }
      }
    }
  }

  companion object {
    private const val TAG = "StaffEventsViewModel"

    fun factory(loader: MyEventsLoader, withdrawer: EventWithdrawer): ViewModelProvider.Factory =
        viewModelFactory {
          initializer { StaffEventsViewModel(loader, withdrawer) }
        }
  }
}
