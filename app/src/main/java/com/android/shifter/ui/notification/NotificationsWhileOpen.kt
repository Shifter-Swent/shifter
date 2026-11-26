// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.notification

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.swent.shifter.model.RepositoryProvider
import com.swent.shifter.model.notification.DeviceNotifications
import com.swent.shifter.model.notification.Notifier
import com.swent.shifter.model.notification.logFailedRun
import com.swent.shifter.model.notification.runNotificationsWhileOpen
import com.swent.shifter.ui.location.publishWhileForeground
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Runs the [notifications] of the signed-in user while the app is in the foreground, and clears
 * them on sign-out (see [runAppNotifications]). It draws nothing.
 *
 * @param dispatcher off the main thread, since a run writes the phone's stores to disk.
 */
@Composable
fun NotificationsWhileOpen(
    repositories: RepositoryProvider,
    notifications: DeviceNotifications,
    dispatcher: CoroutineDispatcher,
) {
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  LaunchedEffect(lifecycle, repositories, notifications, dispatcher) {
    runAppNotifications(
        lifecycle = lifecycle,
        userIds = repositories.authRepository.authState.map { it?.uid },
        notifier = notifications.notifier,
        dispatcher = dispatcher,
        runForUser = { userId ->
          runNotificationsWhileOpen(
              userId,
              notifications.engine,
              repositories.notificationInbox,
              repositories.minuteClock,
              notifications.wholeRuns,
              onFailure = ::logFailedRun,
          )
        },
    )
  }
}

/**
 * Runs [runForUser] for the signed-in user of [userIds] while [lifecycle] is in the foreground, and
 * stops it in the background, where WorkManager runs the notifications, and at once on sign-out or
 * a change of account, as [publishWhileForeground] does. On sign-out, the only way out of an
 * account, it then clears [notifier]: [collectLatest] waits for the run it cancels to end, so a
 * notification the run was showing never stays after the clear, to open the screens of the account
 * signed out. Runs on [dispatcher]. Returns only when cancelled.
 */
internal suspend fun runAppNotifications(
    lifecycle: Lifecycle,
    userIds: Flow<String?>,
    notifier: Notifier,
    dispatcher: CoroutineDispatcher,
    runForUser: suspend (userId: String) -> Unit,
): Unit =
    withContext(dispatcher) {
      userIds.distinctUntilChanged().collectLatest { userId ->
        if (userId == null) notifier.clear()
        else lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { runForUser(userId) }
      }
    }
