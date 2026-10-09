// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.swent.shifter.firebase.AuthEmulator
import com.swent.shifter.model.authentication.AuthRepositoryFirebase
import com.swent.shifter.model.authentication.DefaultGoogleSignInHelper
import com.swent.shifter.ui.authentication.SignInScreenTestTags
import com.swent.shifter.ui.event.CreateEventScreenTestTags
import com.swent.shifter.ui.events.MyEventsTestTags
import com.swent.shifter.ui.mission.AddMissionScreenTestTags
import com.swent.shifter.ui.navigation.NavigationActions
import com.swent.shifter.ui.navigation.NavigationTestTags
import com.swent.shifter.ui.navigation.OrganizerEvent
import com.swent.shifter.ui.navigation.OrganizerEventScreen
import com.swent.shifter.ui.navigation.OrganizerTabs
import com.swent.shifter.ui.navigation.TabSet
import com.swent.shifter.ui.navigation.Volunteer
import com.swent.shifter.ui.navigation.VolunteerEvent
import com.swent.shifter.ui.navigation.VolunteerTabs
import com.swent.shifter.ui.settings.SettingsScreenTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that [ShifterApp] starts on the destination matching the restored session. */
@RunWith(AndroidJUnit4::class)
class ShifterAppTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Before
  fun setUp() {
    // The screens build the production repository: keep it on the emulator, signed out.
    AuthEmulator.auth.signOut()
  }

  @After
  fun tearDown() {
    AuthEmulator.auth.signOut()
    AuthEmulator.clearAccounts()
  }

  @Test
  fun signedOut_startsOnSignIn() {
    composeTestRule.setContent { ShifterTheme { ShifterApp(isSignedIn = false) } }

    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOGIN_BUTTON).assertIsDisplayed()
  }

  @Test
  fun signedIn_skipsSignInAndStartsOnVolunteerEvents() {
    composeTestRule.setContent { ShifterTheme { ShifterApp(isSignedIn = true) } }

    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOGIN_BUTTON).assertDoesNotExist()
  }

  @Test
  fun staffEvents_avatarOpensSettingsAndBackReturns() {
    composeTestRule.setContent { ShifterTheme { ShifterApp(isSignedIn = true) } }

    composeTestRule.onNodeWithTag(MyEventsTestTags.AVATAR).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.SCREEN).assertIsDisplayed()

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
  }

  @Test
  fun settings_signOutClearsTheSessionAndReturnsToSignIn() {
    runBlocking {
      AuthRepositoryFirebase(AuthEmulator.auth, DefaultGoogleSignInHelper())
          .signInWithGoogle(googleCredential())
          .getOrThrow()
    }
    composeTestRule.setContent { ShifterTheme { ShifterApp(isSignedIn = true) } }

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
    composeTestRule.setContent { ShifterTheme { ShifterApp(isSignedIn = true) } }

    composeTestRule.onNodeWithTag(MyEventsTestTags.PAST_TAB).performClick()

    composeTestRule.onNodeWithTag(MyEventsTestTags.PAST_TAB).assertIsSelected()
  }

  @Test
  fun staffEvents_scanQrOpensTheJoinPlaceholderAndBackReturns() {
    composeTestRule.setContent { ShifterTheme { ShifterApp(isSignedIn = true) } }

    composeTestRule.onNodeWithTag(MyEventsTestTags.SCAN_QR_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertTextEquals(Volunteer.QrApply.title)

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
  }

  @Test
  fun organizerView_switchesModesBothWays() {
    composeTestRule.setContent { ShifterTheme { ShifterApp(isSignedIn = true) } }

    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_SCREEN).assertIsDisplayed()

    composeTestRule.onNodeWithTag(MyEventsTestTags.VOLUNTEER_VIEW_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
  }

  @Test
  fun organizerEvents_avatarOpensSettingsAndBackReturns() {
    composeTestRule.setContent { ShifterTheme { ShifterApp(isSignedIn = true) } }
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()

    composeTestRule.onNodeWithTag(MyEventsTestTags.AVATAR).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.SCREEN).assertIsDisplayed()

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_SCREEN).assertIsDisplayed()
  }

  @Test
  fun organizerEvents_createEventOpensTheFormAndBackReturns() {
    composeTestRule.setContent { ShifterTheme { ShifterApp(isSignedIn = true) } }
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()

    composeTestRule.onNodeWithTag(MyEventsTestTags.CREATE_EVENT_BUTTON).performClick()
    composeTestRule.onNodeWithTag(CreateEventScreenTestTags.TITLE_FIELD).assertIsDisplayed()

    composeTestRule.onNodeWithTag(CreateEventScreenTestTags.BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_SCREEN).assertIsDisplayed()
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

    composeTestRule.onNodeWithTag(ShifterAppTestTags.ADD_MISSION_BUTTON).performClick()
    composeTestRule.onNodeWithTag(AddMissionScreenTestTags.TITLE_FIELD).assertIsDisplayed()

    composeTestRule.onNodeWithTag(AddMissionScreenTestTags.BACK_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertTextEquals(OrganizerEventScreen.Overview.title)
  }

  /**
   * Shows the signed-in app and returns its navigation, to open an event: the lists have no cards
   * until an events ViewModel loads them.
   */
  private fun setAppAndGetNavigation(): NavigationActions {
    lateinit var navController: NavHostController
    composeTestRule.setContent {
      navController = rememberNavController()
      ShifterTheme { ShifterApp(isSignedIn = true, navController = navController) }
    }
    composeTestRule.waitForIdle()
    return NavigationActions(navController)
  }

  /** The event opens on the set's home tab, then each tab shows its own screen. */
  private fun assertTabsReachable(tabs: TabSet) {
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertTextEquals(tabs.home.home.title)
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
    tabs.all.forEach { tab ->
      composeTestRule.onNodeWithTag(tab.testTag).performClick()
      composeTestRule
          .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
          .assertTextEquals(tab.home.title)
    }
  }

  /** The credential Credential Manager would return for a fresh fake Google account. */
  private fun googleCredential(): GoogleIdTokenCredential {
    val email = "volunteer-${UUID.randomUUID()}@example.com"
    return GoogleIdTokenCredential.Builder()
        .setId(email)
        .setIdToken(AuthEmulator.fakeGoogleIdToken("google-" + UUID.randomUUID(), email, "Ada"))
        .build()
  }

  private companion object {
    const val TIMEOUT_MILLIS = 20_000L
  }
}
