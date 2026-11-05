// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import com.swent.shifter.R
import com.swent.shifter.model.location.GeoPosition
import java.util.Locale

object LocationPickerTestTags {
  const val PICKER = "LocationPicker"
  const val HINT = "LocationPickerHint"
  const val SELECTED = "LocationPickerSelected"
}

/** The id of the one marker the picker draws, at the point chosen. */
internal const val SELECTION_MARKER_ID = "selection"

/** The zoom the picker opens at on a point: close enough to place it within a few metres. */
private const val PICKER_ZOOM = 16f

/**
 * Draws the map of a [LocationPicker]: the [markers] to show, [onMapClick] to call with the point
 * tapped, and the [cameraPositionState] to open at. The picker draws a [ShifterMap] by default; a
 * test passes another, since a tap inside the native map cannot be made from a Compose test.
 */
internal typealias PickerMap =
    @Composable
    (
        markers: List<MapMarker>,
        onMapClick: (GeoPosition) -> Unit,
        cameraPositionState: CameraPositionState,
    ) -> Unit

/**
 * A map to pick a place on: tapping it calls [onSelect] with the point tapped, and the caller
 * passes it back as [selected], drawn as a marker. Tapping elsewhere moves it. It knows nothing
 * about events or missions, and keeps no state of its own but the camera. The caller keeps
 * [selected] across configuration changes, in its ViewModel: held in a plain `remember`, it is lost
 * on rotation, and `rememberSaveable` cannot save a [GeoPosition] without a `Saver`.
 *
 * The map opens on [selected], or else on [initialCenter], or else on the app's default place, and
 * is not moved afterwards: a later change of [selected] moves the marker, not the camera, so the
 * view does not jump while the user is picking. Show it once the initial value is known, then, or
 * it opens on the default place. Tapping the marker itself changes nothing: the camera stays put
 * and no info pops up.
 *
 * Give it the whole screen or a dialog rather than a place in a scrolling form, whose vertical
 * scroll would fight the map's own gestures. A map cannot be used with a screen reader, and stays
 * grey with no network or no Maps key while still taking taps, so a form offering it should keep
 * another way to enter a place, such as coordinates typed in.
 */
@Composable
fun LocationPicker(
    selected: GeoPosition?,
    onSelect: (GeoPosition) -> Unit,
    modifier: Modifier = Modifier,
    initialCenter: GeoPosition? = null,
) {
  LocationPicker(selected, onSelect, modifier, initialCenter) {
      markers,
      onMapClick,
      cameraPositionState ->
    ShifterMap(
        markers = markers,
        onMarkerClick = {},
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        onMapClick = onMapClick,
        centerOnMarkerClick = false,
    )
  }
}

/** The [LocationPicker] drawing [map], which a test replaces with a stand-in it can tap. */
@Composable
internal fun LocationPicker(
    selected: GeoPosition?,
    onSelect: (GeoPosition) -> Unit,
    modifier: Modifier,
    initialCenter: GeoPosition?,
    map: PickerMap,
) {
  val cameraPositionState = rememberCameraPositionState {
    val center = selected ?: initialCenter
    position =
        if (center == null) CameraPosition.fromLatLngZoom(DEFAULT_CENTER, DEFAULT_ZOOM)
        else CameraPosition.fromLatLngZoom(center.toLatLng(), PICKER_ZOOM)
  }
  val markerTitle = stringResource(R.string.map_picker_marker)
  val markers =
      remember(selected, markerTitle) {
        listOfNotNull(selected?.let { MapMarker(SELECTION_MARKER_ID, it, markerTitle) })
      }
  val description = stringResource(R.string.map_picker_description)
  Box(
      modifier =
          modifier.testTag(LocationPickerTestTags.PICKER).semantics {
            contentDescription = description
          }
  ) {
    map(markers, onSelect, cameraPositionState)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
    ) {
      Text(
          text =
              if (selected == null) stringResource(R.string.map_picker_hint)
              else
                  stringResource(
                      R.string.map_picker_selected,
                      formatCoordinate(selected.latitude),
                      formatCoordinate(selected.longitude),
                  ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface,
          modifier =
              Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                  .testTag(
                      if (selected == null) LocationPickerTestTags.HINT
                      else LocationPickerTestTags.SELECTED
                  ),
      )
    }
  }
}

/**
 * Five decimals, about a metre, which is as precise as a tap on a phone gets. In [Locale.ROOT], so
 * a French device does not write the decimal point as a comma.
 */
internal fun formatCoordinate(value: Double): String = String.format(Locale.ROOT, "%.5f", value)
