// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.location

import com.swent.shifter.model.location.LocationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationAccessTest {

  @Test
  fun locationAccess_whenGranted_isGrantedWhateverTheDialog() {
    assertEquals(LocationAccess.GRANTED, access(LocationStatus.GRANTED, requested = false))
    assertEquals(LocationAccess.GRANTED, access(LocationStatus.GRANTED, requested = true))
  }

  @Test
  fun locationAccess_whenLocationOff_isLocationOff() {
    assertEquals(LocationAccess.LOCATION_OFF, access(LocationStatus.LOCATION_OFF, requested = true))
  }

  @Test
  fun locationAccess_beforeTheFirstRequest_isNotRequested() {
    assertEquals(LocationAccess.NOT_REQUESTED, access(LocationStatus.DENIED, requested = false))
  }

  @Test
  fun locationAccess_whenRefusedOnce_isDenied() {
    assertEquals(
        LocationAccess.DENIED,
        access(LocationStatus.DENIED, requested = true, rationale = true),
    )
  }

  @Test
  fun locationAccess_withOnlyTheApproximateLocation_isApproximateOnly() {
    assertEquals(
        LocationAccess.APPROXIMATE_ONLY,
        access(LocationStatus.DENIED, requested = true, rationale = true, approximate = true),
    )
  }

  @Test
  fun locationAccess_whenRefusedForGood_isPermanentlyDenied() {
    assertEquals(
        LocationAccess.PERMANENTLY_DENIED,
        access(LocationStatus.DENIED, requested = true, rationale = false),
    )
    assertEquals(
        LocationAccess.PERMANENTLY_DENIED,
        access(LocationStatus.DENIED, requested = true, rationale = false, approximate = true),
    )
  }

  private fun access(
      status: LocationStatus,
      requested: Boolean,
      rationale: Boolean = false,
      approximate: Boolean = false,
  ) = locationAccess(status, requested, rationale, approximate)
}
