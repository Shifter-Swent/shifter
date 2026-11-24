// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.swent.shifter.firebase.AuthEmulator
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.model.authentication.AuthRepositoryFirebase
import com.swent.shifter.model.authentication.DefaultGoogleSignInHelper
import com.swent.shifter.model.user.USERS_COLLECTION
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.model.user.UserRepositoryFirestore
import com.swent.shifter.ui.permission.NOTIFICATION_PERMISSION_ASKED
import com.swent.shifter.ui.permission.NOTIFICATION_PERMISSION_PREFERENCES
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/**
 * Verifies that MainActivity asks for the notification permission once My Events is shown, which a
 * restored session with a profile reaches without any tap. That it does not ask on sign-in is
 * checked by [MainActivityTest].
 *
 * The permission is not granted here, so the system dialog opens at the end; it is closed after the
 * test, and the record of the question cleared, so the next tests start as on a new phone.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityNotificationPermissionTest {

  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

  private val preferences
    get() = context.getSharedPreferences(NOTIFICATION_PERMISSION_PREFERENCES, Context.MODE_PRIVATE)

  private val asked
    get() = preferences.getBoolean(NOTIFICATION_PERMISSION_ASKED, false)

  // Runs before the activity launches, which reads the session in onCreate.
  @get:Rule(order = 0)
  val signedInVolunteer =
      object : ExternalResource() {
        override fun before() {
          preferences.edit(commit = true) { clear() }
          runBlocking {
            val user =
                AuthRepositoryFirebase(AuthEmulator.auth, DefaultGoogleSignInHelper())
                    .signInWithGoogle(googleCredential())
                    .getOrThrow()
            // A profile with a mode: the app opens on My Events, with no choice to make.
            UserRepositoryFirestore(FirestoreEmulator.firestore)
                .getOrCreateUser(user.uid, "Ada", user.email.orEmpty(), UserMode.VOLUNTEER)
          }
        }

        override fun after() {
          // Closes the system dialog, so it does not cover the next tests.
          UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
          preferences.edit(commit = true) { clear() }
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

  @get:Rule(order = 1) val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun restoredSession_asksForTheNotificationPermission_onMyEvents() {
    // Through the compose rule, which drives the app's frames, so the navigation to My Events and
    // the request run. Once the system dialog opens, MainActivity is in the background and the rule
    // finds no compose hierarchy any more: the question was recorded just before.
    try {
      composeTestRule.waitUntil(TIMEOUT_MILLIS) { asked }
    } catch (_: IllegalStateException) {}
    assertTrue(asked)
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
    /** Longer than the read of the profile, which the start screen waits for before My Events. */
    const val TIMEOUT_MILLIS = 10_000L
  }
}
