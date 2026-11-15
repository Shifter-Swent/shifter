// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.checkin.CheckInEligibility
import com.swent.shifter.model.checkin.CheckInMethod
import com.swent.shifter.model.checkin.CheckInRepository
import com.swent.shifter.model.checkin.CheckInRepositoryException
import com.swent.shifter.model.checkin.checkInEligibility
import com.swent.shifter.model.checkin.checkInWindow
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.location.LocationProvider
import com.swent.shifter.model.location.LocationStatus
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.mission.MissionRepositoryException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Why the automatic check-in shows an error. The screen turns each into a translated message. */
enum class AutoCheckInError {
  /** The backend refused the check-in: it is not tried again while the mission stays assigned. */
  NOT_ALLOWED,

  /** The backend could not be reached in time: the check-in is tried again on the next position. */
  OFFLINE,
  UNEXPECTED,
}

/**
 * Why the volunteer is not checked in to one mission yet, as far as the automatic check-in knows.
 * It only tries missions with a place, and only once it has a position: a mission without a place,
 * or the location being off, is for the screen to tell.
 */
enum class NotCheckedIn {
  /** The last position was outside the mission's zone, during its check-in window. */
  OUTSIDE_ZONE,

  /** The backend refused the check-in: it is not tried again while the mission stays open. */
  REFUSED,

  /** The backend could not be reached in time: the check-in is tried again on the next position. */
  OFFLINE,

  /** The check-in failed for another reason: it is tried again on the next position. */
  UNEXPECTED,
}

/** A check-in this ViewModel just recorded, for the confirmation ("Checked in: Bar, 18:02"). */
data class AutoCheckedIn(val missionTitle: String, val time: String)

/**
 * What the automatic check-in shows.
 *
 * @property locationStatus whether positions can be read; the screen asks for the permission or for
 *   the location services when it is not [LocationStatus.GRANTED].
 * @property checkedIn the last check-in recorded, or null when none was recorded yet.
 * @property error the last failure, cleared by the next check-in recorded.
 * @property notCheckedIn why each open mission is not checked in yet, by (event id, mission id);
 *   read it with [whyNotCheckedIn]. A mission checked in, or no longer open, has no entry.
 */
data class AutoCheckInUiState(
    val locationStatus: LocationStatus = LocationStatus.GRANTED,
    val checkedIn: AutoCheckedIn? = null,
    val error: AutoCheckInError? = null,
    val notCheckedIn: Map<Pair<String, String>, NotCheckedIn> = emptyMap(),
)

/**
 * Why the volunteer is not checked in to [mission] yet, or null when nothing keeps them from it.
 */
fun AutoCheckInUiState.whyNotCheckedIn(mission: Mission): NotCheckedIn? =
    notCheckedIn[mission.eventId to mission.id]

/**
 * Checks the signed-in volunteer in to their missions as soon as they are eligible, while the
 * screen collects [uiState]: the positions stop with the collection, so it only runs in the
 * foreground.
 *
 * The assigned missions are followed while the screen is open: a mission assigned meanwhile is
 * checked in to like the others, and one unassigned is no longer tried.
 *
 * Each mission is checked in to at most once: a mission the volunteer is already checked in to is
 * skipped, and new positions are ignored for a mission whose check-in is in flight.
 *
 * @param currentUserId returns the signed-in user's id, or null when nobody is signed in.
 * @param eligibility whether a volunteer at the given position can check in to the mission at the
 *   given time, or why not.
 * @param now the clock, replaceable in tests.
 * @param zone the time zone the confirmation's time is shown in, replaceable in tests.
 */
