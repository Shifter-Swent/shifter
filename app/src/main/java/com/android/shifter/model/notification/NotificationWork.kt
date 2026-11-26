// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
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

private const val TAG = "NotificationWorker"

/**
 * Runs the notifications of the signed-in user every 15 minutes, even with the app closed, and once
 * as soon as it is first scheduled. Called at each launch: a run already planned keeps its period,
 * and one planned by an older version of the app is updated.
 */
fun scheduleNotificationWork(context: Context) {
  WorkManager.getInstance(context)
      .enqueueUniquePeriodicWork(
          NOTIFICATION_WORK,
          ExistingPeriodicWorkPolicy.UPDATE,
          PeriodicWorkRequestBuilder<NotificationWorker>(
                  NOTIFICATION_PERIOD_MINUTES,
                  TimeUnit.MINUTES,
              )
              .build(),
      )
}

/**
 * One background run of the engine of the process, for the user signed in on the phone. It always
 * succeeds: a run that failed lost nothing (see [NotificationEngine.run]) and runs again at the
 * next period, whereas a retry would wait longer at each failure, up to hours.
 */
class NotificationWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    runNotificationWork(
        userId = RepositoryProviderFactory.production.authRepository.currentUser()?.uid,
        run = { DeviceNotifications.of(applicationContext).engine.run(it, Instant.now()) },
        onFailure = { Log.w(TAG, "A background notification run failed", it) },
    )
    return Result.success()
  }
}

/**
 * Runs [run] for [userId], nothing when nobody is signed in, and gives what it throws to
 * [onFailure], a cancellation excepted.
 */
suspend fun runNotificationWork(
    userId: String?,
    run: suspend (userId: String) -> Unit,
    onFailure: (Exception) -> Unit,
) {
  if (userId == null) return
  try {
    run(userId)
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    onFailure(e)
  }
}
