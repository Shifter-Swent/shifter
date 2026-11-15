// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.nowcard

import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.isCompleted
import java.time.Duration
import java.time.Instant

/** The time rules of the volunteer's now card. */
object NowCardRules {
  /**
   * How long before its start a mission is shown: as [NowState.Soon] when nothing runs, and as the
   * `next` strip under a running mission. Long enough to always cover the 15 min check-in window.
   */
  val SOON_BEFORE_START: Duration = Duration.ofMinutes(30)
}

/** What the volunteer should do now, with a following mission when it is close enough to show. */
sealed interface NowState {
  /** Nothing runs, and [next] starts more than [NowCardRules.SOON_BEFORE_START] from now. */
  data class Later(val next: Mission) : NowState

  /**
   * Nothing runs, and [next] starts within [NowCardRules.SOON_BEFORE_START]. It stays so after an
   * early check-in, until [next] starts: the consumer reads the check-ins to word the card.
   */
  data class Soon(val next: Mission) : NowState

  /** [current] runs and the volunteer is not checked in to it. */
  data class Late(val current: Mission, val next: Mission?) : NowState

  /** [current] runs and the volunteer is checked in to it. */
  data class Ongoing(val current: Mission, val next: Mission?) : NowState

  /** No mission is left in the list supplied. */
  data object DoneForToday : NowState
}

/**
 * Classifies the volunteer's [missions] at [now], given the ids of those they are [checkedIn] to.
 *
 * Missions closed by hand or over are left out ([isCompleted]); a mission starts at its start
 * instant and is over at its end instant. The others are ordered by start, end, event id and
 * mission id, so neither the input order nor a title change moves the choice.
 *
 * A volunteer is never expected in two places: missions of a team never overlap, and moving a
 * volunteer cuts the mission they leave. Should two missions still run at once (a General mission,
 * two events), the one they are checked in to is [NowState.Ongoing]; with none, the earliest is
 * [NowState.Late]. `next` is the earliest mission starting within [NowCardRules.SOON_BEFORE_START],
 * whatever the state of the running one.
 *
 * Check-ins are presence records, from GPS, a manager or a pending write: their eligibility is not
 * decided again here. A check-in before the start leaves the mission [NowState.Soon] until it
 * starts.
 *
 * The caller supplies the missions of the horizon to show, today's for the card: no day filter
 * happens here, so a mission of tomorrow in the list is [NowState.Later]. Mission ids are only
 * unique within an event: a caller merging events must not let one event's check-in match another
 * event's mission.
 */
fun nowState(missions: List<Mission>, now: Instant, checkedIn: Set<String>): NowState {
  val remaining =
      missions
          .filterNot { isCompleted(it.completed, it.endAt, now) }
          .sortedWith(compareBy({ it.startAt }, { it.endAt }, { it.eventId }, { it.id }))
  val running = remaining.takeWhile { it.startAt <= now }
  val upcoming = remaining.drop(running.size)
  val next =
      upcoming.firstOrNull()?.takeIf { it.startAt <= now.plus(NowCardRules.SOON_BEFORE_START) }
  val current = running.firstOrNull { it.id in checkedIn } ?: running.firstOrNull()
  return when {
    current != null && current.id in checkedIn -> NowState.Ongoing(current, next)
    current != null -> NowState.Late(current, next)
    next != null -> NowState.Soon(next)
    upcoming.isNotEmpty() -> NowState.Later(upcoming.first())
    else -> NowState.DoneForToday
  }
}
