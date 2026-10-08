// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.mission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

  /** Whether a mission on [day] from [start] to [end] takes place within the event. */
  fun contains(day: LocalDate, start: LocalTime, end: LocalTime): Boolean =
      !day.atTime(start).isBefore(this.start) && !day.atTime(end).isAfter(this.end)
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
  DESCRIPTION_EMPTY(MissionFormField.DESCRIPTION),
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
) {
  /** The error to show under [field], or null when it is valid. */
  fun errorFor(field: MissionFormField): MissionFormError? = errors.firstOrNull {
    it.field == field
  }

  /** Whether the form can be submitted: the event is known and nothing is being saved. */
  val canSubmit: Boolean
    get() = event != null && !isSaving

  companion object {
    /** A mission records a need for at least one person. */
    const val MIN_VOLUNTEERS = 1
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
    val day = form.day ?: return
    val mission =
        Mission(
            eventId = eventId,
            title = form.title.trim(),
            description = form.description.trim(),
            teamId = form.teamId,
            volunteersNeeded = form.volunteersNeeded,
            startAt = day.atTime(form.startTime ?: return).atZone(clock.zone).toInstant(),
            endAt = day.atTime(form.endTime ?: return).atZone(clock.zone).toInstant(),
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

  private fun edit(change: (AddMissionUiState) -> AddMissionUiState) {
    _uiState.update { state ->
      val edited = change(state).copy(saveFailed = false)
      if (submitAttempted) edited.copy(errors = validate(edited)) else edited
    }
  }

  private fun validate(form: AddMissionUiState): Set<MissionFormError> = buildSet {
    if (form.title.isBlank()) add(MissionFormError.TITLE_EMPTY)
    if (form.description.isBlank()) add(MissionFormError.DESCRIPTION_EMPTY)
    if (form.day == null) add(MissionFormError.DAY_MISSING)

    val start = form.startTime
    val end = form.endTime
    when {
      start == null -> add(MissionFormError.START_MISSING)
      end == null -> add(MissionFormError.END_MISSING)
      !end.isAfter(start) -> add(MissionFormError.END_NOT_AFTER_START)
      form.day != null && form.event?.contains(form.day, start, end) == false ->
          add(MissionFormError.OUTSIDE_EVENT)
    }
  }
}
