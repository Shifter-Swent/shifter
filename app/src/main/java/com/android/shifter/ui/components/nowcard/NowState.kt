// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.ui.components.nowcard

import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.isCompleted
import java.time.Duration
import java.time.Instant

/** How soon a future mission becomes the volunteer's immediate next task. */
object NowCardRules {
  val SOON_BEFORE_START: Duration = Duration.ofMinutes(45)
}

/** What the volunteer should do now, with a following mission when it is close enough to show. */
sealed interface NowState {
  data class Later(val next: Mission) : NowState

  data class Soon(val next: Mission) : NowState

  data class Late(val current: Mission, val next: Mission?) : NowState

  data class Ongoing(val current: Mission, val next: Mission?) : NowState

  data object DoneForToday : NowState
}

/**
 * Classifies the supplied volunteer missions at [now], using already observed check-ins.
 *
 * Completed missions, including those closed by hand, are discarded using [isCompleted]. The
 * earliest remaining mission wins, ordered by start, end, event ID and mission ID. Thus overlapping
 * running missions keep the earliest one, even if another has a check-in; input order and title
 * edits do not change the choice. [NowState.Late.next] and [NowState.Ongoing.next] contain the
 * earliest future mission only when it starts within [NowCardRules.SOON_BEFORE_START] of [now].
 *
 * A mission starts at its start instant and is over at its end instant. A check-in can already
 * exist before the start, during the existing check-in window; the mission stays [NowState.Soon]
 * until it starts, then becomes [NowState.Ongoing]. This function consumes presence records without
 * deciding check-in eligibility again, whether they came from GPS, a manager or a pending write.
 *
 * The caller supplies the assigned missions for the desired horizon (for example today's missions)
 * and their checked-in mission IDs. No user or local-day filtering happens here:
 * [NowState.DoneForToday] means no remaining mission in that supplied list. IDs must identify
 * missions unambiguously within the list; callers combining events must account for event-scoped
 * backend document IDs.
 */
fun nowState(missions: List<Mission>, now: Instant, checkedIn: Set<String>): NowState {
  val remaining =
      missions
          .filterNot { isCompleted(it.completed, it.endAt, now) }
          .sortedWith(compareBy({ it.startAt }, { it.endAt }, { it.eventId }, { it.id }))
  val first = remaining.firstOrNull() ?: return NowState.DoneForToday
  val soonUntil = now.plus(NowCardRules.SOON_BEFORE_START)
  if (first.startAt > now) {
    return if (first.startAt <= soonUntil) NowState.Soon(first) else NowState.Later(first)
  }
  val next = remaining.firstOrNull { it.startAt > now && it.startAt <= soonUntil }
  return if (first.id in checkedIn) NowState.Ongoing(first, next) else NowState.Late(first, next)
}
