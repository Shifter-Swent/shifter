// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
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
import com.swent.shifter.model.RepositoryProvider
import com.swent.shifter.model.location.FusedLocationProvider
import com.swent.shifter.model.location.LocationProvider
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.ui.attendance.AttendanceViewModel
import com.swent.shifter.ui.attendance.EventOverviewScreen
import com.swent.shifter.ui.authentication.SignInScreen
import com.swent.shifter.ui.authentication.SignInViewModel
import com.swent.shifter.ui.chat.ChatChannel
import com.swent.shifter.ui.chat.ChatChannelsScreen
import com.swent.shifter.ui.chat.ChatChannelsViewModel
import com.swent.shifter.ui.chat.TeamChatScreen
import com.swent.shifter.ui.chat.TeamChatViewModel
import com.swent.shifter.ui.checkin.AutoCheckInRoute
import com.swent.shifter.ui.checkin.AutoCheckInViewModel
import com.swent.shifter.ui.components.ShifterNavigationBar
import com.swent.shifter.ui.components.ShifterTopBar
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
import com.swent.shifter.ui.location.LiveLocationPublishing
import com.swent.shifter.ui.map.MapScreen
import com.swent.shifter.ui.map.MapViewModel
import com.swent.shifter.ui.map.WatchedTeams
import com.swent.shifter.ui.membership.MembershipRequestVM
import com.swent.shifter.ui.mission.AddMissionScreen
import com.swent.shifter.ui.mission.AddMissionViewModel
import com.swent.shifter.ui.mission.RepositoryAssigneeCandidates
import com.swent.shifter.ui.overview.OrganizerOverviewScreen
import com.swent.shifter.ui.overview.OrganizerOverviewViewModel
import com.swent.shifter.ui.people.ParticipantsViewModel
import com.swent.shifter.ui.people.PeopleScreen
import com.swent.shifter.ui.planning.PlanningScreen
import com.swent.shifter.ui.planning.PlanningViewModel
import com.swent.shifter.ui.settings.SettingsRoute
import com.swent.shifter.ui.settings.SettingsViewModel
import com.swent.shifter.ui.team.TeamSetupMode
import com.swent.shifter.ui.team.TeamSetupScreen
import com.swent.shifter.ui.team.TeamSetupViewModel
import kotlinx.coroutines.flow.filter

