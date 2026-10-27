// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.location

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
}
