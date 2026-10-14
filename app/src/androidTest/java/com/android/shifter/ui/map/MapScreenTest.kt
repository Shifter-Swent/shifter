// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapScreenTest {
  @get:Rule val compose = createComposeRule()

  private var backs = 0
  private var closes = 0
  private var retries = 0

  private fun setContent(uiState: MapUiState) {
    compose.setContent {
      ShifterTheme {
        MapContent(
            uiState = uiState,
            onBack = { backs++ },
            onMarkerClick = {},
            onCloseCard = { closes++ },
            onRetry = { retries++ },
        )
      }
    }
  }

  @Test
  fun showsTheEventAboveTheMapAndBackLeaves() {
    setContent(LOADED)

    compose.onNodeWithText("City Marathon").assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.VENUE).assertIsDisplayed()
    compose.onNodeWithTag(ShifterMapTestTags.MAP).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.NO_MISSIONS).assertDoesNotExist()

    compose.onNodeWithTag(MapScreenTestTags.BACK_BUTTON).performClick()
    assertEquals(1, backs)
  }

  @Test
  fun showsTheSpinnerWhileLoading() {
    setContent(MapUiState())

    compose.onNodeWithTag(MapScreenTestTags.LOADING).assertIsDisplayed()
  }

  @Test
  fun showsTheSelectedMissionAndClosesIt() {
    setContent(LOADED.copy(selected = WATER))

    compose.onNodeWithTag(MapScreenTestTags.MISSION_CARD).assertIsDisplayed()
    compose.onNodeWithText("10:00–11:00 · Stand").assertIsDisplayed()

    compose.onNodeWithTag(MapScreenTestTags.CLOSE_CARD_BUTTON).performClick()
    assertEquals(1, closes)
  }

  @Test
  fun saysWhenNoMissionHasAPlace() {
    setContent(LOADED.copy(markers = emptyList()))

    compose.onNodeWithTag(MapScreenTestTags.NO_MISSIONS).assertIsDisplayed()
  }

  @Test
  fun offersToRetryAfterAnError() {
    setContent(LOADED.copy(error = MapError.OFFLINE))

    compose.onNodeWithTag(MapScreenTestTags.ERROR).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.RETRY_BUTTON).performClick()
    assertEquals(1, retries)
  }

  @Test
  fun doesNotRetryAnEventThatNoLongerExists() {
    setContent(MapUiState(isLoading = false, error = MapError.EVENT_NOT_FOUND))

    compose.onNodeWithTag(MapScreenTestTags.ERROR).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.RETRY_BUTTON).assertDoesNotExist()
  }

  private companion object {
    val WATER = MapMarker("mission-1", 46.519, 6.569, "Water", "10:00–11:00 · Stand")
    val LOADED =
        MapUiState(
            eventTitle = "City Marathon",
            venue = "Place Bellecour",
            center = MapPoint(46.52, 6.57),
            markers = listOf(WATER),
            isLoading = false,
        )
  }
}
