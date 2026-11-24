// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.event.JoinCode
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequestRepositoryException
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.shift.Shift
import com.swent.shifter.model.shift.ShiftRepository
import com.swent.shifter.model.shift.ShiftRepositoryException
import com.swent.shifter.model.shift.availabilityOf
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepository
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Why the join screen shows an error. The screen turns each into a translated message. */
enum class JoinEventError {
  UNKNOWN_CODE,
  NOT_SIGNED_IN,
  OFFLINE,
  PERMISSION_DENIED,
  UNEXPECTED,
}

/**
 * The shifts of the event a volunteer applies to, and the ones they picked: they apply to the
 * shifts they can work, so the organizer only counts on them then.
 *
 * @property shifts the open shifts of the event, sorted by start. Empty when the event has none:
 *   the volunteer then applies for the whole event, as before shifts existed.
 * @property pickedShiftIds the ids of the [shifts] the volunteer picked. Once a request is sent,
 *   the shifts it holds.
 */
data class ShiftPicking(
    val shifts: List<Shift> = emptyList(),
    val pickedShiftIds: Set<String> = emptySet(),
) {
  /** The [shifts] picked, sorted by start. */
  val picked: List<Shift>
    get() = shifts.filter { it.id in pickedShiftIds }

  /** Whether the event has shifts and none is picked yet: the volunteer must pick one to apply. */
  val lacksAPick: Boolean
    get() = shifts.isNotEmpty() && pickedShiftIds.isEmpty()
}

/**
 * What the join screen shows: the code being typed, the event it found, its teams and shifts and
 * the ones picked, and where the volunteer's request stands.
 *
 * @property teams the teams of [event] the volunteer can apply to, by name. Empty while no event is
 *   shown, or when the event has no team yet, in which case nobody can apply.
 * @property selectedTeamId the team of [event] the volunteer applies to, null until they pick one.
 *   Once a request is sent, the team it was sent to.
 * @property shiftPicking the shifts of [event] and the ones the volunteer picked.
 * @property requestStatus the status of the volunteer's request for the event, null while they have
 *   none. Finding an event they already applied to shows that request, possibly decided.
 * @property requestSentTo the title of the event a request was just sent to, so the screen leaves
 *   for My Events; null otherwise, including for a request found already sent. The screen calls
 *   [JoinEventViewModel.onRequestSentHandled] once it has left.
 */
data class JoinEventUiState(
    val joinCode: String = "",
    val event: Event? = null,
    val teams: List<Team> = emptyList(),
    val selectedTeamId: String? = null,
    val shiftPicking: ShiftPicking = ShiftPicking(),
    val isLoading: Boolean = false,
    val requestStatus: MembershipRequestStatus? = null,
    val error: JoinEventError? = null,
    val hasEnded: Boolean = false,
    val requestSentTo: String? = null,
) {
  /** Whether [joinCode] is complete and could exist, so the volunteer can look it up. */
  val canFind: Boolean
    get() = JoinCode.isValid(joinCode)

  /** Whether [joinCode] holds a character no code uses, such as `0` or `I`. */
  val hasUnusedCharacter: Boolean
    get() = !joinCode.all(JoinCode::isUsed)

  /** Whether the volunteer has picked a team and, when the event has shifts, at least one. */
  val canApply: Boolean
    get() = selectedTeamId != null && !shiftPicking.lacksAPick
}

/**
 * Finds an event by its join code, then sends the signed-in volunteer's request to join it.
 *
 * @param currentUserId returns the signed-in user's id, or null when nobody is signed in.
 * @param now the clock stamped on the request, replaceable in tests.
 * @param shiftRepository where the shifts of the event are read, or null while the app has none to
 *   give: every event is then one without shifts. It is null until `RepositoryProvider` holds the
 *   Firestore shift repository (#593).
 */
