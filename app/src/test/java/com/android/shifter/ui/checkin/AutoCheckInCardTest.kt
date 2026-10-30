// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.checkin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.checkin.FakeCheckInRepository
import com.swent.shifter.model.location.FakeLocationProvider
import com.swent.shifter.model.location.LocationStatus
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.ui.location.LocationPermissionTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoCheckInCardTest {

  @get:Rule val compose = createComposeRule()

  private fun show(state: AutoCheckInUiState) {
    compose.setContent { ShifterTheme { AutoCheckInCard(state = state, onRefreshLocation = {}) } }
  }

  @Test
  fun granted_showsTheGpsHintOnly() {
    show(AutoCheckInUiState())

    compose.onNodeWithText("Automatic GPS check-in").assertIsDisplayed()
    compose
        .onNodeWithText("Enter the event zone and the app checks you in automatically.")
        .assertIsDisplayed()
    compose.onNodeWithTag(LocationPermissionTestTags.CARD).assertDoesNotExist()
    compose.onNodeWithTag(AutoCheckInTestTags.CONFIRMATION).assertDoesNotExist()
    compose.onNodeWithTag(AutoCheckInTestTags.ERROR).assertDoesNotExist()
  }

  @Test
  fun locationNotGranted_showsThePermissionCard() {
    show(AutoCheckInUiState(locationStatus = LocationStatus.DENIED))

    compose.onNodeWithTag(LocationPermissionTestTags.CARD).assertIsDisplayed()
  }

  @Test
  fun checkedIn_showsTheConfirmation() {
    show(AutoCheckInUiState(checkedIn = AutoCheckedIn("Bar", "18:02")))

    compose
        .onNodeWithTag(AutoCheckInTestTags.CONFIRMATION)
        .assertTextEquals("Checked in: Bar, 18:02")
  }

  @Test
  fun notAllowed_showsItsMessage() =
      assertError(
          AutoCheckInError.NOT_ALLOWED,
          "The check-in was refused: ask your manager to check you in.",
      )

  @Test
  fun offline_showsItsMessage() =
      assertError(AutoCheckInError.OFFLINE, "Could not reach the server. Check your connection.")

  @Test
  fun unexpected_showsItsMessage() =
      assertError(AutoCheckInError.UNEXPECTED, "Something went wrong with the automatic check-in.")

  @Test
  fun route_showsTheViewModelsState() {
    val location = FakeLocationProvider(LocationStatus.LOCATION_OFF)
    val viewModel =
        AutoCheckInViewModel(
            FakeMissionRepository(),
            FakeCheckInRepository(),
            location,
            { null },
            { _, _, _ -> false },
        )

    compose.setContent { ShifterTheme { AutoCheckInRoute(viewModel) } }

    compose.onNodeWithTag(LocationPermissionTestTags.CARD).assertIsDisplayed()
  }

  private fun assertError(error: AutoCheckInError, message: String) {
    show(AutoCheckInUiState(error = error))

    compose.onNodeWithTag(AutoCheckInTestTags.ERROR).assertTextEquals(message)
  }
}
