// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.mission.MAX_MISSIONS_PER_ADDITION
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionCompletedException
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.mission.MissionRepositoryException
import com.swent.shifter.model.mission.isCompleted
import com.swent.shifter.model.schedule.ScheduleCandidate
import com.swent.shifter.model.schedule.ScheduleProposal
import com.swent.shifter.model.schedule.proposeSchedule
import com.swent.shifter.model.schedule.scheduleCandidates
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.model.user.UserRepository
import com.swent.shifter.model.user.displayNames
import com.swent.shifter.ui.mission.AssignState
import com.swent.shifter.ui.mission.AssigneeCandidates
import com.swent.shifter.ui.mission.AssigneeOption
import com.swent.shifter.ui.mission.AssigneeSelection
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
 *
 * A mission the proposal leaves short is finished by hand in the assign dialog, with
 * [assigneeCandidates] as the people who may be picked; see [AutoScheduleUiState.assigning].
 */
class AutoScheduleViewModel(
    private val eventId: String,
    private val missionRepository: MissionRepository,
    private val teamRepository: TeamRepository,
    private val membershipRequestRepository: MembershipRequestRepository,
    private val userRepository: UserRepository,
    private val eventRepository: EventRepository,
    private val assigneeCandidates: AssigneeCandidates,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AutoScheduleUiState())
  val uiState: StateFlow<AutoScheduleUiState> = _uiState.asStateFlow()

  // Who the proposal shown adds, by mission id: what Accept saves. Set with the missions shown,
  // only by a load that succeeds, so a failed one never leaves a proposal nobody sees.
  private var proposed: Map<String, List<String>> = emptyMap()

  // The volunteers that proposal was made for, so Accept can check it against fresh missions.
  private var candidates: List<ScheduleCandidate> = emptyList()

  // The missions shown, by id, as read: the dialog needs their team and who is on them.
  private var shownMissions: Map<String, Mission> = emptyMap()

  // The names read with them, so the people already assigned keep theirs in the dialog.
  private var names: Map<String, String?> = emptyMap()

  // The load running, cancelled by the next one so an older answer never overwrites a newer one.
  private var loadJob: Job? = null

  // The candidates of the dialog loading, cancelled when it closes or loads again.
  private var candidatesJob: Job? = null

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
   * More missions than one write takes fail it as [ScheduleSaveError.TOO_LARGE]. Before saving, it
   * reads the missions again and runs [proposeSchedule] for the same volunteers: if the missions
   * changed the proposal meanwhile, e.g. a mission started or one of its people was assigned to
   * another at the same time, it fails as [ScheduleSaveError.OUTDATED] without saving anything, so
   * nobody is booked twice.
   */
  fun accept() {
    if (!_uiState.value.canAccept) return
    val additions = proposed
    if (additions.count { it.value.isNotEmpty() } > MAX_MISSIONS_PER_ADDITION) {
      _uiState.update { it.copy(saveError = ScheduleSaveError.TOO_LARGE) }
      return
    }
    _uiState.update { it.copy(isSaving = true, saveError = null) }
    viewModelScope.launch {
      try {
        val missions = missionRepository.getMissionsByEvent(eventId)
        if (proposeSchedule(missions, candidates, now()).proposed != additions) {
          _uiState.update { it.copy(isSaving = false, saveError = ScheduleSaveError.OUTDATED) }
          return@launch
        }
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
        val volunteers = scheduleCandidates(requests, teams)
        val proposal = proposeSchedule(missions, volunteers, now)
        val shown =
            missions
                .filterNot { isCompleted(it.completed, it.endAt, now) }
                .sortedWith(compareBy({ it.startAt }, { it.id }))
        val names = readNames(shown.flatMap { it.assigneeIds + proposal.proposed[it.id].orEmpty() })
        val teamNames = teams.associate { it.id to it.name }
        proposed = proposal.proposed
        candidates = volunteers
        shownMissions = shown.associateBy { it.id }
        this@AutoScheduleViewModel.names = names
        _uiState.update { state ->
          state.copy(
              isLoading = false,
              missions = shown.map { it.toProposed(proposal, teamNames, names, now) },
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
   * Opens the assign dialog for the mission [missionId], still short of people once the proposal is
   * accepted. The people already on it are picked and locked, and the limit is them plus the spots
   * left, so the people proposed keep theirs. Ignored for a mission with no spot left, one whose
   * team no longer exists, or while the proposal is saved.
   */
  fun openAssign(missionId: String) {
    val state = _uiState.value
    val shown = state.missions.firstOrNull { it.id == missionId } ?: return
    val mission = shownMissions[missionId] ?: return
    if (state.isSaving || shown.missing <= 0 || (!shown.isGeneral && shown.teamName == null)) return
    _uiState.update {
      it.copy(
          assigning =
              AssignState(
                  missionId = mission.id,
                  title = mission.title,
                  limit = mission.assigneeIds.size + shown.missing,
                  selection =
                      AssigneeSelection(
                          selectedIds = mission.assigneeIds,
                          lockedIds = mission.assigneeIds.toSet(),
                          isLoading = true,
                      ),
              )
      )
    }
    loadCandidates(mission)
  }

  /** Picks or unpicks the candidate [id] in the dialog, within its limit; locked people stay. */
  fun toggleAssignee(id: String) {
    editAssigning { it.copy(selection = it.selection.toggle(id, it.limit), saveFailed = false) }
  }

  /** Loads the dialog's candidates again after they failed, keeping who is picked. */
  fun retryAssignees() {
    val missionId = _uiState.value.assigning?.missionId ?: return
    shownMissions[missionId]?.let { loadCandidates(it) }
  }

  /**
   * Closes the dialog without saving. Ignored while it saves: the save cannot be taken back, so it
   * stays open to show how it ends.
   */
  fun dismissAssign() {
    if (_uiState.value.assigning?.isSaving == true) return
    candidatesJob?.cancel()
    _uiState.update { it.copy(assigning = null) }
  }

  /**
   * Adds the people picked beyond the locked ones to the mission at once, with
   * [MissionRepository.addAssignees], then closes the dialog and prepares the proposal again, so
   * they show as assigned and nobody is proposed twice. It never removes anyone, even someone
   * assigned from the Overview meanwhile. Nothing is saved before the candidates are read, or with
   * too many people picked; with nobody new picked, it just closes.
   *
   * A mission that filled up, completed or was deleted meanwhile refuses the addition: the dialog
   * closes and the proposal is prepared again, showing what changed. Any other failure keeps it
   * open with the same people picked, to try again.
   */
  fun saveAssignees() {
    val assigning = _uiState.value.assigning ?: return
    val selection = assigning.selection
    if (assigning.isSaving || selection.isLoading || selection.loadFailed || assigning.overLimit) {
      return
    }
    val added = selection.selectedIds.filterNot { it in selection.lockedIds }
    if (added.isEmpty()) {
      dismissAssign()
      return
    }
    editAssigning { it.copy(isSaving = true, saveFailed = false) }
    viewModelScope.launch {
      try {
        missionRepository.addAssignees(eventId, mapOf(assigning.missionId to added))
        closeAssignAndReload(assigning.missionId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        if (saveErrorOf(e) == ScheduleSaveError.OUTDATED) {
          closeAssignAndReload(assigning.missionId)
        } else {
          editAssigning(assigning.missionId) { it.copy(isSaving = false, saveFailed = true) }
        }
      }
    }
  }

  /** Closes the dialog of [missionId], if it is still the one shown, then loads again. */
  private fun closeAssignAndReload(missionId: String) {
    _uiState.update { state ->
      state.copy(assigning = state.assigning?.takeIf { it.missionId != missionId })
    }
    load()
  }

  /**
   * Loads who may be picked for [mission], keeping who is picked. Someone already on it who is no
   * longer a candidate, e.g. who left the team, stays listed, so the count matches the rows.
   */
  private fun loadCandidates(mission: Mission) {
    candidatesJob?.cancel()
    editAssigning { it.copy(selection = it.selection.copy(isLoading = true, loadFailed = false)) }
    candidatesJob = viewModelScope.launch {
      try {
        val loaded = assigneeCandidates.load(eventId, mission.teamId)
        editAssigning(mission.id) {
          val former =
              it.selection.selectedIds
                  .filter { id -> loaded.none { candidate -> candidate.id == id } }
                  .map { id -> AssigneeOption(id, names[id]) }
          it.copy(selection = it.selection.copy(candidates = loaded + former, isLoading = false))
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        editAssigning(mission.id) {
          it.copy(selection = it.selection.copy(isLoading = false, loadFailed = true))
        }
      }
    }
  }

  /**
   * Applies [change] to the dialog shown, if any. With [missionId], only when it is still that
   * mission's: the end of a load or a save must not touch another mission opened since.
   */
  private fun editAssigning(missionId: String? = null, change: (AssignState) -> AssignState) {
    _uiState.update { state ->
      val assigning = state.assigning?.takeIf { missionId == null || it.missionId == missionId }
      if (assigning == null) state else state.copy(assigning = change(assigning))
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
        assigneeCandidates: AssigneeCandidates,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        AutoScheduleViewModel(
            eventId,
            missionRepository,
            teamRepository,
            membershipRequestRepository,
            userRepository,
            eventRepository,
            assigneeCandidates,
        )
      }
    }
  }
}

/**
 * Why saving the proposal failed, as `MissionRepository.addAssignees` reports it. A refused input,
 * such as a mission filled meanwhile, or a mission completed or deleted makes it outdated, so a new
 * proposal is made; anything else may work on a second try, including a refused write, which the
 * contract has no better value for.
 */
private fun saveErrorOf(e: Exception): ScheduleSaveError =
    when (e) {
      is IllegalArgumentException,
      is MissionCompletedException,
      is MissionRepositoryException.NotFound -> ScheduleSaveError.OUTDATED
      else -> ScheduleSaveError.RETRY
    }

/**
 * This mission as the proposed schedule shows it, with the [names] of who is on it and who the
 * [proposal] adds. A name that could not be read is null, and still counts. It has started at
 * [now], the instant the proposal was prepared for, so the two agree on which missions it skips.
 */
private fun Mission.toProposed(
    proposal: ScheduleProposal,
    teamNames: Map<String, String>,
    names: Map<String, String?>,
    now: Instant,
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
        started = now >= startAt,
    )
