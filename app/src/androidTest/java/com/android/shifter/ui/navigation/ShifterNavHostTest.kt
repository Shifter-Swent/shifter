// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.AuthEmulator
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.firebase.signInForFirestore
import com.swent.shifter.model.RepositoryProvider
import com.swent.shifter.model.RepositoryProviderFactory
import com.swent.shifter.model.authentication.AuthUser
import com.swent.shifter.model.chat.ChatChannelId
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.location.FakeLocationProvider
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.model.team.TeamRepositoryException
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.ui.attendance.ManagerOverviewTestTags
import com.swent.shifter.ui.authentication.SignInScreenTestTags
import com.swent.shifter.ui.chat.ChatChannelsTestTags
import com.swent.shifter.ui.chat.TeamChatTestTags
import com.swent.shifter.ui.checkin.AutoCheckInTestTags
import com.swent.shifter.ui.event.CreateEventScreenTestTags
import com.swent.shifter.ui.event.EventCreatedScreenTestTags
import com.swent.shifter.ui.event.JoinEventScreenTestTags
import com.swent.shifter.ui.events.MyEventsTestTags
import com.swent.shifter.ui.map.MapScreenTestTags
import com.swent.shifter.ui.mission.AddMissionScreenTestTags
import com.swent.shifter.ui.overview.OrganizerOverviewScreenTestTags
import com.swent.shifter.ui.people.ParticipantsListTestTags
import com.swent.shifter.ui.planning.PlanningTestTags
import com.swent.shifter.ui.settings.SettingsScreenTestTags
import com.swent.shifter.ui.team.CreateTeamFormTestTags
import com.swent.shifter.ui.team.TeamIcon
import com.swent.shifter.ui.team.TeamSetupScreenTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that [ShifterNavHost] starts on the right destination and links every screen. */
@RunWith(AndroidJUnit4::class)
class ShifterNavHostTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  /** Every repository on the emulators, so no test reaches the production backend. */
  private val repositories =
      RepositoryProviderFactory.create(FirestoreEmulator.firestore, AuthEmulator.auth)

  @Before
  fun setUp() {
    AuthEmulator.auth.signOut()
  }

  /** Documents written by a test, deleted afterwards so the next tests start from a clean state. */
  private val createdPaths = mutableListOf<String>()

  /** The team created with each event, keyed by event id, for volunteers to apply to. */
  private val teamIds = mutableMapOf<String, String>()

  @After
  fun tearDown() {
    runBlocking { createdPaths.forEach { FirestoreEmulatorAdmin.deleteDocument(it) } }
    AuthEmulator.auth.signOut()
    AuthEmulator.clearAccounts()
  }

  @Test
  fun signedOut_startsOnSignIn() {
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = false, repositories = repositories) }
    }

    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOGIN_BUTTON).assertIsDisplayed()
  }

  @Test
  fun signedIn_skipsSignInAndStartsOnVolunteerEvents() {
    signInAndShowApp()

    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOGIN_BUTTON).assertDoesNotExist()
  }

  @Test
  fun staffEvents_avatarOpensSettingsAndBackReturns() {
    signInAndShowApp()

    composeTestRule.onNodeWithTag(MyEventsTestTags.AVATAR).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.SCREEN).assertIsDisplayed()

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
  }

  @Test
  fun settings_signOutClearsTheSessionAndReturnsToSignIn() {
    val navController = signInAndShowApp()

    composeTestRule.onNodeWithTag(MyEventsTestTags.AVATAR).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.SIGN_OUT_BUTTON).performClick()

    waitForNode(SignInScreenTestTags.LOGIN_BUTTON)
    assertNull(AuthEmulator.auth.currentUser)
    // Settings and the session listener both react to the sign-out: only one may navigate.
    composeTestRule.waitForIdle()
    val signInEntries =
        navController.currentBackStack.value.count { it.destination.hasRoute<SignedOut.SignIn>() }
    assertEquals(1, signInEntries)
  }

  @Test
  fun sessionAlreadyGoneAtLaunch_returnsToSignIn() {
    // Started as signed in, but the session ended before the app listened to it.
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = repositories) }
    }

    waitForNode(SignInScreenTestTags.LOGIN_BUTTON)
  }

  @Test
  fun sessionEndingOutsideTheApp_returnsToSignIn() {
    signInAndShowApp()

    // The session ends without any screen asking for it, as when the SDK finds the account gone.
    AuthEmulator.auth.signOut()

    waitForNode(SignInScreenTestTags.LOGIN_BUTTON)
  }

  @Test
  fun sessionEndingOutsideTheApp_fromOrganizerMode_returnsToSignIn() {
    signInAndShowApp()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_SCREEN).assertIsDisplayed()

    AuthEmulator.auth.signOut()

    waitForNode(SignInScreenTestTags.LOGIN_BUTTON)
  }

  @Test
  fun staffEvents_switchesToThePastTab() {
    signInAndShowApp()

    composeTestRule.onNodeWithTag(MyEventsTestTags.PAST_TAB).performClick()

    composeTestRule.onNodeWithTag(MyEventsTestTags.PAST_TAB).assertIsSelected()
  }

  @Test
  fun staffEvents_scanQrOpensJoinEventAndBackReturns() {
    signInAndShowApp()

    composeTestRule.onNodeWithTag(MyEventsTestTags.SCAN_QR_BUTTON).performClick()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.JOIN_CODE_FIELD).assertIsDisplayed()

    pressBack()
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
  }

  @Test
  fun joinEvent_keepsTheTypedCode() {
    signInAndShowApp()

    composeTestRule.onNodeWithTag(MyEventsTestTags.SCAN_QR_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.JOIN_CODE_FIELD)
        .performTextInput("ABC234")

    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.JOIN_CODE_FIELD)
        .assertTextContains("ABC234")
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.FIND_BUTTON).assertIsEnabled()
  }

  @Test
  fun joinEvent_appliesToTheTeamPicked() {
    val event = createEvent(signIn())
    val volunteer = signIn()
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = repositories) }
    }
    composeTestRule.onNodeWithTag(MyEventsTestTags.SCAN_QR_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.JOIN_CODE_FIELD)
        .performTextInput(event.joinCode)
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.FIND_BUTTON).performClick()

    val teamId = teamIds.getValue(event.id)
    waitForNode(JoinEventScreenTestTags.teamOption(teamId))
    // The team list sits below the event details, out of view on small screens.
    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.teamOption(teamId))
        .performScrollTo()
        .performClick()
    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON)
        .assertIsEnabled()
        .performClick()

    waitForNode(JoinEventScreenTestTags.APPLIED_MESSAGE)
    // Deleted before its event, as Firestore does not cascade the deletion.
    createdPaths.add(0, "events/${event.id}/membershipRequests/${volunteer.uid}")
    val request = runBlocking {
      repositories.membershipRequestRepository.getMembershipRequest(event.id, volunteer.uid)
    }
    assertEquals(teamId, request?.teamId)
  }

  @Test
  fun joinEvent_findingAnAppliedEventShowsTheRequestAndTheEventInTheList() {
    val event = createEvent(signIn())
    val volunteer = signIn()
    applyTo(event, volunteer)
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = repositories) }
    }
    composeTestRule.onNodeWithTag(MyEventsTestTags.SCAN_QR_BUTTON).performClick()

    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.JOIN_CODE_FIELD)
        .performTextInput(event.joinCode)
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.FIND_BUTTON).performClick()
    waitForNode(JoinEventScreenTestTags.EVENT_TITLE)
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.EVENT_TITLE).assertTextEquals(event.title)

    // Finding the event shows the request already sent instead of the Apply button.
    waitForNode(JoinEventScreenTestTags.APPLIED_MESSAGE)
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).assertDoesNotExist()
    val request = runBlocking {
      repositories.membershipRequestRepository.getMembershipRequest(event.id, volunteer.uid)
    }
    assertEquals(MembershipRequestStatus.PENDING, request?.status)

    // The screen's Back returns to the code, then the device Back leaves to the events list.
    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.JOIN_CODE_FIELD).assertIsDisplayed()
    pressBack()
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
    // The event applied to shows up in the list.
    waitForNode(MyEventsTestTags.eventCard(event.id))
  }

  @Test
  fun joinEvent_unknownCodeShowsAnError() {
    signIn()
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = repositories) }
    }
    composeTestRule.onNodeWithTag(MyEventsTestTags.SCAN_QR_BUTTON).performClick()

    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.JOIN_CODE_FIELD)
        .performTextInput("ZZZZ22")
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.FIND_BUTTON).performClick()

    waitForNode(JoinEventScreenTestTags.ERROR_MESSAGE)
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.EVENT_TITLE).assertDoesNotExist()
  }

  @Test
  fun organizerView_switchesModesBothWays() {
    signInAndShowApp()

    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_SCREEN).assertIsDisplayed()

    composeTestRule.onNodeWithTag(MyEventsTestTags.VOLUNTEER_VIEW_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
  }

  @Test
  fun organizerEvents_avatarOpensSettingsAndBackReturns() {
    signInAndShowApp()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()

    composeTestRule.onNodeWithTag(MyEventsTestTags.AVATAR).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.SCREEN).assertIsDisplayed()

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_SCREEN).assertIsDisplayed()
  }

  @Test
  fun organizerEvents_createEventOpensTheFormAndBackReturns() {
    signInAndShowApp()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()

    composeTestRule.onNodeWithTag(MyEventsTestTags.CREATE_EVENT_BUTTON).performClick()
    composeTestRule.onNodeWithTag(CreateEventScreenTestTags.TITLE_FIELD).assertIsDisplayed()

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_SCREEN).assertIsDisplayed()
  }

  @Test
  fun createEvent_showsTheCodeOfTheSavedEventAndDoneReturnsToTheEvents() {
    val organizer = openCreateEventForm()

    val event = submitValidEventForm(organizer)
    createTeamInSetup(event, "Logistics")
    proceedFromTeamSetup()

    composeTestRule
        .onNodeWithTag(EventCreatedScreenTestTags.EVENT_TITLE)
        .assertTextEquals(event.title)
    composeTestRule
        .onNodeWithTag(EventCreatedScreenTestTags.JOIN_CODE)
        .assertTextEquals(event.joinCode)

    composeTestRule.onNodeWithTag(EventCreatedScreenTestTags.DONE_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_SCREEN).assertIsDisplayed()
    waitForNode(MyEventsTestTags.eventCard(event.id))
  }

  @Test
  fun eventCreated_deviceBackSkipsTheFormAndReturnsToTheEvents() {
    val organizer = openCreateEventForm()
    val event = submitValidEventForm(organizer)
    createTeamInSetup(event, "Logistics")
    proceedFromTeamSetup()

    pressBack()

    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(CreateEventScreenTestTags.TITLE_FIELD).assertDoesNotExist()
  }

  @Test
  fun createEvent_opensTheTeamsOfTheEventBeforeItsJoinCode() {
    val organizer = openCreateEventForm()

    submitValidEventForm(organizer)

    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.SECTION_TITLE).assertIsDisplayed()
    // The code is the step after the teams, not instead of them.
    composeTestRule.onNodeWithTag(EventCreatedScreenTestTags.JOIN_CODE).assertDoesNotExist()
  }

  @Test
  fun teamSetup_savesTheTeamUnderTheEventAndLeadsToItsCode() {
    val organizer = openCreateEventForm()
    val event = submitValidEventForm(organizer)

    createTeamInSetup(event, "Logistics")
    proceedFromTeamSetup()

    // The team belongs to the event the step was opened for, and the code is that event's.
    val teams = runBlocking { repositories.teamRepository.getTeamsByEvent(event.id) }
    assertEquals(listOf("Logistics"), teams.map { it.name })
    assertEquals(event.id, teams.single().eventId)
    composeTestRule
        .onNodeWithTag(EventCreatedScreenTestTags.JOIN_CODE)
        .assertTextEquals(event.joinCode)
  }

  @Test
  fun teamSetup_deviceBackReturnsToTheEventsAndLeavesTheEventSaved() {
    val organizer = openCreateEventForm()
    val event = submitValidEventForm(organizer)

    pressBack()

    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_SCREEN).assertIsDisplayed()
    // Neither the form that would create a second event, nor the teams step, comes back.
    composeTestRule.onNodeWithTag(CreateEventScreenTestTags.TITLE_FIELD).assertDoesNotExist()
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.SECTION_TITLE).assertDoesNotExist()
    // The event stays: leaving the teams behind does not undo it, nor create it twice.
    assertEquals(
        listOf(event.id),
        runBlocking { repositories.eventRepository.getEventsByOrganizer(organizer.uid) }
            .map { it.id },
    )
    waitForNode(MyEventsTestTags.eventCard(event.id))
  }

  @Test
  fun settings_changeModeEntersTheEventsOfTheNewMode() {
    signInAndShowApp()
    val user = checkNotNull(repositories.authRepository.currentUser())
    runBlocking {
      repositories.userRepository.getOrCreateUser(user.uid, "Ada", "", UserMode.VOLUNTEER)
    }
    createdPaths += listOf("users/${user.uid}", "publicProfiles/${user.uid}")

    composeTestRule.onNodeWithTag(MyEventsTestTags.AVATAR).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.CHANGE_MODE_BUTTON).performClick()

    waitForNode(MyEventsTestTags.ORGANIZER_SCREEN)
    assertEquals(
        UserMode.ORGANIZER,
        runBlocking { repositories.userRepository.getUser(user.uid) }?.mode,
    )
    // The back stack is cleared: the Volunteer events are gone.
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertDoesNotExist()
  }

  @Test
  fun organizerEvent_overviewOpensTeamManagementAndBackReturnsToIt() {
    val event = createEvent(signIn())
    openOrganizerEvent(event)
    waitForNode(OrganizerOverviewScreenTestTags.MANAGE_TEAMS_BUTTON)

    composeTestRule
        .onNodeWithTag(OrganizerOverviewScreenTestTags.MANAGE_TEAMS_BUTTON)
        .performClick()
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.SECTION_TITLE).assertIsDisplayed()

    pressBack()

    waitForNode(OrganizerOverviewScreenTestTags.SCREEN)
    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertTextEquals(event.title)
  }

  @Test
  fun organizerEvent_teamManagementListsTheTeamsTheEventAlreadyHas() {
    val event = createEvent(signIn())
    val bar = createTeam(event, "Bar")
    openOrganizerEvent(event)
    waitForNode(OrganizerOverviewScreenTestTags.MANAGE_TEAMS_BUTTON)

    composeTestRule
        .onNodeWithTag(OrganizerOverviewScreenTestTags.MANAGE_TEAMS_BUTTON)
        .performClick()

    waitForNode(TeamSetupScreenTestTags.savedTeam(bar.id))
  }

  @Test
  fun organizerEvent_overviewFiltersOnATeamAddedInManagement() {
    val event = createEvent(signIn())
    openOrganizerEvent(event)
    waitForNode(OrganizerOverviewScreenTestTags.MANAGE_TEAMS_BUTTON)
    composeTestRule
        .onNodeWithTag(OrganizerOverviewScreenTestTags.MANAGE_TEAMS_BUTTON)
        .performClick()
    // The event already has a team, so the step lists it rather than opening a form of its own.
    waitForNode(TeamSetupScreenTestTags.ADD_ANOTHER_BUTTON)
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.ADD_ANOTHER_BUTTON).performClick()
    waitForNode(TeamSetupScreenTestTags.ACTIVE_FORM)

    val teamId = createTeamInSetup(event, "Logistics")
    // Done returns to the Overview, which reloads and offers the new team as a filter.
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.PROCEED_BUTTON).performClick()

    waitForNode(OrganizerOverviewScreenTestTags.SCREEN)
    waitForNode(OrganizerOverviewScreenTestTags.teamChip(teamId))
  }

  /**
   * An organizer creates an event and two teams through the real forms, then a volunteer finds the
   * event by its code and applies to one of those very teams. The other tests take one side at a
   * time, over teams seeded by hand; this is the one that shows the two halves meet.
   */
  @Test
  fun teamsCreatedByTheOrganizer_areTheOnesAVolunteerAppliesTo() {
    val navController = signInAndShowApp(name = "Olga")
    val organizer = checkNotNull(repositories.authRepository.currentUser())

    // ── The organizer creates the event, then its teams, through the forms ──
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.CREATE_EVENT_BUTTON).performClick()
    val event = submitValidEventForm(organizer)
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.SECTION_TITLE).assertIsDisplayed()

    val logistics = createTeamInSetup(event, "Logistics")
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.ADD_ANOTHER_BUTTON).performClick()
    val bar = createTeamInSetup(event, "Bar")

    // Both teams belong to this event, and nothing created a third.
    val teams = runBlocking { repositories.teamRepository.getTeamsByEvent(event.id) }
    assertEquals(setOf("Logistics", "Bar"), teams.map { it.name }.toSet())
    assertEquals(listOf(event.id, event.id), teams.map { it.eventId })

    proceedFromTeamSetup()
    composeTestRule
        .onNodeWithTag(EventCreatedScreenTestTags.JOIN_CODE)
        .assertTextEquals(event.joinCode)

    // ── A volunteer takes over the session and applies with that code ──
    val volunteer = signIn()
    composeTestRule.waitForIdle()
    composeTestRule.runOnUiThread { NavigationActions(navController).enterApp(Volunteer) }
    waitForNode(MyEventsTestTags.STAFF_SCREEN)

    composeTestRule.onNodeWithTag(MyEventsTestTags.SCAN_QR_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.JOIN_CODE_FIELD)
        .performTextInput(event.joinCode)
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.FIND_BUTTON).performClick()

    // Both teams the organizer just made are offered, and neither belongs to another event.
    waitForNode(JoinEventScreenTestTags.teamOption(logistics))
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.teamOption(bar)).assertExists()

    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.teamOption(bar))
        .performScrollTo()
        .performClick()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).assertIsEnabled()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).performClick()
    waitForNode(JoinEventScreenTestTags.APPLIED_MESSAGE)

    // Deleted before its event, as Firestore does not cascade the deletion.
    createdPaths.add(0, "events/${event.id}/membershipRequests/${volunteer.uid}")
    // Found under this event, so the request hangs where it belongs; the event is its path, not
    // one of its fields.
    val request = runBlocking {
      repositories.membershipRequestRepository.getMembershipRequest(event.id, volunteer.uid)
    }
    assertEquals(bar, request?.teamId)
    assertEquals(volunteer.uid, request?.userId)
    assertEquals(MembershipRequestStatus.PENDING, request?.status)
  }

  @Test
  fun eventsLists_showTheInitialOfTheSignedInUser() {
    signInAndShowApp(name = "grace")

    composeTestRule.onNodeWithTag(MyEventsTestTags.AVATAR).assertTextEquals("G")
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.AVATAR).assertTextEquals("G")
  }

  @Test
  fun volunteerEvent_opensOnOverviewAndReachesEveryTab() {
    val nav = setAppAndGetNavigation()

    composeTestRule.runOnUiThread { nav.enterEvent(VolunteerEvent("event-1", EVENT_TITLE)) }

    assertTabsReachable(VolunteerTabs)
    pressBack()
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
  }

  @Test
  fun volunteerOverview_showsTheAutomaticCheckIn() {
    val event = acceptedEvent(asManager = false)
    showApp()

    openEvent(event)

    waitForNode(AutoCheckInTestTags.CARD)
    composeTestRule.onNodeWithTag(AutoCheckInTestTags.CARD).assertIsDisplayed()
  }

  @Test
  fun volunteerOverview_showsTheMissionsAssignedToTheVolunteer() {
    val organizerAccount = googleCredential("Olga")
    val volunteerAccount = googleCredential("Vera")
    val event = acceptedEvent(asManager = false, organizerAccount, volunteerAccount)
    val volunteer = signIn(volunteerAccount)
    signIn(organizerAccount)
    val mission = runBlocking {
      repositories.missionRepository.createMission(
          Mission(
              eventId = event.id,
              title = "Pick up the litter",
              description = "Along the shore.",
              teamId = teamIds.getValue(event.id),
              volunteersNeeded = 1,
              startAt = event.startAt,
              endAt = event.startAt.plus(Duration.ofHours(1)),
              assigneeIds = listOf(volunteer.uid),
              createdAt = Instant.now(),
          )
      )
    }
    // Deleted before its event, as Firestore does not cascade the deletion.
    createdPaths.add(0, "events/${event.id}/missions/${mission.id}")
    signIn(volunteerAccount)
    showApp()

    openEvent(event)

    waitForNode(PlanningTestTags.mission(mission.id))
    composeTestRule.onNodeWithText("Pick up the litter").assertIsDisplayed()
  }

  @Test
  fun organizerEvent_opensOnOverviewAndReachesEveryTab() {
    val nav = setAppAndGetNavigation()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()

    composeTestRule.runOnUiThread { nav.enterEvent(OrganizerEvent("event-1", EVENT_TITLE)) }

    assertTabsReachable(OrganizerTabs)
    pressBack()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_SCREEN).assertIsDisplayed()
  }

  @Test
  fun organizerEvent_backButtonOnEveryTab_returnsToTheEvents() {
    val nav = setAppAndGetNavigation()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()

    assertBackButtonLeavesEveryTab(
        nav,
        OrganizerEvent("event-1", EVENT_TITLE),
        MyEventsTestTags.ORGANIZER_SCREEN,
    )
  }

  @Test
  fun volunteerEvent_backButtonOnEveryTab_returnsToTheEvents() {
    val nav = setAppAndGetNavigation()

    assertBackButtonLeavesEveryTab(
        nav,
        VolunteerEvent("event-1", EVENT_TITLE),
        MyEventsTestTags.STAFF_SCREEN,
    )
  }

  @Test
  fun organizerEvent_addMissionOpensTheFormAndBackReturns() {
    val nav = setAppAndGetNavigation()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()
    composeTestRule.runOnUiThread { nav.enterEvent(OrganizerEvent("event-1", EVENT_TITLE)) }

    composeTestRule.onNodeWithTag(OrganizerOverviewScreenTestTags.ADD_MISSION_BUTTON).performClick()
    composeTestRule.onNodeWithTag(AddMissionScreenTestTags.TITLE_FIELD).assertIsDisplayed()

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertTextEquals(EVENT_TITLE)
  }

  @Test
  fun addMission_savesTheMissionInTheEventAndReturnsToTheOverview() {
    // The form is only shown to the organizer, so the app keeps their session.
    val event = createEvent(signIn())
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = repositories) }
    }
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()
    waitForNode(MyEventsTestTags.eventCard(event.id))
    composeTestRule.onNodeWithTag(MyEventsTestTags.eventAction(event.id)).performClick()
    composeTestRule.onNodeWithTag(OrganizerOverviewScreenTestTags.ADD_MISSION_BUTTON).performClick()
    // The form reads the event it adds the mission to.
    waitForNode(AddMissionScreenTestTags.EVENT_TITLE)
    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.EVENT_TITLE)
        .assertTextContains(event.title)

    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.TITLE_FIELD)
        .performTextInput("Sort the litter")
    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.DESCRIPTION_FIELD)
        .performTextInput("Separate glass and plastic")
    // The event's team is offered, as the form loads it from the team repository.
    val teamId = teamIds.getValue(event.id)
    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.teamChip(teamId))
        .performScrollTo()
        .performClick()
    // The start picker opens on the event's start; the end one is moved an hour later.
    val start = event.startAt.atZone(ZoneId.systemDefault()).toLocalTime()
    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.START_FIELD)
        .performScrollTo()
        .performClick()
    composeTestRule.onNodeWithTag(AddMissionScreenTestTags.PICKER_CONFIRM).performClick()
    composeTestRule.onNodeWithTag(AddMissionScreenTestTags.END_FIELD).performClick()
    composeTestRule.onNodeWithContentDescription(dialHour(start.hour + 1)).performClick()
    composeTestRule.onNodeWithTag(AddMissionScreenTestTags.PICKER_CONFIRM).performClick()
    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.SUBMIT_BUTTON)
        .performScrollTo()
        .performClick()

    // The form has a title of its own, so wait for the Overview before reading the title.
    waitForNode(OrganizerOverviewScreenTestTags.SCREEN)
    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertTextEquals(event.title)
    val mission = runBlocking {
      repositories.missionRepository.getMissionsByEvent(event.id).single()
    }
    // Deleted before its event, as Firestore does not cascade the deletion.
    createdPaths.add(0, "events/${event.id}/missions/${mission.id}")
    assertEquals("Sort the litter", mission.title)
    assertEquals(teamId, mission.teamId)
    assertEquals(event.startAt, mission.startAt)
    assertEquals(mission.startAt.plus(Duration.ofHours(1)), mission.endAt)
  }

  @Test
  fun organizerEvent_peopleTabOpensThePeopleScreenAndBackReturns() {
    val nav = setAppAndGetNavigation()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()
    composeTestRule.runOnUiThread { nav.enterEvent(OrganizerEvent("event-1", EVENT_TITLE)) }

    composeTestRule.onNodeWithTag(OrganizerTabs.People.testTag).performClick()
    assertPeopleScreenShown()

    // Its own bottom bar leads to the other tabs.
    composeTestRule.onNodeWithTag(OrganizerTabs.Overview.testTag).performClick()
    composeTestRule.onNodeWithTag(OrganizerTabs.Overview.testTag).assertIsSelected()
    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertTextEquals(EVENT_TITLE)

    composeTestRule.onNodeWithTag(OrganizerTabs.People.testTag).performClick()
    pressBack()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_SCREEN).assertIsDisplayed()
  }

  @Test
  fun organizerPeople_reopeningTheTabKeepsTheListWithoutASpinner() {
    val event = createEvent(signIn())
    val holdReads = AtomicBoolean(false)
    val reloadStarted = CompletableDeferred<Unit>()
    val reload = CompletableDeferred<Unit>()
    val requestRepository = repositories.membershipRequestRepository
    val heldRepositories =
        RepositoryProvider(
            authRepository = repositories.authRepository,
            eventRepository = repositories.eventRepository,
            userRepository = repositories.userRepository,
            membershipRequestRepository =
                object : MembershipRequestRepository by requestRepository {
                  override suspend fun getMembershipRequestsByEId(
                      eventId: String
                  ): List<MembershipRequest> {
                    if (holdReads.get()) {
                      reloadStarted.complete(Unit)
                      reload.await()
                    }
                    return requestRepository.getMembershipRequestsByEId(eventId)
                  }
                },
            missionRepository = repositories.missionRepository,
            teamRepository = repositories.teamRepository,
            checkInRepository = repositories.checkInRepository,
            chatRepository = repositories.chatRepository,
            liveLocationRepository = repositories.liveLocationRepository,
        )
    lateinit var navController: NavHostController
    composeTestRule.setContent {
      navController = rememberNavController()
      ShifterTheme {
        ShifterNavHost(
            isSignedIn = true,
            repositories = heldRepositories,
            navController = navController,
        )
      }
    }
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()
    composeTestRule.runOnUiThread {
      NavigationActions(navController).enterEvent(OrganizerEvent(event.id, event.title))
    }
    composeTestRule.onNodeWithTag(OrganizerTabs.People.testTag).performClick()
    val teamBar = ParticipantsListTestTags.teamBar(teamIds.getValue(event.id))
    waitForNode(teamBar)

    holdReads.set(true)
    composeTestRule.onNodeWithTag(OrganizerTabs.Overview.testTag).performClick()
    composeTestRule.onNodeWithTag(OrganizerTabs.People.testTag).performClick()

    // The reload is held: the list on screen can only be the one kept from the first visit.
    composeTestRule.waitUntil(TIMEOUT_MILLIS) { reloadStarted.isCompleted }
    composeTestRule.onNodeWithTag(teamBar).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ParticipantsListTestTags.LOADING).assertDoesNotExist()
    reload.complete(Unit)
  }

  @Test
  fun organizerPeople_acceptingARequestShowsTheVolunteerUnderTheirTeam() {
    val organizerAccount = googleCredential()
    val organizer = signIn(organizerAccount)
    val event = createEvent(organizer)
    val volunteer = signIn()
    applyTo(event, volunteer)
    // Created by the acceptance, and deleted before the event like the request.
    createdPaths.add(0, "eventParticipants/${event.id}")
    signIn(organizerAccount)
    lateinit var navController: NavHostController
    composeTestRule.setContent {
      navController = rememberNavController()
      ShifterTheme {
        ShifterNavHost(
            isSignedIn = true,
            repositories = repositories,
            navController = navController,
        )
      }
    }
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()
    composeTestRule.runOnUiThread {
      NavigationActions(navController).enterEvent(OrganizerEvent(event.id, event.title))
    }
    composeTestRule.onNodeWithTag(OrganizerTabs.People.testTag).performClick()

    composeTestRule.onNodeWithText("Requests").performClick()
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule.onAllNodesWithText("Accept").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("Accept").performClick()
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule.onAllNodesWithText("Accepted").fetchSemanticsNodes().isNotEmpty()
    }

    // Coming back to the participants reloads them, with the volunteer just accepted.
    composeTestRule.onNodeWithText("Participants").performClick()
    waitForNode(ParticipantsListTestTags.participantCard(volunteer.uid))
    composeTestRule
        .onNodeWithTag(ParticipantsListTestTags.teamBar(teamIds.getValue(event.id)))
        .assertTextContains("· 1", substring = true)
  }

  @Test
  fun staffEvents_showTheAppliedEventAndWithdrawRemovesIt() {
    val organizer = signIn()
    val event = createEvent(organizer)
    val volunteer = signIn()
    applyTo(event, volunteer)
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = repositories) }
    }

    waitForNode(MyEventsTestTags.eventCard(event.id))
    composeTestRule.onNodeWithTag(MyEventsTestTags.eventAction(event.id)).performClick()

    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule
          .onAllNodesWithTag(MyEventsTestTags.eventCard(event.id))
          .fetchSemanticsNodes()
          .isEmpty()
    }
    val requests = runBlocking {
      repositories.membershipRequestRepository.getMembershipRequestsByUId(volunteer.uid)
    }
    assertTrue(requests.isEmpty())
  }

  @Test
  fun volunteerEvent_asAManager_opensTheAttendanceOfTheirTeam() {
    val event = acceptedEvent(asManager = true)
    showApp()

    openEvent(event)

    // The manager watches the live attendance of the missions of the team they were given.
    waitForNode(ManagerOverviewTestTags.SCREEN)
    composeTestRule.onNodeWithTag(ManagerOverviewTestTags.SUBTITLE).assertIsDisplayed()
    // The tabs of the event are still the navigation's, under the screen.
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
  }

  @Test
  fun volunteerEvent_asAManager_addMissionOpensTheFormAndBackReturns() {
    val event = acceptedEvent(asManager = true)
    showApp()
    openEvent(event)
    waitForNode(ManagerOverviewTestTags.ADD_MISSION_BUTTON)

    composeTestRule.onNodeWithTag(ManagerOverviewTestTags.ADD_MISSION_BUTTON).performClick()
    waitForNode(AddMissionScreenTestTags.EVENT_TITLE)
    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.EVENT_TITLE)
        .assertTextContains(event.title)

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    waitForNode(ManagerOverviewTestTags.SCREEN)
  }

  @Test
  fun volunteerEvent_asAManager_savedMissionShowsInTheOverview() {
    val event = acceptedEvent(asManager = true)
    showApp()
    openEvent(event)
    waitForNode(ManagerOverviewTestTags.ADD_MISSION_BUTTON)
    composeTestRule.onNodeWithTag(ManagerOverviewTestTags.ADD_MISSION_BUTTON).performClick()
    waitForNode(AddMissionScreenTestTags.EVENT_TITLE)

    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.TITLE_FIELD)
        .performTextInput("Sort the litter")
    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.DESCRIPTION_FIELD)
        .performTextInput("Separate glass and plastic")
    // The manager's only team is already picked: no "General", and no team they do not manage.
    val teamId = teamIds.getValue(event.id)
    // The start picker opens on the event's start; the end one is moved an hour later.
    val start = event.startAt.atZone(ZoneId.systemDefault()).toLocalTime()
    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.START_FIELD)
        .performScrollTo()
        .performClick()
    composeTestRule.onNodeWithTag(AddMissionScreenTestTags.PICKER_CONFIRM).performClick()
    composeTestRule.onNodeWithTag(AddMissionScreenTestTags.END_FIELD).performClick()
    composeTestRule.onNodeWithContentDescription(dialHour(start.hour + 1)).performClick()
    composeTestRule.onNodeWithTag(AddMissionScreenTestTags.PICKER_CONFIRM).performClick()
    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.SUBMIT_BUTTON)
        .performScrollTo()
        .performClick()

    waitForNode(ManagerOverviewTestTags.SCREEN)
    val mission = runBlocking {
      repositories.missionRepository.getMissionsByEvent(event.id).single()
    }
    // Deleted before its event, as Firestore does not cascade the deletion.
    createdPaths.add(0, "events/${event.id}/missions/${mission.id}")
    assertEquals("Sort the litter", mission.title)
    assertEquals(teamId, mission.teamId)
    // Read again on the way back, so the manager sees the mission they just added.
    waitForNode(ManagerOverviewTestTags.mission(mission.id))
  }

  @Test
  fun volunteerEvent_asAVolunteer_opensTheOverviewWithoutTheAttendance() {
    val event = acceptedEvent(asManager = false)
    showApp()

    openEvent(event)

    // Their own Overview, with their Planning; the manager's view is not theirs to see.
    waitForNode(PlanningTestTags.PLANNING)
    composeTestRule.onNodeWithTag(ManagerOverviewTestTags.SCREEN).assertDoesNotExist()
  }

  @Test
  fun volunteerEvent_whenTheRoleCannotBeRead_offersToTryAgainWithoutTheAttendance() {
    val event = acceptedEvent(asManager = true)
    // The events still list, since a team that cannot be read only costs the role label.
    showApp(
        RepositoryProvider(
            repositories.authRepository,
            repositories.eventRepository,
            repositories.userRepository,
            repositories.membershipRequestRepository,
            repositories.missionRepository,
            UnreadableTeams(),
            repositories.checkInRepository,
            repositories.chatRepository,
            repositories.liveLocationRepository,
        )
    )

    openEvent(event)

    // The part they have in the event is unknown, so no attendance is shown, only a way back in.
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("Could not load the missions.").assertIsDisplayed()
    composeTestRule.onNodeWithTag(ManagerOverviewTestTags.SCREEN).assertDoesNotExist()
  }

  @Test
  fun staffEvents_reloadWhenComingBackToTheList() {
    val event = createEvent(signIn())
    val volunteer = signIn()
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = repositories) }
    }
    // Loaded once with no request yet.
    waitForNode(MyEventsTestTags.EMPTY_STATE)

    // The request is sent while another screen is on top of the list.
    composeTestRule.onNodeWithTag(MyEventsTestTags.AVATAR).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.SCREEN).assertIsDisplayed()
    applyTo(event, volunteer)
    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()

    waitForNode(MyEventsTestTags.eventCard(event.id))
  }

  @Test
  fun organizerEvents_showTheOrganizedEventAndManageOpensIt() {
    val event = createEvent(signIn())
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = repositories) }
    }
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()

    waitForNode(MyEventsTestTags.eventCard(event.id))
    composeTestRule.onNodeWithTag(MyEventsTestTags.eventAction(event.id)).performClick()

    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertTextEquals(event.title)
    // Once the attendance is loaded, the name is still only in the top bar.
    waitForNode(OrganizerOverviewScreenTestTags.SCREEN)
    composeTestRule.onAllNodesWithText(event.title).assertCountEquals(1)
  }

  @Test
  fun organizerEvent_overviewListsTheMissionsAndFiltersThemByTeam() {
    val event = createEvent(signIn())
    val bar = createTeam(event, "Bar")
    createMission(event, "Serve drinks", bar.id)
    createMission(event, "Welcome the volunteers", teamId = null)
    openOrganizerEvent(event)

    waitForNode(OrganizerOverviewScreenTestTags.MISSIONS_HEADER)
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule.onAllNodesWithText("Serve drinks").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("Welcome the volunteers").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag(OrganizerOverviewScreenTestTags.teamChip(bar.id)).performClick()
    composeTestRule.onNodeWithText("Serve drinks").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Welcome the volunteers").assertDoesNotExist()
  }

  @Test
  fun organizerEvent_editMission_savesTheChangeAndShowsItInTheOverview() {
    val event = createEvent(signIn())
    val mission =
        createMission(
            event,
            "Set up the stands",
            teamId = null,
            description = "Set up before the doors open.",
        )
    openOrganizerEvent(event)
    waitForNode(OrganizerOverviewScreenTestTags.mission(mission.id))

    composeTestRule
        .onNodeWithTag(OrganizerOverviewScreenTestTags.mission(mission.id))
        .performClick()
    // The form only appears once the mission is read, filled with it.
    waitForNode(AddMissionScreenTestTags.TITLE_FIELD)
    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.TITLE_FIELD)
        .assertTextContains("Set up the stands")
        .performTextReplacement("Set up the bar")
    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.SUBMIT_BUTTON)
        .performScrollTo()
        .performClick()

    // Back on the Overview, whose row of the mission shows the new title.
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule
          .onAllNodes(
              hasTestTag(OrganizerOverviewScreenTestTags.mission(mission.id)) and
                  hasText("Set up the bar", substring = true)
          )
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    val saved = runBlocking { repositories.missionRepository.getMission(event.id, mission.id) }
    assertEquals(mission.copy(title = "Set up the bar"), saved)
  }

  @Test
  fun organizerEvent_overviewShowsAMissionAddedWhileTheFormWasOpen() {
    val event = createEvent(signIn())
    openOrganizerEvent(event)
    waitForNode(OrganizerOverviewScreenTestTags.EMPTY_MISSIONS)

    composeTestRule.onNodeWithTag(OrganizerOverviewScreenTestTags.ADD_MISSION_BUTTON).performClick()
    composeTestRule.onNodeWithTag(AddMissionScreenTestTags.TITLE_FIELD).assertIsDisplayed()
    createMission(event, "Set up the stands", teamId = null)
    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()

    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule.onAllNodesWithText("Set up the stands").fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun organizerDiscussions_opensTheChannelOfTheTeamPicked() {
    val event = createEvent(signIn())
    val bar = createTeam(event, "Bar")
    openOrganizerEvent(event)
    waitForNode(NavigationTestTags.DISCUSSIONS_TAB)

    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()

    // Every team of the event is listed, under the general and admin channels.
    waitForNode(ChatChannelsTestTags.channel(bar.id))
    composeTestRule
        .onNodeWithTag(ChatChannelsTestTags.channel(teamIds.getValue(event.id)))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(ChatChannelsTestTags.GENERAL).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ChatChannelsTestTags.ADMIN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ChatChannelsTestTags.channel(bar.id)).performClick()
    waitForNode(TeamChatTestTags.INPUT)
    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertTextEquals("Bar")
  }

  @Test
  fun volunteerDiscussions_withASingleTeam_listsItAndTheGeneralChannelButNotTheAdminOne() {
    val event = acceptedEvent(asManager = false)
    showApp()
    openEvent(event)
    waitForNode(NavigationTestTags.DISCUSSIONS_TAB)

    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()

    // The list is shown even with a single team, with the general channel above it.
    waitForNode(ChatChannelsTestTags.GENERAL)
    val team = ChatChannelsTestTags.channel(teamIds.getValue(event.id))
    composeTestRule.onNodeWithTag(team).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ChatChannelsTestTags.ADMIN).assertDoesNotExist()
    composeTestRule.onNodeWithTag(TeamChatTestTags.INPUT).assertDoesNotExist()
    val teamChannel = ChatChannelId.Team(event.id, teamIds.getValue(event.id))

    composeTestRule.onNodeWithTag(team).performClick()
    waitForNode(TeamChatTestTags.INPUT)
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertTextEquals("Litter pickers")
    composeTestRule.onNodeWithTag(TeamChatTestTags.INPUT).performTextInput("Hello team")
    composeTestRule.onNodeWithTag(TeamChatTestTags.SEND_BUTTON).performClick()

    // The title comes from the route, so only the message tells the team's channel was opened.
    composeTestRule.waitUntil(TIMEOUT_MILLIS) { texts(teamChannel) == listOf("Hello team") }
    pressBack()
    waitForNode(ChatChannelsTestTags.GENERAL)

    composeTestRule.onNodeWithTag(TeamChatTestTags.INPUT).assertDoesNotExist()
    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertTextEquals(event.title)

    composeTestRule.onNodeWithTag(ChatChannelsTestTags.GENERAL).performClick()
    waitForNode(TeamChatTestTags.INPUT)
    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertTextEquals("General")
    composeTestRule.onNodeWithTag(TeamChatTestTags.INPUT).performTextInput("Hello everyone")
    composeTestRule.onNodeWithTag(TeamChatTestTags.SEND_BUTTON).performClick()

    // The message reaches the event's general channel, not the team's.
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      texts(ChatChannelId.General(event.id)) == listOf("Hello everyone")
    }
    assertEquals(listOf("Hello team"), texts(teamChannel))
  }

  @Test
  fun managerDiscussions_opensTheAdminChannel() {
    val event = acceptedEvent(asManager = true)
    showApp()
    openEvent(event)
    waitForNode(NavigationTestTags.DISCUSSIONS_TAB)

    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()

    waitForNode(ChatChannelsTestTags.ADMIN)
    composeTestRule.onNodeWithTag(ChatChannelsTestTags.ADMIN).performClick()
    waitForNode(TeamChatTestTags.INPUT)
    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertTextEquals("Admin")
    composeTestRule.onNodeWithTag(TeamChatTestTags.INPUT).performTextInput("Two more at the bar")
    composeTestRule.onNodeWithTag(TeamChatTestTags.SEND_BUTTON).performClick()

    // The message reaches the event's admin channel, which the rules open to the manager.
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      texts(ChatChannelId.Admin(event.id)) == listOf("Two more at the bar")
    }
    assertEquals(emptyList<String>(), texts(ChatChannelId.General(event.id)))
  }

  /** The texts of the messages of [channel] as the signed-in user reads them now. */
  private fun texts(channel: ChatChannelId): List<String> = runBlocking {
    repositories.chatRepository.messages(channel).first().map { it.text }
  }

  /** Shows the app opened on the Overview of [event], whose organizer must be signed in. */
  private fun openOrganizerEvent(event: Event) {
    lateinit var navController: NavHostController
    composeTestRule.setContent {
      navController = rememberNavController()
      ShifterTheme {
        ShifterNavHost(
            isSignedIn = true,
            repositories = repositories,
            navController = navController,
        )
      }
    }
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()
    composeTestRule.runOnUiThread {
      NavigationActions(navController).enterEvent(OrganizerEvent(event.id, event.title))
    }
  }

  /** Creates a team named [name] in [event]. */
  private fun createTeam(event: Event, name: String): Team {
    val team = runBlocking {
      repositories.teamRepository.createTeam(
          Team(
              eventId = event.id,
              name = name,
              description = "",
              icon = "wine",
              createdAt = Instant.now(),
          )
      )
    }
    // Deleted before its event, as Firestore does not cascade the deletion.
    createdPaths.add(0, "events/${event.id}/teams/${team.id}")
    return team
  }

  /**
   * Creates a mission of [event] titled [title], in the team [teamId] or in no team when null, and
   * returns it.
   */
  private fun createMission(
      event: Event,
      title: String,
      teamId: String?,
      description: String = "",
  ): Mission {
    val mission = runBlocking {
      repositories.missionRepository.createMission(
          Mission(
              eventId = event.id,
              title = title,
              description = description,
              teamId = teamId,
              volunteersNeeded = 1,
              startAt = event.startAt,
              endAt = event.startAt.plus(Duration.ofHours(1)),
              createdAt = Instant.now(),
          )
      )
    }
    // Deleted before its event, as Firestore does not cascade the deletion.
    createdPaths.add(0, "events/${event.id}/missions/${mission.id}")
    return mission
  }

  /** Signs in and opens the creation form; returns the signed-in organizer. */
  private fun openCreateEventForm(): AuthUser {
    val organizer = signIn()
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = repositories) }
    }
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.CREATE_EVENT_BUTTON).performClick()
    return organizer
  }

  /**
   * Fills the creation form, submits it and waits for the teams step it hands over to; returns the
   * event saved in Firestore, the only one of [organizer]. The join code comes after the teams now,
   * through [proceedFromTeamSetup].
   */
  private fun submitValidEventForm(organizer: AuthUser): Event {
    fun field(tag: String) = composeTestRule.onNodeWithTag(tag).performScrollTo()
    field(CreateEventScreenTestTags.TITLE_FIELD).performTextInput("Paléo Festival")
    field(CreateEventScreenTestTags.DESCRIPTION_FIELD).performTextInput("Open-air festival")
    field(CreateEventScreenTestTags.typeChip(EventType.MUSIC)).performClick()
    field(CreateEventScreenTestTags.ADDRESS_FIELD).performTextInput("Nyon")
    field(CreateEventScreenTestTags.START_FIELD).performTextInput("21/07/2099 14:00")
    field(CreateEventScreenTestTags.END_FIELD).performTextInput("26/07/2099 23:30")
    field(CreateEventScreenTestTags.SUBMIT_BUTTON).performClick()

    waitForNode(TeamSetupScreenTestTags.PROCEED_BUTTON)
    val event = runBlocking {
      repositories.eventRepository.getEventsByOrganizer(organizer.uid).single()
    }
    createdPaths += "events/${event.id}"
    return event
  }

  /** A node of the team form that is open, told apart from the cards of the saved teams. */
  private fun inActiveTeamForm(tag: String) =
      composeTestRule.onNode(
          hasTestTag(tag) and hasAnyAncestor(hasTestTag(TeamSetupScreenTestTags.ACTIVE_FORM))
      )

  /**
   * Creates a team named [name] through the teams step that is open, registers it for cleanup and
   * returns its id. Waits for the backend, so the step is only left once the team exists.
   */
  private fun createTeamInSetup(event: Event, name: String): String {
    inActiveTeamForm(CreateTeamFormTestTags.NAME_FIELD).performTextInput(name)
    inActiveTeamForm(CreateTeamFormTestTags.ICON_SELECTOR).performClick()
    composeTestRule.onNodeWithTag(CreateTeamFormTestTags.iconOption(TeamIcon.TRUCK)).performClick()
    inActiveTeamForm(CreateTeamFormTestTags.DESCRIPTION_FIELD).performTextInput("What $name does")
    inActiveTeamForm(CreateTeamFormTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      runBlocking { repositories.teamRepository.getTeamsByEvent(event.id) }.any { it.name == name }
    }
    val team = runBlocking {
      repositories.teamRepository.getTeamsByEvent(event.id).single { it.name == name }
    }
    // Deleted before its event, as Firestore does not cascade the deletion.
    createdPaths.add(0, "events/${event.id}/teams/${team.id}")
    waitForNode(TeamSetupScreenTestTags.savedTeam(team.id))
    return team.id
  }

  /** Leaves the teams step forward, onto the join code of the event. */
  private fun proceedFromTeamSetup() {
    composeTestRule.onNodeWithTag(TeamSetupScreenTestTags.PROCEED_BUTTON).performClick()
    waitForNode(EventCreatedScreenTestTags.JOIN_CODE)
  }

  /**
   * Shows the signed-in app and returns its navigation, to open an event: the lists have no cards
   * until an events ViewModel loads them.
   */
  private fun setAppAndGetNavigation(): NavigationActions = NavigationActions(signInAndShowApp())

  /** The real People screen is shown, under the event's name, with the People tab selected. */
  private fun assertPeopleScreenShown() {
    composeTestRule.onNodeWithText("Participants").assertIsDisplayed()
    composeTestRule.onNodeWithTag(OrganizerTabs.People.testTag).assertIsSelected()
    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertTextEquals(EVENT_TITLE)
  }

  /** The event opens on the set's home tab, then each tab shows its own screen. */
  private fun assertTabsReachable(tabs: TabSet) {
    assertTabShown(tabs.home)
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
    tabs.all.forEach { tab ->
      composeTestRule.onNodeWithTag(tab.testTag).performClick()
      assertTabShown(tab)
    }
  }

  /** That the screen of [tab] is the one on, read from whatever that tab actually draws. */
  private fun assertTabShown(tab: Tab) {
    when (tab) {
      OrganizerTabs.People -> assertPeopleScreenShown()
      VolunteerTabs.Map,
      OrganizerTabs.Map -> {
        composeTestRule.onNodeWithTag(MapScreenTestTags.SCREEN).assertIsDisplayed()
        assertEventTabSelected(tab)
      }
      // What the Overview of an event draws follows the part the reader has in it: what navigation
      // promises is the event's name, and the tab being the one selected, under the bar that
      // reaches the others.
      VolunteerTabs.Overview -> {
        assertEventTabSelected(tab)
        composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
      }
      else -> assertEventTabSelected(tab)
    }
  }

  /** [tab] is the one selected, under the name of the event, as every tab of an event shows it. */
  private fun assertEventTabSelected(tab: Tab, eventTitle: String = EVENT_TITLE) {
    composeTestRule.onNodeWithTag(tab.testTag).assertIsSelected()
    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertTextEquals(eventTitle)
  }

  /** From each tab of [event], the on-screen back button leads to the list tagged [listTag]. */
  private fun assertBackButtonLeavesEveryTab(
      nav: NavigationActions,
      event: EventMode,
      listTag: String,
  ) {
    event.tabs.all.forEach { tab ->
      composeTestRule.runOnUiThread { nav.enterEvent(event) }
      composeTestRule.onNodeWithTag(tab.testTag).performClick()
      assertEventTabSelected(tab, event.eventTitle)

      composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()

      composeTestRule.onNodeWithTag(listTag).assertIsDisplayed()
    }
  }

  /**
   * Presses the device Back button through the activity's dispatcher, the way the system delivers
   * it to `BackHandler` and the NavHost. Espresso's `pressBack()` first waits for the window to
   * have focus, which a system dialog on the CI emulator can take away, making these tests flaky.
   */
  private fun pressBack() {
    // Like Espresso, let the last action settle so the current screen's BackHandler is set.
    composeTestRule.waitForIdle()
    composeTestRule.runOnUiThread {
      composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    }
    composeTestRule.waitForIdle()
  }

  /**
   * The end-to-end publishing path. A signed-in volunteer, an accepted member of a team of an event
   * in progress with sharing turned on, has their position shared under that team while the app is
   * in the foreground, whatever screen is shown, without any screen being opened for it. This
   * exercises the ShifterNavHost wiring against the real repositories on the emulators, with a fake
   * location provider so no GPS is needed. The composition is torn down at the end of the test,
   * which cancels the publishing session.
   */
  @Test
  fun liveLocation_withAConsentingVolunteerInAnOngoingEvent_sharesTheirPosition() {
    val event = acceptedEvent(asManager = false, running = true)
    val volunteer = checkNotNull(repositories.authRepository.currentUser())
    val teamId = teamIds.getValue(event.id)
    // The rules accept a position only while the volunteer's profile has sharing on, so the profile
    // is created and the consent turned on, as the sign-in and Settings would.
    runBlocking {
      repositories.userRepository.getOrCreateUser(
          volunteer.uid,
          "Vera",
          volunteer.email ?: "vera@example.com",
      )
      repositories.userRepository.setLocationSharing(volunteer.uid, true, emptyList())
    }
    // Children first, so tearDown deletes them before the event and the profile they belong to.
    createdPaths.add(0, "events/${event.id}/teams/$teamId/liveLocations/${volunteer.uid}")
    createdPaths.add(0, "users/${volunteer.uid}")
    createdPaths.add(0, "publicProfiles/${volunteer.uid}")

    val locationProvider = FakeLocationProvider().apply { setPosition(GENEVA) }
    try {
      composeTestRule.setContent {
        ShifterTheme {
          ShifterNavHost(
              isSignedIn = true,
              repositories = repositories,
              locationProvider = locationProvider,
          )
        }
      }
      waitForNode(MyEventsTestTags.STAFF_SCREEN)

      composeTestRule.waitUntil(PUBLISH_TIMEOUT_MILLIS) {
        runBlocking { sharedPosition(event.id, teamId, volunteer.uid) != null }
      }
      val shared = runBlocking { sharedPosition(event.id, teamId, volunteer.uid) }
      assertEquals(GENEVA.latitude, shared!!.latitude, COORDINATE_TOLERANCE)
      assertEquals(GENEVA.longitude, shared.longitude, COORDINATE_TOLERANCE)
    } finally {
      // Stop the publishing session before tearDown deletes the data, even if an assertion above
      // failed: signing out cancels it as in production, so no write can race the cleanup. The wait
      // confirms GPS collection has really stopped, and a successful sign-out reaches it well
      // within
      // the bound, so it does not mask an earlier failure.
      AuthEmulator.auth.signOut()
      composeTestRule.waitUntil(PUBLISH_TIMEOUT_MILLIS) { locationProvider.activeCollectors == 0 }
    }
  }

  /**
   * The position the volunteer has shared in the team, read from their own live-location document,
   * which the rules let them get, or null when none is stored yet.
   */
  private suspend fun sharedPosition(eventId: String, teamId: String, uid: String): GeoPosition? {
    val snapshot =
        FirestoreEmulator.firestore
            .document("events/$eventId/teams/$teamId/liveLocations/$uid")
            .get(Source.SERVER)
            .await()
    // Only a server-confirmed write counts: a locally pending one has not reached the emulator.
    if (!snapshot.exists() || snapshot.metadata.hasPendingWrites()) return null
    val position = snapshot.get("position") as? Map<*, *> ?: return null
    val latitude = (position["latitude"] as? Number)?.toDouble() ?: return null
    val longitude = (position["longitude"] as? Number)?.toDouble() ?: return null
    return GeoPosition(latitude, longitude)
  }

  /**
   * A publishing session whose GPS source fails is reported by the app-level handler and does not
   * crash the composition: the app stays on the events screen. This drives the live-location
   * publisher's failure path end to end, which a unit test cannot reach.
   */
  @Test
  fun liveLocation_whenASessionFails_isReportedWithoutCrashingTheApp() {
    val event = acceptedEvent(asManager = false, running = true)
    val volunteer = checkNotNull(repositories.authRepository.currentUser())
    runBlocking {
      repositories.userRepository.getOrCreateUser(
          volunteer.uid,
          "Vera",
          volunteer.email ?: "vera@example.com",
      )
      repositories.userRepository.setLocationSharing(volunteer.uid, true, emptyList())
    }
    createdPaths.add(0, "users/${volunteer.uid}")
    createdPaths.add(0, "publicProfiles/${volunteer.uid}")

    val locationProvider = FakeLocationProvider().apply { failing = true }
    try {
      composeTestRule.setContent {
        ShifterTheme {
          ShifterNavHost(
              isSignedIn = true,
              repositories = repositories,
              locationProvider = locationProvider,
          )
        }
      }
      waitForNode(MyEventsTestTags.STAFF_SCREEN)

      // The eligible session starts, collects the failing GPS and throws; the handler reports it.
      composeTestRule.waitUntil(PUBLISH_TIMEOUT_MILLIS) { locationProvider.faultTriggered.value }
      composeTestRule.waitForIdle()

      // The fault did not propagate to the UI: the events screen is still usable.
      composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
    } finally {
      AuthEmulator.auth.signOut()
    }
  }

  /** Opens a real session, then shows the app on Volunteer events and returns its controller. */
  private fun signInAndShowApp(name: String = "Ada"): NavHostController {
    runBlocking {
      AuthEmulator.auth.signInForFirestore {
        repositories.authRepository.signInWithGoogle(googleCredential(name)).getOrThrow()
      }
    }
    lateinit var navController: NavHostController
    composeTestRule.setContent {
      navController = rememberNavController()
      ShifterTheme {
        ShifterNavHost(
            isSignedIn = true,
            repositories = repositories,
            navController = navController,
        )
      }
    }
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
    return navController
  }

  /**
   * Signs in [account], a fresh fake Google account by default, which the app then reads as its
   * current user. Signing in the same account again returns to the same user.
   */
  private fun signIn(account: GoogleIdTokenCredential = googleCredential()): AuthUser =
      runBlocking {
        AuthEmulator.auth.signInForFirestore {
          repositories.authRepository.signInWithGoogle(account).getOrThrow()
        }
      }

  /**
   * Creates an event organized by [organizer], who must be the signed-in user: upcoming by default,
   * or in progress now when [running], for the tests that need an event whose period is open.
   */
  private fun createEvent(organizer: AuthUser, running: Boolean = false): Event {
    val now = Instant.now()
    // On a whole minute, as a mission's time is picked to the minute.
    val startAt =
        if (running) now.minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MINUTES)
        else now.plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MINUTES)
    val endAt = if (running) now.plus(Duration.ofHours(1)) else now.plus(Duration.ofDays(2))
    val event = runBlocking {
      repositories.eventRepository.createEvent(
          Event(
              organizerId = organizer.uid,
              title = "Beach cleanup",
              description = "Collect the litter left on the beach.",
              type = EventType.NATURE,
              startAt = startAt,
              endAt = endAt,
              location = EventLocation("Vidy beach, Lausanne"),
              createdAt = now,
          )
      )
    }
    createdPaths += "events/${event.id}"
    // Volunteers apply to a team of the event, so every event gets one.
    val team = runBlocking {
      repositories.teamRepository.createTeam(
          Team(
              eventId = event.id,
              name = "Litter pickers",
              description = "Walk the beach and collect the litter.",
              icon = "trash",
              createdAt = now,
          )
      )
    }
    teamIds[event.id] = team.id
    // Deleted before its event, as Firestore does not cascade the deletion.
    createdPaths.add(0, "events/${event.id}/teams/${team.id}")
    return event
  }

  /**
   * An event the signed-in volunteer has been accepted to, managing its team when [asManager]. The
   * organizer accepts and appoints through the repositories, as the People screen does. The
   * accounts are given when the test signs in as them again afterwards.
   */
  private fun acceptedEvent(
      asManager: Boolean,
      organizerAccount: GoogleIdTokenCredential = googleCredential("Olga"),
      volunteerAccount: GoogleIdTokenCredential = googleCredential(),
      running: Boolean = false,
  ): Event {
    val event = createEvent(signIn(organizerAccount), running = running)
    val volunteer = signIn(volunteerAccount)
    applyTo(event, volunteer)
    signIn(organizerAccount)
    runBlocking {
      repositories.membershipRequestRepository.accept(event.id, volunteer.uid)
      if (asManager) {
        repositories.teamRepository.setManager(
            event.id,
            teamIds.getValue(event.id),
            volunteer.uid,
        )
      }
    }
    // Created by the acceptance, and deleted before the event like the request.
    createdPaths.add(0, "eventParticipants/${'$'}{event.id}")
    signIn(volunteerAccount)
    return event
  }

  /** Shows the app on the volunteer's events, on [provider] when it is not the real one. */
  private fun showApp(provider: RepositoryProvider = repositories) {
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = provider) }
    }
    waitForNode(MyEventsTestTags.STAFF_SCREEN)
  }

  /** Taps the card of [event], which opens it on its Overview. */
  private fun openEvent(event: Event) {
    waitForNode(MyEventsTestTags.eventCard(event.id))
    composeTestRule.onNodeWithTag(MyEventsTestTags.eventCard(event.id)).performClick()
  }

  /** The teams of an event cannot be read, which neither hides it nor makes anyone its manager. */
  private inner class UnreadableTeams : TeamRepository by repositories.teamRepository {
    override suspend fun getTeamsManagedBy(eventId: String, userId: String): List<Team> =
        throw TeamRepositoryException.Unavailable()
  }

  /** Sends a pending request to [event] for [volunteer], who must be the signed-in user. */
  private fun applyTo(event: Event, volunteer: AuthUser) {
    runBlocking {
      repositories.membershipRequestRepository.apply(
          event.id,
          MembershipRequest(
              userId = volunteer.uid,
              teamId = teamIds.getValue(event.id),
              availability = listOf(AvailabilitySlot(event.startAt, event.endAt)),
              createdAt = Instant.now(),
          ),
      )
    }
    // Deleted before its event, as Firestore does not cascade the deletion.
    createdPaths.add(0, "events/${event.id}/membershipRequests/${volunteer.uid}")
  }

  /** How the 24-hour time picker describes [hour] on its dial, in English. */
  private fun dialHour(hour: Int): String = "${hour % 24} hours"

  private fun waitForNode(tag: String) {
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  /** The credential Credential Manager would return for a fresh fake Google account. */
  private fun googleCredential(name: String = "Ada"): GoogleIdTokenCredential {
    val email = "volunteer-${UUID.randomUUID()}@example.com"
    return GoogleIdTokenCredential.Builder()
        .setId(email)
        .setIdToken(AuthEmulator.fakeGoogleIdToken("google-" + UUID.randomUUID(), email, name))
        .build()
  }

  private companion object {
    const val TIMEOUT_MILLIS = 20_000L

    /** Long enough for the coordinator to resolve the target and the publisher to write once. */
    const val PUBLISH_TIMEOUT_MILLIS = 30_000L

    const val COORDINATE_TOLERANCE = 1e-9

    val GENEVA = GeoPosition(46.2044, 6.1432)
    const val EVENT_TITLE = "City Marathon"
  }
}
