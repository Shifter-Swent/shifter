// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.swent.shifter.model.RepositoryProviderFactory
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** The name of the periodic run, so scheduling it again keeps the one already planned. */
private const val NOTIFICATION_WORK = "notifications"

/** How often the notifications run with the app closed: the shortest period Android allows. */
private const val NOTIFICATION_PERIOD_MINUTES = 15L

/**
 * Runs the notifications of the signed-in user every 15 minutes, even with the app closed, and once
 * as soon as it is first scheduled. Called at each launch: a run already planned is kept, so its
 * period does not start again.
 */
fun scheduleNotificationWork(context: Context) {
  WorkManager.getInstance(context)
      .enqueueUniquePeriodicWork(
          NOTIFICATION_WORK,
          ExistingPeriodicWorkPolicy.KEEP,
          PeriodicWorkRequestBuilder<NotificationWorker>(
                  NOTIFICATION_PERIOD_MINUTES,
                  TimeUnit.MINUTES,
              )
              .build(),
      )
}

/** One background run of the engine of the process, for the user signed in on the phone. */
class NotificationWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

  override suspend fun doWork(): Result =
      notificationWorkResult(
          userId = RepositoryProviderFactory.production.authRepository.currentUser()?.uid,
          run = { DeviceNotifications.of(applicationContext).engine.run(it, Instant.now()) },
      )
}

/**
 * What a background run of [run] for [userId] reports to WorkManager: success with nobody signed
 * in, and a retry when it fails, since the engine lost nothing (see [NotificationEngine.run]).
 */
suspend fun notificationWorkResult(
    userId: String?,
    run: suspend (userId: String) -> Unit,
): ListenableWorker.Result {
  if (userId == null) return ListenableWorker.Result.success()
  return try {
    run(userId)
    ListenableWorker.Result.success()
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    ListenableWorker.Result.retry()
  }
}
