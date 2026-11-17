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
import com.swent.shifter.model.time.MinuteClock
import com.swent.shifter.ui.components.days.DAY_STARTS_AT_HOUR
import com.swent.shifter.ui.components.days.eventDay
import com.swent.shifter.ui.components.nowcard.NowState
import com.swent.shifter.ui.components.nowcard.nowStates
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
 * @property now what the volunteer should do now, for the now card, from the missions of the
 *   current event day (04:00 to 04:00, as the Planning groups them). Null while it is not known:
 *   before the missions and the check-ins are loaded, after they failed to load, and until the
 *   first tick of the clock once the screen first collects [uiState]. When the screen comes back,
 *   the last state stays until the next tick, which comes at once. A consumer tells loading from
 *   failure with the other fields: null with [isLoading] is still loading; null with [error], or
 *   with [checkInsUnavailable] once loaded, failed and stays null until the next
 *   [PlanningViewModel.load].
 */
data class PlanningUiState(
    val missions: List<PlanningMission> = emptyList(),
    val isLoading: Boolean = true,
    val error: PlanningError? = null,
    val checkInsUnavailable: Boolean = false,
    val now: NowState? = null,
)

/**
 * Loads the missions the signed-in user is assigned to in the event [eventId], and keeps their
 * check-in status live: a check-in recorded while the Planning is open shows up without reloading.
 *
 * When the check-ins cannot be read, the missions stay shown without their status, so the volunteer
 * still knows where to be and when.
 *
 * The [now][PlanningUiState.now] state comes from [nowStates], fed with today's missions among the
 * same missions, their check-ins and [clock]. The clock is only followed while [uiState] is
 * collected: the screen must collect it with its lifecycle (`collectAsStateWithLifecycle`), so the
 * timer stops when it is in the background.
 *
 * Only the clock and the check-ins are live: the missions are read once per [load], so a mission
 * completed, rescheduled or reassigned meanwhile only shows after the next [load].
 *
 * @param zone the time zone that decides which missions are today's.
 * @param currentUserId returns the signed-in user's id, or null when nobody is signed in.
 */
class PlanningViewModel(
    private val eventId: String,
    private val missionRepository: MissionRepository,
    private val checkInRepository: CheckInRepository,
    private val clock: MinuteClock,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val currentUserId: () -> String?,
) : ViewModel() {

  private val _uiState = MutableStateFlow(PlanningUiState())
  val uiState: StateFlow<PlanningUiState> = _uiState.asStateFlow()

  /** The loading in progress, with the check-ins it observes. */
  private var loading: Job? = null

  /**
   * What one load gives [nowStates]: its [missions], and the ids of the missions checked in to. A
   * plain class, so that each load is a new value even with the same missions, and the now state is
   * computed again from it.
   */
  private class NowInputs(val missions: List<Mission>) {
    private val read = MutableStateFlow<Set<String>?>(null)

    /** The ids of the missions checked in to, once read: an empty set before would mean none. */
    val checkedIn: Flow<Set<String>> = read.filterNotNull()

    fun show(checkIns: List<CheckIn>) {
      read.value = checkIns.map { it.missionId }.toSet()
    }
  }

  /** The inputs of the current load, null until its missions are loaded. */
  private val nowInputs = MutableStateFlow<NowInputs?>(null)

  init {
    load()
    followNow()
  }

  /** Keeps [PlanningUiState.now] up to date while [uiState] is collected, and only then. */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun followNow() {
    viewModelScope.launch {
      _uiState.subscriptionCount
          .map { it > 0 }
          .distinctUntilChanged()
          .collectLatest { watched ->
            if (watched) {
              nowInputs
                  .flatMapLatest { inputs ->
                    if (inputs == null) flowOf(null)
                    else
                        nowStates(
                            todaysMissions(inputs.missions),
                            inputs.checkedIn,
                            clock,
                        )
                  }
                  .collect { now -> _uiState.update { it.copy(now = now) } }
            }
          }
    }
  }

  /**
   * The [missions] of the current event day, again each time the day changes. The days run from
   * 04:00 to 04:00 ([eventDay]), as the Planning groups the missions. A mission running across
   * 04:00 belongs to both days, so a shift running then stays shown.
   *
   * The day follows its own reading of the ticks: on the tick at 04:00, [nowStates] may compute
   * once with the new time and the previous day's missions, and is corrected right after.
   */
  private fun todaysMissions(missions: List<Mission>): Flow<List<Mission>> =
      clock.ticks
          .map { it.eventDay(zone) }
          .distinctUntilChanged()
          .map { day -> missions.filter { it.isOn(day) } }

  private fun Mission.isOn(day: LocalDate): Boolean =
      endAt > day.start() && startAt < day.plusDays(1).start()

  /** When the event day [this] starts: at 04:00 local time, also on a DST day, as [eventDay]. */
  private fun LocalDate.start(): Instant =
      atTime(LocalTime.of(DAY_STARTS_AT_HOUR.toInt(), 0)).atZone(zone).toInstant()

  /** Loads the missions and starts observing the check-ins, again after an error. */
  fun load() {
    // Stops observing the check-ins of the previous load, so they are never observed twice.
    loading?.cancel()
    nowInputs.value = null
    _uiState.update {
      it.copy(isLoading = true, error = null, checkInsUnavailable = false, now = null)
    }
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
      // The now state waits for the check-ins of this load, so it is never computed without them.
      val inputs = NowInputs(missions).also { nowInputs.value = it }
      try {
        checkInRepository.observeMyCheckIns(eventId, userId).collect { checkIns ->
          show(missions, checkIns)
          inputs.show(checkIns)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Keeps the statuses already shown, if any: a check-in is never taken back. Without any,
        // the now state stays unknown rather than calling a checked-in volunteer late.
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
                    description = mission.description,
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
        clock: MinuteClock,
        currentUserId: () -> String?,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        PlanningViewModel(
            eventId,
            missionRepository,
            checkInRepository,
            clock,
            currentUserId = currentUserId,
        )
      }
    }
  }
}
