// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.team

import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepository
import kotlinx.coroutines.CompletableDeferred

/**
 * The teams of the event, in memory: the fakes of the unit tests live in `src/test` and are out of
 * reach here. Ids are assigned in creation order, and [failure] makes every call throw.
 *
 * A deletion waits for [deletionAnswer] when one is set, then throws [deletionFailure] if set.
 */
internal class FakeTeams(initialTeams: List<Team> = emptyList()) : TeamRepository {
  val teams: MutableList<Team> = initialTeams.toMutableList()
  var failure: Exception? = null
  var beforeUpdate: suspend () -> Unit = {}
  var deletionAnswer: CompletableDeferred<Unit>? = null
  var deletionFailure: Exception? = null
  private var createdCount = 0

  override suspend fun createTeam(team: Team): Team {
    failure?.let { throw it }
    return team.copy(id = "team-${++createdCount}").also { teams += it }
  }

  override suspend fun getTeamsByEvent(eventId: String): List<Team> {
    failure?.let { throw it }
    return teams.filter { it.eventId == eventId }
  }

  override suspend fun getTeam(eventId: String, teamId: String): Team? = null

  override suspend fun getTeamsOfMember(eventId: String, userId: String): List<Team> = emptyList()

  override suspend fun getTeamsManagedBy(eventId: String, userId: String): List<Team> = emptyList()

  override suspend fun setManager(eventId: String, teamId: String, managerId: String?) = Unit

  override suspend fun updateTeam(
      eventId: String,
      teamId: String,
      name: String,
      description: String,
      icon: String,
  ) {
    beforeUpdate()
    failure?.let { throw it }
    val index = teams.indexOfFirst { it.id == teamId }
    teams[index] = teams[index].copy(name = name, description = description, icon = icon)
  }

  override suspend fun addMember(eventId: String, teamId: String, userId: String) = Unit

  override suspend fun removeMember(eventId: String, teamId: String, userId: String) = Unit

  override suspend fun deleteTeam(eventId: String, teamId: String) {
    deletionAnswer?.await()
    deletionFailure?.let { throw it }
    teams.removeAll { it.eventId == eventId && it.id == teamId }
  }
}
