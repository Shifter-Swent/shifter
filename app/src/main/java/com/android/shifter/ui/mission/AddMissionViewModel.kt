// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.mission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionLocation
import com.swent.shifter.model.mission.MissionOverlapException
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A team ("pôle") a mission can be attached to.
 *
 * Only what the form shows, so the screen does not depend on the full team model.
 */
data class TeamOption(val id: String, val name: String)

/** The [TeamOption] of this team. */
internal fun Team.toOption() = TeamOption(id = id, name = name)

/**
 * The part of the event the form needs: its name, and the period a mission has to fit in, as local
 * date-times in the clock's zone.
 */
data class EventPeriod(val title: String, val start: LocalDateTime, val end: LocalDateTime) {

  /** Whether a mission from [start] to [end] takes place within the event. */
  fun contains(start: LocalDateTime, end: LocalDateTime): Boolean =
      !start.isBefore(this.start) && !end.isAfter(this.end)
}

/**
 * When a mission starting on [day] at [start] and ending at [end] takes place. An end earlier than
 * the start is on the next day, so a night slot such as 22:00 to 02:00 can be entered.
 */
internal fun missionSlot(
    day: LocalDate,
    start: LocalTime,
    end: LocalTime,
): Pair<LocalDateTime, LocalDateTime> {
  val endDay = if (end.isBefore(start)) day.plusDays(1) else day
  return day.atTime(start) to endDay.atTime(end)
}

/**
 * The coordinate typed in [text], or null when it is not a number within [range]. A decimal comma
 * is accepted as well as a point, as typed on many keyboards.
 */
internal fun parseCoordinate(text: String, range: ClosedFloatingPointRange<Double>): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in range }

/**
 * An existing mission of the same team whose slot overlaps the one being created, as local
 * date-times in the clock's zone.
 */
data class MissionConflict(val title: String, val start: LocalDateTime, val end: LocalDateTime)

/** A form field of the mission creation screen. */
enum class MissionFormField {
  TITLE,
  DESCRIPTION,
  DAY,
  SCHEDULE,
  ADDRESS,
  LATITUDE,
  LONGITUDE,
}

/**
 * Why a field of the mission creation form is invalid. Each error belongs to exactly one [field].
 */
enum class MissionFormError(val field: MissionFormField) {
  TITLE_EMPTY(MissionFormField.TITLE),
  TITLE_TOO_LONG(MissionFormField.TITLE),
  DESCRIPTION_EMPTY(MissionFormField.DESCRIPTION),
  DESCRIPTION_TOO_LONG(MissionFormField.DESCRIPTION),
  DAY_MISSING(MissionFormField.DAY),
  START_MISSING(MissionFormField.SCHEDULE),
  END_MISSING(MissionFormField.SCHEDULE),
  END_NOT_AFTER_START(MissionFormField.SCHEDULE),
  OUTSIDE_EVENT(MissionFormField.SCHEDULE),
  ADDRESS_MISSING(MissionFormField.ADDRESS),
  LATITUDE_INVALID(MissionFormField.LATITUDE),
  LONGITUDE_INVALID(MissionFormField.LONGITUDE),
}

/**
 * State of the mission creation screen.
 *
 * @property event the event the mission is added to, null while it loads or when loading failed.
 * @property notAllowed whether the user may not add missions to this event, being neither its
 *   organizer nor the manager of one of its teams. The form is then not shown.
 * @property teams the teams the user may attach the mission to: every team of the event for its
 *   organizer, only the teams they manage for a manager.
 * @property canChooseGeneral whether the mission may belong to no team. Only the organizer may
 *   create such missions; a manager creates them for a team they manage.
 * @property teamId id of the selected team, or null for "General", the missions of no team.
 * @property assignees who may be assigned to the mission and who is picked, reloaded and cleared
 *   whenever the team changes. Never more people than [volunteersNeeded].
 * @property address the address of the optional location, as typed. The location is set as soon as
 *   one of [address], [latitude] or [longitude] is filled, and then all three are required.
 * @property latitude the latitude of the optional location, as typed.
 * @property longitude the longitude of the optional location, as typed.
 * @property errors the current validation errors. Empty until the organizer or a manager first
 *   tries to submit, so an untouched form is not shown in red; from then on it is kept up to date
 *   on every edit.
 * @property conflict the mission of the same team the last submit overlapped, null otherwise.
 * @property createdMission the persisted mission once creation succeeded, null before.
 * @property createdMissionHandled whether the screen already reacted to [createdMission], e.g. by
 *   going back, so it does not react again after a recomposition or configuration change.
 */
