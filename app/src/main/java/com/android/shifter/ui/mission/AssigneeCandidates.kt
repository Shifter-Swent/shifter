// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.mission

import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.model.user.UserRepository
import com.swent.shifter.model.user.UserRepositoryException

/**
 * Someone who may be assigned to a mission.
 *
 * @property name their display name, or null when it cannot be shown.
 */
data class AssigneeOption(val id: String, val name: String?)

/**
 * The people who may be assigned to a mission, and who is picked among them.
 *
 * @property candidates who may be assigned, empty while they load.
 * @property selectedIds the ids of the candidates picked, in the order they were picked.
 * @property isLoading whether [candidates] are being loaded.
 * @property loadFailed whether loading [candidates] failed.
 */
data class AssigneeSelection(
    val candidates: List<AssigneeOption> = emptyList(),
    val selectedIds: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
) {

  /**
   * Unpicks the candidate [id] if picked, and picks them otherwise, unless [limit] people are
   * already picked. An id that is not a candidate is ignored.
   */
  fun toggle(id: String, limit: Int): AssigneeSelection =
      when {
        id in selectedIds -> copy(selectedIds = selectedIds - id)
        candidates.none { it.id == id } || selectedIds.size >= limit -> this
        else -> copy(selectedIds = selectedIds + id)
      }
}

/** Finds who may be assigned to a mission. */
fun interface AssigneeCandidates {

  /**
   * Returns who may be assigned to a mission of the team [teamId] of the event [eventId], or of
   * "General" when [teamId] is null, sorted by name.
   */
  suspend fun load(eventId: String, teamId: String?): List<AssigneeOption>
}

/**
 * [AssigneeCandidates] read from the repositories: the members of the team, or the accepted
 * participants of the event for a "General" mission. Only the organizer creates General missions,
 * and only they may read every membership request of the event.
 *
 * A name that cannot be read is left out rather than failing the whole list.
 */
class RepositoryAssigneeCandidates(
    private val teamRepository: TeamRepository,
    private val membershipRequestRepository: MembershipRequestRepository,
    private val userRepository: UserRepository,
) : AssigneeCandidates {

  override suspend fun load(eventId: String, teamId: String?): List<AssigneeOption> {
    val ids =
        if (teamId == null) {
          membershipRequestRepository
              .getMembershipRequestsByEId(eventId)
              .filter { it.status == MembershipRequestStatus.ACCEPTED }
              .map { it.userId }
        } else {
          teamRepository.getTeam(eventId, teamId)?.memberIds.orEmpty()
        }
    return ids.distinct()
        .map { AssigneeOption(it, name(it)) }
        .sortedWith(compareBy(nullsLast()) { it.name?.lowercase() })
  }

  private suspend fun name(userId: String): String? =
      try {
        userRepository.getPublicProfile(userId)?.displayName?.takeIf { it.isNotBlank() }
      } catch (e: UserRepositoryException) {
        null
      }
}
