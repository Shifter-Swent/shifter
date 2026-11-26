// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.notification

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.swent.shifter.model.RepositoryProvider
import com.swent.shifter.model.notification.NotificationEngine
import com.swent.shifter.model.notification.Notifier
import com.swent.shifter.model.notification.clearWhenSignedOut
import com.swent.shifter.model.notification.runNotificationsWhileOpen
import com.swent.shifter.ui.location.publishWhileForeground
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "NotificationsWhileOpen"

/**
 * Runs [engine] for the signed-in user while the app is in the foreground, and clears [notifier] on
 * sign-out (see [runAppNotifications]). It draws nothing.
 *
 * @param dispatcher off the main thread, since a run writes the phone's stores to disk.
 */
@Composable
fun NotificationsWhileOpen(
    repositories: RepositoryProvider,
    engine: NotificationEngine,
    notifier: Notifier,
    dispatcher: CoroutineDispatcher,
) {
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  LaunchedEffect(lifecycle, repositories, engine, notifier, dispatcher) {
    runAppNotifications(
        lifecycle = lifecycle,
        userIds = repositories.authRepository.authState.map { it?.uid },
        notifier = notifier,
        dispatcher = dispatcher,
        runForUser = { userId ->
          runNotificationsWhileOpen(
              userId,
              engine,
              repositories.notificationInbox,
              repositories.minuteClock,
              onFailure = { Log.w(TAG, "A notification run failed", it) },
          )
        },
    )
  }
}

/**
 * Runs [runForUser] for the signed-in user of [userIds] while [lifecycle] is in the foreground, and
 * stops it in the background, where WorkManager runs the notifications, and at once on sign-out or
 * a change of account (see [publishWhileForeground]). It also clears [notifier] on sign-out, the
 * only way out of an account, which happens with the app open (see [clearWhenSignedOut]). Both run
 * on [dispatcher]. Returns only when cancelled.
 */
suspend fun runAppNotifications(
    lifecycle: Lifecycle,
    userIds: Flow<String?>,
    notifier: Notifier,
    dispatcher: CoroutineDispatcher,
    runForUser: suspend (userId: String) -> Unit,
): Unit =
    withContext(dispatcher) {
      launch { clearWhenSignedOut(userIds, notifier) }
      publishWhileForeground(lifecycle, userIds, runForUser)
    }
