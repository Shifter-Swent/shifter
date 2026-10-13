// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.team

/**
 * In-memory [TeamRepository] for unit tests.
 *
 * Ids are assigned in creation order, so a test can assert on them. Setting [failure] makes every
 * call throw it, to exercise error handling.
 *
 * It deliberately does not repeat the argument checks of [FirestoreTeamRepository], which the
 * emulator tests already cover: this fake answers what a ViewModel asks of a backend, it is not a
 * second implementation of the contract.
 *
 * @param initialTeams the teams the fake starts with, for a test that reads rather than creates.
 */
class FakeTeamRepository(initialTeams: List<Team> = emptyList()) : TeamRepository {

  /** Every team the fake holds: the ones it was seeded with, then those created, in order. */
  val teams: MutableList<Team> = initialTeams.toMutableList()

  var failure: Exception? = null

  /** Counts creations only, so a seeded team can never take the id of a created one. */
  private var createdCount = 0

  override suspend fun createTeam(team: Team): Team {
    failure?.let { throw it }
    val persisted = team.copy(id = "team-${++createdCount}")
    teams += persisted
    return persisted
  }

  override suspend fun getTeam(eventId: String, teamId: String): Team? {
    failure?.let { throw it }
    return teams.firstOrNull { it.eventId == eventId && it.id == teamId }
  }

  override suspend fun getTeamsByEvent(eventId: String): List<Team> {
    failure?.let { throw it }
    return teams.filter { it.eventId == eventId }
  }

  override suspend fun getTeamsOfMember(eventId: String, userId: String): List<Team> {
    failure?.let { throw it }
    return teams.filter { it.eventId == eventId && userId in it.memberIds }
  }

  override suspend fun getTeamsManagedBy(eventId: String, userId: String): List<Team> {
    failure?.let { throw it }
    return teams.filter { it.eventId == eventId && it.managerId == userId }
  }

  /** Like the backend, appointing a member leaves them out of the members in the same write. */
  override suspend fun setManager(eventId: String, teamId: String, managerId: String?) {
    update(eventId, teamId) {
      it.copy(managerId = managerId, memberIds = it.memberIds - setOfNotNull(managerId))
    }
  }

  /** Adding a member twice has no effect, as `arrayUnion` guarantees on the backend. */
  override suspend fun addMember(eventId: String, teamId: String, userId: String) {
    update(eventId, teamId) {
      if (userId in it.memberIds) it else it.copy(memberIds = it.memberIds + userId)
    }
  }

  override suspend fun removeMember(eventId: String, teamId: String, userId: String) {
    update(eventId, teamId) { it.copy(memberIds = it.memberIds - userId) }
  }

  /**
   * Replaces the team in place, so the order teams were created in is preserved.
   *
   * Throws [NoSuchElementException] when the team does not exist, because the contract says the
   * write fails there and the backend does: a silent no-op would let a test pass where production
   * throws.
   */
  private fun update(eventId: String, teamId: String, change: (Team) -> Team) {
    failure?.let { throw it }
    val index = teams.indexOfFirst { it.eventId == eventId && it.id == teamId }
    if (index < 0) {
      throw NoSuchElementException("Team '$teamId' of event '$eventId' does not exist")
    }
    teams[index] = change(teams[index])
  }
}
