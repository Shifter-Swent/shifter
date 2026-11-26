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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStoreOwner
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
import com.swent.shifter.model.chat.ChannelKind
import com.swent.shifter.model.location.FusedLocationProvider
import com.swent.shifter.model.location.LocationProvider
import com.swent.shifter.model.notification.NotificationTarget
import com.swent.shifter.model.scan.CameraQrScanner
import com.swent.shifter.model.scan.QrScanner
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.ui.attendance.AttendanceViewModel
import com.swent.shifter.ui.attendance.EventOverviewScreen
import com.swent.shifter.ui.authentication.ChooseModeScreen
import com.swent.shifter.ui.authentication.ChooseModeViewModel
import com.swent.shifter.ui.authentication.SignInScreen
import com.swent.shifter.ui.authentication.SignInViewModel
import com.swent.shifter.ui.authentication.StartScreen
import com.swent.shifter.ui.authentication.StartViewModel
import com.swent.shifter.ui.chat.ChatChannelsScreen
import com.swent.shifter.ui.chat.ChatChannelsViewModel
import com.swent.shifter.ui.chat.TeamChatScreen
import com.swent.shifter.ui.chat.TeamChatViewModel
import com.swent.shifter.ui.checkin.AutoCheckInViewModel
import com.swent.shifter.ui.components.ShifterNavigationBar
import com.swent.shifter.ui.components.ShifterTopBar
import com.swent.shifter.ui.event.CreateEventScreen
import com.swent.shifter.ui.event.CreateEventViewModel
import com.swent.shifter.ui.event.EventCreatedScreen
import com.swent.shifter.ui.event.EventCreatedViewModel
import com.swent.shifter.ui.event.JoinEventScreen
import com.swent.shifter.ui.event.JoinEventViewModel
import com.swent.shifter.ui.event.RequestSentSnackbar
import com.swent.shifter.ui.event.putRequestSent
import com.swent.shifter.ui.events.MembershipEventWithdrawer
import com.swent.shifter.ui.events.MyEventsViewModel
import com.swent.shifter.ui.events.OrganizerEventsLoader
import com.swent.shifter.ui.events.StaffEventsLoader
import com.swent.shifter.ui.events.StaffEventsViewModel
import com.swent.shifter.ui.events.avatarInitial
import com.swent.shifter.ui.events.organizer.OrgaEventsScreen
import com.swent.shifter.ui.events.staff.StaffEventsScreen
import com.swent.shifter.ui.location.LiveLocationPublishing
import com.swent.shifter.ui.location.LocalLocationSharing
import com.swent.shifter.ui.location.LocationSharingUi
import com.swent.shifter.ui.location.LocationSharingViewModel
import com.swent.shifter.ui.map.MapScreen
import com.swent.shifter.ui.map.MapViewModel
import com.swent.shifter.ui.map.WatchedTeams
import com.swent.shifter.ui.membership.MembershipRequestVM
import com.swent.shifter.ui.mission.AddMissionScreen
import com.swent.shifter.ui.mission.AddMissionViewModel
import com.swent.shifter.ui.mission.RepositoryAssigneeCandidates
import com.swent.shifter.ui.overview.OrganizerOverviewScreen
import com.swent.shifter.ui.overview.OrganizerOverviewViewModel
import com.swent.shifter.ui.overview.VolunteerOverview
import com.swent.shifter.ui.people.ParticipantsViewModel
import com.swent.shifter.ui.people.PeopleScreen
import com.swent.shifter.ui.people.PeopleTab
import com.swent.shifter.ui.planning.PlanningViewModel
import com.swent.shifter.ui.scan.ScanScreen
import com.swent.shifter.ui.scan.toScanResult
import com.swent.shifter.ui.settings.SettingsRoute
import com.swent.shifter.ui.settings.SettingsViewModel
import com.swent.shifter.ui.sharing.AndroidEventSharer
import com.swent.shifter.ui.sharing.EventSharer
import com.swent.shifter.ui.sharing.SharingScreen
import com.swent.shifter.ui.team.TeamSetupMode
import com.swent.shifter.ui.team.TeamSetupScreen
import com.swent.shifter.ui.team.TeamSetupViewModel
import kotlinx.coroutines.flow.filter

