// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.checkin.CheckIn
import com.swent.shifter.model.checkin.CheckInMethod
import com.swent.shifter.model.checkin.CheckInRepository
import com.swent.shifter.model.checkin.CheckInRepositoryException
import com.swent.shifter.model.checkin.checkInWindow
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.model.user.UserRepository
import com.swent.shifter.model.user.displayNames
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
 * @property checkInPending whether their check-in is not confirmed by the backend yet: still in its
 *   undo window, waiting for the network, or not acknowledged. [checkedInAt] is then an estimate.
 * @property checkedInManually whether a manager or the organizer checked them in, rather than the
 *   automatic check-in of their own phone.
 */
data class AttendanceVolunteer(
    val userId: String,
    val displayName: String?,
    val checkedInAt: Instant? = null,
    val checkInPending: Boolean = false,
    val checkedInManually: Boolean = false,
)

/**
 * A manual check-in that can still be undone, shown with an "Undo" action until it is sent.
 *
 * @property displayName the name of the volunteer, or null when it could not be read.
 */
data class UndoableCheckIn(val missionId: String, val userId: String, val displayName: String?)

/** Why a manual check-in was not recorded. The screen turns each into a message. */
enum class ManualCheckInError {
  /** The backend refused it, e.g. the volunteer is no longer assigned to the mission. */
  NOT_ALLOWED,

  /** Any other failure: the manager may try again. */
  FAILED,
}

/**
 * A manual check-in that was not recorded, and of whom, since several may fail at once when the
 * device comes back online.
 *
 * @property displayName the name of the volunteer, or null when it could not be read.
 */
data class ManualCheckInFailure(val reason: ManualCheckInError, val displayName: String?)

/**
 * A mission of the attendance screen and the people expected on it.
 *
 * @property teamId id of the team of the mission, or null for "General", no team; [teamName] is
 *   null for those and for a team that no longer exists.
 * @property assignees the people assigned, in the order the mission lists them. How many there are
 *   is how many are expected, never how many are present: presence comes from the check-ins.
 * @property attendanceKnown whether the check-ins of the mission have arrived and are still being
 *   followed. While they are not, nobody is known to be present, the timestamps last collected are
 *   possibly stale, and nobody is reported late: either nothing has arrived yet, or the observation
 *   failed.
 * @property attendanceFailed whether the check-ins of this mission could not be observed, which
 *   leaves it listed with its attendance not [known][attendanceKnown] rather than dropping it.
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
    val attendanceFailed: Boolean = false,
) {
  /** The assignees who checked in, in the order the mission lists them. */
  val presentAssignees: List<AttendanceVolunteer>
    get() = assignees.filter { it.checkedInAt != null }

  /**
   * The assignees who have not checked in, in the order the mission lists them, and none at all
   * while the attendance is not [known][attendanceKnown]: not being seen is not being absent.
   */
  val missingAssignees: List<AttendanceVolunteer>
    get() = if (attendanceKnown) assignees.filter { it.checkedInAt == null } else emptyList()

  /** How many assignees are present: the "present" of "present / needed". */
  val presentCount: Int
    get() = presentAssignees.size

  /**
   * Whether fewer people are present than the mission needs, which the screen warns about, and
   * never while the attendance is not [known][attendanceKnown].
   */
  val isUnderstaffed: Boolean
    get() = attendanceKnown && presentCount < volunteersNeeded
}

/**
 * State of the attendance screen.
 *
 * @property loadFailed whether the missions could not be loaded, which drops them. A mission whose
 *   check-ins could not be observed is a failure of that mission alone,
 *   [AttendanceMission.attendanceFailed], and leaves the others as they are.
 * @property eventTitle title of the event watched, empty until it has been read.
 * @property canViewAttendance whether the user may watch the attendance of this event: they manage
 *   one of its teams, or they organize it, so it is wider than being a manager. It is null while
 *   that is not known, which is what a first load that failed leaves it at, so the attendance is
 *   never offered to someone whose part in the event is unknown. It is not read from [missions]
 *   being empty: someone allowed to watch may have none.
 * @property undoableCheckIn the manual check-in just made, while it can still be undone.
 * @property checkInErrors the manual check-ins not recorded, oldest first, each until the screen
 *   has shown it: several may fail at once when the device is back online.
 */
data class AttendanceUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val isOrganizer: Boolean = false,
    val eventTitle: String = "",
    val canViewAttendance: Boolean? = null,
    val missions: List<AttendanceMission> = emptyList(),
    val undoableCheckIn: UndoableCheckIn? = null,
    val checkInErrors: List<ManualCheckInFailure> = emptyList(),
) {
  /** Whether the user is known to watch no mission: not while loading, nor after a failure. */
  val isEmpty: Boolean
    get() = !isLoading && !loadFailed && missions.isEmpty()

  /**
   * How many check-ins were sent and are not confirmed by the backend yet, across the missions
   * whose attendance is known. The one still in its undo window is left out: nothing was sent yet.
   */
  val pendingCheckInCount: Int
    get() =
        missions
            .filter { it.attendanceKnown }
            .sumOf { mission ->
              mission.presentAssignees.count {
                it.checkInPending &&
                    !(undoableCheckIn?.missionId == mission.id &&
                        undoableCheckIn.userId == it.userId)
              }
            }
}

