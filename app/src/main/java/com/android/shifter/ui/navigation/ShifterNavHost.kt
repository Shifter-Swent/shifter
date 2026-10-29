// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.swent.shifter.R
import com.swent.shifter.model.RepositoryProvider
import com.swent.shifter.ui.attendance.AttendanceViewModel
import com.swent.shifter.ui.attendance.ManagerOverviewContent
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
import com.swent.shifter.ui.membership.MembershipRequestVM
import com.swent.shifter.ui.mission.AddMissionScreen
import com.swent.shifter.ui.mission.AddMissionViewModel
import com.swent.shifter.ui.mission.RepositoryAssigneeCandidates
import com.swent.shifter.ui.overview.OrganizerOverviewScreen
import com.swent.shifter.ui.overview.OrganizerOverviewViewModel
import com.swent.shifter.ui.people.ParticipantsViewModel
import com.swent.shifter.ui.people.PeopleScreen
import com.swent.shifter.ui.settings.SettingsRoute
import com.swent.shifter.ui.settings.SettingsViewModel
import com.swent.shifter.ui.team.TeamSetupMode
import com.swent.shifter.ui.team.TeamSetupScreen
import com.swent.shifter.ui.team.TeamSetupViewModel
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter

/**
 * The app's navigation graph.
 *
 * @param isSignedIn Whether a session was restored at launch; only read for the start destination.
 * @param repositories The repositories the screens' ViewModels are built from. It has no default,
 *   so a test cannot reach the production backend by forgetting to pass the emulator ones.
 * @param credentialManager The Google account picker of sign-in, and what sign-out clears. A test
 *   passes one that answers with a fake account, as the system picker cannot be driven.
 */
