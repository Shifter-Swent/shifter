// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.shifter.R
import com.swent.shifter.model.notification.NotificationKind
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.ui.navigation.NavigationTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private var backClicks = 0
  private var signOutClicks = 0
  private var changeModeClicks = 0

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val sharingChanges = mutableListOf<Boolean>()
  private val notificationChanges = mutableListOf<Pair<NotificationKind, Boolean>>()

  /** What the screen shows: a test changes it after [setScreen] to have the screen recompose. */
  private var state by mutableStateOf(SettingsUiState())

  private fun setScreen(initial: SettingsUiState = SettingsUiState()) {
    state = initial
    composeTestRule.setContent {
      ShifterTheme {
        SettingsScreen(
            state = state,
            onBack = { backClicks++ },
            onChangeMode = { changeModeClicks++ },
            onSignOut = { signOutClicks++ },
            onLocationSharingChange = { sharingChanges += it },
            onNotificationChange = { kind, enabled -> notificationChanges += kind to enabled },
        )
      }
    }
  }

  // The screen scrolls: each helper brings its node into view first.
  private fun sharingSwitch() =
      composeTestRule
          .onNodeWithTag(SettingsScreenTestTags.LOCATION_SHARING_SWITCH)
          .performScrollTo()

  private fun signOutButton() =
      composeTestRule.onNodeWithTag(SettingsScreenTestTags.SIGN_OUT_BUTTON).performScrollTo()

  private fun changeModeButton() =
      composeTestRule.onNodeWithTag(SettingsScreenTestTags.CHANGE_MODE_BUTTON).performScrollTo()

  private fun notificationSwitch(kind: NotificationKind) =
      composeTestRule
          .onNodeWithTag(SettingsScreenTestTags.notificationSwitch(kind))
          .performScrollTo()

  private fun text(id: Int, vararg args: Any) =
      composeTestRule.onNodeWithText(context.getString(id, *args)).performScrollTo()

  @Test
  fun notifications_inVolunteerMode_showTheVolunteerAndManagerGroups() {
    setScreen(SettingsUiState(savedDisabledNotifications = emptySet()))

    for (group in listOf(NotificationGroup.VOLUNTEER, NotificationGroup.MANAGER)) {
      text(group.title).assertIsDisplayed()
      for (kind in group.kinds) notificationSwitch(kind).assertIsOn().assertIsEnabled()
    }
    text(R.string.notification_channel_missed_check_in_description).assertIsDisplayed()
    text(R.string.settings_notifications_help).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.notificationGroup(NotificationGroup.ORGANIZER))
        .assertDoesNotExist()
    sharingSwitch().assertIsDisplayed()
  }

  @Test
  fun notifications_inOrganizerMode_showTheOrganizerGroupOnly() {
    setScreen(SettingsUiState(mode = UserMode.ORGANIZER, savedDisabledNotifications = emptySet()))

    text(R.string.settings_notifications_as_organizer).assertIsDisplayed()
    for (kind in NotificationGroup.ORGANIZER.kinds) notificationSwitch(kind).assertIsOn()
    text(R.string.notification_channel_open_spots_description, 24).assertIsDisplayed()
    for (group in listOf(NotificationGroup.VOLUNTEER, NotificationGroup.MANAGER)) {
      composeTestRule
          .onNodeWithTag(SettingsScreenTestTags.notificationGroup(group))
          .assertDoesNotExist()
    }
  }

  @Test
  fun locationSharing_inOrganizerMode_isStillShown() {
    // The position is published whatever the mode: the same account may volunteer elsewhere.
    setScreen(SettingsUiState(mode = UserMode.ORGANIZER, locationSharing = true))

    sharingSwitch().assertIsDisplayed().assertIsOn()
  }

  @Test
  fun notificationClick_asksToTurnThatKindOff() {
    setScreen(SettingsUiState(savedDisabledNotifications = setOf(NotificationKind.ANNOUNCEMENT)))

    notificationSwitch(NotificationKind.ANNOUNCEMENT).assertIsOff()
    notificationSwitch(NotificationKind.MISSION_CHANGED).assertIsOn().performClick()

    assertEquals(listOf(NotificationKind.MISSION_CHANGED to false), notificationChanges)
  }

  @Test
  fun notifications_whileUnknownOrSaving_cannotBeChanged() {
    setScreen()
    notificationSwitch(NotificationKind.ANNOUNCEMENT).assertIsNotEnabled()

    state =
        SettingsUiState(
            savedDisabledNotifications = emptySet(),
            pendingNotifications = mapOf(NotificationKind.ANNOUNCEMENT to false),
        )

    notificationSwitch(NotificationKind.ANNOUNCEMENT).assertIsOff().assertIsNotEnabled()
    notificationSwitch(NotificationKind.ANNOUNCEMENT).performClick()
    notificationSwitch(NotificationKind.MISSION_CHANGED).assertIsEnabled()
    assertEquals(emptyList<Pair<NotificationKind, Boolean>>(), notificationChanges)
  }

  @Test
  fun notifications_showEachNotice() {
    setScreen(SettingsUiState(savedDisabledNotifications = emptySet()))
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.NOTIFICATIONS_NOTICE).assertDoesNotExist()
    val messages =
        mapOf(
            NotificationsNotice.LOAD_FAILED to R.string.settings_notifications_load_failed,
            NotificationsNotice.SAVE_FAILED to R.string.settings_notifications_save_failed,
            NotificationsNotice.SAVED_OFFLINE to R.string.settings_notifications_saved_offline,
        )

    for ((notice, message) in messages) {
      state = SettingsUiState(notificationsNotice = notice)

      composeTestRule
          .onNodeWithTag(SettingsScreenTestTags.NOTIFICATIONS_NOTICE)
          .assertTextEquals(context.getString(message))
    }
  }

  @Test
  fun locationSharing_showsTheSettingAndExplainsWhoSeesIt() {
    setScreen(SettingsUiState(locationSharing = true))

    sharingSwitch().assertIsOn().assertIsEnabled()
    composeTestRule
        .onNodeWithText(context.getString(R.string.settings_privacy_section).uppercase())
        .performScrollTo()
        .assertIsDisplayed()
    text(R.string.settings_location_sharing_explanation).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.LOCATION_SHARING_NOTICE)
        .assertDoesNotExist()
  }

  @Test
  fun locationSharingClick_asksForTheOtherValue() {
    setScreen(SettingsUiState(locationSharing = false))

    sharingSwitch().performClick()

    assertEquals(listOf(true), sharingChanges)
  }

  @Test
  fun locationSharing_whileUnknownOrSaving_cannotBeChanged() {
    setScreen()
    sharingSwitch().assertIsOff().assertIsNotEnabled()

    state = SettingsUiState(locationSharing = true, savingLocationSharing = true)

    sharingSwitch().assertIsOn().assertIsNotEnabled().performClick()
    assertEquals(emptyList<Boolean>(), sharingChanges)
  }

  @Test
  fun locationSharing_showsEachNotice() {
    setScreen()
    val messages =
        mapOf(
            LocationSharingNotice.LOAD_FAILED to R.string.settings_location_sharing_load_failed,
            LocationSharingNotice.SAVE_FAILED to R.string.settings_location_sharing_save_failed,
            LocationSharingNotice.SAVED_OFFLINE to R.string.settings_location_sharing_saved_offline,
        )

    for ((notice, message) in messages) {
      state = SettingsUiState(locationSharing = false, locationSharingNotice = notice)

      composeTestRule
          .onNodeWithTag(SettingsScreenTestTags.LOCATION_SHARING_NOTICE)
          .assertTextEquals(context.getString(message))
    }
  }

  @Test
  fun changeModeClick_callsOnChangeModeOnly() {
    setScreen()

    changeModeButton().performClick()

    assertEquals(1, changeModeClicks)
    assertEquals(0, signOutClicks)
  }

  @Test
  fun changeMode_offersTheOtherMode() {
    setScreen(SettingsUiState(mode = UserMode.ORGANIZER))

    composeTestRule
        .onNodeWithText(context.getString(R.string.settings_switch_to_volunteer))
        .assertIsDisplayed()
  }

  @Test
  fun changingMode_disablesTheButton() {
    setScreen(SettingsUiState(changingMode = true))

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.CHANGE_MODE_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun opening_displaysHeaderAndSignOut() {
    setScreen()

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.SCREEN).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertTextEquals(context.getString(R.string.settings_title))
    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).assertIsDisplayed()
    text(R.string.settings_sign_out).assertIsDisplayed()
    text(R.string.settings_switch_to_organizer).assertIsDisplayed()
  }

  @Test
  fun signOutClick_callsOnSignOutOnly() {
    setScreen()

    signOutButton().performClick()

    assertEquals(1, signOutClicks)
    assertEquals(0, backClicks)
  }

  @Test
  fun signOut_whileLocationSharingIsSaved_cannotBeClicked() {
    setScreen()
    signOutButton().assertIsEnabled()

    state = SettingsUiState(locationSharing = false, savingLocationSharing = true)
    signOutButton().assertIsNotEnabled().performClick()
    assertEquals(0, signOutClicks)

    state = SettingsUiState(locationSharing = false)
    signOutButton().assertIsEnabled().performClick()
    assertEquals(1, signOutClicks)
  }

  @Test
  fun backClick_callsOnBackOnly() {
    setScreen()

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()

    assertEquals(1, backClicks)
    assertEquals(0, signOutClicks)
  }
}
