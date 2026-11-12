// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.shifter.model.RepositoryProvider
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.location.LiveLocationSharingState
import com.swent.shifter.model.location.LiveLocationTarget
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.model.team.TeamRepositoryException
import com.swent.shifter.model.user.UserRepository
import com.swent.shifter.model.user.displayNames
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the volunteer reads about a target: the event, the team, and the team's manager. */
data class SharingTargetLabel(
    val eventTitle: String,
    val teamName: String,
    /** `null` when the team has no manager or their name cannot be read. */
    val managerName: String?,
)

/**
 * The state of the location sharing for the indicator in the top bar (#405), shared by every screen
 * of the app.
 *
 * The states come from the foreground publisher through [onSharingStates]: this reads no position
 * and runs no session. The names of the targets are only read when the volunteer opens the sheet
 * listing them ([loadLabels]), since the icon alone does not need them.
 */
class LocationSharingViewModel(
    private val eventRepository: EventRepository,
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

  private val _sharingStates =
      MutableStateFlow<Map<LiveLocationTarget, LiveLocationSharingState>>(emptyMap())

  /** What the sharing is doing for each target a session runs for, empty while none runs. */
  val sharingStates: StateFlow<Map<LiveLocationTarget, LiveLocationSharingState>> =
      _sharingStates.asStateFlow()

  private val _labels = MutableStateFlow<Map<LiveLocationTarget, SharingTargetLabel>>(emptyMap())

  /** The names of the targets read so far. A target missing here is shown without them. */
  val labels: StateFlow<Map<LiveLocationTarget, SharingTargetLabel>> = _labels.asStateFlow()

  private var loadJob: Job? = null

  /**
   * Called by the publisher with every change, and with an empty map once it stops.
   *
   * Its stop - background, sign-out, another account - also forgets the names, so they are read
   * again the next time: a team renamed or given another manager meanwhile is never shown under its
   * old one, nor a manager whose name could not be read left out for good.
   */
  fun onSharingStates(states: Map<LiveLocationTarget, LiveLocationSharingState>) {
    _sharingStates.value = states
    if (states.isEmpty()) {
      loadJob?.cancel()
      _labels.value = emptyMap()
    }
  }

  /**
   * Reads the names of the current targets that have none yet, all in parallel.
   *
   * A target whose event or team cannot be read keeps no label, so it is shown without names and
   * read again the next time the sheet opens: a one-off read, so a failure never stops a later one.
   * A load already running is not started twice. The names read are kept until the publisher stops
   * (see [onSharingStates]), so within one stay in the foreground a change is not seen.
   */
  fun loadLabels() {
    if (loadJob?.isActive == true) return
    val missing = _sharingStates.value.keys - _labels.value.keys
    if (missing.isEmpty()) return
    loadJob = viewModelScope.launch {
      val read = missing.map { target -> async { target to readTeam(target) } }.awaitAll()
      val found = read.mapNotNull { (target, pair) -> pair?.let { target to it } }
      val managers =
          userRepository.displayNames(found.mapNotNull { (_, pair) -> pair.second.managerId })
      _labels.update { labels ->
        labels +
            found.associate { (target, pair) ->
              val (event, team) = pair
              val manager = team.managerId?.let { managers[it] }
              target to SharingTargetLabel(event.title, team.name, manager)
            }
      }
    }
  }

  /** The event and the team of [target], or `null` when either is missing or cannot be read. */
  private suspend fun readTeam(target: LiveLocationTarget) =
      try {
        val event = eventRepository.getEvent(target.eventId)
        val team = teamRepository.getTeam(target.eventId, target.teamId)
        if (event != null && team != null) event to team else null
      } catch (_: EventRepositoryException) {
        null
      } catch (_: TeamRepositoryException) {
        null
      }

  companion object {
    /** A factory taking the repositories this ViewModel reads out of [repositories]. */
    fun factory(repositories: RepositoryProvider): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        LocationSharingViewModel(
            repositories.eventRepository,
            repositories.teamRepository,
            repositories.userRepository,
        )
      }
    }
  }
}
