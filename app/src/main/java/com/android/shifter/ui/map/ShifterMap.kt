// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.swent.shifter.model.location.GeoPosition

object ShifterMapTestTags {
  const val MAP = "ShifterMap"
  const val MY_POSITION = "ShifterMapMyPosition"
}

/** EPFL, Lausanne: where the map opens when the caller doesn't move the camera. */
internal val DEFAULT_CENTER = LatLng(46.5191, 6.5668)
internal const val DEFAULT_ZOOM = 13f

/**
 * This position as a Maps `LatLng`, converted at the edge so the rest of the app never sees one.
 */
internal fun GeoPosition.toLatLng() = LatLng(latitude, longitude)

/**
 * This Maps `LatLng` as a position, at the same edge. Maps already keeps a latitude within -90 to
 * 90 and wraps a longitude into -180 to 180, so every point the map gives back is a valid one.
 */
internal fun LatLng.toGeoPosition() = GeoPosition(latitude, longitude)

/** Keeps a highlighted marker above the others, whose labels may overlap it. */
private const val HIGHLIGHTED_Z_INDEX = 1f

/** Keeps the user's position above every mission, so it stays visible when they are at one. */
private const val MY_POSITION_Z_INDEX = 2f

/** The callers draw their own controls, such as a recenter button, over the map. */
private val UI_SETTINGS = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false)

/**
 * A Google map showing [markers]. It knows nothing about missions or events: the caller maps its
 * own data to [MapMarker]s and decides what a tap does through [onMarkerClick].
 *
 * The map is drawn in the app's dark style. Without [markerContent], each marker is the default
 * pin, whose title and info pop up when tapped; with it, each marker is drawn by [markerContent],
 * anchored by its bottom centre. Either way, a tap centres the camera on the marker, unless
 * [centerOnMarkerClick] is false, which also keeps the default pin's info from popping up, and a
 * [MapMarker.highlighted] marker is drawn above the others.
 *
 * [onMapClick] receives the point tapped anywhere but on a marker, which Maps keeps for
 * [onMarkerClick]; it is how a caller lets the user pick a place, as [LocationPicker] does.
 *
 * [myPosition] is the user's position, drawn as a dot above the markers; a tap on it does nothing.
 */
@Composable
fun ShifterMap(
    markers: List<MapMarker>,
    onMarkerClick: (MapMarker) -> Unit,
    modifier: Modifier = Modifier,
    myPosition: GeoPosition? = null,
    cameraPositionState: CameraPositionState = rememberCameraPositionState {
      position = CameraPosition.fromLatLngZoom(DEFAULT_CENTER, DEFAULT_ZOOM)
    },
    markerContent: (@Composable (MapMarker) -> Unit)? = null,
    onMapClick: ((GeoPosition) -> Unit)? = null,
    centerOnMarkerClick: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(),
) {
  val context = LocalContext.current
  val properties = remember {
    MapProperties(mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style))
  }
  val myPositionLabel = stringResource(R.string.map_my_position)
  GoogleMap(
      modifier = modifier.testTag(ShifterMapTestTags.MAP),
      cameraPositionState = cameraPositionState,
      properties = properties,
      uiSettings = UI_SETTINGS,
      contentPadding = contentPadding,
      onMapClick = { onMapClick?.invoke(it.toGeoPosition()) },
  ) {
    markers.forEach { marker ->
      // Keyed so each marker keeps its own state when the list changes.
      key(marker.id) {
        val state = rememberUpdatedMarkerState(marker.position.toLatLng())
        val zIndex = if (marker.highlighted) HIGHLIGHTED_Z_INDEX else 0f
        if (markerContent == null) {
          Marker(
              state = state,
              title = marker.title,
              snippet = marker.info,
              zIndex = zIndex,
              onClick = {
                onMarkerClick(marker)
                !centerOnMarkerClick
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
                !centerOnMarkerClick
              },
          ) {
            markerContent(marker)
          }
        }
      }
    }
    myPosition?.let {
      MarkerComposable(
          state = rememberUpdatedMarkerState(it.toLatLng()),
          contentDescription = myPositionLabel,
          anchor = Offset(0.5f, 0.5f),
          zIndex = MY_POSITION_Z_INDEX,
          onClick = { true },
      ) {
        MyPositionDot()
      }
    }
  }
}

/** The user's position on the map: a dot with a light ring, to stand out on the dark style. */
@Composable
internal fun MyPositionDot() {
  Box(
      modifier =
          Modifier.size(20.dp)
              .testTag(ShifterMapTestTags.MY_POSITION)
              .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
              .padding(3.dp)
              .background(MaterialTheme.colorScheme.primary, CircleShape)
  )
}
