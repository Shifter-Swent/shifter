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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
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
import com.swent.shifter.ui.events.organizer.OrgaEventsContent
import com.swent.shifter.ui.events.staff.StaffEventsContent
import com.swent.shifter.ui.navigation.Destination
import com.swent.shifter.ui.navigation.NavigationActions
import com.swent.shifter.ui.navigation.NavigationTestTags
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

  NavHost(navController, startDestination = start, modifier = modifier) {
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
            onScanQrClick = { nav.navigateTo(Volunteer.QrApply) },
            onAvatarClick = { nav.navigateTo(Volunteer.ProfileSettings) },
        )
      }
      composable<Volunteer.ProfileSettings> {
        SettingsRoute(onBack = { nav.goBack() }, onSignedOut = { nav.enterApp(SignedOut) })
      }
      composable<Volunteer.QrApply> { PlaceholderPage(Volunteer.QrApply, onBack = nav::goBack) }
      navigation<VolunteerEvent>(startDestination = VolunteerTabs.home) {
        tab<VolunteerTabs.Overview, VolunteerEventScreen.Overview>(VolunteerTabs.Overview, nav)
        tab<VolunteerTabs.Map, VolunteerEventScreen.Map>(VolunteerTabs.Map, nav)
        tab<VolunteerTabs.Discussions, VolunteerEventScreen.Discussions>(
            VolunteerTabs.Discussions,
            nav,
        )
      }
    }

    navigation<Organizer>(startDestination = Organizer.Events) {
      composable<Organizer.Events> {
        // No events ViewModel yet: only the selected tab is held here.
        var selectedTab by rememberSaveable { mutableStateOf(EventTab.UPCOMING) }
        OrgaEventsContent(
            state = MyEventsUiState(selectedTab = selectedTab),
            avatarInitial = "A",
            onTabSelected = { selectedTab = it },
            onManageEvent = { eventId -> nav.enterEvent(OrganizerEvent(eventId)) },
            onCreateEventClick = { /* TODO */ },
            onVolunteerViewClick = { nav.enterApp(Volunteer) },
            onAvatarClick = { nav.navigateTo(Organizer.ProfileSettings) },
        )
      }
      composable<Organizer.ProfileSettings> {
        SettingsRoute(onBack = { nav.goBack() }, onSignedOut = { nav.enterApp(SignedOut) })
      }
      navigation<OrganizerEvent>(startDestination = OrganizerTabs.home) {
        tab<OrganizerTabs.Overview, OrganizerEventScreen.Overview>(OrganizerTabs.Overview, nav)
        tab<OrganizerTabs.People, OrganizerEventScreen.People>(OrganizerTabs.People, nav)
        tab<OrganizerTabs.Map, OrganizerEventScreen.Map>(OrganizerTabs.Map, nav)
        tab<OrganizerTabs.Discussions, OrganizerEventScreen.Discussions>(
            OrganizerTabs.Discussions,
            nav,
        )
      }
    }
  }
}

/** Registers [tab] as its own graph, so it keeps its own back stack, holding its home screen. */
private inline fun <reified T : Tab, reified S : Destination> NavGraphBuilder.tab(
    tab: Tab,
    nav: NavigationActions,
) {
  navigation<T>(startDestination = tab.home) { composable<S> { TabPage(tab, nav) } }
}

/** Stand-in for a screen that is not built yet: its title, and a Back button when given one. */
@Composable
private fun PlaceholderPage(destination: Destination, onBack: (() -> Unit)? = null) {
  Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
    if (onBack != null) {
      TextButton(
          onClick = onBack,
          modifier = Modifier.testTag(NavigationTestTags.GO_BACK_BUTTON),
      ) {
        Text("Back")
      }
    }
    Text(destination.title, modifier = Modifier.testTag(NavigationTestTags.TOP_BAR_TITLE))
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
        NavigationBar(modifier = Modifier.testTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU)) {
          current.set.all.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { nav.switchTab(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                modifier = Modifier.testTag(tab.testTag),
            )
          }
        }
      }
  ) { padding ->
    Box(modifier = Modifier.padding(padding)) { PlaceholderPage(current.home) }
  }
}

@Preview(showBackground = true)
@Composable
fun ShifterAppPreview() {
  ShifterTheme { ShifterApp(isSignedIn = false) }
}
