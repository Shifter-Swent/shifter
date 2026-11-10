// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.mission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionLocation
import com.swent.shifter.model.mission.MissionOverlapException
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.mission.MissionRepositoryException
import com.swent.shifter.model.mission.VolunteersBelowAssigneesException
import com.swent.shifter.model.mission.isCompleted
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.ui.map.formatCoordinate
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
 * An existing mission of the same team whose slot overlaps the one being saved, as local date-times
 * in the clock's zone.
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
 * State of the mission form, which adds a mission or edits one.
 *
 * @property event the event the mission is added to, null while it loads or when loading failed.
 * @property notAllowed whether the user may not add or edit this mission, being neither the event's
 *   organizer nor the manager of its team. The form is then not shown.
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
 * @property eventPosition where the event takes place, if known: the map opens there when the
 *   mission has no point yet.
 * @property isPickingLocation whether the map to pick the location on is open.
 * @property locationDraft the point picked on the open map, not used until it is confirmed.
 * @property errors the current validation errors. Empty until the organizer or a manager first
 *   tries to submit, so an untouched form is not shown in red; from then on it is kept up to date
 *   on every edit.
 * @property conflict the mission of the same team the last submit overlapped, null otherwise.
 * @property createdMission the persisted mission once it was saved, created or edited, null before.
 * @property createdMissionHandled whether the screen already reacted to [createdMission], e.g. by
 *   going back, so it does not react again after a recomposition or configuration change.
 * @property isEditing whether the form edits an existing mission rather than adding one. Its team
 *   and its assignees are then kept as they are.
 * @property assigneeCount how many people are assigned to the mission being edited.
 * @property missionLoaded whether the mission being edited was read. Until then the form is not
 *   shown, so nothing typed is overwritten when it arrives.
 * @property missionNotFound whether the mission being edited no longer exists.
 * @property missionCompleted whether the mission being edited is completed, closed by hand or past
 *   its end, so it can no longer be changed.
 * @property slotNotEditable whether the slot of the mission being edited lasts a day or more, which
 *   the form, a day and two times, cannot show without shortening it.
 * @property belowAssignees the number of people assigned when the last save asked for fewer
 *   volunteers than that, null otherwise.
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
    val eventPosition: GeoPosition? = null,
    val isPickingLocation: Boolean = false,
    val locationDraft: GeoPosition? = null,
    val errors: Set<MissionFormError> = emptySet(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val conflict: MissionConflict? = null,
    val createdMission: Mission? = null,
    val createdMissionHandled: Boolean = false,
    val isEditing: Boolean = false,
    val assigneeCount: Int = 0,
    val missionLoaded: Boolean = false,
    val missionNotFound: Boolean = false,
    val missionCompleted: Boolean = false,
    val slotNotEditable: Boolean = false,
    val belowAssignees: Int? = null,
) {
  /** The error to show under [field], or null when it is valid. */
  fun errorFor(field: MissionFormField): MissionFormError? = errors.firstOrNull {
    it.field == field
  }

  /**
   * Whether the form can be submitted: the event is known, the user may add or edit this mission,
   * the mission being edited can still change, nothing is being saved and the mission has not been
   * saved yet, so a second tap before leaving the screen cannot save it twice.
   */
  val canSubmit: Boolean
    get() =
        event != null &&
            !notAllowed &&
            !isSaving &&
            createdMission == null &&
            !missionNotFound &&
            !missionCompleted &&
            !slotNotEditable

  /** The lowest need the stepper may go down to: one, or the number of people already assigned. */
  val minVolunteers: Int
    get() = maxOf(MIN_VOLUNTEERS, if (isEditing) assigneeCount else assignees.selectedIds.size)

  /** Whether the chosen end time falls on the day after the mission starts. */
  val endsNextDay: Boolean
    get() = startTime != null && endTime != null && endTime.isBefore(startTime)

  /** Whether the user started to give a location, which then has to be complete. */
  val hasLocation: Boolean
    get() = address.isNotBlank() || latitude.isNotBlank() || longitude.isNotBlank()

  /** The point the typed coordinates give, or null while either of them is missing or invalid. */
  val position: GeoPosition?
    get() {
      val lat = parseCoordinate(latitude, GeoPosition.LATITUDE_RANGE) ?: return null
      val lng = parseCoordinate(longitude, GeoPosition.LONGITUDE_RANGE) ?: return null
      return GeoPosition(lat, lng)
    }

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
 * Holds the mission form of the event [eventId], validates it and creates the mission, or saves the
 * changes of the mission [missionId], through [missionRepository].
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
 * @param missionId the mission to edit, or null to add a new one. The form then opens filled with
 *   it and saves the changes with [MissionRepository.updateMission].
 */
class AddMissionViewModel(
    private val eventId: String,
    private val missionRepository: MissionRepository,
    private val eventRepository: EventRepository,
    private val teamRepository: TeamRepository,
    private val assigneeCandidates: AssigneeCandidates,
    private val currentUserId: () -> String?,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val missionId: String? = null,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AddMissionUiState(isEditing = missionId != null))
  val uiState: StateFlow<AddMissionUiState> = _uiState.asStateFlow()

  /** The mission being edited as stored, whose team, assignees and creation time are kept. */
  private var edited: Mission? = null

  /** Becomes true on the first submit, after which errors follow every edit. */
  private var submitAttempted = false

  /** Loads the candidates of the selected team; replaced when the team changes. */
  private var candidatesJob: Job? = null

  /** The point of the form when the map was opened, so confirming it unmoved changes nothing. */
  private var pickerStart: GeoPosition? = null

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
        if (missionId != null) {
          val mission = missionRepository.getMission(eventId, missionId)
          showMission(mission, period, teams, isOrganizer, event.location.position)
        } else {
          showNewMissionForm(period, teams, isOrganizer, event.location.position)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _uiState.update { it.copy(isLoadingEvent = false, loadFailed = true) }
      }
    }
  }

  /**
   * Opens the form of a new mission of the event taking place over [period], then loads who may be
   * assigned to it, unless the user may not add missions.
   */
  private fun showNewMissionForm(
      period: EventPeriod,
      teams: List<TeamOption>,
      isOrganizer: Boolean,
      eventPosition: GeoPosition?,
  ) {
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
          eventPosition = eventPosition,
          // Most events last a single day, which is then the only one to pick.
          day = it.day ?: period.start.toLocalDate(),
      )
    }
    if (!_uiState.value.notAllowed) loadCandidates()
  }

  /**
   * Fills the form with the [mission] being edited, or tells it no longer exists. Only its
   * organizer, or the manager of its team, may change it; its assignees are kept, so their
   * candidates are not loaded.
   */
  private fun showMission(
      mission: Mission?,
      period: EventPeriod,
      teams: List<TeamOption>,
      isOrganizer: Boolean,
      eventPosition: GeoPosition?,
  ) {
    edited = mission
    submitAttempted = false
    _uiState.update { state ->
      // A retry starts over: nothing of a previous attempt is kept.
      val loaded =
          state.copy(
              event = period,
              isLoadingEvent = false,
              loadFailed = false,
              teams = teams,
              canChooseGeneral = isOrganizer,
              eventPosition = eventPosition,
              assignees = AssigneeSelection(),
              missionLoaded = true,
              missionNotFound = false,
              missionCompleted = false,
              slotNotEditable = false,
              belowAssignees = null,
              errors = emptySet(),
          )
      if (mission == null) {
        return@update loaded.copy(
            notAllowed = !isOrganizer && teams.isEmpty(),
            missionNotFound = true,
        )
      }
      val start = LocalDateTime.ofInstant(mission.startAt, clock.zone)
      val end = LocalDateTime.ofInstant(mission.endAt, clock.zone)
      val location = mission.location.toFields()
      loaded.copy(
          notAllowed = !isOrganizer && teams.none { it.id == mission.teamId },
          title = mission.title,
          description = mission.description,
          teamId = mission.teamId,
          volunteersNeeded = mission.volunteersNeeded,
          assigneeCount = mission.assigneeIds.size,
          day = start.toLocalDate(),
          startTime = start.toLocalTime(),
          endTime = end.toLocalTime(),
          address = location.address,
          latitude = location.latitude,
          longitude = location.longitude,
          missionCompleted = isCompleted(mission.completed, mission.endAt, clock.instant()),
          // A day and two times hold a slot of less than a day, ending the same day or the next.
          slotNotEditable =
              !end.isBefore(start.plusDays(1)) ||
                  end.toLocalDate().isAfter(start.toLocalDate().plusDays(1)),
      )
    }
  }

  /** The address and coordinates of a location as the form shows them, empty for none. */
  private data class LocationFields(
      val address: String = "",
      val latitude: String = "",
      val longitude: String = "",
  )

  private fun MissionLocation?.toFields(): LocationFields =
      if (this == null) LocationFields()
      else
          LocationFields(
              address = address,
              latitude = formatCoordinate(position.latitude),
              longitude = formatCoordinate(position.longitude),
          )

  fun onTitleChange(title: String) = edit { it.copy(title = title) }

  fun onDescriptionChange(description: String) = edit { it.copy(description = description) }

  /**
   * Selects the team [teamId], or "General" when null. An unknown team is ignored, and so is
   * "General" when the user may not choose it.
   */
  fun onTeamChange(teamId: String?) {
    val form = _uiState.value
    // An edited mission keeps its team, and so its assignees.
    if (form.isEditing) return
    if (teamId == null && !form.canChooseGeneral) return
    if (teamId != null && form.teams.none { it.id == teamId }) return
    edit { it.copy(teamId = teamId) }
    // The people picked belong to the previous team, so the selection starts over.
    if (teamId != form.teamId) loadCandidates()
  }

  /**
   * Picks or unpicks the candidate [id], never more people than the mission needs. Ignored when
   * editing: the assignees of an existing mission are changed from the Overview.
   */
  fun onToggleAssignee(id: String) {
    if (_uiState.value.isEditing) return
    edit { it.copy(assignees = it.assignees.toggle(id, it.volunteersNeeded)) }
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

  /**
   * Opens the map on the point the coordinates give. With no complete point, e.g. a latitude typed
   * and not the longitude yet, it opens on the event instead, and the point picked replaces both.
   * Ignored until the event is known, as the map would not know where it takes place.
   */
  fun onPickLocation() {
    if (_uiState.value.event == null) return
    pickerStart = _uiState.value.position
    _uiState.update { it.copy(isPickingLocation = true, locationDraft = pickerStart) }
  }

  /** Moves the point picked on the open map, without touching the form yet. */
  fun onLocationDraftChange(position: GeoPosition) {
    _uiState.update { if (it.isPickingLocation) it.copy(locationDraft = position) else it }
  }

  /**
   * Closes the map and fills the coordinates with the point picked. A point left where the map
   * opened is not written again: the coordinates typed keep their precision, rather than being
   * rounded to the decimals the map gives.
   */
  fun onConfirmLocation() {
    val draft = _uiState.value.locationDraft
    if (draft == null || draft == pickerStart) {
      onCancelLocationPicker()
      return
    }
    edit {
      it.copy(
          latitude = formatCoordinate(draft.latitude),
          longitude = formatCoordinate(draft.longitude),
          isPickingLocation = false,
          locationDraft = null,
      )
    }
  }

  /** Closes the map and leaves the coordinates as they were. */
  fun onCancelLocationPicker() {
    _uiState.update { it.copy(isPickingLocation = false, locationDraft = null) }
  }

  /** Empties the address and the coordinates: the mission is then added with no location. */
  fun onClearLocation() = edit { it.copy(address = "", latitude = "", longitude = "") }

  /**
   * Validates the form and, when it is valid, creates the mission, or saves the changes of the one
   * being edited. Ignored until it can submit.
   */
  fun addMission() {
    val form = _uiState.value
    if (!form.canSubmit) return
    submitAttempted = true
    val errors = validate(form)
    if (errors.isNotEmpty()) {
      _uiState.update { it.copy(errors = errors, saveFailed = false, conflict = null) }
      return
    }
    edited?.let {
      saveChanges(it, form)
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
        _uiState.update { it.copy(isSaving = false, conflict = e.toConflict()) }
      } catch (_: Exception) {
        _uiState.update { it.copy(isSaving = false, saveFailed = true) }
      }
    }
  }

  /**
   * Saves the details of the validated [form] on the [stored] mission, which keeps its id, team,
   * assignees and creation time.
   */
  private fun saveChanges(stored: Mission, form: AddMissionUiState) {
    val (startAt, endAt) =
        missionSlot(
            checkNotNull(form.day),
            checkNotNull(form.startTime),
            checkNotNull(form.endTime),
        )
    val changed =
        stored.copy(
            title = form.title.trim(),
            description = form.description.trim(),
            volunteersNeeded = form.volunteersNeeded,
            startAt = startAt.atZone(clock.zone).toInstant(),
            endAt = endAt.atZone(clock.zone).toInstant(),
            // Unchanged fields keep the stored point, not its rounding to the decimals shown.
            location =
                if (form.locationFields() == stored.location.toFields()) stored.location
                else form.toLocation(),
        )
    _uiState.update {
      it.copy(
          errors = emptySet(),
          isSaving = true,
          saveFailed = false,
          conflict = null,
          belowAssignees = null,
      )
    }
    viewModelScope.launch {
      try {
        missionRepository.updateMission(changed)
        _uiState.update { it.copy(isSaving = false, createdMission = changed) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: MissionOverlapException) {
        _uiState.update { it.copy(isSaving = false, conflict = e.toConflict()) }
      } catch (e: VolunteersBelowAssigneesException) {
        _uiState.update {
          it.copy(
              isSaving = false,
              belowAssignees = e.assigneeCount,
              // The need goes back up to the people assigned, the lowest it may be.
              volunteersNeeded = maxOf(it.volunteersNeeded, e.assigneeCount),
              assigneeCount = e.assigneeCount,
          )
        }
      } catch (_: MissionRepositoryException.NotFound) {
        _uiState.update { it.copy(isSaving = false, missionNotFound = true) }
      } catch (_: Exception) {
        _uiState.update { it.copy(isSaving = false, saveFailed = true) }
      }
    }
  }

  private fun MissionOverlapException.toConflict() =
      MissionConflict(
          title = conflictingMission.title,
          start = LocalDateTime.ofInstant(conflictingMission.startAt, clock.zone),
          end = LocalDateTime.ofInstant(conflictingMission.endAt, clock.zone),
      )

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
      val changed = change(state).copy(saveFailed = false, conflict = null, belowAssignees = null)
      if (submitAttempted) changed.copy(errors = validate(changed)) else changed
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
      if (parseCoordinate(form.latitude, GeoPosition.LATITUDE_RANGE) == null) {
        add(MissionFormError.LATITUDE_INVALID)
      }
      if (parseCoordinate(form.longitude, GeoPosition.LONGITUDE_RANGE) == null) {
        add(MissionFormError.LONGITUDE_INVALID)
      }
    }
  }

  /** The address and coordinates typed, to compare with those the form opened with. */
  private fun AddMissionUiState.locationFields() = LocationFields(address, latitude, longitude)

  /** The location of a validated [AddMissionUiState], or null when none was given. */
  private fun AddMissionUiState.toLocation(): MissionLocation? =
      if (!hasLocation) null
      else
          MissionLocation(
              address = address.trim(),
              position =
                  GeoPosition(
                      checkNotNull(parseCoordinate(latitude, GeoPosition.LATITUDE_RANGE)),
                      checkNotNull(parseCoordinate(longitude, GeoPosition.LONGITUDE_RANGE)),
                  ),
          )

  private fun EventPeriod.containsSlot(day: LocalDate, start: LocalTime, end: LocalTime): Boolean {
    val (startAt, endAt) = missionSlot(day, start, end)
    return contains(startAt, endAt)
  }

  companion object {
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
        missionId: String? = null,
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
            missionId,
        )
      }
    }
  }
}
