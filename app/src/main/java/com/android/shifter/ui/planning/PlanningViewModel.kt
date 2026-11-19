// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.checkin.CheckIn
import com.swent.shifter.model.checkin.CheckInRepository
import com.swent.shifter.model.event.EventRepository
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
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
 * @property missionsOutdated whether the missions stopped being followed after they were shown, so
 *   a change made since may be missing. They are followed again every
 *   [PlanningViewModel.RETRY_DELAY_MILLIS], and the flag is cleared by the next list read.
 * @property now what the volunteer should do now, for the now card, from the missions of the
 *   current event day (04:00 to 04:00, as the Planning groups them). Null while it is not known:
 *   before the missions and the check-ins are loaded, after they failed to load, and until the
 *   first tick of the clock once the screen first collects [uiState]. When the screen comes back,
 *   the last state stays until the next tick, which comes at once. A consumer tells loading from
 *   failure with the other fields: null with [isLoading] is still loading; null with [error], or
 *   with [checkInsUnavailable] once loaded, failed and stays null until the next
 *   [PlanningViewModel.load]. With [missionsOutdated], it follows the last missions read.
 * @property eventPeriod when the event starts and ends, so the Planning can offer each of its days,
 *   those without missions included. Null while unknown: the event is still being read, or could
 *   not be, and the Planning falls back on the days of the missions. The missions never wait for
 *   it.
 */
data class PlanningUiState(
    val missions: List<PlanningMission> = emptyList(),
    val isLoading: Boolean = true,
    val error: PlanningError? = null,
    val checkInsUnavailable: Boolean = false,
    val missionsOutdated: Boolean = false,
    val now: NowState? = null,
    val eventPeriod: ClosedRange<Instant>? = null,
)

/**
 * Follows the missions the signed-in user is assigned to in the event [eventId], and their check-in
 * status: a mission completed, rescheduled, assigned or unassigned, or a check-in recorded, while
 * the Planning is open shows without reload, in the list and in the now state.
 *
 * When the missions fail once shown, the last ones stay and are followed again after
 * [RETRY_DELAY_MILLIS]: the ViewModel outlives a tab switch, so it cannot wait for a reload.
 *
 * When the check-ins or the event cannot be read, the missions stay shown without their status or
 * the event's dates, so the volunteer still knows where to be and when.
 *
 * The [now][PlanningUiState.now] state comes from [nowStates], fed with today's missions among the
 * same missions, their check-ins and [clock]. The clock is only followed while [uiState] is
 * collected: the screen must collect it with its lifecycle (`collectAsStateWithLifecycle`), so the
 * timer stops when it is in the background.
 *
 * @param zone the time zone that decides which missions are today's.
 * @param currentUserId returns the signed-in user's id, or null when nobody is signed in.
 */
