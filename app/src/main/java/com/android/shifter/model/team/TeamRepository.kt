// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.team

/**
 * Read and write access to the teams of the events, independent of any backend.
 *
 * ViewModels depend on this interface and never on an implementation, so the Firestore repository
 * can be replaced by a fake in unit tests.
 *
 * The manager of a team is never one of its members: [Team.managerId] and [Team.memberIds] are
 * disjoint, so "the teams I manage" and "the teams I belong to" are two separate queries.
 */
interface TeamRepository {

  /**
   * Persists [team] under its event and returns it with the id the backend assigns.
   *
   * Throws [IllegalArgumentException] when the team has no event, or when its manager is also
   * listed among its members.
   */
  suspend fun createTeam(team: Team): Team

  /**
   * Returns the team identified by [teamId] in the event [eventId], or null when that event has no
   * such team.
   */
  suspend fun getTeam(eventId: String, teamId: String): Team?

  /** Returns every team of the event [eventId], or an empty list when there is none. */
  suspend fun getTeamsByEvent(eventId: String): List<Team>

  /** Returns the teams of the event [eventId] that [userId] belongs to as a volunteer. */
  suspend fun getTeamsOfMember(eventId: String, userId: String): List<Team>

  /** Returns the teams of the event [eventId] that [userId] manages. */
  suspend fun getTeamsManagedBy(eventId: String, userId: String): List<Team>

  /**
   * Appoints [managerId] as the manager of the team, or leaves the team without one when it is
   * null. A member appointed manager leaves [Team.memberIds] in the same write: promoting a
   * volunteer of the team is a single call. The previous manager does not become a member.
   *
   * Fails when the team does not exist.
   */
  suspend fun setManager(eventId: String, teamId: String, managerId: String?)

  /**
   * Adds [userId] to the members of the team. Adding a member twice has no effect.
   *
   * Throws [IllegalArgumentException] when [userId] manages the team, and fails when the team does
   * not exist.
   */
  suspend fun addMember(eventId: String, teamId: String, userId: String)

  /**
   * Removes [userId] from the members of the team. Removing someone who is not a member has no
   * effect.
   *
   * Fails when the team does not exist.
   */
  suspend fun removeMember(eventId: String, teamId: String, userId: String)
}
