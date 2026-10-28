// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.checkin.CheckIn
import com.swent.shifter.model.checkin.CheckInRepository
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.model.user.UserRepository
import com.swent.shifter.model.user.UserRepositoryException
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Someone assigned to a mission, with their public name or null when it cannot be read.
 *
 * @property checkedInAt when they checked in, or null while they have not. It stays null until the
 *   check-ins of their mission have arrived, which [AttendanceMission.attendanceKnown] tells apart
 *   from nobody being there.
 */
data class AttendanceVolunteer(
    val userId: String,
    val displayName: String?,
    val checkedInAt: Instant? = null,
)

/**
 * A mission of the attendance screen and the people expected on it.
 *
 * @property teamId id of the team of the mission, or null for "General", no team; [teamName] is
 *   null for those and for a team that no longer exists.
 * @property assignees the people assigned, in the order the mission lists them. How many there are
 *   is how many are expected, never how many are present: presence comes from the check-ins.
 * @property attendanceKnown whether the check-ins of the mission have arrived. Until they have,
 *   nobody is known to be present and [missingAssignees] is not yet a list of who is late.
 */
data class AttendanceMission(
    val id: String,
    val title: String,
    val teamId: String?,
    val teamName: String?,
    val volunteersNeeded: Int,
    val startAt: Instant,
    val endAt: Instant,
    val assignees: List<AttendanceVolunteer>,
    val attendanceKnown: Boolean = false,
) {
  /** The assignees who checked in, in the order the mission lists them. */
  val presentAssignees: List<AttendanceVolunteer>
    get() = assignees.filter { it.checkedInAt != null }

  /** The assignees who have not checked in, in the order the mission lists them. */
  val missingAssignees: List<AttendanceVolunteer>
    get() = assignees.filter { it.checkedInAt == null }

  /** How many assignees are present: the "present" of "present / needed". */
  val presentCount: Int
    get() = presentAssignees.size

  /** Whether fewer people are present than the mission needs, which the screen warns about. */
  val isUnderstaffed: Boolean
    get() = presentCount < volunteersNeeded
}

/**
 * State of the attendance screen.
 *
 * @property loadFailed whether the missions could not be loaded, which drops them, or the check-ins
 *   of one of them could not be observed, which keeps them listed with their attendance not
 *   [known][AttendanceMission.attendanceKnown] rather than read as nobody being present.
 */
data class AttendanceUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val isOrganizer: Boolean = false,
    val missions: List<AttendanceMission> = emptyList(),
) {
  /** Whether the user is known to watch no mission: not while loading, nor after a failure. */
  val isEmpty: Boolean
    get() = !isLoading && !loadFailed && missions.isEmpty()
}

/**
 * Loads the missions whose attendance [currentUserId] may watch in the event [eventId], by start
 * time, with the people assigned to each of them, and keeps who is present up to date from the
 * check-ins.
 *
 * The organizer watches all missions of the event, including the "General" ones of no team. Anyone
 * else watches those of the teams they manage, and none at all when they manage none.
 */
