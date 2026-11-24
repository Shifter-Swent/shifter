// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.permission

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * Covers [NotificationPermissionRequest] in a real activity: the permission is asked once the app
 * is reached past sign-in and in the foreground, once per installation, and not when it is granted.
 *
 * Every test runs on the default SDK of `robolectric.properties`: another SDK would make
 * Robolectric download its Android jar, which the CI cannot, and fail every test class of that run.
 */
@RunWith(AndroidJUnit4::class)
class NotificationPermissionRequestTest {

  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val inApp = MutableStateFlow(false)

  private val app = ApplicationProvider.getApplicationContext<Application>()

  private val preferences
    get() = app.getSharedPreferences(NOTIFICATION_PERMISSION_PREFERENCES, Context.MODE_PRIVATE)

  private val asked
    get() = preferences.getBoolean(NOTIFICATION_PERMISSION_ASKED, false)

  /** The permissions the activity last asked for, none if it never asked. */
  private fun requested(): List<String>? =
      shadowOf(compose.activity).lastRequestedPermission?.requestedPermissions?.toList()

  private fun show() {
    compose.setContent { NotificationPermissionRequest(inApp) }
    compose.waitForIdle()
  }

  @Test
  fun asks_onceTheAppIsReached_andRemembersIt() {
    show()
    assertNull(requested())

    inApp.value = true
    compose.waitForIdle()

    assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), requested())
    assertTrue(asked)
  }

  @Test
  fun waitsForTheForeground_beforeAsking() {
    show()
    compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

    inApp.value = true
    compose.waitForIdle()
    assertFalse(asked)

    compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    compose.waitForIdle()
    assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), requested())
  }

  @Test
  fun neverAsksAgain_onceAsked() {
    preferences.edit().putBoolean(NOTIFICATION_PERMISSION_ASKED, true).commit()
    inApp.value = true

    show()

    assertNull(requested())
  }

  @Test
  fun doesNotAsk_whenAlreadyGranted() {
    shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    inApp.value = true

    show()

    assertNull(requested())
    // Nothing asked, so nothing remembered: a permission revoked later is asked then.
    assertFalse(asked)
  }

  @Test
  fun doesNotAsk_whenGrantedWhileSigningIn() {
    show()
    shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

    inApp.value = true
    compose.waitForIdle()

    assertNull(requested())
    assertFalse(asked)
  }
}
