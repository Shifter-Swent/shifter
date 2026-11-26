// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.navigation.compose.rememberNavController
import com.swent.shifter.model.RepositoryProviderFactory
import com.swent.shifter.model.notification.NotificationTarget
import com.swent.shifter.model.notification.notificationTarget
import com.swent.shifter.model.notification.notificationTargetToOpen
import com.swent.shifter.model.notification.putNotificationTargetToOpen
import com.swent.shifter.model.notification.scheduleNotificationWork
import com.swent.shifter.resources.C
import com.swent.shifter.ui.navigation.ShifterNavHost
import com.swent.shifter.ui.navigation.isInEventsApp
import com.swent.shifter.ui.permission.NotificationPermissionRequest
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.enableShifterEdgeToEdge
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {

  /**
   * The screen of the notification tapped, until the app has opened it (#612). Saved when the
   * activity is recreated, so a target waiting for the sign-in is not lost.
   */
  private var notificationTarget by mutableStateOf<NotificationTarget?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableShifterEdgeToEdge()
    notificationTarget = notificationTargetToOpen(intent, savedInstanceState)
    val repositories = RepositoryProviderFactory.production
    scheduleNotificationWork(this)
    // Firebase restores the previous session synchronously, so a returning user skips sign-in.
    val isSignedIn = repositories.authRepository.currentUser() != null
    setContent {
      ShifterTheme {
        // A surface container using the 'background' color from the theme
        Surface(
            modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.main_screen_container },
            color = MaterialTheme.colorScheme.background,
        ) {
          val navController = rememberNavController()
          ShifterNavHost(
              isSignedIn = isSignedIn,
              repositories = repositories,
              navController = navController,
              notificationTarget = notificationTarget,
              onNotificationTargetOpened = { notificationTarget = null },
          )
          // Here rather than in ShifterNavHost, so the tests that build the graph never open the
          // system dialog.
          val inApp =
              remember(navController) {
                navController.currentBackStackEntryFlow.map { it.isInEventsApp() }
              }
          NotificationPermissionRequest(inApp)
        }
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putNotificationTargetToOpen(notificationTarget)
  }

  /** A notification tapped while the app is open, which brings this activity back. */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    intent.notificationTarget()?.let { notificationTarget = it }
  }
}
