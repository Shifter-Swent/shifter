// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.location.LocationProvider
import com.swent.shifter.model.location.LocationStatus
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the map shows an error. The screen turns each into a translated message. */
enum class MapError {
  EVENT_NOT_FOUND,
  OFFLINE,
  UNEXPECTED,
}

/**
 * The next mission the signed-in user is assigned to: its marker and its hours.
 *
 * @property isOngoing whether it has already started, in which case the screen says until when it
 *   runs rather than when it starts.
 */
data class NextMission(
    val markerId: String,
    val startTime: String,
    val endTime: String,
    val isOngoing: Boolean,
)

/**
 * What the map screen shows: the event, a marker for each of its missions that has a location, and
 * the mission tapped last.
 *
 * @property venue the event's address, shown under its title.
 * @property center where the camera opens: the venue when it has coordinates, else the first
 *   mission. Null keeps the map's default position.
 * @property selected the marker of the mission tapped last, shown in a card over the map.
 * @property next the signed-in user's next mission on the map, highlighted; null when they have
 *   none left.
 */
data class MapUiState(
    val eventTitle: String = "",
    val venue: String = "",
    val center: GeoPosition? = null,
    val markers: List<MapMarker> = emptyList(),
    val selected: MapMarker? = null,
    val next: NextMission? = null,
    val isLoading: Boolean = true,
    val error: MapError? = null,
)

/**
 * Loads the event [eventId] and puts its missions on the map. Missions with no location have no
 * place on it and are left out.
 *
 * @param currentUserId returns the signed-in user's id, or null when nobody is signed in.
 * @param now the clock telling which missions are over, replaceable in tests.
 * @param zone the time zone the mission hours are shown in, replaceable in tests.
 * @param locationProvider gives the user's position to show on the map; null for a user the map
 *   does not locate, such as an organizer.
 */
class MapViewModel(
    private val eventId: String,
    private val eventRepository: EventRepository,
    private val missionRepository: MissionRepository,
    private val currentUserId: () -> String?,
    private val now: () -> Instant = Instant::now,
    private val zone: ZoneId = ZoneId.systemDefault(),
    locationProvider: LocationProvider? = null,
) : ViewModel() {

  private val _uiState = MutableStateFlow(MapUiState())
  val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

  /**
   * Where the user is, or null when unknown: no provider, no access, a failed GPS, or no position
   * yet. Positions only come while the screen collects this, and the last one is forgotten when it
   * stops, so coming back from the background never shows an old position.
   */
  val myPosition: StateFlow<GeoPosition?> =
      (locationProvider?.positionWhileGranted() ?: flowOf(null)).stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0),
          null,
      )

  init {
    load()
  }

  /** Loads the event and its missions, again after an error. */
  fun load() {
    _uiState.update { it.copy(isLoading = true, error = null) }
    viewModelScope.launch {
      try {
        val event = eventRepository.getEvent(eventId)
        if (event == null) {
          _uiState.update { it.copy(error = MapError.EVENT_NOT_FOUND) }
          return@launch
        }
        val missions =
            missionRepository
                .getMissionsByEvent(eventId)
                .filter { it.isGeoTimed }
                .sortedBy { it.startAt }
        val markers = missions.mapNotNull { it.toMarker() }
        val userId = currentUserId()
        val now = now()
        val next =
            missions
                .firstOrNull { userId in it.assigneeIds && it.endAt > now }
                ?.let {
                  NextMission(
                      markerId = it.id,
                      startTime = HOURS.format(it.startAt.atZone(zone)),
                      endTime = HOURS.format(it.endAt.atZone(zone)),
                      isOngoing = it.startAt <= now,
                  )
                }
        val center = event.location.position ?: markers.firstOrNull()?.position
        _uiState.update {
          it.copy(
              eventTitle = event.title,
              venue = event.location.address,
              center = center,
              markers = markers,
              selected = null,
              next = next,
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: EventRepositoryException.Unavailable) {
        _uiState.update { it.copy(error = MapError.OFFLINE) }
      } catch (e: Exception) {
        _uiState.update { it.copy(error = MapError.UNEXPECTED) }
      } finally {
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  /** Shows the card of the mission behind [marker]. */
  fun selectMarker(marker: MapMarker) = _uiState.update { it.copy(selected = marker) }

  /** Closes the card of the selected mission. */
  fun clearSelection() = _uiState.update { it.copy(selected = null) }

  /**
   * The provider's positions, null once access is lost or if the GPS fails: the map still works.
   * Merged rather than combined, so that getting access back waits for a new position instead of
   * showing the last one. A position is checked when it arrives, as access may be lost by then.
   */
  private fun LocationProvider.positionWhileGranted(): Flow<GeoPosition?> =
      merge(
              positions().map { position ->
                position.takeIf { status.value == LocationStatus.GRANTED }
              },
              status.filter { it != LocationStatus.GRANTED }.map { null },
          )
          .catch { emit(null) }

  /** The marker of this mission, titled with its name and its hours, or null with no location. */
  private fun Mission.toMarker(): MapMarker? {
    val location = location ?: return null
    val hours = "${HOURS.format(startAt.atZone(zone))}–${HOURS.format(endAt.atZone(zone))}"
    return MapMarker(
        id = id,
        position = location.position,
        title = title,
        info = "$hours · ${location.address}",
    )
  }

  companion object {
    /** Keeps the positions coming across a configuration change, not in the background. */
    const val STOP_TIMEOUT_MILLIS = 5_000L

    private val HOURS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(
        eventId: String,
        eventRepository: EventRepository,
        missionRepository: MissionRepository,
        currentUserId: () -> String?,
        locationProvider: LocationProvider? = null,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        MapViewModel(
            eventId,
            eventRepository,
            missionRepository,
            currentUserId,
            locationProvider = locationProvider,
        )
      }
    }
  }
}
