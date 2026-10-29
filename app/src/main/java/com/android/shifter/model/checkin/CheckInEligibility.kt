// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.checkin

import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.mission.Mission
import java.time.Duration
import java.time.Instant

/** Whether a volunteer can check in to a mission by themself, or why not. */
sealed interface CheckInEligibility {

  /** The volunteer is in the mission's zone during its window: they can check in. */
  data object Eligible : CheckInEligibility

  /** The volunteer cannot check in, for the given [reason]. */
  data class Ineligible(val reason: Reason) : CheckInEligibility

  /** Why a volunteer cannot check in. */
  enum class Reason {
    /** The mission has no location, so its assignees are checked in by a manager instead. */
    NO_LOCATION,

    /** The check-in window has not opened yet. */
    TOO_EARLY,

    /** The mission is over. */
    TOO_LATE,

    /** The volunteer's position is not known yet. */
    NO_POSITION,

    /** The volunteer is outside the mission's zone. */
    TOO_FAR,
  }
}

/** The check-in window and zone, in one place so they are easy to change. */
object CheckInRules {
  /** How long before the mission's start the check-in opens. It closes when the mission ends. */
  val OPENS_BEFORE_START: Duration = Duration.ofMinutes(15)

  /** The radius of the zone around the mission's location, in meters. */
  const val RADIUS_METERS = 200.0
}

/**
 * When a volunteer can check in to a mission running from [startAt] to [endAt], both bounds
 * included: it opens [CheckInRules.OPENS_BEFORE_START] early and closes with the mission.
 *
 * It takes the two instants rather than a [Mission] so a screen holding its own model of one reads
 * the same window, instead of spelling the rule out a second time.
 */
fun checkInWindow(startAt: Instant, endAt: Instant): ClosedRange<Instant> =
    (startAt - CheckInRules.OPENS_BEFORE_START)..endAt

/** When a volunteer can check in to this mission by themself, both bounds included. */
fun Mission.checkInWindow(): ClosedRange<Instant> = checkInWindow(startAt, endAt)

/**
 * Tells whether a volunteer at [position] can check in to [mission] at [now]: during its
 * [checkInWindow], within [CheckInRules.RADIUS_METERS] of the mission's location, both bounds
 * included.
 *
 * It does not check that the volunteer is assigned to the mission: the caller only passes their
 * missions, and the backend rules enforce it. The time is checked before the position, so a mission
 * outside its window can be skipped without waiting for a position.
 *
 * @param position the volunteer's last known position, or null if there is none yet.
 */
fun checkInEligibility(
    mission: Mission,
    now: Instant,
    position: GeoPosition?,
): CheckInEligibility {
  val location =
      mission.location
          ?: return CheckInEligibility.Ineligible(CheckInEligibility.Reason.NO_LOCATION)
  val window = mission.checkInWindow()
  val reason =
      when {
        now < window.start -> CheckInEligibility.Reason.TOO_EARLY
        now > window.endInclusive -> CheckInEligibility.Reason.TOO_LATE
        position == null -> CheckInEligibility.Reason.NO_POSITION
        position.distanceTo(GeoPosition(location.latitude, location.longitude)) >
            CheckInRules.RADIUS_METERS -> CheckInEligibility.Reason.TOO_FAR
        else -> return CheckInEligibility.Eligible
      }
  return CheckInEligibility.Ineligible(reason)
}
