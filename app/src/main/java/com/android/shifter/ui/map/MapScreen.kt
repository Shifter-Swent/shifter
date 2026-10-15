// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import com.swent.shifter.R
import com.swent.shifter.ui.components.FormHeader
import com.swent.shifter.ui.theme.shifter_warning
import kotlinx.coroutines.launch

object MapScreenTestTags {
  const val SCREEN = "MapScreen"
  const val BACK_BUTTON = "MapBackButton"
  const val VENUE = "MapVenue"
  const val LOADING = "MapLoading"
  const val ERROR = "MapError"
  const val RETRY_BUTTON = "MapRetryButton"
  const val NO_MISSIONS = "MapNoMissions"
  const val MISSION_CARD = "MapMissionCard"
  const val CLOSE_CARD_BUTTON = "MapCloseCardButton"
  const val RECENTER_BUTTON = "MapRecenterButton"
}

/** Zoom the camera opens at once the event is loaded: close enough to tell missions apart. */
private const val EVENT_ZOOM = 15f

/** The Map tab of an event: its missions on a map, driven by [viewModel]. */
@Composable
fun MapScreen(viewModel: MapViewModel, onBack: () -> Unit) {
  val uiState by viewModel.uiState.collectAsState()
  MapContent(
      uiState = uiState,
      onBack = onBack,
      onMarkerClick = viewModel::selectMarker,
      onCloseCard = viewModel::clearSelection,
      onRetry = viewModel::load,
  )
}

/**
 * Stateless map screen: the event's title and venue above a map of its missions.
 *
 * @param cameraPositionState the camera of the map, which tests pass to check where it points.
 */
@Composable
fun MapContent(
    uiState: MapUiState,
    onBack: () -> Unit,
    onMarkerClick: (MapMarker) -> Unit,
    onCloseCard: () -> Unit,
    onRetry: () -> Unit,
    cameraPositionState: CameraPositionState = rememberCameraPositionState {
      position = CameraPosition.fromLatLngZoom(DEFAULT_CENTER, DEFAULT_ZOOM)
    },
) {
  Column(modifier = Modifier.fillMaxSize().testTag(MapScreenTestTags.SCREEN)) {
    FormHeader(
        backLabel = stringResource(R.string.map_back),
        title = uiState.eventTitle,
        backButtonTestTag = MapScreenTestTags.BACK_BUTTON,
        onBack = onBack,
    )
    if (uiState.venue.isNotEmpty()) {
      Column(
          modifier =
              Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                  .testTag(MapScreenTestTags.VENUE)
      ) {
        Text(
            text = uiState.venue,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.map_venue_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      LaunchedEffect(uiState.center) {
        uiState.center?.let {
          cameraPositionState.position =
              CameraPosition.fromLatLngZoom(LatLng(it.latitude, it.longitude), EVENT_ZOOM)
        }
      }
      val next = uiState.next
      val nextCaption =
          when {
            next == null -> null
            next.isOngoing -> stringResource(R.string.map_ongoing_mission, next.endTime)
            else -> stringResource(R.string.map_next_mission, next.startTime)
          }
      ShifterMap(
          markers =
              uiState.markers.map {
                if (it.id == next?.markerId) it.copy(caption = nextCaption, highlighted = true)
                else it
              },
          onMarkerClick = onMarkerClick,
          modifier = Modifier.fillMaxSize(),
          cameraPositionState = cameraPositionState,
          markerContent = { MissionLabel(it) },
      )
      if (uiState.isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center).testTag(MapScreenTestTags.LOADING)
        )
      }
      Column(
          modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        val scope = rememberCoroutineScope()
        uiState.center?.let { center ->
          RecenterButton(
              onClick = {
                scope.launch {
                  cameraPositionState.animate(
                      CameraUpdateFactory.newLatLngZoom(
                          LatLng(center.latitude, center.longitude),
                          EVENT_ZOOM,
                      )
                  )
                }
              }
          )
        }
        val overlay = Modifier.fillMaxWidth()
        when {
          uiState.isLoading -> Unit
          uiState.error != null -> ErrorCard(uiState.error, onRetry, overlay)
          uiState.selected != null -> MissionCard(uiState.selected, onCloseCard, overlay)
          uiState.markers.isEmpty() ->
              MapCard(overlay.testTag(MapScreenTestTags.NO_MISSIONS)) {
                Text(stringResource(R.string.map_no_missions))
              }
        }
      }
    }
  }
}

/**
 * A mission as drawn on the map: a card with its name and hours above a pin. A [MapMarker.caption]
 * is shown above the name, and a [MapMarker.highlighted] marker, such as the next mission, has its
 * card and pin in the accent colour.
 */
@Composable
internal fun MissionLabel(marker: MapMarker) {
  val accent = if (marker.highlighted) shifter_warning else MaterialTheme.colorScheme.primary
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border =
            BorderStroke(
                1.dp,
                if (marker.highlighted) accent else MaterialTheme.colorScheme.outline,
            ),
    ) {
      Column(
          modifier = Modifier.widthIn(max = 200.dp).padding(horizontal = 10.dp, vertical = 6.dp)
      ) {
        marker.caption?.let {
          Text(
              text = it,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = accent,
          )
        }
        Text(
            text = marker.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        marker.info?.let {
          Text(
              text = it,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
          )
        }
      }
    }
    Icon(
        Icons.Filled.LocationOn,
        contentDescription = null,
        tint = accent,
        modifier = Modifier.size(32.dp),
    )
  }
}

/** Moves the camera back to where the map opened, the venue or the first mission. */
@Composable
private fun RecenterButton(onClick: () -> Unit) {
  Surface(
      onClick = onClick,
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surfaceContainer,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      modifier = Modifier.size(48.dp).testTag(MapScreenTestTags.RECENTER_BUTTON),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
          Icons.Outlined.MyLocation,
          contentDescription = stringResource(R.string.map_recenter),
          tint = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

/** The tapped mission: its name, then its hours and address. */
@Composable
private fun MissionCard(marker: MapMarker, onClose: () -> Unit, modifier: Modifier) {
  MapCard(modifier.testTag(MapScreenTestTags.MISSION_CARD)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = marker.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        marker.info?.let {
          Text(
              text = it,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      TextButton(
          onClick = onClose,
          modifier = Modifier.testTag(MapScreenTestTags.CLOSE_CARD_BUTTON),
      ) {
        Text(stringResource(R.string.map_close))
      }
    }
  }
}

@Composable
private fun ErrorCard(error: MapError, onRetry: () -> Unit, modifier: Modifier) {
  val message =
      when (error) {
        MapError.EVENT_NOT_FOUND -> R.string.map_error_not_found
        MapError.OFFLINE -> R.string.map_error_offline
        MapError.UNEXPECTED -> R.string.map_error_unexpected
      }
  MapCard(modifier.testTag(MapScreenTestTags.ERROR)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
          text = stringResource(message),
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.weight(1f),
      )
      // An event that no longer exists will not come back, so only the other errors retry.
      if (error != MapError.EVENT_NOT_FOUND) {
        TextButton(
            onClick = onRetry,
            modifier = Modifier.testTag(MapScreenTestTags.RETRY_BUTTON),
        ) {
          Text(stringResource(R.string.map_retry))
        }
      }
    }
  }
}

/** A card floating over the bottom of the map. */
@Composable
private fun MapCard(modifier: Modifier, content: @Composable () -> Unit) {
  Surface(
      modifier = modifier,
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 2.dp,
      shadowElevation = 4.dp,
  ) {
    Box(modifier = Modifier.padding(16.dp)) { content() }
  }
}
