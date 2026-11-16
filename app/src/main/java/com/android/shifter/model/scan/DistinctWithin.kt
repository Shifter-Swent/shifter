// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.scan

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Drops a text seen again less than [windowMillis] after it was last seen. Each sighting counts,
 * emitted or not: a camera sees a code about ten times a second, so a code held in view is emitted
 * once, and again only after it has been out of view for [windowMillis]. Another text always
 * passes.
 *
 * The texts seen are remembered per collector.
 *
 * @param now the current time in milliseconds, e.g. `SystemClock.elapsedRealtime`.
 */
fun Flow<String>.distinctWithin(windowMillis: Long, now: () -> Long): Flow<String> = flow {
  val lastSeen = mutableMapOf<String, Long>()
  collect { text ->
    val time = now()
    val previous = lastSeen.put(text, time)
    if (previous == null || time - previous >= windowMillis) emit(text)
  }
}