/**
 * The app's navigation graph.
 *
 * @param isSignedIn Whether a session was restored at launch; only read for the start destination.
 * @param repositories The repositories the screens' ViewModels are built from. It has no default,
 *   so a test cannot reach the production backend by forgetting to pass the emulator ones.
 * @param credentialManager The Google account picker of sign-in, and what sign-out clears. A test
 *   passes one that answers with a fake account, as the system picker cannot be driven.
 * @param locationProvider The volunteer's position, for the automatic check-in and their map.
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
    locationProvider: LocationProvider =
        LocalContext.current.let { context ->
          remember(context) { FusedLocationProvider(context) }
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

  // Shares the signed-in volunteer's position while the app is in the foreground, whatever the
  // screen below. Here at the root so it survives navigation, rather than on an event screen.
  LiveLocationPublishing(repositories = repositories, locationProvider = locationProvider)

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
            onOpenEvent = { card -> nav.enterEvent(VolunteerEvent(card.id, card.title)) },
            onOrganizerViewClick = { nav.enterApp(Organizer) },
            onScanQrClick = { nav.navigateTo(Volunteer.QrApply) },
            onAvatarClick = { nav.navigateTo(Volunteer.ProfileSettings) },
        )
      }
      composable<Volunteer.ProfileSettings> {
        SettingsRoute(
            onBack = nav::goBack,
            onSignedOut = { nav.enterApp(SignedOut) },
            onModeChanged = { mode -> nav.enterApp(appOfMode(mode)) },
            viewModel =
                viewModel(factory = SettingsViewModel.factory(repositories, UserMode.VOLUNTEER)),
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
        tab<VolunteerTabs.Overview, VolunteerEventScreen.Overview, VolunteerEvent>(
            VolunteerTabs.Overview,
            nav,
            navController,
            // The Overview lays out its own content, so it is not put in the stand-in page.
            built = true,
            content = { entry ->
              val eventId =
                  remember(entry) {
                    navController
                        .getBackStackEntry<VolunteerEvent>()
                        .toRoute<VolunteerEvent>()
                        .eventId
                  }
              val userId = currentUserId()
              // No session: authState above is already on its way back to sign-in, and nobody is
              // the manager of anything, so no event is loaded on behalf of an empty user.
              if (userId != null) {
                val viewModel: AttendanceViewModel =
                    viewModel(
                        factory =
                            AttendanceViewModel.factory(
                                eventId,
                                userId,
                                repositories.eventRepository,
                                repositories.missionRepository,
                                repositories.teamRepository,
                                repositories.userRepository,
                                repositories.checkInRepository,
                            )
                    )
                // Shows the missions a manager added while the creation form was on top.
                RefreshOnReturn(viewModel::refresh)
                EventOverviewScreen(
                    viewModel = viewModel,
                    onAddMission = { nav.navigateTo(VolunteerEventScreen.AddMission) },
                    volunteerOverview = {
                      // Scrolls as one page, the card and the Planning together.
                      Column(
                          modifier =
                              Modifier.fillMaxSize()
                                  .verticalScroll(rememberScrollState())
                                  .padding(16.dp)
                      ) {
                        AutoCheckInRoute(
                            viewModel =
                                viewModel(
                                    factory =
                                        AutoCheckInViewModel.factory(
                                            repositories.missionRepository,
                                            repositories.checkInRepository,
                                            locationProvider,
                                            currentUserId,
                                        )
                                )
                        )
                        PlanningScreen(
                            viewModel =
                                viewModel(
                                    factory =
                                        PlanningViewModel.factory(
                                            eventId,
                                            repositories.missionRepository,
                                            repositories.checkInRepository,
                                            currentUserId,
                                        )
                                )
                        )
                      }
                    },
                )
              }
            },
        ) {
          composable<VolunteerEventScreen.AddMission> { entry ->
            // The event's id is in the route of the event graph this screen belongs to.
            val eventId =
                remember(entry) {
                  navController
                      .getBackStackEntry<VolunteerEvent>()
                      .toRoute<VolunteerEvent>()
                      .eventId
                }
            AddMissionRoute(eventId, repositories, currentUserId, onBack = nav::goBack)
          }
        }
        mapTab<VolunteerTabs.Map, VolunteerEventScreen.Map, VolunteerEvent>(
            VolunteerTabs.Map,
            nav,
            navController,
            repositories,
            currentUserId,
            locationProvider,
            // A manager follows the volunteers of the teams they manage.
            watchedTeams = { eventId, userId ->
              repositories.teamRepository.getTeamsManagedBy(eventId, userId)
            },
        )
        discussionsTab<
            VolunteerTabs.Discussions,
            VolunteerEventScreen.Discussions,
            VolunteerEventScreen.Channel,
            VolunteerEvent,
        >(
            VolunteerTabs.Discussions,
            asOrganizer = false,
            nav,
            navController,
            repositories,
            currentUserId,
            channel = { VolunteerEventScreen.Channel(it.teamId, it.name) },
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
            onManageEvent = { card -> nav.enterEvent(OrganizerEvent(card.id, card.title)) },
            onCreateEventClick = { nav.navigateTo(Organizer.CreateEvent) },
            onVolunteerViewClick = { nav.enterApp(Volunteer) },
            onAvatarClick = { nav.navigateTo(Organizer.ProfileSettings) },
        )
      }
      composable<Organizer.ProfileSettings> {
        SettingsRoute(
            onBack = nav::goBack,
            onSignedOut = { nav.enterApp(SignedOut) },
            onModeChanged = { mode -> nav.enterApp(appOfMode(mode)) },
            viewModel =
                viewModel(factory = SettingsViewModel.factory(repositories, UserMode.ORGANIZER)),
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
        tab<OrganizerTabs.Overview, OrganizerEventScreen.Overview, OrganizerEvent>(
            OrganizerTabs.Overview,
            nav,
            navController,
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
                  onEditMission = {
                    // A second row tapped before the form opens would reuse its entry, with the
                    // first mission's ViewModel: only the Overview still on top may open one.
                    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                      nav.navigateTo(OrganizerEventScreen.EditMission(it))
                    }
                  },
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
            AddMissionRoute(eventId, repositories, currentUserId, onBack = nav::goBack)
          }
          composable<OrganizerEventScreen.EditMission> { entry ->
            val eventId =
                remember(entry) {
                  navController
                      .getBackStackEntry<OrganizerEvent>()
                      .toRoute<OrganizerEvent>()
                      .eventId
                }
            val missionId =
                remember(entry) { entry.toRoute<OrganizerEventScreen.EditMission>().missionId }
            AddMissionRoute(eventId, repositories, currentUserId, nav::goBack, missionId)
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
            BackHandler { nav.leaveEvent(OrganizerTabs) }
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
                eventTitle = eventTitle<OrganizerEvent>(navController, entry),
                onBack = { nav.leaveEvent(OrganizerTabs) },
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
        discussionsTab<
            OrganizerTabs.Discussions,
            OrganizerEventScreen.Discussions,
            OrganizerEventScreen.Channel,
            OrganizerEvent,
        >(
            OrganizerTabs.Discussions,
            asOrganizer = true,
            nav,
            navController,
            repositories,
            currentUserId,
            channel = { OrganizerEventScreen.Channel(it.teamId, it.name) },
        )
      }
    }
  }
}

/**
 * Registers [tab] as its own graph, so it keeps its own back stack: its home screen, showing
 * [content], given its back stack entry, under a bar with its title and a back button leaving the
 * event, and the [screens] opened on top of it.
 */
