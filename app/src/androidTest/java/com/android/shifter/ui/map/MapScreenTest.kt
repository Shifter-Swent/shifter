// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.location.LocationStatus
import com.swent.shifter.ui.location.LocationPermissionTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapScreenTest {
  @get:Rule val compose = createComposeRule()

  private var closes = 0
  private var retries = 0
  private var refreshes = 0

  private fun setContent(
      uiState: MapUiState,
      volunteers: List<VolunteerPin> = emptyList(),
      myPosition: GeoPosition? = null,
      locationStatus: LocationStatus? = null,
  ) {
    compose.setContent {
      ShifterTheme {
        MapContent(
            uiState = uiState,
            onMarkerClick = {},
            onCloseCard = { closes++ },
            onRetry = { retries++ },
            myPosition = myPosition,
            volunteers = volunteers,
            locationStatus = locationStatus,
            onRefreshLocation = { refreshes++ },
        )
      }
    }
  }

  @Test
  fun loaded_showsTheVenueAboveTheMap() {
    setContent(LOADED)

    compose.onNodeWithTag(MapScreenTestTags.VENUE).assertIsDisplayed()
    compose.onNodeWithText("Event venue").assertIsDisplayed()
    compose.onNodeWithTag(ShifterMapTestTags.MAP).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.NO_MISSIONS).assertDoesNotExist()
  }

  @Test
  fun locationDenied_showsTheGateOverTheMapAndRefreshesTheStatus() {
    setContent(LOADED, locationStatus = LocationStatus.DENIED)

    compose.onNodeWithTag(MapScreenTestTags.LOCATION_GATE).assertIsDisplayed()
    // Its action depends on the device's real permissions, which other tests may have granted.
    compose.onNodeWithTag(LocationPermissionTestTags.ACTION).assertIsDisplayed()
    compose.onNodeWithTag(ShifterMapTestTags.MAP).assertIsDisplayed()
    compose.waitForIdle()
    assertTrue(refreshes > 0)
  }

  @Test
  fun locationOff_offersToTurnItOnWithTheMapsMessage() {
    setContent(LOADED, locationStatus = LocationStatus.LOCATION_OFF)

    compose.onNodeWithTag(MapScreenTestTags.LOCATION_GATE).assertIsDisplayed()
    compose.onNodeWithText("the map cannot show you", substring = true).assertIsDisplayed()
    compose.onNodeWithText("checked in", substring = true).assertDoesNotExist()
    compose.onNodeWithText("Turn on location").assertIsDisplayed()
  }

  @Test
  fun locationGranted_showsNoGate() {
    setContent(LOADED, locationStatus = LocationStatus.GRANTED)

    compose.onNodeWithTag(MapScreenTestTags.LOCATION_GATE).assertDoesNotExist()
  }

  @Test
  fun noLocationStatus_showsNoGate() {
    setContent(LOADED, locationStatus = null)

    compose.onNodeWithTag(MapScreenTestTags.LOCATION_GATE).assertDoesNotExist()
  }

  @Test
  fun locationGranted_hidesTheGateAndShowsTheUser() {
    var status by mutableStateOf(LocationStatus.DENIED)
    var position by mutableStateOf<GeoPosition?>(null)
    compose.setContent {
      ShifterTheme {
        MapContent(
            uiState = LOADED,
            onMarkerClick = {},
            onCloseCard = {},
            onRetry = {},
            myPosition = position,
            locationStatus = status,
        )
      }
    }
    compose.onNodeWithTag(MapScreenTestTags.LOCATION_GATE).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.CENTER_ON_ME_BUTTON).assertDoesNotExist()

    status = LocationStatus.GRANTED
    position = ME

    compose.onNodeWithTag(MapScreenTestTags.LOCATION_GATE).assertDoesNotExist()
    compose.onNodeWithTag(MapScreenTestTags.CENTER_ON_ME_BUTTON).assertIsDisplayed()
  }

  @Test
  fun loading_showsTheSpinnerWithNothingToRecenterOn() {
    setContent(MapUiState())

    compose.onNodeWithTag(MapScreenTestTags.LOADING).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.RECENTER_BUTTON).assertDoesNotExist()
  }

  @Test
  fun contentPadding_keepsTheRecenterButtonAboveTheBar() {
    val barHeight = 100.dp
    compose.setContent {
      ShifterTheme {
        MapContent(
            uiState = LOADED,
            onMarkerClick = {},
            onCloseCard = {},
            onRetry = {},
            contentPadding = PaddingValues(bottom = barHeight),
        )
      }
    }

    val screen = compose.onNodeWithTag(MapScreenTestTags.SCREEN).getUnclippedBoundsInRoot()
    val button = compose.onNodeWithTag(MapScreenTestTags.RECENTER_BUTTON).getUnclippedBoundsInRoot()

    assertTrue(button.bottom <= screen.bottom - barHeight)
  }

  @Test
  fun loading_withMyPosition_waitsForTheEventToOfferTheButtons() {
    setContent(MapUiState(), myPosition = ME)

    compose.onNodeWithTag(MapScreenTestTags.LOADING).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.CENTER_ON_ME_BUTTON).assertDoesNotExist()
    compose.onNodeWithTag(MapScreenTestTags.RECENTER_BUTTON).assertDoesNotExist()
  }

  @Test
  fun eventNotFound_withMyPosition_offersNoButton() {
    setContent(MapUiState(isLoading = false, error = MapError.EVENT_NOT_FOUND), myPosition = ME)

    compose.onNodeWithTag(MapScreenTestTags.ERROR).assertIsDisplayed()
    compose.onNodeWithTag(MapScreenTestTags.CENTER_ON_ME_BUTTON).assertDoesNotExist()
    compose.onNodeWithTag(MapScreenTestTags.RECENTER_BUTTON).assertDoesNotExist()
  }

  @Test
  fun centerOnMeButton_centersOnTheUser() {
    val camera = setContentWithCamera(myPosition = ME)

    compose
        .onNodeWithTag(MapScreenTestTags.CENTER_ON_ME_BUTTON)
        .assertContentDescriptionEquals("Center on your position")
        .performClick()

    compose.waitUntilCentred(camera, ME, zoom = 15f)
  }

  @Test
  fun recenterButton_withMyPosition_stillGoesBackToTheEvent() {
    val camera = setContentWithCamera(myPosition = ME)

    compose
        .onNodeWithTag(MapScreenTestTags.RECENTER_BUTTON)
        .assertContentDescriptionEquals("Center on the event")
        .performClick()

    compose.waitUntilCentred(camera, EVENT, zoom = 15f)
  }

  @Test
  fun recenterButton_withoutMyPosition_centersOnTheEventAlone() {
    val camera = setContentWithCamera(myPosition = null)

    compose.onNodeWithTag(MapScreenTestTags.CENTER_ON_ME_BUTTON).assertDoesNotExist()
    compose.onNodeWithTag(MapScreenTestTags.RECENTER_BUTTON).performClick()

    compose.waitUntilCentred(camera, EVENT, zoom = 15f)
  }

  @Test
  fun centerOnMeButton_secondTap_zoomsInOnTheUser() {
    val camera = setContentWithCamera(myPosition = ME)

    tapAndWait(MapScreenTestTags.CENTER_ON_ME_BUTTON, camera, ME, zoom = 15f)
    tapAndWait(MapScreenTestTags.CENTER_ON_ME_BUTTON, camera, ME, zoom = 16.5f)
  }

  @Test
  fun centerOnMeButton_onceCentred_stopsZoomingInAt19() {
    val camera = setContentWithCamera(myPosition = ME, start = cameraAt(ME, 18f))

    // 18 + 1.5 would pass the limit.
    tapAndWait(MapScreenTestTags.CENTER_ON_ME_BUTTON, camera, ME, zoom = 19f)
  }

  @Test
  fun centerOnMeButton_afterTheUserWalkedAFewMeters_stillZoomsIn() {
    lateinit var camera: CameraPositionState
    var myPosition by mutableStateOf(ME)
    compose.setContent {
      ShifterTheme {
        camera = rememberCameraPositionState()
        MapContent(
            uiState = LOADED,
            onMarkerClick = {},
            onCloseCard = {},
            onRetry = {},
            myPosition = myPosition,
            cameraPositionState = camera,
        )
      }
    }
    awaitTheMap(camera, cameraAt(ME, 15f))

    // About 22 m to the north, within the margin of a centred camera.
    val walked = GeoPosition(ME.latitude + 0.0002, ME.longitude)
    compose.runOnIdle { myPosition = walked }

    tapAndWait(MapScreenTestTags.CENTER_ON_ME_BUTTON, camera, walked, zoom = 16.5f)
  }

  @Test
  fun recenterButton_secondTap_zoomsInOnTheEvent() {
    val camera = setContentWithCamera(myPosition = null)

    tapAndWait(MapScreenTestTags.RECENTER_BUTTON, camera, EVENT, zoom = 15f)
    tapAndWait(MapScreenTestTags.RECENTER_BUTTON, camera, EVENT, zoom = 16.5f)
  }

  @Test
  fun recenterButton_onceCentred_stopsZoomingInAt19() {
    val camera = setContentWithCamera(myPosition = null, start = cameraAt(EVENT, 18f))

    // 18 + 1.5 would pass the limit.
    tapAndWait(MapScreenTestTags.RECENTER_BUTTON, camera, EVENT, zoom = 19f)
  }

  @Test
  fun recenterButton_afterCentringOnTheUser_centresOnTheEventAtTheEventZoom() {
    val camera = setContentWithCamera(myPosition = ME, start = cameraAt(ME, 16.5f))

    tapAndWait(MapScreenTestTags.RECENTER_BUTTON, camera, EVENT, zoom = 15f)
  }

  /** Taps the button of [tag], then waits for the camera to rest on [target] at [zoom]. */
  private fun tapAndWait(
      tag: String,
      camera: CameraPositionState,
      target: GeoPosition,
      zoom: Float,
  ) {
    compose.onNodeWithTag(tag).performClick()
    compose.waitUntilCentred(camera, target, zoom)
  }

  /**
   * Waits for the camera to rest on [target] at [zoom], so the next tap sees it still. Once the map
   * is bound, only its own listeners update the state's position, so a camera seen on [target] is
   * the map's report. Generous, since the emulators of the CI draw the map in software.
   */
  private fun ComposeContentTestRule.waitUntilCentred(
      camera: CameraPositionState,
      target: GeoPosition,
      zoom: Float,
  ) {
    try {
      waitUntil(timeoutMillis = 20_000) { !camera.isMoving && camera.position.isAt(target, zoom) }
    } catch (e: ComposeTimeoutException) {
      throw AssertionError(
          "Expected $target at zoom $zoom, camera at ${camera.position}, moving=${camera.isMoving}",
          e,
      )
    }
  }

  /**
   * Waits for the map to be bound, then parks its camera at [start] and waits for the map to report
   * it. Until the map has drawn, the state still shows the camera it had before the map, so a tap
   * would read a stale camera, and an animation started then can be dropped.
   */
  private fun awaitTheMap(camera: CameraPositionState, start: CameraPosition) {
    // The projection is read on the main thread, as every call to the map must be.
    compose.waitUntil(timeoutMillis = 20_000) {
      compose.runOnUiThread { camera.projection != null }
    }
    val target = start.target.toGeoPosition()
    compose.runOnIdle {
      // Else the camera the state had before the map would pass for the map's report.
      check(!camera.position.isAt(target, start.zoom)) {
        "Start the camera away from ${camera.position}, where it opens"
      }
      camera.move(CameraUpdateFactory.newCameraPosition(start))
    }
    compose.waitUntilCentred(camera, target, start.zoom)
  }

  private fun CameraPosition.isAt(point: GeoPosition, zoom: Float) =
      this.target.isNear(point) && abs(this.zoom - zoom) < 0.01f

  private fun cameraAt(position: GeoPosition, zoom: Float) =
      CameraPosition.fromLatLngZoom(position.toLatLng(), zoom)

  /**
   * Shows the loaded map with [myPosition], once the map is ready, its camera parked at [start]: by
   * default away from where the buttons go, and at another zoom, so the first tap is seen to set
   * it.
   */
  private fun setContentWithCamera(
      myPosition: GeoPosition?,
      start: CameraPosition = AWAY,
  ): CameraPositionState {
    lateinit var camera: CameraPositionState
    compose.setContent {
      ShifterTheme {
        camera = rememberCameraPositionState()
        MapContent(
            uiState = LOADED,
            onMarkerClick = {},
            onCloseCard = {},
            onRetry = {},
            myPosition = myPosition,
            cameraPositionState = camera,
        )
      }
    }
    awaitTheMap(camera, start)
    return camera
  }

  @Test
  fun focus_movesTheCameraToTheMissionOnce() {
    lateinit var camera: CameraPositionState
    var shown = 0
    compose.setContent {
      ShifterTheme {
        camera = rememberCameraPositionState()
        MapContent(
            uiState = LOADED.copy(selected = WATER, focus = FOCUS),
            onMarkerClick = {},
            onCloseCard = {},
            onRetry = {},
            onFocusShown = { shown++ },
            cameraPositionState = camera,
        )
      }
    }

    // The move waits for the map, which may be ready after the effect ran.
    compose.waitUntil(timeoutMillis = 5_000) { camera.position.target.isNear(FOCUS) }
    compose.waitForIdle()
    assertEquals(1, shown)
  }

  @Test
  fun focus_onAMapAlreadyShown_movesTheCameraToTheMission() {
    lateinit var camera: CameraPositionState
    var uiState by mutableStateOf(LOADED)
    compose.setContent {
      ShifterTheme {
        camera = rememberCameraPositionState()
        MapContent(
            uiState = uiState,
            onMarkerClick = {},
            onCloseCard = {},
            onRetry = {},
            onFocusShown = { uiState = uiState.copy(focus = null) },
            cameraPositionState = camera,
        )
      }
    }
    compose.waitUntil(timeoutMillis = 5_000) { camera.position.target.isNear(LOADED.center!!) }

    // "View on map" with the Map tab already open.
    compose.runOnIdle { uiState = uiState.copy(selected = WATER, focus = FOCUS) }

    compose.waitUntil(timeoutMillis = 5_000) { camera.position.target.isNear(FOCUS) }
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
    /** The user, far enough from the event for the camera to tell them apart. */
    val ME = GeoPosition(46.53, 6.60)
    val WATER = MapMarker("mission-1", GeoPosition(46.519, 6.569), "Water", "10:00–11:00 · Stand")
    /** Away from the center the camera opens on. */
    val FOCUS = GeoPosition(46.53, 6.58)
    /** Far from the event and the user, at another zoom than the buttons'. */
    val AWAY: CameraPosition = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 10f)
    /** Where the loaded event centres the map. */
    val EVENT = GeoPosition(46.52, 6.57)
    val LOADED =
        MapUiState(
            venue = "Place Bellecour",
            center = EVENT,
            markers = listOf(WATER),
            isLoading = false,
        )
  }
}
