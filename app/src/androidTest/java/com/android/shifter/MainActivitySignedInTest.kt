// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.swent.shifter.firebase.AuthEmulator
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.model.authentication.AuthRepositoryFirebase
import com.swent.shifter.model.authentication.DefaultGoogleSignInHelper
import com.swent.shifter.model.notification.AppNotification
import com.swent.shifter.model.notification.NotificationKind
import com.swent.shifter.model.notification.NotificationTarget
import com.swent.shifter.model.notification.notificationTapIntent
import com.swent.shifter.model.user.USERS_COLLECTION
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.model.user.UserRepositoryFirestore
import com.swent.shifter.ui.authentication.SignInScreenTestTags
import com.swent.shifter.ui.events.MyEventsTestTags
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/**
 * Verifies that launching the activity with a restored session skips sign-in, then enters the saved
 * mode once it is read.
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySignedInTest {

  // Runs before the activity launches, since MainActivity reads the session in onCreate. It also
  // wires the shared Firestore instance to the emulator before RepositoryProviderFactory.production
  // can reach it, so a later useEmulator call in this process does not throw.
  @get:Rule(order = 0)
  val signedIn =
      object : ExternalResource() {
        override fun before() {
          val firestore = FirestoreEmulator.firestore
          runBlocking {
            val user =
                AuthRepositoryFirebase(AuthEmulator.auth, DefaultGoogleSignInHelper())
                    .signInWithGoogle(googleCredential())
                    .getOrThrow()
            // An account that already chose Volunteer: without a profile, the app would ask.
            UserRepositoryFirestore(firestore)
                .getOrCreateUser(user.uid, "Ada", user.email.orEmpty(), UserMode.VOLUNTEER)
          }
        }

        override fun after() {
          AuthEmulator.auth.currentUser?.uid?.let { uid ->
            runBlocking {
              FirestoreEmulatorAdmin.deleteDocument("$USERS_COLLECTION/$uid")
              FirestoreEmulatorAdmin.deleteDocument("publicProfiles/$uid")
            }
          }
          AuthEmulator.auth.signOut()
          AuthEmulator.clearAccounts()
        }
      }

  // Granted before launch, so the system dialog MainActivity opens once My Events is shown
  // (NotificationPermissionRequest) does not cover the screens under test.
  @get:Rule(order = 1)
  val notifications: GrantPermissionRule =
      GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

  @get:Rule(order = 2) val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun launch_withRestoredSession_opensTheVolunteerEvents() {
    // The start screen reads the mode first, for up to StartViewModel.READ_TIMEOUT_MILLIS.
    waitForNode(MyEventsTestTags.STAFF_SCREEN)
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOGIN_BUTTON).assertDoesNotExist()
  }

  @Test
  fun notificationTapped_whileInTheApp_opensItsTarget() {
    waitForNode(MyEventsTestTags.STAFF_SCREEN)

    // The tap brings back the activity already shown (singleTop), through onNewIntent.
    tap(NotificationTarget.MyEvents(UserMode.ORGANIZER))

    // The saved mode is Volunteer: only the target leads to the organizer's events.
    waitForNode(MyEventsTestTags.ORGANIZER_SCREEN)
  }

  @Test
  fun recreated_afterATargetOpened_staysOnIt() {
    waitForNode(MyEventsTestTags.STAFF_SCREEN)
    tap(NotificationTarget.MyEvents(UserMode.ORGANIZER))
    waitForNode(MyEventsTestTags.ORGANIZER_SCREEN)

    // As after a rotation: the activity saves its state, then is created again from it.
    composeTestRule.activityRule.scenario.recreate()

    waitForNode(MyEventsTestTags.ORGANIZER_SCREEN)
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertDoesNotExist()
  }

  /** Sends the intent of a tap on a notification opening [target], as Android would. */
  private fun tap(target: NotificationTarget) {
    val activity = composeTestRule.activity
    val notification =
        AppNotification(
            kind = NotificationKind.NEW_APPLICATION,
            key = "event-1",
            title = "New applications",
            text = "Ada applied",
            at = Instant.EPOCH,
            target = target,
        )
    activity.startActivity(notificationTapIntent(activity, notification))
  }

  private fun waitForNode(tag: String) {
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
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
    /** Longer than the read of the mode, which falls back to Volunteer once it times out. */
    const val TIMEOUT_MILLIS = 10_000L
  }
}
