// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.location

/**
 * A position measured by the device, with how far off it may be. Kept apart from [GeoPosition],
 * which also stands for fixed points such as a mission's location, that have no accuracy.
 *
 * @property accuracyMeters the radius around [position] in which the device most likely is (68%
 *   confidence, as Android gives it): a few meters outdoors, often 50 to 150 indoors. Infinite when
 *   the device gave none.
 */
data class MeasuredPosition(val position: GeoPosition, val accuracyMeters: Double) {
  init {
    require(accuracyMeters >= 0) { "Accuracy must be positive, but was $accuracyMeters" }
  }
}