/**
 * The app's navigation graph, with what every screen shares: the volunteer's position published
 * while the app is in the foreground, and its state shown in the top bars (#405).
 *
 * @param isSignedIn Whether a session was restored at launch; only read for the start destination,
 *   which then reads the mode saved on the profile.
 * @param repositories The repositories the screens' ViewModels are built from. It has no default,
 *   so a test cannot reach the production backend by forgetting to pass the emulator ones.
 * @param credentialManager The Google account picker of sign-in, and what sign-out clears. A test
 *   passes one that answers with a fake account, as the system picker cannot be driven.
 * @param locationProvider The user's position, for the volunteer's automatic check-in and the map
 *   of either mode.
 * @param eventSharer What sends an event's join code to another app from its Sharing tab. A test
 *   passes a fake, as the system share sheet cannot be driven.
 * @param qrScanner The camera that reads the event's QR code to join it. A test passes a fake one.
 * @param notificationTarget The screen of the notification tapped, opened once the user is in the
 *   app: at once when they already are, else once signed in and their mode read or chosen.
 * @param onNotificationTargetOpened Called once [notificationTarget] is open, so it is not opened
 *   again.
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
    eventSharer: EventSharer =
        LocalContext.current.let { context -> remember(context) { AndroidEventSharer(context) } },
    qrScanner: QrScanner =
        LocalContext.current.let { context ->
          remember(context) { CameraQrScanner(context.applicationContext) }
        },
    notificationTarget: NotificationTarget? = null,
    onNotificationTargetOpened: () -> Unit = {},
) {
  val nav = rememberNavigationActions(navController)
  val locationSharing: LocationSharingViewModel =
      viewModel(factory = LocationSharingViewModel.factory(repositories))

  // Shares the signed-in volunteer's position while the app is in the foreground, whatever the
  // screen below. Here at the root so it survives navigation, rather than on an event screen.
  LiveLocationPublishing(
      repositories = repositories,
      locationProvider = locationProvider,
      onSharingStates = locationSharing::onSharingStates,
  )

  val sharingStates by locationSharing.sharingStates.collectAsState()
  val sharingLabels by locationSharing.labels.collectAsState()
  val sharing =
      LocationSharingUi(
          states = sharingStates,
          labels = sharingLabels,
          onOpen = locationSharing::loadLabels,
          onOpenSettings = nav::openSettings,
      )
  // Every top bar reads the sharing from here, rather than each screen passing it on.
  CompositionLocalProvider(LocalLocationSharing provides sharing) {
    ShifterNavGraph(
        isSignedIn,
        repositories,
        modifier,
        navController,
        nav,
        credentialManager,
        locationProvider,
        eventSharer,
        qrScanner,
        notificationTarget,
        onNotificationTargetOpened,
    )
  }
}

/** The destinations of [ShifterNavHost], whose parameters it takes. */
@Composable
private fun ShifterNavGraph(
    isSignedIn: Boolean,
    repositories: RepositoryProvider,
    modifier: Modifier,
    navController: NavHostController,
    nav: NavigationActions,
    credentialManager: CredentialManager,
    locationProvider: LocationProvider,
    eventSharer: EventSharer,
    qrScanner: QrScanner,
    notificationTarget: NotificationTarget?,
    onNotificationTargetOpened: () -> Unit,
) {
  val auth = repositories.authRepository
  // Read on every load, so the lists follow the account signed in at that time.
  val currentUserId = { auth.currentUser()?.uid }
  val start = remember { startApp(isSignedIn) }
  // Returns to sign-in whenever the SDK reports no session: signed out, an account disabled or
  // deleted on the server (found at the next token refresh, see AuthRepository.authState), or a
  // session already gone at launch. exitToSignIn does nothing when already on sign-in.
  LaunchedEffect(auth, nav) { auth.authState.filter { it == null }.collect { nav.exitToSignIn() } }

  val channelNames =
      ChannelNames(
          general = stringResource(R.string.chat_channel_general),
          admin = stringResource(R.string.chat_channel_admin),
      )
  val waitingTarget by rememberUpdatedState(notificationTarget)
  val onTargetOpened by rememberUpdatedState(onNotificationTargetOpened)
  // Enters the app of the user's mode, or the screen of the notification tapped, in its own mode,
  // when one waits for the user to be signed in.
  val enterAppOrTarget = { mode: UserMode ->
    val target = waitingTarget
    if (target == null) nav.enterApp(appOfMode(mode))
    else {
      nav.openTarget(target, channelNames)
      onTargetOpened()
    }
  }
  // A notification tapped while in the app opens at once. On sign-in, while the mode is read or
  // chosen, it waits for enterAppOrTarget.
  LaunchedEffect(notificationTarget) {
    if (notificationTarget != null && nav.isInEitherApp()) {
      nav.openTarget(notificationTarget, channelNames)
      onTargetOpened()
    }
  }

  NavHost(navController, startDestination = start, modifier = modifier) {
    navigation<SignedOut>(startDestination = SignedOut.SignIn) {
      // The user enters the mode saved on their profile, or chooses one without a profile.
      composable<SignedOut.SignIn> {
        SignInScreen(
            authViewModel =
                viewModel(factory = SignInViewModel.factory(auth, repositories.userRepository)),
            credentialManager = credentialManager,
            onSignedIn = { saved -> enterAppOrTarget(saved) },
            onNewAccount = { nav.enterApp(ChoosingMode) },
        )
      }
    }

    navigation<Starting>(startDestination = Starting.Loading) {
      composable<Starting.Loading> {
        StartScreen(
            viewModel =
                viewModel(factory = StartViewModel.factory(auth, repositories.userRepository)),
            onModeRead = { mode -> enterAppOrTarget(mode) },
            onNoProfile = { nav.enterApp(ChoosingMode) },
        )
      }
    }

    navigation<ChoosingMode>(startDestination = ChoosingMode.Choice) {
      composable<ChoosingMode.Choice> {
        ChooseModeScreen(
            viewModel =
                viewModel(factory = ChooseModeViewModel.factory(auth, repositories.userRepository)),
            onModeChosen = { mode -> enterAppOrTarget(mode) },
        )
      }
    }

    navigation<Volunteer>(startDestination = Volunteer.Events) {
      composable<Volunteer.Events> { entry ->
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
        val snackbarHostState = remember { SnackbarHostState() }
        RequestSentSnackbar(entry.savedStateHandle, snackbarHostState)
        StaffEventsScreen(
            viewModel = viewModel,
            avatarInitial = remember { avatarInitial(auth.currentUser()) },
            onOpenEvent = { card -> nav.enterEvent(VolunteerEvent(card.id, card.title)) },
            onScanQrClick = { nav.navigateTo(Volunteer.QrApply) },
            onAvatarClick = { nav.navigateTo(Volunteer.ProfileSettings) },
            snackbarHostState = snackbarHostState,
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
      composable<Volunteer.QrApply> { entry ->
        // On the event, Back returns to the code; on the code, the screen's Back and the device's
        // leave it, back to the events list. Once a request is sent, the list confirms it.
        val joinEvent = joinEventViewModel(entry, repositories, currentUserId)
        JoinEventScreen(
            viewModel = joinEvent,
            onLeave = nav::goBack,
            onScanQrCode = {
              // The error of a code typed before would show as the result of the scan.
              joinEvent.changeCode()
              nav.navigateTo(Volunteer.Scan)
            },
            onRequestSent = { eventTitle ->
              navController
                  .getBackStackEntry<Volunteer.Events>()
                  .savedStateHandle
                  .putRequestSent(eventTitle)
              nav.backTo(Volunteer.Events)
            },
        )
      }
      composable<Volunteer.Scan> { entry ->
        // A code read fills in the join screen under it, which looks its event up, as typed. The
        // scan shows what it found: Continue goes back to the join screen, showing the event.
        val joinEntry = remember(entry) { navController.getBackStackEntry<Volunteer.QrApply>() }
        val joinEvent = joinEventViewModel(joinEntry, repositories, currentUserId)
        val joinState by joinEvent.uiState.collectAsState()
        // Leaving any other way, the system Back included, drops the result, even one still looked
        // up: the join screen keeps the code read, filled in.
        val leave = {
          joinEvent.cancelLookup()
          nav.goBack()
        }
        ScanScreen(
            scanner = qrScanner,
            onCodeScanned = joinEvent::onCodeScanned,
            onEnterCode = leave,
            onBack = leave,
            result = joinState.toScanResult(),
            onContinue = nav::goBack,
            onScanAgain = joinEvent::changeCode,
        )
      }
      navigation<VolunteerEvent>(startDestination = VolunteerTabs.home) {
        tab<VolunteerTabs.Overview, VolunteerEventScreen.Overview, VolunteerEvent>(
            VolunteerTabs.Overview,
            nav,
            navController,
            // The Overview lays out its own content, so it is not put in the stand-in page.
            built = true,
            content = { entry, event ->
              val eventId = event.eventId
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
                // Shows the missions a manager added or changed while the form was on top.
                RefreshOnReturn(viewModel::refresh)
                val generalChannel = stringResource(R.string.chat_channel_general)
                EventOverviewScreen(
                    viewModel = viewModel,
                    clock = repositories.minuteClock,
                    // The now card shows the whole team, so the Map opens as its tab, with no
                    // mission of its own to center on.
                    onViewOnMap = { nav.switchTab(VolunteerTabs.Map) },
                    onAddMission = { nav.navigateTo(VolunteerEventScreen.AddMission) },
                    onEditMission = {
                      // A second pencil tapped before the form opens would reuse its entry, with
                      // the first mission's ViewModel: only the Overview still on top may open one.
                      if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                        nav.navigateTo(VolunteerEventScreen.EditMission(it))
                      }
                    },
                    volunteerOverview = {
                      VolunteerOverview(
                          planning =
                              viewModel(
                                  factory =
                                      PlanningViewModel.factory(
                                          eventId,
                                          repositories.missionRepository,
                                          repositories.checkInRepository,
                                          repositories.eventRepository,
                                          repositories.minuteClock,
                                          currentUserId,
                                      )
                              ),
                          autoCheckIn =
                              viewModel(
                                  factory =
                                      AutoCheckInViewModel.factory(
                                          repositories.missionRepository,
                                          repositories.checkInRepository,
                                          locationProvider,
                                          currentUserId,
                                      )
                              ),
                          clock = repositories.minuteClock,
                          onOpenOnMap = { mission ->
                            navController
                                .getBackStackEntry<VolunteerEvent>()
                                .savedStateHandle[OPEN_MISSION_KEY] = mission.id
                            nav.switchTab(VolunteerTabs.Map)
                          },
                          onContact = { mission ->
                            // As for the pencil, a second tap before the channel opens is ignored.
                            if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                              nav.openChannel(
                                  VolunteerTabs.Chat,
                                  contactChannel(mission, generalChannel),
                              )
                            }
                          },
                      )
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
          composable<VolunteerEventScreen.EditMission> { entry ->
            val eventId =
                remember(entry) {
                  navController
                      .getBackStackEntry<VolunteerEvent>()
                      .toRoute<VolunteerEvent>()
                      .eventId
                }
            val missionId =
                remember(entry) { entry.toRoute<VolunteerEventScreen.EditMission>().missionId }
            AddMissionRoute(eventId, repositories, currentUserId, nav::goBack, missionId)
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
        chatTab<
            VolunteerTabs.Chat,
            VolunteerEventScreen.Chat,
            VolunteerEventScreen.Channel,
            VolunteerEvent,
        >(
            VolunteerTabs.Chat,
            asOrganizer = false,
            nav,
            navController,
            repositories,
            currentUserId,
            channel = VolunteerEventScreen::Channel,
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
            content = { entry, event ->
              val eventId = event.eventId
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
                              repositories.eventRepository,
                          )
                  )
              // Shows the missions added while the creation form was on top.
              RefreshOnReturn(viewModel::refresh)
              OrganizerOverviewScreen(
                  viewModel = viewModel,
                  clock = repositories.minuteClock,
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
            val eventId = eventRoute<OrganizerEvent>(navController, entry).eventId
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
            val event = eventRoute<OrganizerEvent>(navController, entry)
            val eventId = event.eventId
            // Asked by a notification of new requests. Read once: the tab picked is then saved by
            // the screen, so coming back to People later shows the tab left. Read in composition,
            // as openTarget always enters the app afresh: People is then a new entry, never one
            // already shown.
            val initialTab =
                remember(entry) {
                  val handle = navController.getBackStackEntry<OrganizerEvent>().savedStateHandle
                  if (handle.remove<Boolean>(OPEN_REQUESTS_KEY) == true) PeopleTab.REQUESTS
                  else PeopleTab.PARTICIPANTS
                }
            val requests: MembershipRequestVM =
                viewModel(
                    factory =
                        MembershipRequestVM.factory(
                            eventId,
                            repositories.membershipRequestRepository,
                            repositories.userRepository,
                            repositories.eventRepository,
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
                onMoveToTeam = participants::moveToTeam,
                eventTitle = event.eventTitle,
                onBack = { nav.leaveEvent(OrganizerTabs) },
                onNavigate = nav::switchTab,
                onManagerChangeFailureShown = participants::onManagerChangeFailureShown,
                initialTab = initialTab,
            )
          }
        }
        mapTab<OrganizerTabs.Map, OrganizerEventScreen.Map, OrganizerEvent>(
            OrganizerTabs.Map,
            nav,
            navController,
            repositories,
            currentUserId,
            // Shows the organizer where they are, to them alone: the map shares nothing.
            locationProvider,
        )
        chatTab<
            OrganizerTabs.Chat,
            OrganizerEventScreen.Chat,
            OrganizerEventScreen.Channel,
            OrganizerEvent,
        >(
            OrganizerTabs.Chat,
            asOrganizer = true,
            nav,
            navController,
            repositories,
            currentUserId,
            channel = OrganizerEventScreen::Channel,
        )
        tab<OrganizerTabs.Sharing, OrganizerEventScreen.Sharing, OrganizerEvent>(
            OrganizerTabs.Sharing,
            nav,
            navController,
            built = true,
            content = { _, event ->
              SharingScreen(
                  viewModel =
                      viewModel(
                          factory =
                              EventCreatedViewModel.factory(
                                  repositories.eventRepository,
                                  event.eventId,
                              )
                      ),
                  sharer = eventSharer,
              )
            },
        )
      }
    }
  }
}

/**
 * Registers [tab] as its own graph, so it keeps its own back stack: its home screen, showing
 * [content], given its back stack entry and event route, under a bar with the event's name and a
 * back button leaving the event, and the [screens] opened on top of it.
 */
private inline fun <reified T : Tab, reified S : Destination, reified E : EventMode> NavGraphBuilder
    .tab(
    tab: Tab,
    nav: NavigationActions,
    navController: NavHostController,
    built: Boolean = false,
    noinline content: @Composable (NavBackStackEntry, E) -> Unit = { _, _ -> },
    crossinline screens: NavGraphBuilder.() -> Unit = {},
) {
  navigation<T>(startDestination = tab.home) {
    composable<S> { entry ->
      val event = eventRoute<E>(navController, entry)
      TabPage(tab, nav, event.eventTitle) { padding ->
        // Consumed, so the content, e.g. its imePadding, does not leave room for the bars again.
        Box(modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
          if (built) content(entry, event) else PlaceholderPage { content(entry, event) }
        }
      }
    }
    screens()
  }
}

/**
 * The key, in the saved state of an event's graph, of the mission its Map tab is asked to open: the
 * tabs share no ViewModel, and the Map's route takes no argument as the home of its tab.
 */
private const val OPEN_MISSION_KEY = "openMission"

/**
 * Registers the Map [tab] of the event open in the graph [E], showing its missions on a map, the
 * user's position read from [locationProvider], and the volunteers of [watchedTeams] who share
 * their position. Without [watchedTeams], the map shows no volunteer.
 */
private inline fun <reified T : Tab, reified S : Destination, reified E : EventMode> NavGraphBuilder
    .mapTab(
    tab: Tab,
    nav: NavigationActions,
    navController: NavHostController,
    repositories: RepositoryProvider,
    noinline currentUserId: () -> String?,
    locationProvider: LocationProvider,
    watchedTeams: WatchedTeams? = null,
) {
  navigation<T>(startDestination = tab.home) {
    composable<S> { entry ->
      val event = eventRoute<E>(navController, entry)
      val viewModel: MapViewModel =
          viewModel(
              factory =
                  MapViewModel.factory(
                      event.eventId,
                      repositories.eventRepository,
                      repositories.missionRepository,
                      currentUserId,
                      repositories.minuteClock,
                      locationProvider,
                      liveLocations = repositories.liveLocationRepository,
                      watchedTeams = watchedTeams,
                      userRepository = repositories.userRepository,
                  )
          )
      // A mission another tab asked to show, such as the now card's "View on map": read once, so
      // coming back to the map later does not open it again.
      val handle = remember(entry) { navController.getBackStackEntry<E>().savedStateHandle }
      val missionToOpen by handle.getStateFlow<String?>(OPEN_MISSION_KEY, null).collectAsState()
      LaunchedEffect(missionToOpen) {
        missionToOpen?.let {
          viewModel.openMission(it)
          handle[OPEN_MISSION_KEY] = null
        }
      }
      TabPage(tab, nav, event.eventTitle) { padding ->
        MapScreen(viewModel = viewModel, contentPadding = padding)
      }
    }
  }
}

/**
 * Registers the Chat [tab] of the event open in the graph [E]: the event's channels and the team
 * channels the user can open, every team's for its organizer ([asOrganizer]), and on top of it the
 * channel picked, as the destination [channel] makes of its kind, team id (for a team's channel)
 * and name, under a bar with that name, where the organizer may announce in the event's channels.
 */
private inline fun <
    reified T : Tab,
    reified S : Destination,
    reified C : ChannelScreen,
    reified E : EventMode,
> NavGraphBuilder.chatTab(
    tab: Tab,
    asOrganizer: Boolean,
    nav: NavigationActions,
    navController: NavHostController,
    repositories: RepositoryProvider,
    noinline currentUserId: () -> String?,
    noinline channel: (kind: ChannelKind, teamId: String?, name: String?) -> C,
) {
  tab<T, S, E>(
      tab,
      nav,
      navController,
      built = true,
      content = { _, event ->
        ChatChannelsScreen(
            viewModel =
                viewModel(
                    factory =
                        ChatChannelsViewModel.factory(
                            event.eventId,
                            asOrganizer,
                            repositories.teamRepository,
                            currentUserId,
                        )
                ),
            onOpenChannel = { kind, teamId, name -> nav.navigateTo(channel(kind, teamId, name)) },
        )
      },
  ) {
    composable<C> { entry ->
      val eventId = eventRoute<E>(navController, entry).eventId
      val route = remember(entry) { entry.toRoute<C>() }
      val chat: TeamChatViewModel =
          viewModel(
              factory =
                  TeamChatViewModel.factory(
                      route.channelId(eventId),
                      asOrganizer,
                      repositories.chatRepository,
                      repositories.userRepository,
                      repositories.teamRepository,
                      currentUserId,
                  )
          )
      // A team's channel opened without its name shows the team's once read.
      val chatState by chat.uiState.collectAsState()
      val title = route.name ?: chatState.teamName ?: stringResource(R.string.chat_channel_team)
      Scaffold(topBar = { ShifterTopBar(title, onBack = nav::goBack) }) { padding ->
        TeamChatScreen(
            viewModel = chat,
            // The bar's height is already taken, so the field only rises by the keyboard's.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )
      }
    }
  }
}

/** The event open in the graph [E], decoded once and kept for [entry]. */
@Composable
private inline fun <reified E : EventMode> eventRoute(
    navController: NavHostController,
    entry: NavBackStackEntry,
): E = remember(navController, entry) { navController.getBackStackEntry<E>().toRoute<E>() }

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

/**
 * The join screen's ViewModel, kept in [owner], the join screen's entry, which the scan fills too.
 */
@Composable
private fun joinEventViewModel(
    owner: ViewModelStoreOwner,
    repositories: RepositoryProvider,
    currentUserId: () -> String?,
): JoinEventViewModel =
    viewModel(
        viewModelStoreOwner = owner,
        factory =
            JoinEventViewModel.factory(
                repositories.eventRepository,
                repositories.membershipRequestRepository,
                repositories.teamRepository,
                repositories.shiftRepository,
                currentUserId,
            ),
    )
