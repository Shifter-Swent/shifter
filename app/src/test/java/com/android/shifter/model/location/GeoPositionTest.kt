// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeoPositionTest {

  @Test
  fun geoPosition_withBoundaryCoordinates_isAccepted() {
    val north = GeoPosition(90.0, 180.0)
    val south = GeoPosition(-90.0, -180.0)

    assertEquals(90.0, north.latitude, 0.0)
    assertEquals(180.0, north.longitude, 0.0)
    assertEquals(-90.0, south.latitude, 0.0)
    assertEquals(-180.0, south.longitude, 0.0)
  }

  @Test
  fun geoPosition_withOutOfRangeLatitude_throws() {
    assertThrows(IllegalArgumentException::class.java) { GeoPosition(90.1, 0.0) }
    assertThrows(IllegalArgumentException::class.java) { GeoPosition(-90.1, 0.0) }
  }

  @Test
  fun geoPosition_withOutOfRangeLongitude_throws() {
    assertThrows(IllegalArgumentException::class.java) { GeoPosition(0.0, 180.1) }
    assertThrows(IllegalArgumentException::class.java) { GeoPosition(0.0, -180.1) }
  }

  @Test
  fun geoPosition_withNaN_throws() {
    // NaN is in no range, so a position computed from a broken sensor value cannot slip through.
    assertThrows(IllegalArgumentException::class.java) { GeoPosition(Double.NaN, 0.0) }
    assertThrows(IllegalArgumentException::class.java) { GeoPosition(0.0, Double.NaN) }
  }
}
