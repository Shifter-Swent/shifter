// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.maps.model.CameraPosition
import com.swent.shifter.model.location.GeoPosition
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The zoom a recenter button of the map sends the camera to, by [recenterZoom]. */
@RunWith(AndroidJUnit4::class)
class RecenterZoomTest {

  private fun cameraAt(position: GeoPosition, zoom: Float) =
      CameraPosition.fromLatLngZoom(position.toLatLng(), zoom)

  @Test
  fun recenterZoom_awayFromTheTarget_isTheEventZoom() {
    assertEquals(15f, recenterZoom(cameraAt(FAR, 18f), isMoving = false, target = TARGET))
  }

  @Test
  fun recenterZoom_centredOnTheTarget_zoomsInByOneAndAHalf() {
    assertEquals(16.5f, recenterZoom(cameraAt(TARGET, 15f), isMoving = false, target = TARGET))
  }

  @Test
  fun recenterZoom_centredButZoomedOut_isTheEventZoom() {
    assertEquals(15f, recenterZoom(cameraAt(TARGET, 8f), isMoving = false, target = TARGET))
    assertEquals(15f, recenterZoom(cameraAt(TARGET, 14f), isMoving = false, target = TARGET))
  }

  @Test
  fun recenterZoom_withinTheMargin_zoomsIn() {
    assertEquals(16.5f, recenterZoom(cameraAt(NEAR, 15f), isMoving = false, target = TARGET))
  }

  @Test
  fun recenterZoom_justBeyondTheMargin_isTheEventZoom() {
    assertEquals(15f, recenterZoom(cameraAt(BEYOND, 17f), isMoving = false, target = TARGET))
  }

  @Test
  fun recenterZoom_nearTheLimit_stopsAt19() {
    assertEquals(19f, recenterZoom(cameraAt(TARGET, 18f), isMoving = false, target = TARGET))
    assertEquals(19f, recenterZoom(cameraAt(TARGET, 19f), isMoving = false, target = TARGET))
  }

  @Test
  fun recenterZoom_pinchedPastTheLimit_doesNotZoomOut() {
    assertEquals(20f, recenterZoom(cameraAt(TARGET, 20f), isMoving = false, target = TARGET))
  }

  @Test
  fun recenterZoom_whileTheCameraMoves_isTheEventZoom() {
    assertEquals(15f, recenterZoom(cameraAt(TARGET, 16.5f), isMoving = true, target = TARGET))
  }

  private companion object {
    val TARGET = GeoPosition(46.5191, 6.5668)

    // A degree of latitude is about 111 km, so 0.0002 is about 22 m and 0.00025 about 28 m.
    val NEAR = GeoPosition(TARGET.latitude + 0.0002, TARGET.longitude)
    val BEYOND = GeoPosition(TARGET.latitude + 0.00025, TARGET.longitude)
    val FAR = GeoPosition(46.53, 6.60)
  }
}
