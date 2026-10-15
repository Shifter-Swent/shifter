// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.swent.shifter.R

object ShifterMapTestTags {
  const val MAP = "ShifterMap"
}

/** EPFL, Lausanne: where the map opens when the caller doesn't move the camera. */
internal val DEFAULT_CENTER = LatLng(46.5191, 6.5668)
internal const val DEFAULT_ZOOM = 13f

/** Keeps a highlighted marker above the others, whose labels may overlap it. */
private const val HIGHLIGHTED_Z_INDEX = 1f

/** The callers draw their own controls, such as a recenter button, over the map. */
private val UI_SETTINGS = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false)

/**
 * A Google map showing [markers]. It knows nothing about missions or events: the caller maps its
 * own data to [MapMarker]s and decides what a tap does through [onMarkerClick].
 *
 * The map is drawn in the app's dark style. Without [markerContent], each marker is the default
 * pin, whose title and info pop up when tapped; with it, each marker is drawn by [markerContent],
 * anchored by its bottom centre. Either way, a tap centres the camera on the marker, and a
 * [MapMarker.highlighted] marker is drawn above the others.
 */
@Composable
fun ShifterMap(
    markers: List<MapMarker>,
    onMarkerClick: (MapMarker) -> Unit,
    modifier: Modifier = Modifier,
    cameraPositionState: CameraPositionState = rememberCameraPositionState {
      position = CameraPosition.fromLatLngZoom(DEFAULT_CENTER, DEFAULT_ZOOM)
    },
    markerContent: (@Composable (MapMarker) -> Unit)? = null,
) {
  val context = LocalContext.current
  val properties = remember {
    MapProperties(mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style))
  }
  GoogleMap(
      modifier = modifier.testTag(ShifterMapTestTags.MAP),
      cameraPositionState = cameraPositionState,
      properties = properties,
      uiSettings = UI_SETTINGS,
  ) {
    markers.forEach { marker ->
      // Keyed so each marker keeps its own state when the list changes.
      key(marker.id) {
        val state = rememberUpdatedMarkerState(LatLng(marker.latitude, marker.longitude))
        val zIndex = if (marker.highlighted) HIGHLIGHTED_Z_INDEX else 0f
        if (markerContent == null) {
          Marker(
              state = state,
              title = marker.title,
              snippet = marker.info,
              zIndex = zIndex,
              onClick = {
                onMarkerClick(marker)
                false
              },
          )
        } else {
          // The content is drawn once into a bitmap, redrawn only when the marker changes.
          MarkerComposable(
              marker,
              state = state,
              zIndex = zIndex,
              onClick = {
                onMarkerClick(marker)
                false
              },
          ) {
            markerContent(marker)
          }
        }
      }
    }
  }
}
