// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState
import com.swent.shifter.R
import com.swent.shifter.ui.components.FormHeader

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

/** Stateless map screen: the event's title and venue above a map of its missions. */
@Composable
fun MapContent(
    uiState: MapUiState,
    onBack: () -> Unit,
    onMarkerClick: (MapMarker) -> Unit,
    onCloseCard: () -> Unit,
    onRetry: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize().testTag(MapScreenTestTags.SCREEN)) {
    FormHeader(
        backLabel = stringResource(R.string.map_back),
        title = uiState.eventTitle,
        backButtonTestTag = MapScreenTestTags.BACK_BUTTON,
        onBack = onBack,
    )
    if (uiState.venue.isNotEmpty()) {
      Text(
          text = uiState.venue,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier =
              Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                  .testTag(MapScreenTestTags.VENUE),
      )
    }
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DEFAULT_CENTER, DEFAULT_ZOOM)
      }
      LaunchedEffect(uiState.center) {
        uiState.center?.let {
          cameraPositionState.position =
              CameraPosition.fromLatLngZoom(LatLng(it.latitude, it.longitude), EVENT_ZOOM)
        }
      }
      ShifterMap(
          markers = uiState.markers,
          onMarkerClick = onMarkerClick,
          modifier = Modifier.fillMaxSize(),
          cameraPositionState = cameraPositionState,
      )
      val overlay = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth()
      when {
        uiState.isLoading ->
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).testTag(MapScreenTestTags.LOADING)
            )
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
