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
 *
 * Every method throws a [TeamRepositoryException] when the backend fails, and an
 * [IllegalArgumentException] for invalid input.
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
   * Appoints [managerId], a member of the team, as its manager, or leaves the team without one when
   * it is null. A team has at most one manager, so the previous one, if any, goes back to
   * [Team.memberIds], while the member appointed leaves them, both in the same write.
   *
   * Throws [IllegalArgumentException] when [managerId] is neither a member nor the current manager,
   * and [TeamRepositoryException.NotFound] when the team does not exist.
   *
   * Appointing someone takes them out of the members, so a Firestore-backed implementation deletes
   * the position they shared with this team in the same write (#305); leaving the team without a
   * manager deletes nothing, the previous one going back to the members. The in-memory fakes do not
   * model it.
   */
  suspend fun setManager(eventId: String, teamId: String, managerId: String?)

  /**
   * Replaces the [name], [description] and [icon] of the team, and only them: its members and its
   * manager are left as they are, even when they change at the same time.
   *
   * Throws [TeamRepositoryException.NotFound] when the team does not exist.
   */
  suspend fun updateTeam(
      eventId: String,
      teamId: String,
      name: String,
      description: String,
      icon: String,
  )

  /**
   * Adds [userId] to the members of the team. Adding a member twice has no effect.
   *
   * Throws [IllegalArgumentException] when [userId] manages the team, and
   * [TeamRepositoryException.NotFound] when the team does not exist.
   */
  suspend fun addMember(eventId: String, teamId: String, userId: String)

  /**
   * Removes [userId] from the members of the team. Removing someone who is not a member has no
   * effect.
   *
   * A Firestore-backed implementation also deletes the position [userId] shared with this team, in
   * the same write (#305), so their former manager stops seeing it; the position they shared with
   * another team of the event is left alone. The in-memory fakes do not model it.
   *
   * Throws [TeamRepositoryException.NotFound] when the team does not exist.
   */
  suspend fun removeMember(eventId: String, teamId: String, userId: String)

  /**
   * Deletes the team with everything that belongs to it: its missions with their check-ins, its
   * chat messages, the positions its members shared with it and the pending applications to it. Its
   * members stay participants of the event, without a team, and its manager stops managing it.
   * Deleting a team that no longer exists has no effect.
   *
   * The deletion takes several writes, the team going last, so one interrupted halfway (connection
   * lost, app killed) is completed by calling it again. Until then, the team's chat accepts no
   * message.
   *
   * It needs a connection. Offline from the start, it fails with
   * [TeamRepositoryException.Unavailable] before deleting anything. If the connection drops
   * halfway, it either fails the same way at its next read, or waits for the connection to come
   * back while a write is queued: a caller that wants a time limit sets its own.
   *
   * Throws [TeamRepositoryException.PermissionDenied] when the user is not the event's organizer.
   */
  suspend fun deleteTeam(eventId: String, teamId: String)
}
