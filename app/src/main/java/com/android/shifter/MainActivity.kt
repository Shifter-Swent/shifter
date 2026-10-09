// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.swent.shifter.model.authentication.AuthRepositoryProvider
import com.swent.shifter.resources.C
import com.swent.shifter.ui.authentication.SignInScreen
import com.swent.shifter.ui.events.EventTab
import com.swent.shifter.ui.events.MyEventsUiState
import com.swent.shifter.ui.events.staff.StaffEventsContent
import com.swent.shifter.ui.navigation.NavigationActions
import com.swent.shifter.ui.navigation.Organizer
import com.swent.shifter.ui.navigation.OrganizerEvent
import com.swent.shifter.ui.navigation.OrganizerEventScreen
import com.swent.shifter.ui.navigation.OrganizerTabs
import com.swent.shifter.ui.navigation.SignedOut
import com.swent.shifter.ui.navigation.Tab
import com.swent.shifter.ui.navigation.Volunteer
import com.swent.shifter.ui.navigation.VolunteerEvent
import com.swent.shifter.ui.navigation.VolunteerEventScreen
import com.swent.shifter.ui.navigation.VolunteerTabs
import com.swent.shifter.ui.navigation.rememberNavigationActions
import com.swent.shifter.ui.navigation.startApp
import com.swent.shifter.ui.settings.SettingsRoute
import com.swent.shifter.ui.theme.ShifterTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Firebase restores the previous session synchronously, so a returning user skips sign-in.
    val isSignedIn = AuthRepositoryProvider.repository.currentUser() != null
    setContent {
      ShifterTheme {
        // A surface container using the 'background' color from the theme
        Surface(
            modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.main_screen_container },
            color = MaterialTheme.colorScheme.background,
        ) {
          ShifterApp(isSignedIn = isSignedIn)
        }
      }
    }
  }
}

/**
 * The app's navigation graph.
 *
 * @param isSignedIn Whether a session was restored at launch; only read for the start destination.
 */
@Composable
fun ShifterApp(
    isSignedIn: Boolean,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
  val nav = rememberNavigationActions(navController)
  val start = remember { startApp(isSignedIn, wasOrganizer = false) }

  NavHost(navController, startDestination = start) {
    navigation<SignedOut>(startDestination = SignedOut.SignIn) {
      composable<SignedOut.SignIn> { SignInScreen(onSignedIn = { nav.enterApp(Volunteer) }) }
    }

    navigation<Volunteer>(startDestination = Volunteer.Events) {
      composable<Volunteer.Events> {
        // No events ViewModel yet: only the selected tab is held here.
        var selectedTab by rememberSaveable { mutableStateOf(EventTab.UPCOMING) }
        StaffEventsContent(
            state = MyEventsUiState(selectedTab = selectedTab),
            avatarInitial = "A",
            onTabSelected = { selectedTab = it },
            onWithdraw = { /* TODO */ },
            onOrganizerViewClick = { nav.enterApp(Organizer) },
            onScanQrClick = { /* TODO */ },
            onAvatarClick = { nav.navigateTo(Volunteer.ProfileSettings) },
        )
      }
      composable<Volunteer.ProfileSettings> {
        SettingsRoute(onBack = { nav.goBack() }, onSignedOut = { nav.enterApp(SignedOut) })
      }
      navigation<VolunteerEvent>(startDestination = VolunteerTabs.Overview) {
        navigation<VolunteerTabs.Overview>(startDestination = VolunteerEventScreen.Overview) {
          composable<VolunteerEventScreen.Overview> { TabPage(VolunteerTabs.Overview, nav) }
        }
      }
    }

    navigation<Organizer>(startDestination = Organizer.Events) {
      composable<Organizer.Events> {
        Page("My events") {
          Button(onClick = { nav.enterEvent(OrganizerEvent("demo")) }) { Text("Open event") }
          Button(onClick = { nav.enterApp(Volunteer) }) { Text("Switch to Volunteer") }
        }
      }
      navigation<OrganizerEvent>(startDestination = OrganizerTabs.People) {
        navigation<OrganizerTabs.People>(startDestination = OrganizerEventScreen.People) {
          composable<OrganizerEventScreen.People> { TabPage(OrganizerTabs.People, nav) }
        }
        navigation<OrganizerTabs.Overview>(startDestination = OrganizerEventScreen.Overview) {
          composable<OrganizerEventScreen.Overview> { TabPage(OrganizerTabs.Overview, nav) }
        }
      }
    }
  }
}

/** A title with optional buttons below it. */
@Composable
private fun Page(title: String, content: @Composable () -> Unit = {}) {
  Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
    Text(title)
    content()
  }
}

/** A tab of an event, with the bottom bar listing the other tabs of the same event. */
@Composable
private fun TabPage(current: Tab, nav: NavigationActions) {
  BackHandler {
    when (current.set) {
      VolunteerTabs -> nav.backTo(Volunteer.Events)
      OrganizerTabs -> nav.backTo(Organizer.Events)
    }
  }
  Scaffold(
      bottomBar = {
        NavigationBar {
          current.set.all.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { nav.switchTab(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
            )
          }
        }
      }
  ) { padding ->
    Box(modifier = Modifier.padding(padding)) { Page(current.home.title) }
  }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  ShifterTheme { ShifterApp(isSignedIn = false) }
}
