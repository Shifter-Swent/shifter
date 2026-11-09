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
import com.swent.shifter.model.location.LiveLocation
import com.swent.shifter.model.location.LiveLocationRepository
import com.swent.shifter.model.location.LiveLocationRepositoryException
import com.swent.shifter.model.location.LocationProvider
import com.swent.shifter.model.location.LocationStatus
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.user.UserRepository
import com.swent.shifter.model.user.displayNames
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.retryWhen
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
 * @property venue the event's address, shown above the map.
 * @property center where the camera opens: the venue when it has coordinates, else the first
 *   mission. Null keeps the map's default position.
 * @property selected the marker of the mission tapped last, shown in a card over the map.
 * @property next the signed-in user's next mission on the map, highlighted; null when they have
 *   none left.
 */
data class MapUiState(
    val venue: String = "",
    val center: GeoPosition? = null,
    val markers: List<MapMarker> = emptyList(),
    val selected: MapMarker? = null,
    val next: NextMission? = null,
    val isLoading: Boolean = true,
    val error: MapError? = null,
)

/**
 * Which teams' volunteers the map shows to the signed-in user: the teams they manage, and later
 * every team of the event for its organizer. The map only follows the positions of these teams.
 */
fun interface WatchedTeams {
  suspend fun of(eventId: String, userId: String): List<Team>
}

/**
 * A volunteer of a watched team who shares their position, as the map shows them.
 *
 * @property name their display name, or null when it is unknown: the screen shows a generic one.
 * @property minutesAgo how long ago [position] was measured, in whole minutes.
 * @property isStale whether [position] is older than [MapViewModel.STALE_AFTER]: the volunteer may
 *   have moved since, so the screen greys it out.
 */
data class VolunteerPin(
    val userId: String,
    val name: String?,
    val position: GeoPosition,
    val minutesAgo: Long,
    val isStale: Boolean,
)

/**
 * Loads the event [eventId] and puts its missions on the map. Missions with no location have no
 * place on it and are left out.
 *
 * With [liveLocations] and [watchedTeams], it also follows the volunteers of the watched teams who
 * share their position, in [volunteers]. Without them, as on the organizer's map for now, it shows
 * no volunteer.
 *
 * @param currentUserId returns the signed-in user's id, or null when nobody is signed in.
 * @param now the clock telling which missions are over and how old a position is, replaceable in
 *   tests.
 * @param zone the time zone the mission hours are shown in, replaceable in tests.
 * @param locationProvider gives the user's position to show on the map; null for a user the map
 *   does not locate, such as an organizer.
 * @param userRepository reads the volunteers' names; without it, every name is unknown.
 */