private inline fun <reified T : Tab, reified S : Destination, reified E : EventMode> NavGraphBuilder
    .tab(
    tab: Tab,
    nav: NavigationActions,
    navController: NavHostController,
    built: Boolean = false,
    noinline content: @Composable (NavBackStackEntry) -> Unit = {},
    crossinline screens: NavGraphBuilder.() -> Unit = {},
) {
  navigation<T>(startDestination = tab.home) {
    composable<S> { entry ->
      TabPage(tab, nav, eventTitle<E>(navController, entry)) { padding ->
        // Consumed, so the content, e.g. its imePadding, does not leave room for the bars again.
        Box(modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
          if (built) content(entry) else PlaceholderPage { content(entry) }
        }
      }
    }
    screens()
  }
}

/**
 * Registers the Map [tab] of the event open in the graph [E], showing its missions on a map, and
 * the user's position when given a [locationProvider], and the volunteers of [watchedTeams] who
 * share their position. Without [watchedTeams], the map shows no volunteer.
 */
private inline fun <reified T : Tab, reified S : Destination, reified E : EventMode> NavGraphBuilder
    .mapTab(
    tab: Tab,
    nav: NavigationActions,
    navController: NavHostController,
    repositories: RepositoryProvider,
    noinline currentUserId: () -> String?,
    locationProvider: LocationProvider? = null,
    watchedTeams: WatchedTeams? = null,
) {
  navigation<T>(startDestination = tab.home) {
    composable<S> { entry ->
      val eventId = remember(entry) { navController.getBackStackEntry<E>().toRoute<E>().eventId }
      TabPage(tab, nav, eventTitle<E>(navController, entry)) { padding ->
        MapScreen(
            viewModel =
                viewModel(
                    factory =
                        MapViewModel.factory(
                            eventId,
                            repositories.eventRepository,
                            repositories.missionRepository,
                            currentUserId,
                            locationProvider,
                            liveLocations = repositories.liveLocationRepository,
                            watchedTeams = watchedTeams,
                            userRepository = repositories.userRepository,
                        )
                ),
            contentPadding = padding,
        )
      }
    }
  }
}

