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
import com.swent.shifter.ui.event.CreateEventContent
import com.swent.shifter.ui.event.CreateEventUiState
import com.swent.shifter.ui.event.EventCreatedContent
import com.swent.shifter.ui.event.EventCreatedUiState
import com.swent.shifter.ui.event.JoinEventContent
import com.swent.shifter.ui.event.JoinEventUiState
import com.swent.shifter.ui.events.EventTab
import com.swent.shifter.ui.events.MyEventsUiState
import com.swent.shifter.ui.events.organizer.OrgaEventsContent
import com.swent.shifter.ui.events.staff.StaffEventsContent
import com.swent.shifter.ui.mission.AddMissionActions
import com.swent.shifter.ui.mission.AddMissionContent
import com.swent.shifter.ui.mission.AddMissionUiState
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
      composable<Volunteer.QrApply> {
        // No repositories are wired yet: only the typed code is held here, no event is found.
        var joinCode by rememberSaveable { mutableStateOf("") }
        JoinEventContent(
            uiState = JoinEventUiState(joinCode = joinCode),
            onJoinCodeChange = { joinCode = it },
            onFindEvent = {},
            onBack = {},
            onApply = {},
        )
      }
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
            onCreateEventClick = { nav.navigateTo(Organizer.CreateEvent) },
            onVolunteerViewClick = { nav.enterApp(Volunteer) },
            onAvatarClick = { nav.navigateTo(Organizer.ProfileSettings) },
        )
      }
      composable<Organizer.ProfileSettings> {
        SettingsRoute(onBack = { nav.goBack() }, onSignedOut = { nav.enterApp(SignedOut) })
      }
      composable<Organizer.CreateEvent> {
        // No repositories are wired yet: the form only shows and goes back.
        CreateEventContent(
            state = CreateEventUiState(),
            onTitleChange = {},
            onDescriptionChange = {},
            onTypeChange = {},
            onAddressChange = {},
            onStartAtChange = {},
            onEndAtChange = {},
            onSubmit = {
              // The form gives way to the code: Back from there returns to the events list.
              nav.backTo(Organizer.Events)
              nav.navigateTo(Organizer.EventCreated)
            },
            onBack = nav::goBack,
        )
      }
      composable<Organizer.EventCreated> {
        // No repositories are wired yet: the code is empty until an event is really created.
        EventCreatedContent(
            state = EventCreatedUiState(isLoading = false),
            onRetry = {},
            onDone = { nav.backTo(Organizer.Events) },
        )
      }
      navigation<OrganizerEvent>(startDestination = OrganizerTabs.home) {
        tab<OrganizerTabs.Overview, OrganizerEventScreen.Overview>(
            OrganizerTabs.Overview,
            nav,
            content = {
              Button(
                  onClick = { nav.navigateTo(OrganizerEventScreen.AddMission) },
                  modifier = Modifier.testTag(ShifterAppTestTags.ADD_MISSION_BUTTON),
              ) {
                Text(OrganizerEventScreen.AddMission.title)
              }
            },
        ) {
          composable<OrganizerEventScreen.AddMission> {
            // No repositories are wired yet: the form only shows and goes back.
            AddMissionContent(
                state = AddMissionUiState(isLoadingEvent = false),
                actions = AddMissionActions(onBack = nav::goBack),
            )
          }
        }
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

/**
 * Registers [tab] as its own graph, so it keeps its own back stack: its home screen, showing
 * [content] under its title, and the [screens] opened on top of it.
 */
private inline fun <reified T : Tab, reified S : Destination> NavGraphBuilder.tab(
    tab: Tab,
    nav: NavigationActions,
    noinline content: @Composable () -> Unit = {},
    crossinline screens: NavGraphBuilder.() -> Unit = {},
) {
  navigation<T>(startDestination = tab.home) {
    composable<S> { TabPage(tab, nav, content) }
    screens()
  }
}

object ShifterAppTestTags {
  const val ADD_MISSION_BUTTON = "AddMissionButton"
}

/**
 * Stand-in for a screen that is not built yet: its title, a Back button when given one, and the
 * [content] that links to the screens already built.
 */
@Composable
private fun PlaceholderPage(
    destination: Destination,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
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
    content()
  }
}

/** A tab of an event, with the bottom bar listing the other tabs of the same event. */
@Composable
private fun TabPage(current: Tab, nav: NavigationActions, content: @Composable () -> Unit = {}) {
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
    Box(modifier = Modifier.padding(padding)) { PlaceholderPage(current.home, content = content) }
  }
}

@Preview(showBackground = true)
@Composable
fun ShifterAppPreview() {
  ShifterTheme { ShifterApp(isSignedIn = false) }
}
