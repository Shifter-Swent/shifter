// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.model.mission

import kotlinx.coroutines.flow.Flow

/**
 * Read and write access to the missions of the events, independent of any backend.
 *
 * ViewModels depend on this interface and never on an implementation, so the Firestore repository
 * can be replaced by a fake in unit tests.
 *
 * Every method throws a [MissionRepositoryException] when the backend fails, and an
 * [IllegalArgumentException] for invalid input.
 */
interface MissionRepository {

  /**
   * Persists [mission] under its event and returns it with the id the backend assigns.
   *
   * @throws MissionOverlapException when [mission] [conflictsWith][Mission.conflictsWith] a mission
   *   of the same event; nothing is written then.
   * @throws IllegalArgumentException when [Mission.assigneeIds] lists someone twice or holds more
   *   people than [Mission.volunteersNeeded]; nothing is written then.
   */
  suspend fun createMission(mission: Mission): Mission

  /**
   * Returns the mission identified by [missionId] in the event [eventId], or null when that event
   * has no such mission.
   */
  suspend fun getMission(eventId: String, missionId: String): Mission?

  /** Returns every mission of the event [eventId], or an empty list when there is none. */
  suspend fun getMissionsByEvent(eventId: String): List<Mission>

  /**
   * Returns the missions of the event [eventId] attached to one of [teamIds], in no particular
   * order, or an empty list when [teamIds] is empty or none of those teams has a mission.
   *
   * The "General" missions, attached to no team, are never among them: a team manager reads the
   * missions of their teams with this, where the organizer reads them all with
   * [getMissionsByEvent].
   */
  suspend fun getMissionsByTeams(eventId: String, teamIds: Set<String>): List<Mission>

  /**
   * Returns every mission, across all events, whose [Mission.assigneeIds] contains [userId],
   * earliest [Mission.startAt] first, or an empty list when they have none. Each mission carries
   * its [Mission.eventId], so a caller interested in one event filters on it.
   */
  suspend fun getMissionsAssignedTo(userId: String): List<Mission>

  /**
   * Emits the missions [getMissionsAssignedTo] returns, then again each time one is assigned to
   * [userId], unassigned or changed, so automatic check-in follows assignments made while it runs.
   * A backend failure ends the flow with a [MissionRepositoryException].
   */
  fun observeMissionsAssignedTo(userId: String): Flow<List<Mission>>

  /**
   * Replaces the [Mission.assigneeIds] of the mission identified by [missionId] in the event
   * [eventId] with [assigneeIds], in the given order. An empty list leaves the mission with nobody
   * assigned.
   *
   * Throws [IllegalArgumentException] when [assigneeIds] lists someone twice or holds more people
   * than the mission's [Mission.volunteersNeeded]; nothing is written then. Throws
   * [MissionRepositoryException.NotFound] when the mission does not exist.
   */
  suspend fun updateAssignees(eventId: String, missionId: String, assigneeIds: List<String>)

  /**
   * Saves the [Mission.title], [Mission.description], [Mission.startAt], [Mission.endAt],
   * [Mission.location] and [Mission.volunteersNeeded] of [mission] on the existing mission
   * identified by its [Mission.eventId] and [Mission.id]. Its team, assignees, creation time and
   * completion are kept as stored, whatever [mission] holds. Volunteers already checked in keep
   * their check-in.
   *
   * The checks run in this order, so every implementation reports the same error first. Nothing is
   * written when one fails.
   *
   * @throws IllegalArgumentException when [Mission.id] or [Mission.eventId] is empty, the slot does
   *   not end after it starts, or [Mission.volunteersNeeded] is below 1.
   * @throws MissionRepositoryException.NotFound when the mission does not exist.
   * @throws MissionOverlapException when the new values make the mission
   *   [conflict][Mission.conflictsWith] with another mission of its stored team.
   * @throws VolunteersBelowAssigneesException when [Mission.volunteersNeeded] is lower than the
   *   number of people already assigned.
   */
  suspend fun updateMission(mission: Mission)

  /**
   * Closes the mission identified by [missionId] in the event [eventId] before its end, which
   * cannot be undone.
   *
   * Throws [MissionRepositoryException.NotFound] when the mission does not exist. Offline, it only
   * returns once the device reconnects and the backend accepts the write. A caller may show the
   * mission as completed while waiting, and restore its state if the write is refused. Cancelling
   * the wait does not cancel a queued write and must not be reported as a refused completion.
   */
  suspend fun markCompleted(eventId: String, missionId: String)
}
