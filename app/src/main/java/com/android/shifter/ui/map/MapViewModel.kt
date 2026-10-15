// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionRepository
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the map shows an error. The screen turns each into a translated message. */
enum class MapError {
  EVENT_NOT_FOUND,
  OFFLINE,
  UNEXPECTED,
}

/** A point the map centres on, in degrees. */
data class MapPoint(val latitude: Double, val longitude: Double)

/**
 * What the map screen shows: the event, a marker for each of its missions that has a location, and
 * the mission tapped last.
 *
 * @property venue the event's address, shown under its title.
 * @property center where the camera opens: the venue when it has coordinates, else the first
 *   mission. Null keeps the map's default position.
 * @property selected the marker of the mission tapped last, shown in a card over the map.
 */
data class MapUiState(
    val eventTitle: String = "",
    val venue: String = "",
    val center: MapPoint? = null,
    val markers: List<MapMarker> = emptyList(),
    val selected: MapMarker? = null,
    val isLoading: Boolean = true,
    val error: MapError? = null,
)

/**
 * Loads the event [eventId] and puts its missions on the map. Missions with no location have no
 * place on it and are left out.
 *
 * @param zone the time zone the mission hours are shown in, replaceable in tests.
 */
class MapViewModel(
    private val eventId: String,
    private val eventRepository: EventRepository,
    private val missionRepository: MissionRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(MapUiState())
  val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

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
        val markers =
            missionRepository
                .getMissionsByEvent(eventId)
                .sortedBy { it.startAt }
                .mapNotNull { it.toMarker() }
        val center =
            event.location.toPoint()
                ?: markers.firstOrNull()?.let { MapPoint(it.latitude, it.longitude) }
        _uiState.update {
          it.copy(
              eventTitle = event.title,
              venue = event.location.address,
              center = center,
              markers = markers,
              selected = null,
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

  /** The marker of this mission, titled with its name and its hours, or null with no location. */
  private fun Mission.toMarker(): MapMarker? {
    val location = location ?: return null
    val hours = "${HOURS.format(startAt.atZone(zone))}–${HOURS.format(endAt.atZone(zone))}"
    return MapMarker(
        id = id,
        latitude = location.latitude,
        longitude = location.longitude,
        title = title,
        info = "$hours · ${location.address}",
    )
  }

  private fun EventLocation.toPoint(): MapPoint? {
    return MapPoint(latitude ?: return null, longitude ?: return null)
  }

  companion object {
    private val HOURS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(
        eventId: String,
        eventRepository: EventRepository,
        missionRepository: MissionRepository,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { MapViewModel(eventId, eventRepository, missionRepository) }
    }
  }
}
