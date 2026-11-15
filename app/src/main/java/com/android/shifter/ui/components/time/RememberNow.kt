// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.time

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.shifter.model.time.MinuteClock
import java.time.Instant

/**
 * Now as [clock] tells it, moved on at each new minute while the screen is at least started.
 *
 * The ticks are only collected with the screen's lifecycle, so the clock's timer stops when the app
 * goes to the background and the next tick on return carries the time of that return. On first
 * composition, now is read at once, so the screen never shows an empty time.
 */
@Composable
fun rememberNow(clock: MinuteClock): Instant {
  val initial = remember(clock) { clock.now() }
  val now by clock.ticks.collectAsStateWithLifecycle(initialValue = initial)
  return now
}
