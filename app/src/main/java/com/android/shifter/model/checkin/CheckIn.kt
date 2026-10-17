// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.checkin

import java.time.Instant

/**
 * The record that a volunteer was present at a mission. There is at most one per volunteer and
 * mission, and once written it never changes: see [CheckInRepository] for the rules.
 *
 * The model stays free of Firebase types so it can be used by ViewModels and tested without a
 * backend.
 *
 * @property eventId id of the event the mission belongs to.
 * @property missionId id of the mission the volunteer checked in to.
 * @property userId id of the volunteer who is present.
 * @property checkedInAt when the check-in was recorded, set by the backend rather than the caller.
 * @property method whether the volunteer checked in themself or was checked in by a manager.
 * @property checkedInBy id of the user who recorded the check-in: [userId] for
 *   [CheckInMethod.SELF], the manager or organizer for [CheckInMethod.MANAGER].
 */
data class CheckIn(
    val eventId: String,
    val missionId: String,
    val userId: String,
    val checkedInAt: Instant,
    val method: CheckInMethod,
    val checkedInBy: String,
)

/** How a [CheckIn] was recorded. */
enum class CheckInMethod {
  /** Automatically, by the volunteer's own app once they are in the mission's zone and window. */
  SELF,

  /** Manually, by the manager of the mission's team or the organizer, when GPS fails. */
  MANAGER,
}
