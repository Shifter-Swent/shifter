// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.swent.shifter.model.RepositoryProvider
import com.swent.shifter.ui.authentication.SignInScreen
import com.swent.shifter.ui.authentication.SignInViewModel
import com.swent.shifter.ui.event.CreateEventContent
import com.swent.shifter.ui.event.CreateEventUiState
import com.swent.shifter.ui.event.EventCreatedContent
import com.swent.shifter.ui.event.EventCreatedUiState
import com.swent.shifter.ui.event.JoinEventContent
import com.swent.shifter.ui.event.JoinEventUiState
import com.swent.shifter.ui.events.MembershipEventWithdrawer
import com.swent.shifter.ui.events.MyEventsViewModel
import com.swent.shifter.ui.events.OrganizerEventsLoader
import com.swent.shifter.ui.events.StaffEventsLoader
import com.swent.shifter.ui.events.StaffEventsViewModel
import com.swent.shifter.ui.events.avatarInitial
import com.swent.shifter.ui.events.organizer.OrgaEventsScreen
import com.swent.shifter.ui.events.staff.StaffEventsScreen
import com.swent.shifter.ui.membership.MembershipRequestUIState
import com.swent.shifter.ui.mission.AddMissionActions
import com.swent.shifter.ui.mission.AddMissionContent
import com.swent.shifter.ui.mission.AddMissionUiState
import com.swent.shifter.ui.people.PeopleScreen
import com.swent.shifter.ui.settings.SettingsRoute
import com.swent.shifter.ui.settings.SettingsViewModel

/**
 * The app's navigation graph.
 *
 * @param isSignedIn Whether a session was restored at launch; only read for the start destination.
 * @param repositories The repositories the screens' ViewModels are built from. It has no default,
 *   so a test cannot reach the production backend by forgetting to pass the emulator ones.
 */
@Composable
fun ShifterNavHost(
    isSignedIn: Boolean,
    repositories: RepositoryProvider,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
  val auth = repositories.authRepository
  // Read on every load, so the lists follow the account signed in at that time.
  val currentUserId = { auth.currentUser()?.uid }
  val nav = rememberNavigationActions(navController)
  val start = remember { startApp(isSignedIn, wasOrganizer = false) }

  NavHost(navController, startDestination = start, modifier = modifier) {
    navigation<SignedOut>(startDestination = SignedOut.SignIn) {
      composable<SignedOut.SignIn> {
        SignInScreen(
            authViewModel = viewModel(factory = SignInViewModel.factory(auth)),
            onSignedIn = { nav.enterApp(Volunteer) },
        )
      }
    }

    navigation<Volunteer>(startDestination = Volunteer.Events) {
      composable<Volunteer.Events> {
        val viewModel: StaffEventsViewModel =
            viewModel(
                factory =
                    StaffEventsViewModel.factory(
                        loader =
                            StaffEventsLoader(
                                repositories.eventRepository,
                                repositories.membershipRequestRepository,
                                currentUserId,
                            ),
                        withdrawer =
                            MembershipEventWithdrawer(
                                repositories.membershipRequestRepository,
                                currentUserId,
                            ),
                    )
            )
        RefreshOnReturn(viewModel)
        StaffEventsScreen(
            viewModel = viewModel,
            avatarInitial = remember { avatarInitial(auth.currentUser()) },
            onOrganizerViewClick = { nav.enterApp(Organizer) },
            onScanQrClick = { nav.navigateTo(Volunteer.QrApply) },
            onAvatarClick = { nav.navigateTo(Volunteer.ProfileSettings) },
        )
      }
      composable<Volunteer.ProfileSettings> {
        SettingsRoute(
            onBack = nav::goBack,
            onSignedOut = { nav.enterApp(SignedOut) },
            viewModel = viewModel(factory = SettingsViewModel.factory(auth)),
        )
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
        val viewModel: MyEventsViewModel =
            viewModel(
                factory =
                    MyEventsViewModel.factory(
                        OrganizerEventsLoader(repositories.eventRepository, currentUserId)
                    )
            )
        RefreshOnReturn(viewModel)
        OrgaEventsScreen(
            viewModel = viewModel,
            avatarInitial = remember { avatarInitial(auth.currentUser()) },
            onManageEvent = { eventId -> nav.enterEvent(OrganizerEvent(eventId)) },
            onCreateEventClick = { nav.navigateTo(Organizer.CreateEvent) },
            onVolunteerViewClick = { nav.enterApp(Volunteer) },
            onAvatarClick = { nav.navigateTo(Organizer.ProfileSettings) },
        )
      }
      composable<Organizer.ProfileSettings> {
        SettingsRoute(
            onBack = nav::goBack,
            onSignedOut = { nav.enterApp(SignedOut) },
            viewModel = viewModel(factory = SettingsViewModel.factory(auth)),
        )
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
                  modifier = Modifier.testTag(ShifterNavHostTestTags.ADD_MISSION_BUTTON),
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
        navigation<OrganizerTabs.People>(startDestination = OrganizerTabs.People.home) {
          composable<OrganizerEventScreen.People> {
            BackHandler { nav.backTo(Organizer.Events) }
            // No repositories are wired yet: the requests list is empty and its buttons do nothing.
            // PeopleScreen draws its own bottom bar, so it is not wrapped in a TabPage.
            PeopleScreen(
                uiState = MembershipRequestUIState(isLoading = false),
                onAccept = {},
                onReject = {},
                onRetry = {},
                onNavigate = nav::switchTab,
            )
          }
        }
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

/**
 * Reloads [viewModel] each time its list comes back on top, so it shows what changed while another
 * screen was open, such as a request just sent. The first resume is skipped, since the ViewModel
 * already loads once created.
 */
@Composable
private fun RefreshOnReturn(viewModel: MyEventsViewModel) {
  // Saveable, as the list leaves the composition while another screen is on top of it.
  var shownOnce by rememberSaveable { mutableStateOf(false) }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    if (shownOnce) viewModel.refresh() else shownOnce = true
  }
}

object ShifterNavHostTestTags {
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
