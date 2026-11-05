// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.maps.model.LatLng
import com.swent.shifter.model.location.GeoPosition
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The conversions between positions and Maps `LatLng`s at the edge of [ShifterMap]. */
@RunWith(AndroidJUnit4::class)
class ShifterMapConversionsTest {

  @Test
  fun toGeoPosition_isTheInverseOfToLatLng() {
    val epfl = GeoPosition(46.5191, 6.5668)

    assertEquals(epfl, epfl.toLatLng().toGeoPosition())
    assertEquals(GeoPosition(-90.0, -180.0), LatLng(-90.0, -180.0).toGeoPosition())
  }
}
