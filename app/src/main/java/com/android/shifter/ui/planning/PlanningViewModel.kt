// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.checkin.CheckIn
import com.swent.shifter.model.checkin.CheckInRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.mission.MissionRepositoryException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the Planning shows an error. The screen turns each into a translated message. */
enum class PlanningError {
  OFFLINE,
  UNEXPECTED,
}

/**
 * What the volunteer's Planning shows.
 *
 * @property missions the missions the signed-in user is assigned to in the event, with their
 *   check-in time once they checked in.
 * @property error why the missions could not be loaded, cleared by the next
 *   [PlanningViewModel.load].
 * @property checkInsUnavailable whether the check-ins could not be read, so the statuses shown may
 *   be missing or outdated. Cleared by the next [PlanningViewModel.load].
 */
data class PlanningUiState(
    val missions: List<PlanningMission> = emptyList(),
    val isLoading: Boolean = true,
    val error: PlanningError? = null,
    val checkInsUnavailable: Boolean = false,
)

/**
 * Loads the missions the signed-in user is assigned to in the event [eventId], and keeps their
 * check-in status live: a check-in recorded while the Planning is open shows up without reloading.
 *
 * When the check-ins cannot be read, the missions stay shown without their status, so the volunteer
 * still knows where to be and when.
 *
 * @param currentUserId returns the signed-in user's id, or null when nobody is signed in.
 */
class PlanningViewModel(
    private val eventId: String,
    private val missionRepository: MissionRepository,
    private val checkInRepository: CheckInRepository,
    private val currentUserId: () -> String?,
) : ViewModel() {

  private val _uiState = MutableStateFlow(PlanningUiState())
  val uiState: StateFlow<PlanningUiState> = _uiState.asStateFlow()

  /** The loading in progress, with the check-ins it observes. */
  private var loading: Job? = null

  init {
    load()
  }

  /** Loads the missions and starts observing the check-ins, again after an error. */
  fun load() {
    // Stops observing the check-ins of the previous load, so they are never observed twice.
    loading?.cancel()
    _uiState.update { it.copy(isLoading = true, error = null, checkInsUnavailable = false) }
    loading = viewModelScope.launch {
      val userId = currentUserId()
      val missions =
          try {
            // Nobody signed in only happens while the app is going back to sign-in.
            checkNotNull(userId) { "No signed-in user" }
            missionRepository.getMissionsAssignedTo(userId).filter { it.eventId == eventId }
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            val error =
                if (e is MissionRepositoryException.Unavailable) PlanningError.OFFLINE
                else PlanningError.UNEXPECTED
            _uiState.update { it.copy(isLoading = false, error = error) }
            return@launch
          }
      try {
        checkInRepository.observeMyCheckIns(eventId, userId).collect { checkIns ->
          show(missions, checkIns)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Keeps the statuses already shown, if any: a check-in is never taken back.
        if (_uiState.value.isLoading) show(missions, emptyList())
        _uiState.update { it.copy(checkInsUnavailable = true) }
      }
    }
  }

  private fun show(missions: List<Mission>, checkIns: List<CheckIn>) {
    val checkedInAt = checkIns.associate { it.missionId to it.checkedInAt }
    _uiState.update {
      it.copy(
          missions =
              missions.map { mission ->
                PlanningMission(
                    id = mission.id,
                    title = mission.title,
                    startAt = mission.startAt,
                    endAt = mission.endAt,
                    address = mission.location?.address,
                    checkedInAt = checkedInAt[mission.id],
                )
              },
          isLoading = false,
      )
    }
  }

  companion object {
    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(
        eventId: String,
        missionRepository: MissionRepository,
        checkInRepository: CheckInRepository,
        currentUserId: () -> String?,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        PlanningViewModel(eventId, missionRepository, checkInRepository, currentUserId)
      }
    }
  }
}