/**
 * Loads the missions whose attendance [currentUserId] may watch in the event [eventId], by start
 * time, with the people assigned to each of them, and keeps who is present up to date from the
 * check-ins.
 *
 * The organizer watches all missions of the event, including the "General" ones of no team. Anyone
 * else watches those of the teams they manage, and none at all when they manage none.
 *
 * They can also check a missing assignee in by hand ([checkIn]), when the automatic check-in fails.
 * The check-in shows at once and can be undone until the screen's "Undo" goes away, which then
 * calls [sendUndoableCheckIn]: the screen decides when, so "Undo" never stays offered for a
 * check-in already sent. Whether it is confirmed comes from the check-ins observed, so a check-in
 * queued offline stays marked pending, and one the backend refuses, e.g. because the volunteer
 * checked in first, goes back to what the backend holds.
 *
 * @param now the clock giving the time shown for a check-in not confirmed yet, and telling which
 *   missions are open for check-in, replaceable in tests.
 */
class AttendanceViewModel(
    private val eventId: String,
    private val currentUserId: String,
    private val eventRepository: EventRepository,
    private val missionRepository: MissionRepository,
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
    private val checkInRepository: CheckInRepository,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AttendanceUiState())
  val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

  /** The load running, if any: a newer one cancels it so it cannot publish stale missions. */
  private var loadJob: Job? = null

  /** The check-ins last observed, by mission id: only missions whose attendance is known. */
  private val observed = mutableMapOf<String, List<CheckIn>>()

  /** A manual check-in made on this screen and not confirmed yet, at the time it was made. */
  private data class LocalCheckIn(
      val missionId: String,
      val userId: String,
      val displayName: String?,
      val at: Instant,
  )

  /** The manual check-in still in its undo window, and the timer that sends it. */
  private var undoable: LocalCheckIn? = null
  private var undoTimer: Job? = null

  /** Manual check-ins sent and not answered yet, possibly for as long as the device is offline. */
  private val sending = mutableSetOf<LocalCheckIn>()

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
        // The check-ins of the load it supersedes would be applied to missions they no longer
        // describe: they come again from the collections started below.
        observed.clear()
        _uiState.update {
          it.copy(
              isLoading = false,
              isOrganizer = isOrganizer,
              eventTitle = event.title,
              // The teams already read say whether they manage any, so no role is asked for twice.
              canViewAttendance = isOrganizer || teams.isNotEmpty(),
              missions = missions,
          )
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
    val names = userRepository.displayNames(missions.flatMap { it.assigneeIds })
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
            observed[mission.id] = checkIns
            _uiState.update { it.withAttendance(mission.id) }
          }
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          // Its attendance stops being known rather than being read as nobody present, or, once
          // something was collected, left shown as current when it may already be out of date. The
          // other missions keep theirs, and the missions themselves loaded, so the load has not
          // failed. Who was shown present is cleared with it, so an undo, which can no longer be
          // applied to check-ins not observed, does not leave someone shown present.
          observed.remove(mission.id)
          _uiState.update { state ->
            state.copy(
                missions =
                    state.missions.map {
                      if (it.id == mission.id)
                          it.copy(
                              attendanceKnown = false,
                              attendanceFailed = true,
                              assignees = it.assignees.map { assignee -> assignee.absent() },
                          )
                      else it
                    },
            )
          }
        }
      }
    }
  }

  /**
   * Checks [userId] in to the mission [missionId] by hand, as its manager or the organizer. They
   * show as present at once, and the check-in is sent once the screen's "Undo" is gone, as soon as
   * another one is made or the screen is left ([sendUndoableCheckIn]).
   *
   * Only a missing assignee of a mission whose attendance is known can be checked in: someone
   * present, pending or not known to be missing is left alone, so a double tap sends one check-in.
   * Nor can anyone be checked in outside the mission's [checkInWindow], the one of the automatic
   * check-in: the check-in would record the time of the tap as their arrival, before they could be
   * there or once the mission is over.
   */
  fun checkIn(missionId: String, userId: String) {
    val mission = _uiState.value.missions.firstOrNull { it.id == missionId } ?: return
    if (now() !in checkInWindow(mission.startAt, mission.endAt)) return
    val volunteer = mission.missingAssignees.firstOrNull { it.userId == userId } ?: return
    // One undo at a time: the previous check-in is no longer the one the action would undo.
    sendUndoableCheckIn()
    undoable = LocalCheckIn(missionId, userId, volunteer.displayName, now())
    // A failure not shown yet is left for the screen to show: it is about another check-in.
    _uiState.update {
      it.copy(undoableCheckIn = UndoableCheckIn(missionId, userId, volunteer.displayName))
          .withAttendance(missionId)
    }
    undoTimer = viewModelScope.launch {
      delay(UNDO_TIMEOUT_MILLIS)
      sendUndoableCheckIn()
    }
  }

  /** Takes back the manual check-in still in its undo window: nothing is sent. */
  fun undoCheckIn() {
    val checkIn = undoable ?: return
    undoable = null
    undoTimer?.cancel()
    _uiState.update { it.copy(undoableCheckIn = null).withAttendance(checkIn.missionId) }
  }

  /**
   * Sends the manual check-in still in its undo window, if any. The screen calls it once its "Undo"
   * is gone, and when it is left or goes to the background, so a check-in is not lost with it.
   *
   * The write is started before this returns: once started, Firestore keeps it queued until the
   * device is back online, even if the screen and this ViewModel are gone by then. A check-in still
   * in its undo window when the system kills the app is lost, though: nothing has been written yet.
   */
  fun sendUndoableCheckIn() {
    val checkIn = undoable ?: return
    undoable = null
    undoTimer?.cancel()
    sending += checkIn
    _uiState.update { it.copy(undoableCheckIn = null) }
    viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) { send(checkIn) }
  }

  /** Takes the oldest of [AttendanceUiState.checkInErrors] out once the screen has shown it. */
  fun onCheckInErrorShown() = _uiState.update { it.copy(checkInErrors = it.checkInErrors.drop(1)) }

  private fun fail(reason: ManualCheckInError, checkIn: LocalCheckIn) = _uiState.update {
    it.copy(checkInErrors = it.checkInErrors + ManualCheckInFailure(reason, checkIn.displayName))
  }

  private suspend fun send(checkIn: LocalCheckIn) {
    try {
      // Offline, this only returns once the device is back online: the check-in stays pending
      // until then, from the check-ins observed.
      checkInRepository.checkIn(eventId, checkIn.missionId, checkIn.userId, CheckInMethod.MANAGER)
    } catch (e: CancellationException) {
      throw e
    } catch (_: CheckInRepositoryException.AlreadyCheckedIn) {
      // Someone checked them in first, e.g. their own phone: they are present, which was the aim.
    } catch (_: CheckInRepositoryException.NotAllowed) {
      fail(ManualCheckInError.NOT_ALLOWED, checkIn)
    } catch (_: Exception) {
      fail(ManualCheckInError.FAILED, checkIn)
    } finally {
      sending -= checkIn
      _uiState.update { it.withAttendance(checkIn.missionId) }
    }
  }

  /**
   * The check-ins observed for the mission [missionId] applied to its assignees, with the manual
   * check-ins of this screen not observed yet, leaving every other mission as it is. A mission
   * whose check-ins are not observed is left as it is too: its attendance is not known.
   *
   * A check-in of someone the mission does not list is not one of its assignees being present, so
   * it is ignored.
   */
  private fun AttendanceUiState.withAttendance(missionId: String): AttendanceUiState {
    val checkIns = observed[missionId] ?: return this
    // At most one check-in per volunteer per mission, which the repository guarantees.
    val arrivals = checkIns.associateBy { it.userId }
    val local =
        (listOfNotNull(undoable) + sending)
            .filter { it.missionId == missionId }
            .associateBy { it.userId }
    return copy(
        missions =
            missions.map { mission ->
              if (mission.id != missionId) mission
              else
                  mission.copy(
                      attendanceKnown = true,
                      attendanceFailed = false,
                      assignees = mission.assignees.map { it.withCheckIn(arrivals, local) },
                  )
            }
    )
  }

  /**
   * This assignee as [arrivals] say, else as a manual check-in of this screen says, else absent.
   */
  private fun AttendanceVolunteer.withCheckIn(
      arrivals: Map<String, CheckIn>,
      local: Map<String, LocalCheckIn>,
  ): AttendanceVolunteer {
    val arrival = arrivals[userId]
    val manual = local[userId]
    return when {
      // What the backend, or Firestore's cache of it, holds wins over what this screen did.
      arrival != null ->
          copy(
              checkedInAt = arrival.checkedInAt,
              checkInPending = arrival.pending,
              checkedInManually = arrival.method == CheckInMethod.MANAGER,
          )
      manual != null ->
          copy(checkedInAt = manual.at, checkInPending = true, checkedInManually = true)
      else -> absent()
    }
  }

  /** This assignee with no check-in. */
  private fun AttendanceVolunteer.absent() =
      copy(checkedInAt = null, checkInPending = false, checkedInManually = false)

  companion object {
    /**
     * How long a manual check-in waits for the screen before it is sent anyway. Longer than a short
     * snackbar, even one an accessibility setting has lengthened, so it never cuts an "Undo" short.
     */
    const val UNDO_TIMEOUT_MILLIS = 30_000L

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