class MapViewModel(
    private val eventId: String,
    private val eventRepository: EventRepository,
    private val missionRepository: MissionRepository,
    private val currentUserId: () -> String?,
    private val now: () -> Instant = Instant::now,
    private val zone: ZoneId = ZoneId.systemDefault(),
    locationProvider: LocationProvider? = null,
    private val liveLocations: LiveLocationRepository? = null,
    private val watchedTeams: WatchedTeams? = null,
    private val userRepository: UserRepository? = null,
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

  /**
   * The volunteers of the watched teams who share their position, followed live while the map is on
   * screen, their age refreshed every minute even when no new position arrives.
   *
   * Only the current members of a team are shown: the rules let a position stay readable for a
   * while after its volunteer left the team. The teams are read again every minute, so a volunteer
   * who joins or leaves, or a team the user starts or stops managing, shows up or goes within a
   * minute. A volunteer in several watched teams is shown once, at their latest position.
   *
   * A team that cannot be read just has nobody to show, and is tried again every minute: reading
   * only opens with the event, so a map opened early shows the volunteers once it starts.
   * [READABLE_AFTER_END] after the event's end, nothing is followed any more, without waiting for a
   * refusal that may never come. Any other failure keeps the last positions, which keep ageing
   * until they are greyed out, and is retried every [RETRY_DELAY_MILLIS]. Once the map is left, the
   * list is cleared, so coming back never shows old positions.
   */
  val volunteers: StateFlow<List<VolunteerPin>> =
      volunteerPins()
          .stateIn(
              viewModelScope,
              SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0),
              emptyList(),
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

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun volunteerPins(): Flow<List<VolunteerPin>> {
    val repository = liveLocations ?: return flowOf(emptyList())
    val teamsToWatch = watchedTeams ?: return flowOf(emptyList())
    // The teams are read again every minute, and their positions observed again only when they
    // changed, such as a volunteer who joined or left, or a team the user started or stopped
    // managing.
    val locations = flow {
      val userId = currentUserId()
      // An open listener is only refused on the next change of the team's positions, which may
      // never come once the event is over: past the end of reading, no team is watched any more.
      val readableUntil = eventRepository.getEvent(eventId)?.endAt?.plus(READABLE_AFTER_END)
      if (userId == null || readableUntil == null) emit(emptyList<Team>())
      else
          emitAll(
              everyMinute().map {
                if (now() > readableUntil) emptyList() else teamsToWatch.of(eventId, userId)
              }
          )
    }
        .distinctUntilChanged()
        .flatMapLatest { teams -> latestLocations(repository, teams) }
        // Offline or any other failure: no position comes meanwhile, so the last ones stay on
        // the map and keep ageing until they are greyed out, while the read is tried again.
        .retryWhen { cause, _ ->
          val retry = cause is Exception
          if (retry) delay(RETRY_DELAY_MILLIS)
          retry
        }
    return flow {
      // Read once per volunteer; a failed read is not kept, so it is tried again later.
      val names = mutableMapOf<String, String?>()
      emitAll(
          combine(locations, everyMinute()) { latest, _ -> latest }
              .map { latest ->
                val unknown = latest.map { it.userId }.filter { it !in names }
                if (unknown.isNotEmpty() && userRepository != null) {
                  names += userRepository.displayNames(unknown)
                }
                val now = now()
                latest.map { it.toPin(names[it.userId], now) }.sortedBy { it.userId }
              }
      )
    }
  }

  /**
   * The latest position of each current member of [teams] who shares it: a volunteer in several of
   * them is kept once, at their latest position.
   */
  private fun latestLocations(
      repository: LiveLocationRepository,
      teams: List<Team>,
  ): Flow<List<LiveLocation>> {
    if (teams.isEmpty()) return flowOf(emptyList())
    return combine(
        teams.map { team ->
          repository
              .observeTeamLocations(eventId, team.id)
              .map { locations -> locations.filter { it.userId in team.memberIds } }
              // A team that cannot be read, before the event or once it has another manager, just
              // has nobody to show: the other teams keep working. It is tried again every minute,
              // since reading only opens with the event; a team no longer managed stops being tried
              // at the next read of the teams, and every team once the event is over.
              .retryWhen { cause, _ ->
                val refused = cause is LiveLocationRepositoryException.PermissionDenied
                if (refused) {
                  emit(emptyList())
                  delay(AGE_REFRESH_MILLIS)
                }
                refused
              }
        }
    ) { lists ->
      lists
          .flatMap { it }
          .groupBy { it.userId }
          .map { (_, locations) -> locations.maxBy { it.measuredAt } }
    }
  }

  private fun LiveLocation.toPin(name: String?, now: Instant): VolunteerPin {
    // A device clock ahead of ours could make the age negative: it is shown as just measured.
    val age = Duration.between(measuredAt, now).coerceAtLeast(Duration.ZERO)
    return VolunteerPin(userId, name, position, age.toMinutes(), isStale = age > STALE_AFTER)
  }

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

    /** How old a volunteer's position may be before the map greys it out. */
    val STALE_AFTER: Duration = Duration.ofMinutes(10)

    /** How often the age of the positions is refreshed when no new position arrives. */
    const val AGE_REFRESH_MILLIS = 60_000L

    /** How long to wait before reading the positions again after a failure. */
    const val RETRY_DELAY_MILLIS = 10_000L

    /**
     * How long after the event's end the positions can still be read, as `firestore.rules` allow:
     * the map stops following them then.
     */
    val READABLE_AFTER_END: Duration = Duration.ofHours(1)

    /** Emits now, then every [AGE_REFRESH_MILLIS], so an age grows without a new position. */
    private fun everyMinute(): Flow<Unit> = flow {
      while (true) {
        emit(Unit)
        delay(AGE_REFRESH_MILLIS)
      }
    }

    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments. Without [liveLocations] and [watchedTeams], the map shows no volunteer.
     */
    fun factory(
        eventId: String,
        eventRepository: EventRepository,
        missionRepository: MissionRepository,
        currentUserId: () -> String?,
        locationProvider: LocationProvider? = null,
        liveLocations: LiveLocationRepository? = null,
        watchedTeams: WatchedTeams? = null,
        userRepository: UserRepository? = null,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        MapViewModel(
            eventId,
            eventRepository,
            missionRepository,
            currentUserId,
            locationProvider = locationProvider,
            liveLocations = liveLocations,
            watchedTeams = watchedTeams,
            userRepository = userRepository,
        )
      }
    }
  }
}