class AutoCheckInViewModel(
    private val missionRepository: MissionRepository,
    private val checkInRepository: CheckInRepository,
    private val locationProvider: LocationProvider,
    private val currentUserId: () -> String?,
    private val eligibility: (Mission, Instant, GeoPosition) -> CheckInEligibility,
    private val now: () -> Instant = Instant::now,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(AutoCheckInUiState(locationProvider.status.value))

  /**
   * Missions tried, as (event id, mission id): in flight, recorded or refused. A mission is
   * forgotten once it leaves the open missions, so that it is tried again if it comes back.
   */
  private val tried = mutableSetOf<Pair<String, String>>()

  // [watch] never emits: it only updates [_uiState], and runs while the screen collects.
  val uiState: StateFlow<AutoCheckInUiState> =
      merge(_uiState, flow<AutoCheckInUiState> { watch() })
          .stateIn(
              viewModelScope,
              SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
              _uiState.value,
          )

  /** Reads the permission and the location services again, e.g. after the permission dialog. */
  fun refreshLocation() = locationProvider.refreshStatus()

  private suspend fun watch() {
    coroutineScope {
      launch {
        locationProvider.status.collect { status ->
          _uiState.update {
            // Without positions, the last one says nothing about where the volunteer is now.
            val notCheckedIn =
                if (status == LocationStatus.GRANTED) it.notCheckedIn
                else it.notCheckedIn.filterValues { reason -> reason != NotCheckedIn.OUTSIDE_ZONE }
            it.copy(locationStatus = status, notCheckedIn = notCheckedIn)
          }
        }
      }
      try {
        checkInWhenEligible()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val offline =
            e is MissionRepositoryException.Unavailable ||
                e is CheckInRepositoryException.Unavailable
        // Nothing is evaluated any more: the reasons would no longer follow the volunteer.
        _uiState.update { it.copy(notCheckedIn = emptyMap()) }
        showError(if (offline) AutoCheckInError.OFFLINE else AutoCheckInError.UNEXPECTED)
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun checkInWhenEligible() = coroutineScope {
    val userId = currentUserId() ?: return@coroutineScope
    val missions =
        missionRepository
            .observeMissionsAssignedTo(userId)
            .flatMapLatest { assigned -> openMissions(assigned) }
            .stateIn(this)
    // A refusal is about the assignment it was made for: a mission unassigned then assigned again
    // is tried again.
    launch {
      missions.collect { open ->
        val keys = open.map { it.key }.toSet()
        tried.retainAll(keys)
        _uiState.update {
          it.copy(notCheckedIn = it.notCheckedIn.filterKeys { key -> key in keys })
        }
      }
    }
    // Follows the check-ins of the events the missions belong to, including events that appear
    // later.
    val known =
        missions
            .map { open -> open.map { it.eventId }.toSet() }
            .distinctUntilChanged()
            .flatMapLatest { events -> checkInsTo(events, userId) }
            .stateIn(this, SharingStarted.Eagerly, KnownCheckIns())
    // Positions are only read while there is a mission to check in to. When they stop, null drops
    // the last one, which says nothing about where the volunteer is when a mission comes next.
    val positions: Flow<GeoPosition?> =
        missions
            .map { it.isNotEmpty() }
            .distinctUntilChanged()
            .flatMapLatest { any -> if (any) locationProvider.positions() else flowOf(null) }
    // The last position is checked again when the check-ins change, so a position that came before
    // they were known is not lost until the next one.
    combine(positions, known) { position, checkIns -> position to checkIns }
        .collect { (position, checkIns) ->
          if (position == null) return@collect
          val time = now()
          missions.value
              // Before the check-ins of a mission's event are known, a position could check in to
              // it twice: the mission waits for the next position.
              .filter { it.eventId in checkIns.events }
              .forEach { mission ->
                when {
                  mission.key in checkIns.done -> report(mission, null)
                  mission.key in tried -> Unit
                  else -> {
                    val result = eligibility(mission, time, position)
                    if (result == CheckInEligibility.Eligible) {
                      // A failure of the last try stays until this one answers.
                      if (_uiState.value.whyNotCheckedIn(mission) == NotCheckedIn.OUTSIDE_ZONE) {
                        report(mission, null)
                      }
                      tried += mission.key
                      launch { checkIn(mission, userId, known) }
                    } else {
                      val outside =
                          result.isTooFar && _uiState.value.locationStatus == LocationStatus.GRANTED
                      report(mission, if (outside) NotCheckedIn.OUTSIDE_ZONE else null)
                    }
                  }
                }
              }
        }
  }

  /**
   * The missions of [assigned] still open to a check-in, filtered again each time one's window
   * ends, so the positions stop with the last window even when the assignments do not change. A
   * mission closed by hand or whose window is over cannot become eligible again; a window not open
   * yet can, so this is not a check that now is in the window.
   */
  private fun openMissions(assigned: List<Mission>): Flow<List<Mission>> = flow {
    while (true) {
      val open = assigned.filter {
        !it.completed && it.isGeoTimed && now() <= it.checkInWindow().endInclusive
      }
      emit(open)
      val nextEnd = open.minOfOrNull { it.checkInWindow().endInclusive } ?: break
      // Just past the end, as a mission is still open at the end itself.
      delay(Duration.between(now(), nextEnd).toMillis() + 1)
    }
  }

  private fun checkInsTo(events: Set<String>, userId: String): Flow<KnownCheckIns> =
      if (events.isEmpty()) flowOf(KnownCheckIns())
      else
          combine(events.map { checkInRepository.observeMyCheckIns(it, userId) }) { lists ->
            val done = lists.flatMap { list -> list.map { it.eventId to it.missionId } }
            KnownCheckIns(events, done.toSet())
          }

  private suspend fun checkIn(mission: Mission, userId: String, known: StateFlow<KnownCheckIns>) {
    try {
      // An offline write may never answer (#181): the volunteer must not wait for it forever.
      withTimeout(CHECK_IN_TIMEOUT_MILLIS) {
        checkInRepository.checkIn(mission.eventId, mission.id, userId, CheckInMethod.SELF)
      }
      val time = HOURS.format(now().atZone(zone))
      report(mission, null)
      _uiState.update { it.copy(checkedIn = AutoCheckedIn(mission.title, time), error = null) }
    } catch (_: CheckInRepositoryException.AlreadyCheckedIn) {
      // Checked in from elsewhere in the meantime: nothing left to do.
      report(mission, null)
    } catch (_: CheckInRepositoryException.NotAllowed) {
      reportFailure(mission, NotCheckedIn.REFUSED, known)
      showError(AutoCheckInError.NOT_ALLOWED)
    } catch (_: TimeoutCancellationException) {
      reportFailure(mission, NotCheckedIn.OFFLINE, known)
      tried -= mission.key
      showError(AutoCheckInError.OFFLINE)
    } catch (e: CancellationException) {
      // The screen left before the answer: the next collection tries again, and a write that went
      // through is skipped as checked in.
      tried -= mission.key
      throw e
    } catch (e: Exception) {
      val offline = e is CheckInRepositoryException.Unavailable
      reportFailure(mission, if (offline) NotCheckedIn.OFFLINE else NotCheckedIn.UNEXPECTED, known)
      tried -= mission.key
      showError(if (offline) AutoCheckInError.OFFLINE else AutoCheckInError.UNEXPECTED)
    }
  }

  private fun showError(error: AutoCheckInError) = _uiState.update { it.copy(error = error) }

  /** Records why [mission] is not checked in yet, or that nothing keeps it from it when null. */
  private fun report(mission: Mission, reason: NotCheckedIn?) = _uiState.update {
    it.copy(
        notCheckedIn =
            if (reason == null) it.notCheckedIn - mission.key
            else it.notCheckedIn + (mission.key to reason)
    )
  }

  /**
   * Records why the check-in to [mission] failed, unless the answer came too late to matter: the
   * mission is no longer open, or the volunteer is checked in to it meanwhile (a pending offline
   * write already counts).
   */
  private fun reportFailure(
      mission: Mission,
      reason: NotCheckedIn,
      known: StateFlow<KnownCheckIns>,
  ) {
    if (mission.key in tried && mission.key !in known.value.done) report(mission, reason)
  }

  private val CheckInEligibility.isTooFar
    get() = this is CheckInEligibility.Ineligible && reason == CheckInEligibility.Reason.TOO_FAR

  private val Mission.key
    get() = eventId to id

  /**
   * The volunteer's check-ins to the missions of [events]: [done] lists them as (event id, mission
   * id). A mission of another event is not known to be done or not yet.
   */
  private data class KnownCheckIns(
      val events: Set<String> = emptySet(),
      val done: Set<Pair<String, String>> = emptySet(),
  )

  companion object {
    /** Keeps the positions coming across a configuration change, not in the background. */
    const val STOP_TIMEOUT_MILLIS = 5_000L

    /** How long a check-in may take before it is reported as offline and tried again. */
    const val CHECK_IN_TIMEOUT_MILLIS = 10_000L

    private val HOURS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments. A mission is eligible when [checkInEligibility] says so.
     */
    fun factory(
        missionRepository: MissionRepository,
        checkInRepository: CheckInRepository,
        locationProvider: LocationProvider,
        currentUserId: () -> String?,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        AutoCheckInViewModel(
            missionRepository,
            checkInRepository,
            locationProvider,
            currentUserId,
            ::checkInEligibility,
        )
      }
    }
  }
}
