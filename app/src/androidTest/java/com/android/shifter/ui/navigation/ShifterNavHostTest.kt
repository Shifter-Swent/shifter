// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.swent.shifter.firebase.AuthEmulator
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.model.RepositoryProviderFactory
import com.swent.shifter.model.authentication.AuthUser
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.team.Team
import com.swent.shifter.ui.authentication.SignInScreenTestTags
import com.swent.shifter.ui.event.CreateEventScreenTestTags
import com.swent.shifter.ui.event.EventCreatedScreenTestTags
import com.swent.shifter.ui.event.EventDetailsTestTags
import com.swent.shifter.ui.event.JoinEventScreenTestTags
import com.swent.shifter.ui.events.MyEventsTestTags
import com.swent.shifter.ui.map.MapScreenTestTags
import com.swent.shifter.ui.mission.AddMissionScreenTestTags
import com.swent.shifter.ui.people.ParticipantsListTestTags
import com.swent.shifter.ui.settings.SettingsScreenTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.runBlocking
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
        .performTextInput("ABC123")

    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.JOIN_CODE_FIELD)
        .assertTextContains("ABC123")
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
    composeTestRule.onNodeWithTag(EventDetailsTestTags.BACK_BUTTON).performClick()
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
        .performTextInput("NOPE00")
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

    composeTestRule.onNodeWithTag(CreateEventScreenTestTags.BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_SCREEN).assertIsDisplayed()
  }

  @Test
  fun createEvent_showsTheCodeOfTheSavedEventAndDoneReturnsToTheEvents() {
    val organizer = openCreateEventForm()

    val event = submitValidEventForm(organizer)

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
    submitValidEventForm(organizer)

    pressBack()

    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(CreateEventScreenTestTags.TITLE_FIELD).assertDoesNotExist()
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

    composeTestRule.runOnUiThread { nav.enterEvent(VolunteerEvent("event-1")) }

    assertTabsReachable(VolunteerTabs)
    pressBack()
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
  }

  @Test
  fun organizerEvent_opensOnOverviewAndReachesEveryTab() {
    val nav = setAppAndGetNavigation()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()

    composeTestRule.runOnUiThread { nav.enterEvent(OrganizerEvent("event-1")) }

    assertTabsReachable(OrganizerTabs)
    pressBack()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_SCREEN).assertIsDisplayed()
  }

  @Test
  fun organizerEvent_addMissionOpensTheFormAndBackReturns() {
    val nav = setAppAndGetNavigation()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()
    composeTestRule.runOnUiThread { nav.enterEvent(OrganizerEvent("event-1")) }

    composeTestRule.onNodeWithTag(ShifterNavHostTestTags.ADD_MISSION_BUTTON).performClick()
    composeTestRule.onNodeWithTag(AddMissionScreenTestTags.TITLE_FIELD).assertIsDisplayed()

    composeTestRule.onNodeWithTag(AddMissionScreenTestTags.BACK_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertTextEquals(OrganizerEventScreen.Overview.title)
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
    composeTestRule.onNodeWithTag(ShifterNavHostTestTags.ADD_MISSION_BUTTON).performClick()
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

    waitForNode(NavigationTestTags.TOP_BAR_TITLE)
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertTextEquals(OrganizerEventScreen.Overview.title)
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
    composeTestRule.runOnUiThread { nav.enterEvent(OrganizerEvent("event-1")) }

    composeTestRule.onNodeWithTag(OrganizerTabs.People.testTag).performClick()
    assertPeopleScreenShown()

    // Its own bottom bar leads to the other tabs.
    composeTestRule.onNodeWithTag(OrganizerTabs.Overview.testTag).performClick()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertTextEquals(OrganizerEventScreen.Overview.title)

    composeTestRule.onNodeWithTag(OrganizerTabs.People.testTag).performClick()
    pressBack()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_SCREEN).assertIsDisplayed()
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
      NavigationActions(navController).enterEvent(OrganizerEvent(event.id))
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

    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertTextEquals(OrganizerEventScreen.Overview.title)
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
   * Fills the creation form, submits it and waits for the join code; returns the event saved in
   * Firestore, the only one of [organizer].
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

    waitForNode(EventCreatedScreenTestTags.JOIN_CODE)
    val event = runBlocking {
      repositories.eventRepository.getEventsByOrganizer(organizer.uid).single()
    }
    createdPaths += "events/${event.id}"
    return event
  }

  /**
   * Shows the signed-in app and returns its navigation, to open an event: the lists have no cards
   * until an events ViewModel loads them.
   */
  private fun setAppAndGetNavigation(): NavigationActions = NavigationActions(signInAndShowApp())

  /** The real People screen is shown, with its own bottom bar on the People tab. */
  private fun assertPeopleScreenShown() {
    composeTestRule.onNodeWithText("Participants").assertIsDisplayed()
    composeTestRule.onNodeWithTag(OrganizerTabs.People.testTag).assertIsSelected()
    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertDoesNotExist()
  }

  /** The event opens on the set's home tab, then each tab shows its own screen. */
  private fun assertTabsReachable(tabs: TabSet) {
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertTextEquals(tabs.home.home.title)
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
    tabs.all.forEach { tab ->
      composeTestRule.onNodeWithTag(tab.testTag).performClick()
      when (tab) {
        OrganizerTabs.People -> assertPeopleScreenShown()
        VolunteerTabs.Map,
        OrganizerTabs.Map ->
            composeTestRule.onNodeWithTag(MapScreenTestTags.SCREEN).assertIsDisplayed()
        else ->
            composeTestRule
                .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
                .assertTextEquals(tab.home.title)
      }
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

  /** Opens a real session, then shows the app on Volunteer events and returns its controller. */
  private fun signInAndShowApp(name: String = "Ada"): NavHostController {
    runBlocking {
      repositories.authRepository.signInWithGoogle(googleCredential(name)).getOrThrow()
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
        repositories.authRepository.signInWithGoogle(account).getOrThrow()
      }

  /** Creates an upcoming event organized by [organizer], who must be the signed-in user. */
  private fun createEvent(organizer: AuthUser): Event {
    val now = Instant.now()
    val event = runBlocking {
      repositories.eventRepository.createEvent(
          Event(
              organizerId = organizer.uid,
              title = "Beach cleanup",
              description = "Collect the litter left on the beach.",
              type = EventType.NATURE,
              // On a whole minute, as a mission's time is picked to the minute.
              startAt = now.plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MINUTES),
              endAt = now.plus(Duration.ofDays(2)),
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
  }
}
