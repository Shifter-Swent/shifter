// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.navigation

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso.pressBack
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
import com.swent.shifter.ui.authentication.SignInScreenTestTags
import com.swent.shifter.ui.event.CreateEventScreenTestTags
import com.swent.shifter.ui.event.EventCreatedScreenTestTags
import com.swent.shifter.ui.event.EventDetailsTestTags
import com.swent.shifter.ui.event.JoinEventScreenTestTags
import com.swent.shifter.ui.events.MyEventsTestTags
import com.swent.shifter.ui.mission.AddMissionScreenTestTags
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

  @get:Rule val composeTestRule = createComposeRule()

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
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = repositories) }
    }

    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOGIN_BUTTON).assertDoesNotExist()
  }

  @Test
  fun staffEvents_avatarOpensSettingsAndBackReturns() {
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = repositories) }
    }

    composeTestRule.onNodeWithTag(MyEventsTestTags.AVATAR).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.SCREEN).assertIsDisplayed()

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
  }

  @Test
  fun settings_signOutClearsTheSessionAndReturnsToSignIn() {
    runBlocking { repositories.authRepository.signInWithGoogle(googleCredential()).getOrThrow() }
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = repositories) }
    }

    composeTestRule.onNodeWithTag(MyEventsTestTags.AVATAR).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.SIGN_OUT_BUTTON).performClick()

    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule
          .onAllNodesWithTag(SignInScreenTestTags.LOGIN_BUTTON)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    assertNull(AuthEmulator.auth.currentUser)
  }

  @Test
  fun staffEvents_switchesToThePastTab() {
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = repositories) }
    }

    composeTestRule.onNodeWithTag(MyEventsTestTags.PAST_TAB).performClick()

    composeTestRule.onNodeWithTag(MyEventsTestTags.PAST_TAB).assertIsSelected()
  }

  @Test
  fun staffEvents_scanQrOpensJoinEventAndBackReturns() {
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = repositories) }
    }

    composeTestRule.onNodeWithTag(MyEventsTestTags.SCAN_QR_BUTTON).performClick()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.JOIN_CODE_FIELD).assertIsDisplayed()

    pressBack()
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
  }

  @Test
  fun joinEvent_keepsTheTypedCode() {
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = repositories) }
    }

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
  fun joinEvent_applyingShowsTheRequestOnFindingAgainAndTheEventInTheList() {
    val event = createEvent(signIn())
    val volunteer = signIn()
    lateinit var backDispatcher: OnBackPressedDispatcher
    composeTestRule.setContent {
      backDispatcher =
          checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = repositories) }
    }
    composeTestRule.onNodeWithTag(MyEventsTestTags.SCAN_QR_BUTTON).performClick()

    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.JOIN_CODE_FIELD)
        .performTextInput(event.joinCode)
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.FIND_BUTTON).performClick()
    waitForNode(JoinEventScreenTestTags.EVENT_TITLE)
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.EVENT_TITLE).assertTextEquals(event.title)
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).performClick()
    waitForNode(JoinEventScreenTestTags.APPLIED_MESSAGE)
    // Deleted before its event, as Firestore does not cascade the deletion.
    createdPaths.add(0, "events/${event.id}/membershipRequests/${volunteer.uid}")

    val request = runBlocking {
      repositories.membershipRequestRepository.getMembershipRequest(event.id, volunteer.uid)
    }
    assertEquals(MembershipRequestStatus.PENDING, request?.status)

    // Finding the event again shows the request already sent instead of the Apply button.
    composeTestRule.onNodeWithTag(EventDetailsTestTags.BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.FIND_BUTTON).performClick()
    waitForNode(JoinEventScreenTestTags.APPLIED_MESSAGE)
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).assertDoesNotExist()

    // The screen's Back returns to the code, then the device Back leaves to the events list. The
    // device Back is sent to the dispatcher rather than through Espresso's pressBack(), which waits
    // for window focus and fails whenever a system dialog takes it on the emulator.
    composeTestRule.onNodeWithTag(EventDetailsTestTags.BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.JOIN_CODE_FIELD).assertIsDisplayed()
    composeTestRule.runOnUiThread { backDispatcher.onBackPressed() }
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
    // The list reloads on return, so the event just applied to shows up in it.
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
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = repositories) }
    }

    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_SCREEN).assertIsDisplayed()

    composeTestRule.onNodeWithTag(MyEventsTestTags.VOLUNTEER_VIEW_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
  }

  @Test
  fun organizerEvents_avatarOpensSettingsAndBackReturns() {
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = repositories) }
    }
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()

    composeTestRule.onNodeWithTag(MyEventsTestTags.AVATAR).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.SCREEN).assertIsDisplayed()

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_SCREEN).assertIsDisplayed()
  }

  @Test
  fun organizerEvents_createEventOpensTheFormAndBackReturns() {
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = repositories) }
    }
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
    runBlocking {
      repositories.authRepository.signInWithGoogle(googleCredential(name = "grace")).getOrThrow()
    }
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = repositories) }
    }

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
    val event = createEvent(signIn())
    val nav = setAppAndGetNavigation()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()
    composeTestRule.runOnUiThread { nav.enterEvent(OrganizerEvent(event.id)) }
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
    composeTestRule.setContent {
      ShifterTheme { ShifterNavHost(isSignedIn = true, repositories = repositories) }
    }
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.CREATE_EVENT_BUTTON).performClick()
  }

  /**
   * Shows the signed-in app and returns its navigation, to open an event without creating one: no
   * account is signed in, so the lists have no cards.
   */
  private fun setAppAndGetNavigation(): NavigationActions {
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
    composeTestRule.waitForIdle()
    return NavigationActions(navController)
  }

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
              // On a whole minute, as a mission's time is picked to the minute.
              startAt = now.plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MINUTES),
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
