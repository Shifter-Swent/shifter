// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.nowcard

import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.time.MinuteClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The shared calculation for every volunteer now-card consumer. Waits for all three inputs, then
 * reclassifies on each change using the emitted tick consistently, without reading the clock again.
 *
 * Emits only when [NowState] changes; a card's countdown also needs the clock's ticks as its `now`
 * input. Collection owns the input subscriptions and cancellation releases them. A ViewModel that
 * shares this flow should use lifecycle-aware subscriptions as documented by [MinuteClock].
 *
 * Every value of [missions] and [checkedIn] is taken as loaded data: an empty list while loading
 * would show [NowState.DoneForToday], and an empty set after a failed read would show a checked-in
 * volunteer [NowState.Late]. So the inputs emit nothing until loaded, and an error of an input is
 * not hidden: it ends this flow, for the caller to show it or retry.
 */
fun nowStates(
    missions: Flow<List<Mission>>,
    checkedIn: Flow<Set<String>>,
    clock: MinuteClock,
): Flow<NowState> =
    combine(missions, checkedIn, clock.ticks) { assigned, present, now ->
          nowState(assigned, now, present)
        }
        .distinctUntilChanged()
