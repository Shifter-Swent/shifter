// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import java.time.Duration
import java.time.Instant

/**
 * How much earlier than [PeriodicRuns.period] a run may come: the minute ticks land a few
 * milliseconds after the minute, so an exact period would often wait one more minute.
 */
private val PERIOD_TOLERANCE: Duration = Duration.ofSeconds(30)

/**
 * When the app open last ran the whole engine, and for whom, kept for the whole process: coming
 * back to the foreground or rotating the phone does not run it again before [period].
 */
class PeriodicRuns(private val period: Duration) {

  private var last: Pair<String, Instant>? = null

  /**
   * Whether a whole run for [userId] is due at [tick], marking it as done if so: the first run of
   * the process or of another user, one [period] after the last, or at once if the clock went back
   * before the last run.
   */
  @Synchronized
  fun takeIfDue(userId: String, tick: Instant): Boolean {
    val (lastUser, lastTick) = last ?: (null to null)
    val due =
        lastUser != userId ||
            lastTick == null ||
            tick.isBefore(lastTick) ||
            Duration.between(lastTick, tick) >= period - PERIOD_TOLERANCE
    if (due) last = userId to tick
    return due
  }
}
