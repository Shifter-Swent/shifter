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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.shifter.R
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
        )
      }
    }
  }

  private fun sharingSwitch() =
      composeTestRule.onNodeWithTag(SettingsScreenTestTags.LOCATION_SHARING_SWITCH)

  @Test
  fun locationSharing_showsTheSettingAndExplainsWhoSeesIt() {
    setScreen(SettingsUiState(locationSharing = true))

    sharingSwitch().assertIsOn().assertIsEnabled()
    composeTestRule
        .onNodeWithText(context.getString(R.string.settings_location_sharing_explanation))
        .assertIsDisplayed()
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

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.CHANGE_MODE_BUTTON).performClick()

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
    composeTestRule
        .onNodeWithText(context.getString(R.string.settings_sign_out))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.settings_switch_to_organizer))
        .assertIsDisplayed()
  }

  @Test
  fun signOutClick_callsOnSignOutOnly() {
    setScreen()

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.SIGN_OUT_BUTTON).performClick()

    assertEquals(1, signOutClicks)
    assertEquals(0, backClicks)
  }

  @Test
  fun backClick_callsOnBackOnly() {
    setScreen()

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()

    assertEquals(1, backClicks)
    assertEquals(0, signOutClicks)
  }
}
