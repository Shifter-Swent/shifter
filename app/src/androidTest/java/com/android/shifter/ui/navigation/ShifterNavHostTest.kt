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
import com.swent.shifter.ui.authentication.SignInScreenTestTags
import com.swent.shifter.ui.event.CreateEventScreenTestTags
import com.swent.shifter.ui.event.EventCreatedScreenTestTags
import com.swent.shifter.ui.event.JoinEventScreenTestTags
import com.swent.shifter.ui.events.MyEventsTestTags
import com.swent.shifter.ui.mission.AddMissionScreenTestTags
import com.swent.shifter.ui.settings.SettingsScreenTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Duration
import java.time.Instant
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

    waitForSignIn()
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

    waitForSignIn()
  }

  @Test
  fun sessionEndingOutsideTheApp_returnsToSignIn() {
    signInAndShowApp()

    // As when the session expires or the account is disabled: no screen asked for it.
    AuthEmulator.auth.signOut()

    waitForSignIn()
  }

  @Test
  fun sessionEndingOutsideTheApp_fromOrganizerMode_returnsToSignIn() {
    signInAndShowApp()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_SCREEN).assertIsDisplayed()

    AuthEmulator.auth.signOut()

    waitForSignIn()
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
  fun createEvent_submitShowsTheCodeAndDoneReturnsToTheEvents() {
    openCreateEventForm()

    composeTestRule
        .onNodeWithTag(CreateEventScreenTestTags.SUBMIT_BUTTON)
        .performScrollTo()
        .performClick()
    composeTestRule.onNodeWithTag(EventCreatedScreenTestTags.DONE_BUTTON).assertIsDisplayed()

    composeTestRule.onNodeWithTag(EventCreatedScreenTestTags.DONE_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_SCREEN).assertIsDisplayed()
  }

  @Test
  fun eventCreated_deviceBackSkipsTheFormAndReturnsToTheEvents() {
    openCreateEventForm()
    composeTestRule
        .onNodeWithTag(CreateEventScreenTestTags.SUBMIT_BUTTON)
        .performScrollTo()
        .performClick()

    composeTestRule.onNodeWithTag(EventCreatedScreenTestTags.DONE_BUTTON).assertIsDisplayed()

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

  private fun openCreateEventForm() {
    signInAndShowApp()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.CREATE_EVENT_BUTTON).performClick()
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
      if (tab == OrganizerTabs.People) {
        assertPeopleScreenShown()
      } else {
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

  private fun waitForSignIn() {
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule
          .onAllNodesWithTag(SignInScreenTestTags.LOGIN_BUTTON)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
  }

  /** Signs in a fresh fake Google account, which the app then reads as its current user. */
  private fun signIn(): AuthUser = runBlocking {
    repositories.authRepository.signInWithGoogle(googleCredential()).getOrThrow()
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
              startAt = now.plus(Duration.ofDays(1)),
              endAt = now.plus(Duration.ofDays(2)),
              location = EventLocation("Vidy beach, Lausanne"),
              createdAt = now,
          )
      )
    }
    createdPaths += "events/${event.id}"
    return event
  }

  /** Sends a pending request to [event] for [volunteer], who must be the signed-in user. */
  private fun applyTo(event: Event, volunteer: AuthUser) {
    runBlocking {
      repositories.membershipRequestRepository.apply(
          event.id,
          MembershipRequest(
              userId = volunteer.uid,
              availability = listOf(AvailabilitySlot(event.startAt, event.endAt)),
              createdAt = Instant.now(),
          ),
      )
    }
    // Deleted before its event, as Firestore does not cascade the deletion.
    createdPaths.add(0, "events/${event.id}/membershipRequests/${volunteer.uid}")
  }

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
