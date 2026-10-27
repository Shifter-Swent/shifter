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

  @Test
  fun distanceTo_samePoint_isZero() {
    val point = GeoPosition(46.5191, 6.5668)

    assertEquals(0.0, point.distanceTo(point), 0.0)
  }

  @Test
  fun distanceTo_isSymmetric() {
    val lausanne = GeoPosition(46.5191, 6.5668)
    val geneva = GeoPosition(46.2044, 6.1432)

    assertEquals(lausanne.distanceTo(geneva), geneva.distanceTo(lausanne), 1e-9)
  }

  @Test
  fun distanceTo_oneDegreeOfLatitude_isAbout111Kilometers() {
    // One degree of a great circle is 2π R / 360 on a sphere of the mean Earth radius.
    val distance = GeoPosition(0.0, 0.0).distanceTo(GeoPosition(1.0, 0.0))

    assertEquals(111_195.0, distance, 1.0)
  }
}