@Composable
fun ShifterNavHost(
    isSignedIn: Boolean,
    repositories: RepositoryProvider,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    credentialManager: CredentialManager =
        LocalContext.current.let { context ->
          remember(context) { CredentialManager.create(context) }
        },
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
            credentialManager = credentialManager,
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
                                repositories.teamRepository,
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
            onOpenEvent = { eventId -> nav.enterEvent(VolunteerEvent(eventId)) },
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
            credentialManager = credentialManager,
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
        tab<VolunteerTabs.Overview, VolunteerEventScreen.Overview>(
            VolunteerTabs.Overview,
            nav,
            // The Overview draws itself, so it is not put under the stand-in title.
            built = true,
            content = { entry ->
              val eventId =
                  remember(entry) {
                    navController
                        .getBackStackEntry<VolunteerEvent>()
                        .toRoute<VolunteerEvent>()
                        .eventId
                  }
              EventOverview(eventId, repositories, currentUserId)
            },
        )
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
            credentialManager = credentialManager,
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
              // The form gives way to the teams of the event it just saved. Dropping the form
              // first means Back from there returns to the events list instead of to a form that
              // would create a second event.
              nav.backTo(Organizer.Events)
              nav.navigateTo(Organizer.TeamSetup(event.id))
            },
            onBack = nav::goBack,
        )
      }
      composable<Organizer.TeamSetup> { entry ->
        val eventId = entry.toRoute<Organizer.TeamSetup>().eventId
        TeamSetupScreen(
            viewModel =
                viewModel(
                    factory = TeamSetupViewModel.factory(eventId, repositories.teamRepository)
                ),
            // The event is saved either way: both ways out of here lead forward, never back to
            // the form, and neither deletes it.
            onBack = { nav.backTo(Organizer.Events) },
            onProceed = {
              nav.backTo(Organizer.Events)
              nav.navigateTo(Organizer.EventCreated(eventId))
            },
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
            content = { entry ->
              val eventId =
                  remember(entry) {
                    navController
                        .getBackStackEntry<OrganizerEvent>()
                        .toRoute<OrganizerEvent>()
                        .eventId
                  }
              val viewModel: OrganizerOverviewViewModel =
                  viewModel(
                      factory =
                          OrganizerOverviewViewModel.factory(
                              eventId,
                              repositories.missionRepository,
                              repositories.teamRepository,
                              RepositoryAssigneeCandidates(
                                  repositories.teamRepository,
                                  repositories.membershipRequestRepository,
                                  repositories.userRepository,
                              ),
                          )
                  )
              // Shows the missions added while the creation form was on top.
              RefreshOnReturn(viewModel::refresh)
              OrganizerOverviewScreen(
                  viewModel = viewModel,
                  onAddMission = { nav.navigateTo(OrganizerEventScreen.AddMission) },
                  onManageTeams = { nav.navigateTo(OrganizerEventScreen.ManageTeams) },
              )
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
                                RepositoryAssigneeCandidates(
                                    repositories.teamRepository,
                                    repositories.membershipRequestRepository,
                                    repositories.userRepository,
                                ),
                                currentUserId,
                            )
                    ),
                // Back to the event's overview, the mission saved.
                onMissionAdded = { nav.goBack() },
                onBack = nav::goBack,
            )
          }
          composable<OrganizerEventScreen.ManageTeams> { entry ->
            // The event's id is in the route of the event graph this screen belongs to.
            val eventId =
                remember(entry) {
                  navController
                      .getBackStackEntry<OrganizerEvent>()
                      .toRoute<OrganizerEvent>()
                      .eventId
                }
            TeamSetupScreen(
                viewModel =
                    viewModel(
                        factory = TeamSetupViewModel.factory(eventId, repositories.teamRepository)
                    ),
                // Both ways out return to the Overview, which reloads and lists the new teams.
                onBack = nav::goBack,
                onProceed = { nav.goBack() },
                mode = TeamSetupMode.MANAGEMENT,
            )
          }
        }
        navigation<OrganizerTabs.People>(startDestination = OrganizerTabs.People.home) {
          composable<OrganizerEventScreen.People> { entry ->
            BackHandler { nav.backTo(Organizer.Events) }
            // The event's id is in the route of the event graph this screen belongs to.
            val eventId =
                remember(entry) {
                  navController
                      .getBackStackEntry<OrganizerEvent>()
                      .toRoute<OrganizerEvent>()
                      .eventId
                }
            val requests: MembershipRequestVM =
                viewModel(
                    factory =
                        MembershipRequestVM.factory(
                            eventId,
                            repositories.membershipRequestRepository,
                            repositories.userRepository,
                        )
                )
            val participants: ParticipantsViewModel =
                viewModel(
                    factory =
                        ParticipantsViewModel.factory(
                            eventId,
                            repositories.membershipRequestRepository,
                            repositories.teamRepository,
                            repositories.userRepository,
                        )
                )
            // Shows the requests and participants that changed while another tab was on top.
            RefreshOnReturn(requests::loadRequests)
            RefreshOnReturn(participants::load)
            // PeopleScreen draws its own bottom bar, so it is not wrapped in a TabPage.
            PeopleScreen(
                uiState = requests.uiState.collectAsState().value,
                onAccept = requests::accept,
                onReject = requests::reject,
                onRetry = requests::loadRequests,
                participants = participants.uiState.collectAsState().value,
                onReloadParticipants = participants::load,
                onAppointManager = participants::appointManager,
                onRemoveManager = participants::removeManager,
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
 * [content], given its back stack entry, under its title, and the [screens] opened on top of it.
 */
private inline fun <reified T : Tab, reified S : Destination> NavGraphBuilder.tab(
    tab: Tab,
    nav: NavigationActions,
    built: Boolean = false,
    noinline content: @Composable (NavBackStackEntry) -> Unit = {},
    crossinline screens: NavGraphBuilder.() -> Unit = {},
) {
  navigation<T>(startDestination = tab.home) {
    composable<S> { entry ->
      TabPage(tab, nav) {
        if (built) content(entry) else PlaceholderPage(tab.home) { content(entry) }
      }
    }
    screens()
  }
}

/**
 * The Overview of an event as the signed-in user may see it: a manager watches the live attendance
 * of the missions of the teams they manage, anyone else is still waiting on their own screen.
 *
 * Whether they may watch it comes from [AttendanceViewModel], which reads it from the teams it
 * already loads, so opening this tab asks the backend for nothing more than it is allowed to show.
 */
@Composable
private fun EventOverview(
    eventId: String,
    repositories: RepositoryProvider,
    currentUserId: () -> String?,
) {
  val viewModel: AttendanceViewModel =
      viewModel(
          factory =
              AttendanceViewModel.factory(
                  eventId,
                  currentUserId().orEmpty(),
                  repositories.eventRepository,
                  repositories.missionRepository,
                  repositories.teamRepository,
                  repositories.userRepository,
                  repositories.checkInRepository,
              )
      )
  val state by viewModel.uiState.collectAsState()
  when (state.canViewAttendance) {
    true ->
        ManagerOverviewContent(
            state = state,
            eventTitle = state.eventTitle,
            now = rememberNow(),
            onRetry = viewModel::refresh,
        )
    false -> PlaceholderPage(VolunteerEventScreen.Overview)
    // Not known yet: the tab's own title only, never the manager's view of the event.
    null ->
        PlaceholderPage(VolunteerEventScreen.Overview) {
          if (state.loadFailed) {
            Text(stringResource(R.string.overview_load_failed))
            TextButton(onClick = viewModel::refresh) {
              Text(stringResource(R.string.overview_retry))
            }
          }
        }
  }
}

/**
 * Now, moved on while the screen is composed, so a check-in window opens on its own without an
 * arrival to announce it. The effect is cancelled with the screen, and a minute is fine for a
 * window measured in them.
 */
@Composable
private fun rememberNow(): Instant {
  var now by remember { mutableStateOf(Instant.now()) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(60_000)
      now = Instant.now()
    }
  }
  return now
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
private fun RefreshOnReturn(viewModel: MyEventsViewModel) = RefreshOnReturn(viewModel::refresh)

/** Calls [refresh] each time the screen comes back on top, but not when it is first shown. */
@Composable
private fun RefreshOnReturn(refresh: () -> Unit) {
  // Saveable, as the list leaves the composition while another screen is on top of it.
  var shownOnce by rememberSaveable { mutableStateOf(false) }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (shownOnce) refresh() else shownOnce = true }
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