/**
 * Registers the Discussions [tab] of the event open in the graph [E]: the channels the user can
 * open, every team's for its organizer ([asOrganizer]), and on top of it the channel of a team, as
 * the destination [channel] makes of it, under a bar with the team's name.
 */
private inline fun <
    reified T : Tab,
    reified S : Destination,
    reified C : ChannelScreen,
    reified E : EventMode,
> NavGraphBuilder.discussionsTab(
    tab: Tab,
    asOrganizer: Boolean,
    nav: NavigationActions,
    navController: NavHostController,
    repositories: RepositoryProvider,
    noinline currentUserId: () -> String?,
    noinline channel: (ChatChannel) -> C,
) {
  tab<T, S, E>(
      tab,
      nav,
      navController,
      built = true,
      content = { entry ->
        val eventId = remember(entry) { navController.getBackStackEntry<E>().toRoute<E>().eventId }
        ChatChannelsScreen(
            viewModel =
                viewModel(
                    factory =
                        ChatChannelsViewModel.factory(
                            eventId,
                            asOrganizer,
                            repositories.teamRepository,
                            currentUserId,
                        )
                ),
            onOpenChannel = { nav.navigateTo(channel(it)) },
        )
      },
  ) {
    composable<C> { entry ->
      val eventId = remember(entry) { navController.getBackStackEntry<E>().toRoute<E>().eventId }
      val route = remember(entry) { entry.toRoute<C>() }
      Scaffold(topBar = { ShifterTopBar(route.title, onBack = nav::goBack) }) { padding ->
        TeamChatScreen(
            viewModel =
                viewModel(
                    factory =
                        TeamChatViewModel.factory(
                            eventId,
                            route.teamId,
                            repositories.chatRepository,
                            repositories.userRepository,
                            repositories.teamRepository,
                            currentUserId,
                        )
                ),
            // The bar's height is already taken, so the field only rises by the keyboard's.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )
      }
    }
  }
}

/** The name of the event open in the graph [E], read from its route, kept for [entry]. */
@Composable
private inline fun <reified E : EventMode> eventTitle(
    navController: NavHostController,
    entry: NavBackStackEntry,
): String = remember(entry) { navController.getBackStackEntry<E>().toRoute<E>().eventTitle }

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
 * The mission form of the event [eventId], for its organizer or the manager of one of its teams:
 * the form itself offers each only what they may create. It adds a mission, or edits [missionId]
 * when given.
 *
 * @param onBack goes back to the event's Overview, once the mission is saved or when the user
 *   leaves the form.
 */
@Composable
private fun AddMissionRoute(
    eventId: String,
    repositories: RepositoryProvider,
    currentUserId: () -> String?,
    onBack: () -> Unit,
    missionId: String? = null,
) {
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
                      missionId = missionId,
                  )
          ),
      // Back to the event's overview, the mission saved.
      onMissionSaved = { onBack() },
      onBack = onBack,
  )
}

/** Stand-in for a screen that is not built yet: the padded page of its [content], if any. */
@Composable
private fun PlaceholderPage(content: @Composable () -> Unit) {
  Column(modifier = Modifier.fillMaxSize().padding(16.dp)) { content() }
}

/**
 * A tab of an event showing [content], under a top bar with its title and a way out of the event,
 * and above the floating bar listing the other tabs of the event. [content] fills the page, and
 * gets the padding that keeps it clear of the bars.
 */
@Composable
private fun TabPage(
    current: Tab,
    nav: NavigationActions,
    eventTitle: String,
    content: @Composable (PaddingValues) -> Unit,
) {
  BackHandler { nav.leaveEvent(current.set) }
  Scaffold(
      topBar = { ShifterTopBar(eventTitle, onBack = { nav.leaveEvent(current.set) }) },
      bottomBar = { ShifterNavigationBar(current.set.all, current, nav::switchTab) },
  ) { padding ->
    content(padding)
  }
}

/** The app a user of [mode] enters. */
private fun appOfMode(mode: UserMode): App =
    when (mode) {
      UserMode.VOLUNTEER -> Volunteer
      UserMode.ORGANIZER -> Organizer
    }
