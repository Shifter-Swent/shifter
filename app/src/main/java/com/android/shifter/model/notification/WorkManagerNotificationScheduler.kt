// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * The [NotificationScheduler] of the phone: one WorkManager run of [NotificationWorker] per
 * distinct time, under a unique name made of it, kept if asked again while still planned or
 * running. A time passed runs as soon as possible. Android may run it later while the phone sleeps
 * (Doze), at its next maintenance window.
 *
 * @param clock what "now" is, to turn a time into a delay.
 */
class WorkManagerNotificationScheduler(context: Context, private val clock: Clock) :
    NotificationScheduler {

  private val workManager = WorkManager.getInstance(context.applicationContext)

  override fun checkAt(time: Instant) {
    // Rounded up, so a time finer than a millisecond never runs just before it is due.
    val delayMillis =
        Duration.between(clock.instant(), time).plusNanos(NANOS_PER_MILLI - 1).toMillis()
    workManager.enqueueUniqueWork(
        "notifications-at-${time.toEpochMilli()}",
        ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build(),
    )
  }

  private companion object {
    const val NANOS_PER_MILLI = 1_000_000L
  }
}
