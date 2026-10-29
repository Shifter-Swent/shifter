// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.planning

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanningScreenTest {

  @get:Rule val compose = createComposeRule()

  private var retries = 0

  private fun show(state: PlanningUiState) {
    compose.setContent { ShifterTheme { PlanningContent(state = state, onRetry = { retries++ }) } }
  }

  @Test
  fun loading_showsTheSpinner() {
    show(PlanningUiState(isLoading = true))

    compose.onNodeWithTag(PlanningScreenTestTags.LOADING).assertIsDisplayed()
    compose.onNodeWithTag(PlanningTestTags.PLANNING).assertDoesNotExist()
  }

  @Test
  fun loaded_showsThePlanning() {
    val mission =
        PlanningMission(
            "m1",
            "Hand out water",
            Instant.parse("2025-06-14T08:30:00Z"),
            Instant.parse("2025-06-14T11:00:00Z"),
        )

    show(PlanningUiState(missions = listOf(mission), isLoading = false))

    compose.onNodeWithTag(PlanningTestTags.mission("m1")).assertIsDisplayed()
    compose.onNodeWithTag(PlanningScreenTestTags.LOADING).assertDoesNotExist()
  }

  @Test
  fun offline_showsTheConnectionMessage() {
    show(PlanningUiState(isLoading = false, error = PlanningError.OFFLINE))

    compose
        .onNodeWithTag(PlanningScreenTestTags.ERROR)
        .assertTextEquals("Could not load your missions. Check your connection.")
    compose.onNodeWithTag(PlanningTestTags.PLANNING).assertDoesNotExist()
  }

  @Test
  fun unexpectedError_showsTheGenericMessage() {
    show(PlanningUiState(isLoading = false, error = PlanningError.UNEXPECTED))

    compose
        .onNodeWithTag(PlanningScreenTestTags.ERROR)
        .assertTextEquals("Could not load your missions.")
  }

  @Test
  fun retry_afterAnError_asksToLoadAgain() {
    show(PlanningUiState(isLoading = false, error = PlanningError.OFFLINE))

    compose.onNodeWithTag(PlanningScreenTestTags.RETRY_BUTTON).performClick()

    assertEquals(1, retries)
  }
}
