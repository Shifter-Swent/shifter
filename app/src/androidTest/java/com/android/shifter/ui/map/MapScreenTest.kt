// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.ui.navigation.NavigationTestTags
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

  private fun setContent(uiState: MapUiState, volunteers: List<VolunteerPin> = emptyList()) {
    compose.setContent {
      ShifterTheme {
        MapContent(
            uiState = uiState,
            onBack = { backs++ },
            onMarkerClick = {},
            onCloseCard = { closes++ },
            onRetry = { retries++ },
            volunteers = volunteers,
        )
      }
    }
  }

  @Test
  fun loaded_showsTheEventAboveTheMapAndBackLeaves() {
    setContent(LOADED)

    compose.onNodeWithText("City Marathon").assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.VENUE).assertIsDisplayed()
    compose.onNodeWithText("Event venue").assertIsDisplayed()
    compose.onNodeWithTag(ShifterMapTestTags.MAP).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.NO_MISSIONS).assertDoesNotExist()

    compose.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    assertEquals(1, backs)
  }

  @Test
  fun loading_showsTheSpinnerWithNothingToRecenterOn() {
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
  fun missionLabel_showsItsCaptionOnlyWhenItHasOne() {
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
  fun selectingAMission_showsItAndClosesIt() {
    setContent(LOADED.copy(selected = WATER))

    compose.onNodeWithTag(MapScreenTestTags.MISSION_CARD).assertIsDisplayed()
    compose.onNodeWithText("10:00–11:00 · Stand").assertIsDisplayed()

    compose.onNodeWithTag(MapScreenTestTags.CLOSE_CARD_BUTTON).performClick()
    assertEquals(1, closes)
  }

  @Test
  fun noMissionWithAPlace_saysSo() {
    setContent(LOADED.copy(markers = emptyList()))

    compose.onNodeWithTag(MapScreenTestTags.NO_MISSIONS).assertIsDisplayed()
  }

  @Test
  fun error_offersToRetry() {
    setContent(LOADED.copy(error = MapError.OFFLINE))

    compose.onNodeWithTag(MapScreenTestTags.ERROR).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.RETRY_BUTTON).performClick()
    assertEquals(1, retries)
  }

  @Test
  fun missingEvent_isNotRetried() {
    setContent(MapUiState(isLoading = false, error = MapError.EVENT_NOT_FOUND))

    compose.onNodeWithTag(MapScreenTestTags.ERROR).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.RETRY_BUTTON).assertDoesNotExist()
  }

  private fun LatLng.isNear(point: GeoPosition) =
      abs(latitude - point.latitude) < 1e-4 && abs(longitude - point.longitude) < 1e-4

  @Test
  fun volunteerLabel_showsTheNameAndHowOldThePositionIs() {
    compose.setContent {
      ShifterTheme {
        Column {
          VolunteerLabel(MapMarker("volunteer-alice", HERE, "Alice", age(3)), false)
          Text(age(0))
          Text(age(1))
        }
      }
    }

    compose.onNodeWithTag(MapScreenTestTags.VOLUNTEER).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.STALE_VOLUNTEER).assertDoesNotExist()
    // The name and the age share one line above the dot.
    compose.onNodeWithText("Alice · 3 min").assertIsDisplayed()
    compose.onNodeWithText("now").assertIsDisplayed()
    compose.onNodeWithText("1 min").assertIsDisplayed()
  }

  @Test
  fun volunteerLabel_whenStale_isGreyedOut() {
    compose.setContent {
      ShifterTheme { VolunteerLabel(MapMarker("volunteer-carol", HERE, "Carol", age(17)), true) }
    }

    compose.onNodeWithTag(MapScreenTestTags.STALE_VOLUNTEER).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.VOLUNTEER).assertDoesNotExist()
    compose.onNodeWithText("Carol · 17 min").assertIsDisplayed()
  }

  @Test
  fun volunteerAge_fromAnHour_countsWholeHours() {
    assertEquals("59 min", age(59))
    assertEquals("1 h", age(60))
    // Rounded down: the position is at least that old.
    assertEquals("2 h", age(179))
  }

  private fun age(minutesAgo: Long) =
      volunteerAge(
          InstrumentationRegistry.getInstrumentation().targetContext.resources,
          minutesAgo,
      )

  @Test
  fun loaded_withVolunteers_showsTheMapWithoutAnyCard() {
    setContent(
        LOADED,
        volunteers =
            listOf(
                VolunteerPin("alice", "Alice", HERE, minutesAgo = 2, isStale = false),
                VolunteerPin("bob", null, HERE, minutesAgo = 12, isStale = true),
            ),
    )

    compose.onNodeWithTag(ShifterMapTestTags.MAP).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.MISSION_CARD).assertDoesNotExist()
    compose.onNodeWithTag(MapScreenTestTags.ERROR).assertDoesNotExist()
  }

  private companion object {
    val HERE = GeoPosition(46.52, 6.57)
    val WATER = MapMarker("mission-1", GeoPosition(46.519, 6.569), "Water", "10:00–11:00 · Stand")
    val LOADED =
        MapUiState(
            eventTitle = "City Marathon",
            venue = "Place Bellecour",
            center = GeoPosition(46.52, 6.57),
            markers = listOf(WATER),
            isLoading = false,
        )
  }
}
