// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.location

import com.swent.shifter.model.location.LocationStatus

/** What the location permission UI shows, from the provider's status and the permission dialog. */
enum class LocationAccess {
  /** The permission was never asked in this screen: explain why it is needed and ask. */
  NOT_REQUESTED,

  /** The user refused, but the permission can be asked again. */
  DENIED,

  /** Only the approximate location was granted, which cannot tell if one is in a mission's zone. */
  APPROXIMATE_ONLY,

  /** The user refused for good: the dialog no longer shows, only the app settings can grant it. */
  PERMANENTLY_DENIED,

  /** The permission is granted but location services are turned off. */
  LOCATION_OFF,

  /** Positions are delivered: nothing to show. */
  GRANTED,
}

/**
 * Tells which [LocationAccess] to show. Android only says whether to show a rationale, which is
 * false both before the first request and once the user refused for good, so [requested] tells the
 * two apart. A granted approximate location means the user was already asked, so it needs no
 * [requested].
 *
 * @param requested whether the permission dialog was already shown in this screen.
 * @param shouldShowRationale `shouldShowRequestPermissionRationale` for the precise location.
 * @param approximateGranted whether the approximate location permission is granted.
 */
fun locationAccess(
    status: LocationStatus,
    requested: Boolean,
    shouldShowRationale: Boolean,
    approximateGranted: Boolean,
): LocationAccess =
    when {
      status == LocationStatus.GRANTED -> LocationAccess.GRANTED
      status == LocationStatus.LOCATION_OFF -> LocationAccess.LOCATION_OFF
      approximateGranted ->
          if (shouldShowRationale) LocationAccess.APPROXIMATE_ONLY
          else LocationAccess.PERMANENTLY_DENIED
      !requested -> LocationAccess.NOT_REQUESTED
      shouldShowRationale -> LocationAccess.DENIED
      else -> LocationAccess.PERMANENTLY_DENIED
    }
