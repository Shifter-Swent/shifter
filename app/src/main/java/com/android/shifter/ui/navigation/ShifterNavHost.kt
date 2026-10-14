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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.navigation.toRoute
import com.swent.shifter.model.RepositoryProvider
import com.swent.shifter.ui.authentication.SignInScreen
import com.swent.shifter.ui.authentication.SignInViewModel
import com.swent.shifter.ui.event.CreateEventScreen
import com.swent.shifter.ui.event.CreateEventViewModel
import com.swent.shifter.ui.event.EventCreatedScreen
import com.swent.shifter.ui.event.EventCreatedViewModel
import com.swent.shifter.ui.event.JoinEventScreen
import com.swent.shifter.ui.event.JoinEventViewModel
import com.swent.shifter.ui.events.MembershipEventWithdrawer
import com.swent.shifter.ui.events.MyEventsViewModel
import com.swent.shifter.ui.events.OrganizerEventsLoader
import com.swent.shifter.ui.events.StaffEventsLoader
import com.swent.shifter.ui.events.StaffEventsViewModel
import com.swent.shifter.ui.events.avatarInitial
import com.swent.shifter.ui.events.organizer.OrgaEventsScreen
import com.swent.shifter.ui.events.staff.StaffEventsScreen
import com.swent.shifter.ui.map.MapScreen
import com.swent.shifter.ui.map.MapViewModel
import com.swent.shifter.ui.membership.MembershipRequestUIState
import com.swent.shifter.ui.mission.AddMissionScreen
import com.swent.shifter.ui.mission.AddMissionViewModel
import com.swent.shifter.ui.people.PeopleScreen
import com.swent.shifter.ui.settings.SettingsRoute
import com.swent.shifter.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.filter

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

  // Returns to sign-in whenever the SDK reports no session: signed out, an account disabled or
  // deleted on the server (found at the next token refresh, see AuthRepository.authState), or a
  // session already gone at launch. exitToSignIn does nothing when already on sign-in.
  LaunchedEffect(auth, nav) { auth.authState.filter { it == null }.collect { nav.exitToSignIn() } }

  NavHost(navController, startDestination = start, modifier = modifier) {
    navigation<SignedOut>(startDestination = SignedOut.SignIn) {
      composable<SignedOut.SignIn> {
        SignInScreen(
            authViewModel =
                viewModel(factory = SignInViewModel.factory(auth, repositories.userRepository)),
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
        // The screen's own Back returns from the event to the code; the device Back on the code
        // leaves it, back to the events list.
        JoinEventScreen(
            viewModel =
                viewModel(
                    factory =
                        JoinEventViewModel.factory(
                            repositories.eventRepository,
                            repositories.membershipRequestRepository,
                            repositories.teamRepository,
                            currentUserId,
                        )
                )
        )
      }
      navigation<VolunteerEvent>(startDestination = VolunteerTabs.home) {
        tab<VolunteerTabs.Overview, VolunteerEventScreen.Overview>(VolunteerTabs.Overview, nav)
        mapTab<VolunteerTabs.Map, VolunteerEventScreen.Map, VolunteerEvent>(
            VolunteerTabs.Map,
            nav,
            navController,
            repositories,
            currentUserId,
        )
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
        CreateEventScreen(
            viewModel =
                viewModel(
                    factory =
                        CreateEventViewModel.factory(repositories.eventRepository, currentUserId)
                ),
            onEventCreated = { event ->
              // The form gives way to the code: Back from there returns to the events list.
              nav.backTo(Organizer.Events)
              nav.navigateTo(Organizer.EventCreated(event.id))
            },
            onBack = nav::goBack,
        )
      }
      composable<Organizer.EventCreated> { entry ->
        // The code is read back from the backend, as the route only carries the event's id.
        val eventId = entry.toRoute<Organizer.EventCreated>().eventId
        EventCreatedScreen(
            viewModel =
                viewModel(
                    factory = EventCreatedViewModel.factory(repositories.eventRepository, eventId)
                ),
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
          composable<OrganizerEventScreen.AddMission> { entry ->
            // The event's id is in the route of the event graph this screen belongs to.
            val eventId =
                remember(entry) {
                  navController
                      .getBackStackEntry<OrganizerEvent>()
                      .toRoute<OrganizerEvent>()
                      .eventId
                }
            AddMissionScreen(
                viewModel =
                    viewModel(
                        factory =
                            AddMissionViewModel.factory(
                                eventId,
                                repositories.missionRepository,
                                repositories.eventRepository,
                                repositories.teamRepository,
                                currentUserId,
                            )
                    ),
                // Back to the event's overview, the mission saved.
                onMissionAdded = { nav.goBack() },
                onBack = nav::goBack,
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
        mapTab<OrganizerTabs.Map, OrganizerEventScreen.Map, OrganizerEvent>(
            OrganizerTabs.Map,
            nav,
            navController,
            repositories,
            currentUserId,
        )
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
    composable<S> { TabPage(tab, nav) { PlaceholderPage(tab.home, content = content) } }
    screens()
  }
}

/** Registers the Map [tab] of the event open in the graph [E], showing its missions on a map. */
private inline fun <reified T : Tab, reified S : Destination, reified E : EventMode> NavGraphBuilder
    .mapTab(
    tab: Tab,
    nav: NavigationActions,
    navController: NavHostController,
    repositories: RepositoryProvider,
    noinline currentUserId: () -> String?,
) {
  navigation<T>(startDestination = tab.home) {
    composable<S> { entry ->
      val eventId = remember(entry) { navController.getBackStackEntry<E>().toRoute<E>().eventId }
      TabPage(tab, nav) {
        MapScreen(
            viewModel =
                viewModel(
                    factory =
                        MapViewModel.factory(
                            eventId,
                            repositories.eventRepository,
                            repositories.missionRepository,
                            currentUserId,
                        )
                ),
            onBack = { nav.leaveEvent(tab.set) },
        )
      }
    }
  }
}

/** Goes back from an event to the list of events it was opened from. */
private fun NavigationActions.leaveEvent(set: TabSet) {
  when (set) {
    VolunteerTabs -> backTo(Volunteer.Events)
    OrganizerTabs -> backTo(Organizer.Events)
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

/** A tab of an event showing [content], with the bottom bar listing the other tabs of the event. */
@Composable
private fun TabPage(current: Tab, nav: NavigationActions, content: @Composable () -> Unit) {
  BackHandler { nav.leaveEvent(current.set) }
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
    Box(modifier = Modifier.padding(padding)) { content() }
  }
}
