// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A point on Earth, in degrees. Unlike the Maps `LatLng` it is plain Kotlin, so the domain logic
 * that uses it (such as the check-in eligibility) and its tests need no Android.
 */
data class GeoPosition(val latitude: Double, val longitude: Double) {
  init {
    require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90, but was $latitude" }
    require(longitude in -180.0..180.0) {
      "Longitude must be between -180 and 180, but was $longitude"
    }
  }

  /**
   * The great-circle distance to [other] in meters, by the haversine formula on a spherical Earth.
   * Its error (under 0.5%) is far below the GPS accuracy at the distances of a check-in.
   */
  fun distanceTo(other: GeoPosition): Double {
    val dLat = Math.toRadians(other.latitude - latitude)
    val dLon = Math.toRadians(other.longitude - longitude)
    val a =
        sin(dLat / 2).pow(2) +
            cos(Math.toRadians(latitude)) *
                cos(Math.toRadians(other.latitude)) *
                sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceAtMost(1.0)))
  }

  companion object {
    /** The mean Earth radius, in meters. */
    const val EARTH_RADIUS_METERS = 6_371_000.0
  }
}
