// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

/**
 * Read and write access to the missions of the events, independent of any backend.
 *
 * ViewModels depend on this interface and never on an implementation, so the Firestore repository
 * can be replaced by a fake in unit tests.
 */
interface MissionRepository {

  /**
   * Persists [mission] under its event and returns it with the id the backend assigns.
   *
   * @throws MissionOverlapException when [mission] [conflictsWith][Mission.conflictsWith] a mission
   *   of the same event; nothing is written then.
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
   * Returns every mission, across all events, whose [Mission.assigneeIds] contains [userId],
   * earliest [Mission.startAt] first, or an empty list when they have none. Each mission carries
   * its [Mission.eventId], so a caller interested in one event filters on it.
   */
  suspend fun getMissionsAssignedTo(userId: String): List<Mission>
}
