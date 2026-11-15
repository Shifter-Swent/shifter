// Co-authored-by: OpenAI Codex <noreply@openai.com>
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
