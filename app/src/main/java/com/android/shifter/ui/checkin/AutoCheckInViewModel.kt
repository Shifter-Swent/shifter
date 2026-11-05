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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
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
  /** The backend refused the check-in: it is not tried again. */
  NOT_ALLOWED,

  /** The backend could not be reached in time: the check-in is tried again on the next position. */
  OFFLINE,
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
 */
data class AutoCheckInUiState(
    val locationStatus: LocationStatus = LocationStatus.GRANTED,
    val checkedIn: AutoCheckedIn? = null,
    val error: AutoCheckInError? = null,
)

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
 * @param isEligible whether a volunteer at the given position can check in to the mission at the
 *   given time.
 * @param now the clock, replaceable in tests.
 * @param zone the time zone the confirmation's time is shown in, replaceable in tests.
 */
class AutoCheckInViewModel(
    private val missionRepository: MissionRepository,
    private val checkInRepository: CheckInRepository,
    private val locationProvider: LocationProvider,
    private val currentUserId: () -> String?,
    private val isEligible: (Mission, Instant, GeoPosition) -> Boolean,
    private val now: () -> Instant = Instant::now,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(AutoCheckInUiState(locationProvider.status.value))

  /** Missions tried, as (event id, mission id): in flight, recorded or refused. */
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
          _uiState.update { it.copy(locationStatus = status) }
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
        showError(if (offline) AutoCheckInError.OFFLINE else AutoCheckInError.UNEXPECTED)
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun checkInWhenEligible() = coroutineScope {
    val userId = currentUserId() ?: return@coroutineScope
    // A mission whose window is over cannot become eligible while the screen is open. A window not
    // open yet can, so this is not a check that now is in the window.
    val missions =
        missionRepository
            .observeMissionsAssignedTo(userId)
            .map { assigned ->
              assigned.filter { it.isGeoTimed && now() <= it.checkInWindow().endInclusive }
            }
            .stateIn(this)
    // Follows the check-ins of the events the missions belong to, including events that appear
    // later.
    val known =
        missions
            .map { open -> open.map { it.eventId }.toSet() }
            .distinctUntilChanged()
            .flatMapLatest { events -> checkInsTo(events, userId) }
            .stateIn(this, SharingStarted.Eagerly, KnownCheckIns())
    // Positions are only read while there is a mission to check in to.
    missions
        .map { it.isNotEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { any -> if (any) locationProvider.positions() else emptyFlow() }
        .collect { position ->
          val time = now()
          val checkIns = known.value
          missions.value
              // Before the check-ins of a mission's event are known, a position could check in to
              // it twice: the mission waits for the next position.
              .filter { it.eventId in checkIns.events }
              .filter { it.key !in tried && it.key !in checkIns.done }
              .filter { isEligible(it, time, position) }
              .forEach { mission ->
                tried += mission.key
                launch { checkIn(mission, userId) }
              }
        }
  }

  private fun checkInsTo(events: Set<String>, userId: String): Flow<KnownCheckIns> =
      if (events.isEmpty()) flowOf(KnownCheckIns())
      else
          combine(events.map { checkInRepository.observeMyCheckIns(it, userId) }) { lists ->
            val done = lists.flatMap { list -> list.map { it.eventId to it.missionId } }
            KnownCheckIns(events, done.toSet())
          }

  private suspend fun checkIn(mission: Mission, userId: String) {
    try {
      // An offline write may never answer (#181): the volunteer must not wait for it forever.
      withTimeout(CHECK_IN_TIMEOUT_MILLIS) {
        checkInRepository.checkIn(mission.eventId, mission.id, userId, CheckInMethod.SELF)
      }
      val time = HOURS.format(now().atZone(zone))
      _uiState.update { it.copy(checkedIn = AutoCheckedIn(mission.title, time), error = null) }
    } catch (_: CheckInRepositoryException.AlreadyCheckedIn) {
      // Checked in from elsewhere in the meantime: nothing left to do.
    } catch (_: CheckInRepositoryException.NotAllowed) {
      showError(AutoCheckInError.NOT_ALLOWED)
    } catch (_: TimeoutCancellationException) {
      tried -= mission.key
      showError(AutoCheckInError.OFFLINE)
    } catch (e: CancellationException) {
      // The screen left before the answer: the next collection tries again, and a write that went
      // through is skipped as checked in.
      tried -= mission.key
      throw e
    } catch (e: Exception) {
      tried -= mission.key
      val offline = e is CheckInRepositoryException.Unavailable
      showError(if (offline) AutoCheckInError.OFFLINE else AutoCheckInError.UNEXPECTED)
    }
  }

  private fun showError(error: AutoCheckInError) = _uiState.update { it.copy(error = error) }

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
            { mission, time, at ->
              checkInEligibility(mission, time, at) == CheckInEligibility.Eligible
            },
        )
      }
    }
  }
}
