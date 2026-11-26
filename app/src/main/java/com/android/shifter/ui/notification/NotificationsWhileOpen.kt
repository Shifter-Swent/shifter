// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.notification

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.swent.shifter.model.RepositoryProvider
import com.swent.shifter.model.notification.NotificationEngine
import com.swent.shifter.model.notification.Notifier
import com.swent.shifter.model.notification.clearWhenSignedOut
import com.swent.shifter.model.notification.runNotificationsWhileOpen
import com.swent.shifter.ui.location.publishWhileForeground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs [engine] for the signed-in user while the app is in the foreground (see
 * [runNotificationsWhileOpen]), and stops it in the background, where WorkManager runs it, and at
 * once on sign-out or a change of account. It draws nothing. Off the main thread, since a run
 * writes the phone's stores to disk.
 *
 * It also clears [notifier] on sign-out (see [clearWhenSignedOut]), the only way out of an account,
 * which happens with the app open.
 */
@Composable
fun NotificationsWhileOpen(
    repositories: RepositoryProvider,
    engine: NotificationEngine,
    notifier: Notifier,
) {
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  LaunchedEffect(lifecycle, repositories, engine, notifier) {
    val userIds = repositories.authRepository.authState.map { it?.uid }
    withContext(Dispatchers.IO) {
      launch { clearWhenSignedOut(userIds, notifier) }
      publishWhileForeground(
          lifecycle = lifecycle,
          userIds = userIds,
          publish = { userId ->
            runNotificationsWhileOpen(
                userId,
                engine,
                repositories.notificationInbox,
                repositories.minuteClock,
            )
          },
      )
    }
  }
}
