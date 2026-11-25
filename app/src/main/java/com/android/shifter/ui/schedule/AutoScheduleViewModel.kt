// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.mission.AssigneesAboveVolunteersException
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionCompletedException
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.mission.MissionRepositoryException
import com.swent.shifter.model.mission.isCompleted
import com.swent.shifter.model.schedule.ScheduleProposal
import com.swent.shifter.model.schedule.proposeSchedule
import com.swent.shifter.model.schedule.scheduleCandidates
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.model.user.UserRepository
import com.swent.shifter.model.user.displayNames
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Prepares the proposed schedule of the event [eventId] and saves it once accepted.
 *
 * It reads the missions, the teams and the requests of the event, runs [proposeSchedule] once for
 * the whole event, and shows every mission but the completed ones with the names of who is on it
 * and who the proposal adds; the screen picks the day itself. Also reads the event's period from
 * [eventRepository], for the days the screen offers.
 */
class AutoScheduleViewModel(
    private val eventId: String,
    private val missionRepository: MissionRepository,
    private val teamRepository: TeamRepository,
    private val membershipRequestRepository: MembershipRequestRepository,
    private val userRepository: UserRepository,
    private val eventRepository: EventRepository,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AutoScheduleUiState())
  val uiState: StateFlow<AutoScheduleUiState> = _uiState.asStateFlow()

  // Who the proposal shown adds, by mission id: what Accept saves. Set with the missions shown,
  // only by a load that succeeds, so a failed one never leaves a proposal nobody sees.
  private var proposed: Map<String, List<String>> = emptyMap()

  // The load running, cancelled by the next one so an older answer never overwrites a newer one.
  private var loadJob: Job? = null

  init {
    load()
  }

  /**
   * Prepares the proposal again, after a failed load or a save that found the missions changed.
   * Ignored while the proposal is saved, so the save ends on the proposal it started from.
   */
  fun retry() {
    if (_uiState.value.isSaving) return
    load()
  }

  /**
   * Saves the people the proposal adds, for every day at once, then prepares it again, so they show
   * as already assigned. Ignored unless [AutoScheduleUiState.canAccept].
   *
   * A mission of the proposal that started while the screen was open fails it as
   * [ScheduleSaveError.OUTDATED] without saving anything: nobody is proposed for a mission already
   * running.
   */
  fun accept() {
    val state = _uiState.value
    if (!state.canAccept) return
    val additions = proposed
    val at = now()
    if (state.missions.any { it.proposed.isNotEmpty() && at >= it.startAt }) {
      _uiState.update { it.copy(saveError = ScheduleSaveError.OUTDATED) }
      return
    }
    _uiState.update { it.copy(isSaving = true, saveError = null) }
    viewModelScope.launch {
      try {
        missionRepository.addAssignees(eventId, additions)
        _uiState.update { it.copy(isSaving = false, saved = true) }
        load()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.update { it.copy(isSaving = false, saveError = saveErrorOf(e)) }
      }
    }
  }

  /** Reads everything and runs the proposal again, keeping the missions shown if it fails. */
  private fun load() {
    loadJob?.cancel()
    _uiState.update { it.copy(isLoading = true, loadFailed = false, saveError = null) }
    loadJob = viewModelScope.launch {
      // Alongside the proposal, which never waits for it, and kept as soon as it is read, even if
      // the proposal fails.
      launch {
        readEventPeriod()?.let { period -> _uiState.update { it.copy(eventPeriod = period) } }
      }
      try {
        // Read once, so the proposal and the missions shown agree on which ones are completed.
        val now = now()
        // Every mission, completed ones included: they still book their people at their time.
        val missions = missionRepository.getMissionsByEvent(eventId)
        val teams = teamRepository.getTeamsByEvent(eventId)
        val requests = membershipRequestRepository.getMembershipRequestsByEId(eventId)
        val proposal = proposeSchedule(missions, scheduleCandidates(requests, teams), now)
        val shown =
            missions
                .filterNot { isCompleted(it.completed, it.endAt, now) }
                .sortedWith(compareBy({ it.startAt }, { it.id }))
        val names = readNames(shown.flatMap { it.assigneeIds + proposal.proposed[it.id].orEmpty() })
        val teamNames = teams.associate { it.id to it.name }
        proposed = proposal.proposed
        _uiState.update { state ->
          state.copy(
              isLoading = false,
              missions = shown.map { it.toProposed(proposal, teamNames, names) },
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _uiState.update { it.copy(isLoading = false, loadFailed = true) }
      }
    }
  }

  /**
   * When the event starts and ends, or null when it cannot be read: the days then run from the
   * first mission to the last, and a period already known is kept.
   */
  private suspend fun readEventPeriod(): ClosedRange<Instant>? =
      try {
        eventRepository.getEvent(eventId)?.let { it.startAt..it.endAt }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        null
      }

  /**
   * The names of [userIds], or none when they cannot be read: the chips then say so, and the people
   * still count, rather than the whole proposal failing for its names.
   */
  private suspend fun readNames(userIds: List<String>): Map<String, String?> =
      try {
        userRepository.displayNames(userIds)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        emptyMap()
      }

  companion object {
    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(
        eventId: String,
        missionRepository: MissionRepository,
        teamRepository: TeamRepository,
        membershipRequestRepository: MembershipRequestRepository,
        userRepository: UserRepository,
        eventRepository: EventRepository,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        AutoScheduleViewModel(
            eventId,
            missionRepository,
            teamRepository,
            membershipRequestRepository,
            userRepository,
            eventRepository,
        )
      }
    }
  }
}

/**
 * Why saving the proposal failed, as `MissionRepository.addAssignees` reports it. A mission filled,
 * completed or deleted meanwhile makes it outdated, and too many missions make it too large;
 * anything else may work on a second try, including a refused write, which the contract has no
 * better value for.
 */
private fun saveErrorOf(e: Exception): ScheduleSaveError =
    when (e) {
      // Before IllegalArgumentException, which AssigneesAboveVolunteersException extends.
      is AssigneesAboveVolunteersException,
      is MissionCompletedException,
      is MissionRepositoryException.NotFound -> ScheduleSaveError.OUTDATED
      is IllegalArgumentException -> ScheduleSaveError.TOO_LARGE
      else -> ScheduleSaveError.RETRY
    }

/**
 * This mission as the proposed schedule shows it, with the [names] of who is on it and who the
 * [proposal] adds. A name that could not be read is null, and still counts.
 */
private fun Mission.toProposed(
    proposal: ScheduleProposal,
    teamNames: Map<String, String>,
    names: Map<String, String?>,
) =
    ProposedMission(
        id = id,
        title = title,
        startAt = startAt,
        endAt = endAt,
        isGeneral = teamId == null,
        teamName = teamId?.let { teamNames[it] },
        volunteersNeeded = volunteersNeeded,
        assigned = assigneeIds.map { names[it] },
        proposed = proposal.proposed[id].orEmpty().map { names[it] },
        missing = proposal.unfilled[id] ?: 0,
    )