class JoinEventViewModel(
    private val eventRepository: EventRepository,
    private val membershipRequestRepository: MembershipRequestRepository,
    private val teamRepository: TeamRepository,
    private val currentUserId: () -> String?,
    private val now: () -> Instant = Instant::now,
    private val shiftRepository: ShiftRepository? = null,
) : ViewModel() {

  private val _uiState = MutableStateFlow(JoinEventUiState())
  val uiState: StateFlow<JoinEventUiState> = _uiState.asStateFlow()

  /** The last lookup of a code, so that [cancelLookup] can stop it. */
  private var lookup: Job? = null

  /**
   * Keeps the code typed or pasted so far, upper-cased and cut to [JoinCode.LENGTH], since join
   * codes are upper-case and the screen shows one box per character.
   */
  fun onJoinCodeChange(joinCode: String) = _uiState.update {
    it.copy(joinCode = JoinCode.normalize(joinCode), error = null)
  }

  /**
   * Looks up the event volunteers join with the code typed, once it is valid, its teams and its
   * shifts, with the signed-in volunteer's request for it if they already applied, so they are not
   * offered to apply twice and see the team and the shifts they applied to.
   */
  fun findEvent() {
    val joinCode = _uiState.value.joinCode
    if (!JoinCode.isValid(joinCode) || _uiState.value.isLoading) return
    lookup = launchLoading {
      val event = eventRepository.getEventByJoinCode(joinCode)
      val userId = currentUserId()
      val request =
          if (event == null || userId == null) null
          else membershipRequestRepository.getMembershipRequest(event.id, userId)
      val teams = event?.let { teamRepository.getTeamsByEvent(it.id).sortedBy(Team::name) }
      val shifts = event?.let { shiftRepository?.getShifts(it.id) }.orEmpty()
      _uiState.update {
        it.copy(
            event = event,
            teams = teams.orEmpty(),
            selectedTeamId = request?.teamId,
            shiftPicking = ShiftPicking(shifts, request?.shiftIds.orEmpty().toSet()),
            requestStatus = request?.status,
            error = if (event == null) JoinEventError.UNKNOWN_CODE else null,
            hasEnded = event != null && !now().isBefore(event.endAt),
        )
      }
    }
  }

  /**
   * Leaves the event details and goes back to typing a code, keeping the code typed so far. Ignored
   * while a request is being sent, so its result cannot land on the code step.
   *
   * Also drops what was found for a code, or the error it gave: the scan does so to scan again.
   */
  fun changeCode() {
    if (_uiState.value.isLoading) return
    _uiState.update {
      it.copy(
          event = null,
          teams = emptyList(),
          selectedTeamId = null,
          shiftPicking = ShiftPicking(),
          requestStatus = null,
          error = null,
          hasEnded = false,
      )
    }
  }

  /**
   * Stops the lookup of the code if it still runs, then drops what was found, as [changeCode] does,
   * keeping the code: the scan does so when the volunteer leaves it without going on to the event,
   * so that a lookup ending after they left shows neither its event nor its error. A request being
   * sent is not stopped.
   */
  fun cancelLookup() {
    val running = lookup
    if (running != null && running.isActive) {
      running.cancel()
      _uiState.update { it.copy(isLoading = false) }
    }
    changeCode()
  }

  /**
   * Picks the team of the loaded event to apply to, replacing any previous pick. A volunteer
   * belongs to at most one team per event, so there is a single choice. Ignored once the request is
   * sent, or while it is being sent, so the team shown always matches the request, and for a team
   * the event does not have.
   */
  fun onTeamSelected(teamId: String) {
    val state = _uiState.value
    if (state.isLoading || state.requestStatus != null || state.hasEnded) return
    if (state.teams.none { it.id == teamId }) return
    _uiState.update { it.copy(selectedTeamId = teamId) }
  }

  /**
   * Picks the shift [shiftId] of the loaded event, or lets it go when it was picked: a volunteer
   * picks every shift they can work. Ignored, like [onTeamSelected], once the request is sent or
   * while it is being sent, once the event has ended, and for a shift the event does not have.
   */
  fun onShiftPicked(shiftId: String) {
    val state = _uiState.value
    if (state.isLoading || state.requestStatus != null || state.hasEnded) return
    val picking = state.shiftPicking
    if (picking.shifts.none { it.id == shiftId }) return
    val picked =
        if (shiftId in picking.pickedShiftIds) picking.pickedShiftIds - shiftId
        else picking.pickedShiftIds + shiftId
    _uiState.update { it.copy(shiftPicking = picking.copy(pickedShiftIds = picked)) }
  }

  /**
   * Applies to the selected team of the loaded event, for the shifts picked; does nothing until
   * [JoinEventUiState.canApply]. The request holds the shifts picked, and their slots joined back
   * to back as the volunteer's availability; for an event without shifts, the whole event.
   */
  fun applyToEvent() {
    val state = _uiState.value
    val event = state.event ?: return
    val teamId = state.selectedTeamId ?: return
    if (!state.canApply || state.isLoading || state.requestStatus != null) return
    // The event may end while the screen is open: it no longer recruits, which the rules enforce
    // too.
    if (!now().isBefore(event.endAt)) return _uiState.update { it.copy(hasEnded = true) }
    val userId = currentUserId() ?: return showError(JoinEventError.NOT_SIGNED_IN)
    launchLoading {
      val picked = state.shiftPicking.picked
      val request =
          MembershipRequest(
              userId = userId,
              teamId = teamId,
              availability =
                  if (picked.isEmpty()) listOf(AvailabilitySlot(event.startAt, event.endAt))
                  else availabilityOf(picked),
              shiftIds = picked.map(Shift::id),
              createdAt = now(),
          )
      val sent = membershipRequestRepository.apply(event.id, request)
      _uiState.update { it.copy(requestStatus = sent.status, requestSentTo = event.title) }
    }
  }

  /**
   * Fills in the [code] read from a QR code and looks its event up, as a typed code would be. A
   * code read while a lookup runs is ignored, so the code shown stays the one of the event found.
   */
  fun onCodeScanned(code: String) {
    if (_uiState.value.isLoading) return
    onJoinCodeChange(code)
    findEvent()
  }

  /** Clears [JoinEventUiState.requestSentTo] once the screen has left for My Events. */
  fun onRequestSentHandled() = _uiState.update { it.copy(requestSentTo = null) }

  /** Runs [block] with the loading flag set, turning failures into an error. */
  private fun launchLoading(block: suspend () -> Unit): Job {
    _uiState.update { it.copy(isLoading = true, error = null) }
    return viewModelScope.launch {
      try {
        block()
      } catch (e: CancellationException) {
        throw e
      } catch (e: EventRepositoryException.Unavailable) {
        showError(JoinEventError.OFFLINE)
      } catch (e: MembershipRequestRepositoryException.Unavailable) {
        showError(JoinEventError.OFFLINE)
      } catch (e: ShiftRepositoryException.Unavailable) {
        showError(JoinEventError.OFFLINE)
      } catch (e: ShiftRepositoryException.PermissionDenied) {
        showError(JoinEventError.PERMISSION_DENIED)
      } catch (e: MembershipRequestRepositoryException.PermissionDenied) {
        showError(JoinEventError.PERMISSION_DENIED)
      } catch (e: Exception) {
        showError(JoinEventError.UNEXPECTED)
      } finally {
        // Once cancelled, the loading flag was already lowered, and another lookup may run by now.
        if (isActive) _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  private fun showError(error: JoinEventError) = _uiState.update { it.copy(error = error) }

  companion object {
    /**
     * Builds the factory for `viewModel(factory = ...)`, since this ViewModel needs constructor
     * arguments.
     */
    fun factory(
        eventRepository: EventRepository,
        membershipRequestRepository: MembershipRequestRepository,
        teamRepository: TeamRepository,
        currentUserId: () -> String?,
        shiftRepository: ShiftRepository? = null,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        JoinEventViewModel(
            eventRepository,
            membershipRequestRepository,
            teamRepository,
            currentUserId,
            shiftRepository = shiftRepository,
        )
      }
    }
  }
}
