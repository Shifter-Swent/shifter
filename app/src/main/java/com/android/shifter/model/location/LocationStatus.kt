// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.location

/** Whether the app can read the device's location, and if not, why. */
enum class LocationStatus {
  /** The permission is granted and location services are on: positions are delivered. */
  GRANTED,

  /**
   * The user has not granted the precise location permission (yet, or for good); only granting the
   * approximate location counts as denied too. Telling "not yet" from "for good" needs the
   * Activity, so the permission UI does it, not the provider.
   */
  DENIED,

  /** The permission is granted, but location services are turned off in the device settings. */
  LOCATION_OFF,
}
