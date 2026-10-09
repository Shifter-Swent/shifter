// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.mission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.coroutines.cancellation.CancellationException
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

/** A form field of the mission creation screen. */
enum class MissionFormField {
  TITLE,
  DESCRIPTION,
  DAY,
  SCHEDULE,
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
}

/**
 * State of the mission creation screen.
 *
 * @property event the event the mission is added to, null while it loads or when loading failed.
 * @property teamId id of the selected team, or null for "General", the missions of no team.
 * @property errors the current validation errors. Empty until the organizer first tries to submit,
 *   so an untouched form is not shown in red; from then on it is kept up to date on every edit.
 * @property createdMission the persisted mission once creation succeeded, null before.
 * @property createdMissionHandled whether the screen already reacted to [createdMission], e.g. by
 *   going back, so it does not react again after a recomposition or configuration change.
 */
data class AddMissionUiState(
    val event: EventPeriod? = null,
    val isLoadingEvent: Boolean = true,
    val loadFailed: Boolean = false,
    val teams: List<TeamOption> = emptyList(),
    val title: String = "",
    val description: String = "",
    val teamId: String? = null,
    val volunteersNeeded: Int = MIN_VOLUNTEERS,
    val day: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val errors: Set<MissionFormError> = emptySet(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val createdMission: Mission? = null,
    val createdMissionHandled: Boolean = false,
) {
  /** The error to show under [field], or null when it is valid. */
  fun errorFor(field: MissionFormField): MissionFormError? = errors.firstOrNull {
    it.field == field
  }

  /**
   * Whether the form can be submitted: the event is known, nothing is being saved and the mission
   * has not been created yet, so a second tap before leaving the screen cannot create it twice.
   */
  val canSubmit: Boolean
    get() = event != null && !isSaving && createdMission == null

  /** Whether the chosen end time falls on the day after the mission starts. */
  val endsNextDay: Boolean
    get() = startTime != null && endTime != null && endTime.isBefore(startTime)

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
 * @param teams the teams of the event the mission can be attached to, besides "General".
 * @param clock source of the current time, used to stamp [Mission.createdAt]; its zone is the one
 *   the organizer reads and enters times in. Inject a fixed clock in tests.
 */
class AddMissionViewModel(
    private val eventId: String,
    private val missionRepository: MissionRepository,
    private val eventRepository: EventRepository,
    // TODO(#86): load the event's teams from TeamRepository instead of injecting them.
    teams: List<TeamOption> = emptyList(),
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(AddMissionUiState(teams = teams))
  val uiState: StateFlow<AddMissionUiState> = _uiState.asStateFlow()

  /** Becomes true on the first submit, after which errors follow every edit. */
  private var submitAttempted = false

  init {
    loadEvent()
  }

  /** Loads the event the mission belongs to. Called again to retry after a failure. */
  fun loadEvent() {
    _uiState.update { it.copy(isLoadingEvent = true, loadFailed = false) }
    viewModelScope.launch {
      try {
        val event = eventRepository.getEvent(eventId)
        val period = event?.let {
          EventPeriod(
              title = it.title,
              start = LocalDateTime.ofInstant(it.startAt, clock.zone),
              end = LocalDateTime.ofInstant(it.endAt, clock.zone),
          )
        }
        _uiState.update {
          it.copy(
              event = period,
              isLoadingEvent = false,
              loadFailed = period == null,
              // Most events last a single day, which is then the only one to pick.
              day = it.day ?: period?.start?.toLocalDate(),
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _uiState.update { it.copy(isLoadingEvent = false, loadFailed = true) }
      }
    }
  }

  fun onTitleChange(title: String) = edit { it.copy(title = title) }

  fun onDescriptionChange(description: String) = edit { it.copy(description = description) }

  /** Selects the team [teamId], or "General" when null. An unknown team is ignored. */
  fun onTeamChange(teamId: String?) {
    if (teamId != null && _uiState.value.teams.none { it.id == teamId }) return
    edit { it.copy(teamId = teamId) }
  }

  fun onIncreaseVolunteers() = edit { it.copy(volunteersNeeded = it.volunteersNeeded + 1) }

  /** Lowers the need by one, never below [AddMissionUiState.MIN_VOLUNTEERS]. */
  fun onDecreaseVolunteers() = edit {
    it.copy(volunteersNeeded = maxOf(AddMissionUiState.MIN_VOLUNTEERS, it.volunteersNeeded - 1))
  }

  fun onDayChange(day: LocalDate) = edit { it.copy(day = day) }

  fun onStartTimeChange(time: LocalTime) = edit { it.copy(startTime = time) }

  fun onEndTimeChange(time: LocalTime) = edit { it.copy(endTime = time) }

  /** Validates the form and, when it is valid, creates the mission. Ignored until it can submit. */
  fun addMission() {
    val form = _uiState.value
    if (!form.canSubmit) return
    submitAttempted = true
    val errors = validate(form)
    if (errors.isNotEmpty()) {
      _uiState.update { it.copy(errors = errors, saveFailed = false) }
      return
    }
    // Validation guarantees the day and both times are set.
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
            createdAt = clock.instant(),
        )
    _uiState.update { it.copy(errors = emptySet(), isSaving = true, saveFailed = false) }
    viewModelScope.launch {
      try {
        val created = missionRepository.createMission(mission)
        _uiState.update { it.copy(isSaving = false, createdMission = created) }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _uiState.update { it.copy(isSaving = false, saveFailed = true) }
      }
    }
  }

  /** Records that the screen reacted to [AddMissionUiState.createdMission]. */
  fun onMissionAddedHandled() {
    _uiState.update { it.copy(createdMissionHandled = true) }
  }

  private fun edit(change: (AddMissionUiState) -> AddMissionUiState) {
    _uiState.update { state ->
      val edited = change(state).copy(saveFailed = false)
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
  }

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
        teams: List<TeamOption> = emptyList(),
        clock: Clock = Clock.systemDefaultZone(),
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { AddMissionViewModel(eventId, missionRepository, eventRepository, teams, clock) }
    }
  }
}
