// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.team

/**
 * Read and write access to the teams of an event, independent of any backend.
 *
 * ViewModels depend on this interface and never on an implementation, so the Firestore repository
 * can be replaced by a fake in unit tests.
 *
 * Every method takes the id of the event the team belongs to: a [Team] is stored below its parent
 * event and does not carry an event id of its own, so the caller has to name the event it is
 * working in.
 */
interface TeamRepository {

  /** Persists [team] under the event [eventId] and returns it with the id the backend assigns. */
  suspend fun createTeam(eventId: String, team: Team): Team

  /** Returns every team of [eventId], or an empty list when it has none. */
  suspend fun getTeams(eventId: String): List<Team>

  /** Returns the team [teamId] of [eventId], or null when no such team exists. */
  suspend fun getTeam(eventId: String, teamId: String): Team?

  /**
   * Sets the manager of the team [teamId] of [eventId].
   *
   * Passing null removes the current manager: [Team.managerId] is nullable, so a team without one
   * is a state an organizer can go back to. Only the manager is written, so changing it cannot
   * overwrite a change made to the rest of the team in the meantime.
   */
  suspend fun setTeamManager(eventId: String, teamId: String, managerId: String?)
}