class PlanningViewModel(
    private val eventId: String,
    private val missionRepository: MissionRepository,
    private val checkInRepository: CheckInRepository,
    private val eventRepository: EventRepository,
    private val clock: MinuteClock,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val currentUserId: () -> String?,
) : ViewModel() {

  private val _uiState = MutableStateFlow(PlanningUiState())
  val uiState: StateFlow<PlanningUiState> = _uiState.asStateFlow()

  /** The load in progress, which follows the missions and their check-ins. */
  private var loading: Job? = null

  /**
   * What one load gives [nowStates]: its [missions] and the ids of the missions checked in to, each
   * once read. A plain class, so that each load is a new value even with the same missions, and the
   * now state is computed again from it.
   */
  private class NowInputs {
    private val missionsRead = MutableStateFlow<List<Mission>?>(null)
    private val checkInsRead = MutableStateFlow<Set<String>?>(null)

    /** The missions of the event, again each time one changes. */
    val missions: Flow<List<Mission>> = missionsRead.filterNotNull()

    /** The ids of the missions checked in to, once read: an empty set before would mean none. */
    val checkedIn: Flow<Set<String>> = checkInsRead.filterNotNull()

    fun showMissions(missions: List<Mission>) {
      missionsRead.value = missions
    }

    fun showCheckIns(checkIns: List<CheckIn>) {
      checkInsRead.value = checkIns.map { it.missionId }.toSet()
    }
  }

  /** The inputs of the current load, null until it starts. */
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
   * The [missions] of the current event day, again each time they or the day change. The days run
   * from 04:00 to 04:00 ([eventDay]), as the Planning groups the missions. A mission running across
   * 04:00 belongs to both days, so a shift running then stays shown.
   *
   * The day follows its own reading of the ticks: on the tick at 04:00, [nowStates] may compute
   * once with the new time and the previous day's missions, and is corrected right after.
   */
  private fun todaysMissions(missions: Flow<List<Mission>>): Flow<List<Mission>> =
      combine(clock.ticks.map { it.eventDay(zone) }.distinctUntilChanged(), missions) { day, all ->
        all.filter { it.isOn(day) }
      }

  private fun Mission.isOn(day: LocalDate): Boolean =
      endAt > day.start() && startAt < day.plusDays(1).start()

  /** When the event day [this] starts: at 04:00 local time, also on a DST day, as [eventDay]. */
  private fun LocalDate.start(): Instant =
      atTime(LocalTime.of(DAY_STARTS_AT_HOUR.toInt(), 0)).atZone(zone).toInstant()

  /**
   * Follows the missions and their check-ins, again after an error. The Planning shows once both
   * are read, or the check-ins failed; then each change of either shows without reload.
   */
  fun load() {
    // Stops following the previous load, so the missions and check-ins are never followed twice.
    loading?.cancel()
    nowInputs.value = null
    _uiState.update {
      it.copy(
          isLoading = true,
          error = null,
          checkInsUnavailable = false,
          missionsOutdated = false,
          now = null,
      )
    }
    loading = viewModelScope.launch {
      // Read alongside the missions, which neither depend on it nor wait for it. A failed read
      // keeps the dates of a previous load: they are still the event's.
      launch {
        readEventPeriod()?.let { period -> _uiState.update { it.copy(eventPeriod = period) } }
      }
      val userId = currentUserId()
      val inputs = NowInputs().also { nowInputs.value = it }
      var missions: List<Mission>? = null
      var checkIns: List<CheckIn>? = null
      try {
        // Nobody signed in only happens while the app is going back to sign-in.
        checkNotNull(userId) { "No signed-in user" }
        missionRepository
            .observeMissionsAssignedTo(userId)
            .map { all -> all.filter { it.eventId == eventId } }
            .retryWhen { cause, _ ->
              // A failure before the first list shows the error screen and its Retry instead.
              val retry = cause is Exception && missions != null
              if (retry) {
                _uiState.update { it.copy(missionsOutdated = true) }
                delay(RETRY_DELAY_MILLIS)
              }
              retry
            }
            // Before distinctUntilChanged, so the same list read again still clears the notice.
            .onEach { _uiState.update { it.copy(missionsOutdated = false) } }
            .distinctUntilChanged()
            .collect { latest ->
              val first = missions == null
              missions = latest
              inputs.showMissions(latest)
              if (first) {
                launch {
                  followCheckIns(userId, inputs) {
                    checkIns = it
                    show(checkNotNull(missions), it)
                  }
                }
              } else {
                // Before the check-ins are read, the Planning is still loading.
                checkIns?.let { show(latest, it) }
              }
            }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Only before the first list: later failures are retried.
        nowInputs.value = null
        val error =
            if (e is MissionRepositoryException.Unavailable) PlanningError.OFFLINE
            else PlanningError.UNEXPECTED
        _uiState.update { it.copy(isLoading = false, error = error) }
      }
    }
  }

  /** When the event starts and ends, or null when it cannot be read. */
  private suspend fun readEventPeriod(): ClosedRange<Instant>? =
      try {
        eventRepository.getEvent(eventId)?.let { it.startAt..it.endAt }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        null
      }

  /**
   * Follows the check-ins of the event, passing each list to [onCheckIns]. When they fail, the
   * statuses already shown stay, if any: a check-in is never taken back. Without any, the missions
   * show without status, and the now state stays unknown rather than calling a checked-in volunteer
   * late.
   */
  private suspend fun followCheckIns(
      userId: String,
      inputs: NowInputs,
      onCheckIns: (List<CheckIn>) -> Unit,
  ) {
    try {
      checkInRepository.observeMyCheckIns(eventId, userId).collect { checkIns ->
        onCheckIns(checkIns)
        inputs.showCheckIns(checkIns)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      if (_uiState.value.isLoading) onCheckIns(emptyList())
      _uiState.update { it.copy(checkInsUnavailable = true) }
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
    /** How long the missions wait before being followed again after a failure. */
    const val RETRY_DELAY_MILLIS = 10_000L

    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(
        eventId: String,
        missionRepository: MissionRepository,
        checkInRepository: CheckInRepository,
        eventRepository: EventRepository,
        clock: MinuteClock,
        currentUserId: () -> String?,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        PlanningViewModel(
            eventId,
            missionRepository,
            checkInRepository,
            eventRepository,
            clock,
            currentUserId = currentUserId,
        )
      }
    }
  }
}
