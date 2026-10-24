// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.checkin

import kotlinx.coroutines.flow.Flow

/**
 * Records and observes the check-ins of the volunteers to their missions. ViewModels depend on this
 * interface, never on an implementation.
 *
 * Rules every implementation enforces:
 * - Only a volunteer in the mission's
 *   [assigneeIds][com.swent.shifter.model.mission.Mission.assigneeIds] can be checked in.
 * - A [CheckInMethod.SELF] check-in is recorded by the volunteer themself; a
 *   [CheckInMethod.MANAGER] check-in by the manager of the mission's team or by the organizer.
 * - There is at most one check-in per volunteer and mission.
 * - A check-in is final: it can be neither modified nor deleted, by anyone, so it stays a reliable
 *   record of who was present.
 * - Only the organizer, the manager of the mission's team and the volunteer themself can read a
 *   check-in.
 *
 * Whether a volunteer is close enough and on time to check in automatically is not decided here:
 * the zone is a circle around the mission's
 * [location][com.swent.shifter.model.mission.Mission.location] (a mission without one has no
 * automatic check-in), and the team's `checkInZone` is not used.
 *
 * Every method throws, or every flow fails with, a [CheckInRepositoryException] when the operation
 * fails.
 */
interface CheckInRepository {

  /**
   * Checks [userId] in to the mission [missionId] of [eventId] with [method]. The backend sets
   * [CheckIn.checkedInAt] and [CheckIn.checkedInBy] to the current time and the signed-in user, so
   * a caller cannot backdate a check-in or record it in someone else's name.
   *
   * @throws CheckInRepositoryException.AlreadyCheckedIn if [userId] is already checked in to the
   *   mission; the existing check-in is left unchanged.
   * @throws CheckInRepositoryException.NotAllowed if the rules above deny it.
   */
  suspend fun checkIn(eventId: String, missionId: String, userId: String, method: CheckInMethod)

  /**
   * Emits every check-in of the mission [missionId] of [eventId], then again each time one is
   * added, for the attendance view. Emits an empty list when nobody is checked in yet.
   */
  fun observeCheckIns(eventId: String, missionId: String): Flow<List<CheckIn>>

  /**
   * Emits the check-ins of [userId] to every mission of [eventId], then again each time one is
   * added, so Planning can show which missions are done and automatic check-in never tries the same
   * mission twice. Emits an empty list when they have none.
   *
   * A check-in whose write is still pending (offline, or before the backend has set
   * [CheckIn.checkedInAt]) is emitted too, with an estimated [CheckIn.checkedInAt], so a check-in
   * is never tried twice.
   */
  fun observeMyCheckIns(eventId: String, userId: String): Flow<List<CheckIn>>
}
