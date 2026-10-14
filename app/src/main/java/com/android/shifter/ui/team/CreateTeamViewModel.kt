// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepository
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A form field of the team creation screen. */
enum class TeamFormField {
  NAME,
  ICON,
  DESCRIPTION,
}

/** Why a field of the team creation form is invalid. Each error belongs to exactly one [field]. */
enum class TeamFormError(val field: TeamFormField) {
  NAME_EMPTY(TeamFormField.NAME),
  NAME_TOO_LONG(TeamFormField.NAME),
  ICON_MISSING(TeamFormField.ICON),
  DESCRIPTION_EMPTY(TeamFormField.DESCRIPTION),
  DESCRIPTION_TOO_LONG(TeamFormField.DESCRIPTION),
}

/**
 * State of the team creation screen.
 *
 * @property icon identifier of the icon the team is shown with, as stored in [Team.icon], null
 *   until the organizer picks one. Which identifiers exist, and what each one looks like, is the
 *   screen's business: the form only carries the chosen one through to the backend.
 * @property description what the team does, as the organizer wrote it.
 * @property errors the current validation errors. Empty until the organizer first tries to submit,
 *   so an untouched form is not shown in red; from then on it is kept up to date on every edit.
 * @property createdTeam the persisted team once creation succeeded, null before.
 * @property createdTeamHandled whether the screen already reacted to [createdTeam], e.g. by going
 *   back, so it does not react again after a recomposition or configuration change.
 */
data class CreateTeamUiState(
    val name: String = "",
    val icon: String? = null,
    val description: String = "",
    val errors: Set<TeamFormError> = emptySet(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val createdTeam: Team? = null,
    val createdTeamHandled: Boolean = false,
) {
  /** The error to show under [field], or null when it is valid. */
  fun errorFor(field: TeamFormField): TeamFormError? = errors.firstOrNull { it.field == field }

  /**
   * Whether the form can be submitted: nothing is being saved and the team has not been created
   * yet, so a second tap before leaving the screen cannot create it twice.
   */
  val canSubmit: Boolean
    get() = !isSaving && createdTeam == null
}

/**
 * Holds the team creation form of the event [eventId], validates it and creates the team through
 * [teamRepository].
 *
 * The event itself is never loaded: nothing the form validates depends on it, so the id is all this
 * ViewModel needs and it stays free of [com.swent.shifter.model.event.EventRepository].
 *
 * The form is the one of the designs: a name, an icon and a short description, nothing else. A team
 * is created without a manager and without members, which the organizer assigns afterwards from the
 * team itself; its staffing need and its check-in zone are configured on their own later, for the
 * same reason.
 *
 * @param clock source of the current time, used to stamp [Team.createdAt]. Inject a fixed clock in
 *   tests.
 */
class CreateTeamViewModel(
    private val eventId: String,
    private val teamRepository: TeamRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(CreateTeamUiState())
  val uiState: StateFlow<CreateTeamUiState> = _uiState.asStateFlow()

  /** Becomes true on the first submit, after which errors follow every edit. */
  private var submitAttempted = false

  fun onNameChange(name: String) = edit { it.copy(name = name) }

  fun onIconChange(icon: String) = edit { it.copy(icon = icon) }

  fun onDescriptionChange(description: String) = edit { it.copy(description = description) }

  /** Validates the form and, when it is valid, creates the team. Ignored until it can submit. */
  fun createTeam() {
    val form = _uiState.value
    if (!form.canSubmit) return
    submitAttempted = true
    val errors = validate(form)
    if (errors.isNotEmpty()) {
      _uiState.update { it.copy(errors = errors, saveFailed = false) }
      return
    }
    // Validation guarantees a non-blank icon has been chosen.
    val team =
        Team(
            eventId = eventId,
            name = form.name.trim(),
            description = form.description.trim(),
            icon = checkNotNull(form.icon).trim(),
            managerId = null,
            memberIds = emptyList(),
            volunteersNeeded = null,
            checkInZone = null,
            createdAt = clock.instant(),
        )
    _uiState.update { it.copy(errors = emptySet(), isSaving = true, saveFailed = false) }
    viewModelScope.launch {
      try {
        val created = teamRepository.createTeam(team)
        _uiState.update { it.copy(isSaving = false, createdTeam = created) }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _uiState.update { it.copy(isSaving = false, saveFailed = true) }
      }
    }
  }

  /**
   * Marks [CreateTeamUiState.createdTeam] as handled once the screen reacted to it. The team itself
   * is kept, so the form still cannot create it a second time.
   */
  fun onTeamCreatedHandled() {
    _uiState.update { it.copy(createdTeamHandled = true) }
  }

  private fun edit(change: (CreateTeamUiState) -> CreateTeamUiState) {
    _uiState.update { state ->
      val edited = change(state).copy(saveFailed = false)
      if (submitAttempted) edited.copy(errors = validate(edited)) else edited
    }
  }

  /** The limits apply to the trimmed texts, so padding cannot push a valid entry over them. */
  private fun validate(form: CreateTeamUiState): Set<TeamFormError> = buildSet {
    when {
      form.name.isBlank() -> add(TeamFormError.NAME_EMPTY)
      form.name.trim().length > NAME_MAX_LENGTH -> add(TeamFormError.NAME_TOO_LONG)
    }
    if (form.icon.isNullOrBlank()) add(TeamFormError.ICON_MISSING)
    when {
      form.description.isBlank() -> add(TeamFormError.DESCRIPTION_EMPTY)
      form.description.trim().length > DESCRIPTION_MAX_LENGTH ->
          add(TeamFormError.DESCRIPTION_TOO_LONG)
    }
  }

  companion object {
    /** Longest team name accepted, so it fits on team cards and in lists. */
    const val NAME_MAX_LENGTH = 50

    /** Longest description accepted: the form asks for a short one. */
    const val DESCRIPTION_MAX_LENGTH = 200

    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(
        eventId: String,
        teamRepository: TeamRepository,
        clock: Clock = Clock.systemDefaultZone(),
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { CreateTeamViewModel(eventId, teamRepository, clock) }
    }
  }
}
