// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

object ShifterMapTestTags {
  const val MAP = "ShifterMap"
}

/** EPFL, Lausanne: where the map opens when the caller doesn't move the camera. */
internal val DEFAULT_CENTER = LatLng(46.5191, 6.5668)
internal const val DEFAULT_ZOOM = 13f

/**
 * A Google map showing [markers]. It knows nothing about missions or events: the caller maps its
 * own data to [MapMarker]s and decides what a tap does through [onMarkerClick].
 *
 * The default tap behaviour is kept, so the marker's title and info pop up and the camera centres
 * on it.
 */
@Composable
fun ShifterMap(
    markers: List<MapMarker>,
    onMarkerClick: (MapMarker) -> Unit,
    modifier: Modifier = Modifier,
    cameraPositionState: CameraPositionState = rememberCameraPositionState {
      position = CameraPosition.fromLatLngZoom(DEFAULT_CENTER, DEFAULT_ZOOM)
    },
) {
  GoogleMap(
      modifier = modifier.testTag(ShifterMapTestTags.MAP),
      cameraPositionState = cameraPositionState,
  ) {
    markers.forEach { marker ->
      // Keyed so each marker keeps its own state when the list changes.
      key(marker.id) {
        Marker(
            state = rememberUpdatedMarkerState(LatLng(marker.latitude, marker.longitude)),
            title = marker.title,
            snippet = marker.info,
            onClick = {
              onMarkerClick(marker)
              false
            },
        )
      }
    }
  }
}
