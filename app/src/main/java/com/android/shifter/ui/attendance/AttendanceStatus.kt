// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
package com.swent.shifter.ui.attendance

import com.swent.shifter.model.checkin.checkInWindow
import java.time.Instant

/**
 * What is said about the attendance of a mission at a given instant. Decided by
 * [AttendanceMission.status], whose first condition that holds wins.
 */
internal enum class AttendanceStatus {
  /** Closed by hand or past its end: its attendance is history, and no warning can be acted on. */
  COMPLETED,
  /** The check-in has not opened, so nobody can be present yet, nor late. */
  NOT_OPEN,
  /** The check-ins of the mission could not be observed. */
  FAILED,
  /** The check-ins of the mission have not arrived yet. */
  WAITING,
  /** The check-in is open but the mission has not started: those not there are not late. */
  EARLY,
  /** Fewer people are present than the mission needs, which the manager is warned about. */
  UNDERSTAFFED,
  /** Enough people are present: nothing to report. */
  STAFFED,
}

/**
 * The status of [this] at [now]. The check-in window of the mission is read first, from the shared
 * [checkInWindow], so a mission nobody can have checked in to yet and one that is over are never
 * reported short of volunteers: the warning would be about nothing, or no longer actionable.
 */
internal fun AttendanceMission.status(now: Instant): AttendanceStatus {
  val window = checkInWindow(startAt, endAt)
  return when {
    isCompleted(now) -> AttendanceStatus.COMPLETED
    now < window.start -> AttendanceStatus.NOT_OPEN
    attendanceFailed -> AttendanceStatus.FAILED
    !attendanceKnown -> AttendanceStatus.WAITING
    now < startAt -> AttendanceStatus.EARLY
    isUnderstaffed -> AttendanceStatus.UNDERSTAFFED
    else -> AttendanceStatus.STAFFED
  }
}
