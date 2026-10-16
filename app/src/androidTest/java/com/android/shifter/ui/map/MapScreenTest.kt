// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import com.swent.shifter.ui.theme.ShifterTheme
import kotlin.math.abs
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
    compose.onNodeWithText("Event venue").assertIsDisplayed()
    compose.onNodeWithTag(ShifterMapTestTags.MAP).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.NO_MISSIONS).assertDoesNotExist()

    compose.onNodeWithTag(MapScreenTestTags.BACK_BUTTON).performClick()
    assertEquals(1, backs)
  }

  @Test
  fun showsTheSpinnerWhileLoadingWithNothingToRecenterOn() {
    setContent(MapUiState())

    compose.onNodeWithTag(MapScreenTestTags.LOADING).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.RECENTER_BUTTON).assertDoesNotExist()
  }

  @Test
  fun recenterButton_movesTheCameraBackToTheCenter() {
    lateinit var camera: CameraPositionState
    compose.setContent {
      ShifterTheme {
        camera = rememberCameraPositionState()
        MapContent(
            uiState = LOADED,
            onBack = {},
            onMarkerClick = {},
            onCloseCard = {},
            onRetry = {},
            cameraPositionState = camera,
        )
      }
    }
    // Moved away first, since the camera already opens on the center.
    compose.runOnIdle { camera.position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 15f) }

    compose.onNodeWithTag(MapScreenTestTags.RECENTER_BUTTON).assertIsDisplayed().performClick()

    compose.waitUntil(timeoutMillis = 5_000) { camera.position.target.isNear(LOADED.center!!) }
  }

  // Markers are drawn into bitmaps inside the native map, which Compose tests can't reach, so the
  // label is checked on its own.
  @Test
  fun missionLabelShowsItsCaptionOnlyWhenItHasOne() {
    compose.setContent {
      ShifterTheme {
        Column {
          MissionLabel(WATER.copy(caption = "NEXT MISSION · 10:00", highlighted = true))
          MissionLabel(WATER.copy(id = "mission-2", title = "Cleanup"))
        }
      }
    }

    compose.onNodeWithText("NEXT MISSION · 10:00").assertIsDisplayed()
    compose.onNodeWithText("Water").assertIsDisplayed()
    compose.onNodeWithText("Cleanup").assertIsDisplayed()
    compose.onAllNodesWithText("NEXT MISSION", substring = true).assertCountEquals(1)
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

  private fun LatLng.isNear(point: MapPoint) =
      abs(latitude - point.latitude) < 1e-4 && abs(longitude - point.longitude) < 1e-4

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