class AttendanceViewModel(
    private val eventId: String,
    private val currentUserId: String,
    private val eventRepository: EventRepository,
    private val missionRepository: MissionRepository,
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
    private val checkInRepository: CheckInRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AttendanceUiState())
  val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

  /** The load running, if any: a newer one cancels it so it cannot publish stale missions. */
  private var loadJob: Job? = null

  init {
    refresh()
  }

  /** Loads the missions and their assignees again, e.g. after a load failed. */
  fun refresh() {
    loadJob?.cancel()
    _uiState.update { it.copy(isLoading = true, loadFailed = false) }
    loadJob = viewModelScope.launch {
      try {
        // An unknown event is a failure: the role cannot be decided without its organizer.
        val event = eventRepository.getEvent(eventId) ?: error("No event '$eventId'")
        val isOrganizer = event.organizerId == currentUserId
        val teams =
            if (isOrganizer) teamRepository.getTeamsByEvent(eventId)
            else teamRepository.getTeamsManagedBy(eventId, currentUserId)
        val missions = missions(isOrganizer, teams)
        _uiState.update {
          it.copy(isLoading = false, isOrganizer = isOrganizer, missions = missions)
        }
        observeAttendance(missions)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        // A stale attendance list reads as the current one, so it is dropped.
        _uiState.update { it.copy(isLoading = false, loadFailed = true, missions = emptyList()) }
      }
    }
  }

  /**
   * The missions of [teams], or all those of the event when the user is its organizer. A manager is
   * asked for their teams' missions only, so the others are never downloaded, and someone managing
   * no team is asked for nothing at all.
   */
  private suspend fun missions(isOrganizer: Boolean, teams: List<Team>): List<AttendanceMission> {
    val teamNames = teams.associate { it.id to it.name }
    val missions =
        when {
          isOrganizer -> missionRepository.getMissionsByEvent(eventId)
          teamNames.isEmpty() -> return emptyList()
          else -> missionRepository.getMissionsByTeams(eventId, teamNames.keys)
        }.sortedBy { it.startAt }
    val names = missions.flatMap { it.assigneeIds }.distinct().associateWith { name(it) }
    return missions.map { mission ->
      AttendanceMission(
          id = mission.id,
          title = mission.title,
          teamId = mission.teamId,
          teamName = mission.teamId?.let { teamNames[it] },
          volunteersNeeded = mission.volunteersNeeded,
          startAt = mission.startAt,
          endAt = mission.endAt,
          assignees = mission.assigneeIds.map { AttendanceVolunteer(it, names[it]) },
      )
    }
  }

  /**
   * Collects the check-ins of each mission, so an arrival shows up without a refresh.
   *
   * One collection per mission, each updating that mission only, so a check-in to one leaves the
   * attendance of the others untouched and no mission, team or profile is read again. They are
   * children of the load, which the next [refresh] cancels, taking them with it; no mission means
   * no collection at all.
   */
  private fun CoroutineScope.observeAttendance(missions: List<AttendanceMission>) {
    missions.forEach { mission ->
      launch {
        try {
          checkInRepository.observeCheckIns(eventId, mission.id).collect { checkIns ->
            _uiState.update { it.withAttendance(mission.id, checkIns) }
          }
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          // The attendance of that mission stays unknown rather than read as nobody present.
          _uiState.update { it.copy(loadFailed = true) }
        }
      }
    }
  }

  /**
   * The [checkIns] of the mission [missionId] applied to its assignees, leaving every other mission
   * as it is. A check-in of someone the mission does not list is not one of its assignees being
   * present, so it is ignored.
   */
  private fun AttendanceUiState.withAttendance(
      missionId: String,
      checkIns: List<CheckIn>,
  ): AttendanceUiState {
    // One time per volunteer, the earliest, so the same arrival recorded twice counts once.
    val arrivals =
        checkIns
            .groupBy { it.userId }
            .mapValues { (_, records) -> records.minOf { it.checkedInAt } }
    return copy(
        missions =
            missions.map { mission ->
              if (mission.id != missionId) mission
              else
                  mission.copy(
                      attendanceKnown = true,
                      assignees =
                          mission.assignees.map { it.copy(checkedInAt = arrivals[it.userId]) },
                  )
            }
    )
  }

  /** The display name of [userId], or null when it cannot be read, which does not fail the load. */
  private suspend fun name(userId: String): String? =
      try {
        userRepository.getPublicProfile(userId)?.displayName?.takeIf { it.isNotBlank() }
      } catch (e: UserRepositoryException) {
        null
      }

  companion object {
    /** Builds the factory for `viewModel(factory = ...)`, as this ViewModel takes arguments. */
    fun factory(
        eventId: String,
        currentUserId: String,
        eventRepository: EventRepository,
        missionRepository: MissionRepository,
        teamRepository: TeamRepository,
        userRepository: UserRepository,
        checkInRepository: CheckInRepository,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        AttendanceViewModel(
            eventId,
            currentUserId,
            eventRepository,
            missionRepository,
            teamRepository,
            userRepository,
            checkInRepository,
        )
      }
    }
  }
}