data class AddMissionUiState(
    val event: EventPeriod? = null,
    val isLoadingEvent: Boolean = true,
    val loadFailed: Boolean = false,
    val notAllowed: Boolean = false,
    val teams: List<TeamOption> = emptyList(),
    val canChooseGeneral: Boolean = true,
    val title: String = "",
    val description: String = "",
    val teamId: String? = null,
    val volunteersNeeded: Int = MIN_VOLUNTEERS,
    val assignees: AssigneeSelection = AssigneeSelection(),
    val day: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val address: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val errors: Set<MissionFormError> = emptySet(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val conflict: MissionConflict? = null,
    val createdMission: Mission? = null,
    val createdMissionHandled: Boolean = false,
) {
  /** The error to show under [field], or null when it is valid. */
  fun errorFor(field: MissionFormField): MissionFormError? = errors.firstOrNull {
    it.field == field
  }

  /**
   * Whether the form can be submitted: the event is known, the user may add missions to it, nothing
   * is being saved and the mission has not been created yet, so a second tap before leaving the
   * screen cannot create it twice.
   */
  val canSubmit: Boolean
    get() = event != null && !notAllowed && !isSaving && createdMission == null

  /** The lowest need the stepper may go down to: one, or the number of people already assigned. */
  val minVolunteers: Int
    get() = maxOf(MIN_VOLUNTEERS, assignees.selectedIds.size)

  /** Whether the chosen end time falls on the day after the mission starts. */
  val endsNextDay: Boolean
    get() = startTime != null && endTime != null && endTime.isBefore(startTime)

  /** Whether the user started to give a location, which then has to be complete. */
  val hasLocation: Boolean
    get() = address.isNotBlank() || latitude.isNotBlank() || longitude.isNotBlank()

  companion object {
    /** A mission records a need for at least one person. */
    const val MIN_VOLUNTEERS = 1

    /** Longest title accepted, so it fits on mission cards and in lists. Same as for events. */
    const val TITLE_MAX_LENGTH = 80

    /** Longest description accepted. Same as for events. */
    const val DESCRIPTION_MAX_LENGTH = 2000
  }
}

/**
 * Holds the mission creation form of the event [eventId], validates it and creates the mission
 * through [missionRepository].
 *
 * The organizer of the event attaches a mission to any of its teams, or to none ("General"); a
 * manager only to a team they manage. Anyone else may not add missions.
 *
 * The rules let only the organizer and the event's accepted participants read its teams. A manager
 * promoted from the members of a team is one, but nothing enforces it: a manager who is not a
 * participant only sees the load error, until #160 lets managers read the teams they manage.
 *
 * @param assigneeCandidates finds who may be assigned to the mission of the selected team.
 * @param currentUserId returns the signed-in user's id, or null when nobody is signed in.
 * @param clock source of the current time, used to stamp [Mission.createdAt]; its zone is the one
 *   the organizer or manager reads and enters times in. Inject a fixed clock in tests.
 */
class AddMissionViewModel(
    private val eventId: String,
    private val missionRepository: MissionRepository,
    private val eventRepository: EventRepository,
    private val teamRepository: TeamRepository,
    private val assigneeCandidates: AssigneeCandidates,
    private val currentUserId: () -> String?,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(AddMissionUiState())
  val uiState: StateFlow<AddMissionUiState> = _uiState.asStateFlow()

  /** Becomes true on the first submit, after which errors follow every edit. */
  private var submitAttempted = false

  /** Loads the candidates of the selected team; replaced when the team changes. */
  private var candidatesJob: Job? = null

  init {
    loadEvent()
  }

  /**
   * Loads the event the mission belongs to and the teams the user may attach it to. Called again to
   * retry after a failure.
   */
  fun loadEvent() {
    _uiState.update { it.copy(isLoadingEvent = true, loadFailed = false) }
    viewModelScope.launch {
      try {
        val event = eventRepository.getEvent(eventId)
        if (event == null) {
          _uiState.update { it.copy(event = null, isLoadingEvent = false, loadFailed = true) }
          return@launch
        }
        val userId = currentUserId()
        val isOrganizer = userId != null && userId == event.organizerId
        val teams =
            when {
              isOrganizer -> teamRepository.getTeamsByEvent(eventId)
              userId != null -> teamRepository.getTeamsManagedBy(eventId, userId)
              else -> emptyList()
            }.map { it.toOption() }
        val period =
            EventPeriod(
                title = event.title,
                start = LocalDateTime.ofInstant(event.startAt, clock.zone),
                end = LocalDateTime.ofInstant(event.endAt, clock.zone),
            )
        _uiState.update {
          // A manager picks among their own teams, so one of them is selected from the start.
          val teamId =
              it.teamId?.takeIf { id -> teams.any { team -> team.id == id } }
                  ?: if (isOrganizer) null else teams.firstOrNull()?.id
          it.copy(
              event = period,
              isLoadingEvent = false,
              loadFailed = false,
              notAllowed = !isOrganizer && teams.isEmpty(),
              teams = teams,
              canChooseGeneral = isOrganizer,
              teamId = teamId,
              // Most events last a single day, which is then the only one to pick.
              day = it.day ?: period.start.toLocalDate(),
          )
        }
        if (!_uiState.value.notAllowed) loadCandidates()
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _uiState.update { it.copy(isLoadingEvent = false, loadFailed = true) }
      }
    }
  }

  fun onTitleChange(title: String) = edit { it.copy(title = title) }

  fun onDescriptionChange(description: String) = edit { it.copy(description = description) }

  /**
   * Selects the team [teamId], or "General" when null. An unknown team is ignored, and so is
   * "General" when the user may not choose it.
   */
  fun onTeamChange(teamId: String?) {
    val form = _uiState.value
    if (teamId == null && !form.canChooseGeneral) return
    if (teamId != null && form.teams.none { it.id == teamId }) return
    edit { it.copy(teamId = teamId) }
    // The people picked belong to the previous team, so the selection starts over.
    if (teamId != form.teamId) loadCandidates()
  }

  /** Picks or unpicks the candidate [id], never more people than the mission needs. */
  fun onToggleAssignee(id: String) = edit {
    it.copy(assignees = it.assignees.toggle(id, it.volunteersNeeded))
  }

  /** Loads the candidates of the selected team again, after a failure. */
  fun retryLoadAssignees() = loadCandidates()

  fun onIncreaseVolunteers() = edit { it.copy(volunteersNeeded = it.volunteersNeeded + 1) }

  /** Lowers the need by one, never below [AddMissionUiState.minVolunteers]. */
  fun onDecreaseVolunteers() = edit {
    it.copy(volunteersNeeded = maxOf(it.minVolunteers, it.volunteersNeeded - 1))
  }

  fun onDayChange(day: LocalDate) = edit { it.copy(day = day) }

  fun onStartTimeChange(time: LocalTime) = edit { it.copy(startTime = time) }

  fun onEndTimeChange(time: LocalTime) = edit { it.copy(endTime = time) }

  fun onAddressChange(address: String) = edit { it.copy(address = address) }

  fun onLatitudeChange(latitude: String) = edit { it.copy(latitude = latitude) }

  fun onLongitudeChange(longitude: String) = edit { it.copy(longitude = longitude) }

  /** Validates the form and, when it is valid, creates the mission. Ignored until it can submit. */
  fun addMission() {
    val form = _uiState.value
    if (!form.canSubmit) return
    submitAttempted = true
    val errors = validate(form)
    if (errors.isNotEmpty()) {
      _uiState.update { it.copy(errors = errors, saveFailed = false, conflict = null) }
      return
    }
    // Validation guarantees the day and both times are set, and a complete location if any.
    val (startAt, endAt) =
        missionSlot(
            checkNotNull(form.day),
            checkNotNull(form.startTime),
            checkNotNull(form.endTime),
        )
    val mission =
        Mission(
            eventId = eventId,
            title = form.title.trim(),
            description = form.description.trim(),
            teamId = form.teamId,
            volunteersNeeded = form.volunteersNeeded,
            startAt = startAt.atZone(clock.zone).toInstant(),
            endAt = endAt.atZone(clock.zone).toInstant(),
            location = form.toLocation(),
            assigneeIds = form.assignees.selectedIds,
            createdAt = clock.instant(),
        )
    _uiState.update {
      it.copy(errors = emptySet(), isSaving = true, saveFailed = false, conflict = null)
    }
    viewModelScope.launch {
      try {
        val created = missionRepository.createMission(mission)
        _uiState.update { it.copy(isSaving = false, createdMission = created) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: MissionOverlapException) {
        val conflict =
            MissionConflict(
                title = e.conflictingMission.title,
                start = LocalDateTime.ofInstant(e.conflictingMission.startAt, clock.zone),
                end = LocalDateTime.ofInstant(e.conflictingMission.endAt, clock.zone),
            )
        _uiState.update { it.copy(isSaving = false, conflict = conflict) }
      } catch (_: Exception) {
        _uiState.update { it.copy(isSaving = false, saveFailed = true) }
      }
    }
  }

  /** Records that the screen reacted to [AddMissionUiState.createdMission]. */
  fun onMissionAddedHandled() {
    _uiState.update { it.copy(createdMissionHandled = true) }
  }

  /**
   * Clears the selection and loads who may be assigned to a mission of the selected team. A load
   * still running for a previous team is cancelled, so its result never lands on the new one.
   */
  private fun loadCandidates() {
    candidatesJob?.cancel()
    val teamId = _uiState.value.teamId
    _uiState.update { it.copy(assignees = AssigneeSelection(isLoading = true)) }
    candidatesJob = viewModelScope.launch {
      val loaded =
          try {
            AssigneeSelection(candidates = assigneeCandidates.load(eventId, teamId))
          } catch (e: CancellationException) {
            throw e
          } catch (_: Exception) {
            AssigneeSelection(loadFailed = true)
          }
      _uiState.update { it.copy(assignees = loaded) }
    }
  }

  private fun edit(change: (AddMissionUiState) -> AddMissionUiState) {
    _uiState.update { state ->
      val edited = change(state).copy(saveFailed = false, conflict = null)
      if (submitAttempted) edited.copy(errors = validate(edited)) else edited
    }
  }

  private fun validate(form: AddMissionUiState): Set<MissionFormError> = buildSet {
    when {
      form.title.isBlank() -> add(MissionFormError.TITLE_EMPTY)
      form.title.trim().length > AddMissionUiState.TITLE_MAX_LENGTH ->
          add(MissionFormError.TITLE_TOO_LONG)
    }
    when {
      form.description.isBlank() -> add(MissionFormError.DESCRIPTION_EMPTY)
      form.description.trim().length > AddMissionUiState.DESCRIPTION_MAX_LENGTH ->
          add(MissionFormError.DESCRIPTION_TOO_LONG)
    }
    if (form.day == null) add(MissionFormError.DAY_MISSING)

    val start = form.startTime
    val end = form.endTime
    when {
      start == null -> add(MissionFormError.START_MISSING)
      end == null -> add(MissionFormError.END_MISSING)
      // An earlier end is on the next day; only an equal one leaves the mission with no duration.
      end == start -> add(MissionFormError.END_NOT_AFTER_START)
      form.day != null && form.event?.containsSlot(form.day, start, end) == false ->
          add(MissionFormError.OUTSIDE_EVENT)
    }
    if (form.hasLocation) {
      if (form.address.isBlank()) add(MissionFormError.ADDRESS_MISSING)
      if (parseCoordinate(form.latitude, LATITUDE_RANGE) == null) {
        add(MissionFormError.LATITUDE_INVALID)
      }
      if (parseCoordinate(form.longitude, LONGITUDE_RANGE) == null) {
        add(MissionFormError.LONGITUDE_INVALID)
      }
    }
  }

  /** The location of a validated [AddMissionUiState], or null when none was given. */
  private fun AddMissionUiState.toLocation(): MissionLocation? =
      if (!hasLocation) null
      else
          MissionLocation(
              address = address.trim(),
              latitude = checkNotNull(parseCoordinate(latitude, LATITUDE_RANGE)),
              longitude = checkNotNull(parseCoordinate(longitude, LONGITUDE_RANGE)),
          )

  private fun EventPeriod.containsSlot(day: LocalDate, start: LocalTime, end: LocalTime): Boolean {
    val (startAt, endAt) = missionSlot(day, start, end)
    return contains(startAt, endAt)
  }

  companion object {
    private val LATITUDE_RANGE = -90.0..90.0
    private val LONGITUDE_RANGE = -180.0..180.0

    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(
        eventId: String,
        missionRepository: MissionRepository,
        eventRepository: EventRepository,
        teamRepository: TeamRepository,
        assigneeCandidates: AssigneeCandidates,
        currentUserId: () -> String?,
        clock: Clock = Clock.systemDefaultZone(),
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        AddMissionViewModel(
            eventId,
            missionRepository,
            eventRepository,
            teamRepository,
            assigneeCandidates,
            currentUserId,
            clock,
        )
      }
    }
  }
}
