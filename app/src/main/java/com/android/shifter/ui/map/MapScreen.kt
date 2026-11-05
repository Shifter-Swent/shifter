// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import android.content.res.Resources
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import com.swent.shifter.R
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.ui.components.ShifterTopBar
import com.swent.shifter.ui.theme.shifter_mutedText
import com.swent.shifter.ui.theme.shifter_success
import com.swent.shifter.ui.theme.shifter_warning
import kotlinx.coroutines.launch

object MapScreenTestTags {
  const val SCREEN = "MapScreen"
  const val VENUE = "MapVenue"
  const val LOADING = "MapLoading"
  const val ERROR = "MapError"
  const val RETRY_BUTTON = "MapRetryButton"
  const val NO_MISSIONS = "MapNoMissions"
  const val MISSION_CARD = "MapMissionCard"
  const val CLOSE_CARD_BUTTON = "MapCloseCardButton"
  const val RECENTER_BUTTON = "MapRecenterButton"
  const val VOLUNTEER = "MapVolunteer"
  const val STALE_VOLUNTEER = "MapStaleVolunteer"
}

/** Starts the id of a volunteer's marker, which keeps it apart from the missions' ids. */
private const val VOLUNTEER_MARKER_PREFIX = "volunteer-"

/** Zoom the camera opens at once the event is loaded: close enough to tell missions apart. */
private const val EVENT_ZOOM = 15f

/** The Map tab of an event, titled [title]: its missions on a map, driven by [viewModel]. */
@Composable
fun MapScreen(title: String, viewModel: MapViewModel, onBack: () -> Unit) {
  val uiState by viewModel.uiState.collectAsState()
  // With the lifecycle, so the GPS stops when the app goes to the background.
  val myPosition by viewModel.myPosition.collectAsStateWithLifecycle()
  // Stops following the volunteers' positions while the app is in the background.
  val volunteers by viewModel.volunteers.collectAsStateWithLifecycle()
  MapContent(
      title = title,
      uiState = uiState,
      onBack = onBack,
      onMarkerClick = viewModel::selectMarker,
      onCloseCard = viewModel::clearSelection,
      onRetry = viewModel::load,
      myPosition = myPosition,
      volunteers = volunteers,
  )
}

/**
 * Stateless map screen: [title] and the event's venue above a map of its missions.
 *
 * @param myPosition where the user is, shown on the map; null shows nothing.
 * @param volunteers the volunteers of the manager's teams who share their position, drawn apart
 *   from the missions. A tap on one only centres the map on them.
 * @param cameraPositionState the camera of the map, which tests pass to check where it points.
 */
@Composable
fun MapContent(
    title: String,
    uiState: MapUiState,
    onBack: () -> Unit,
    onMarkerClick: (MapMarker) -> Unit,
    onCloseCard: () -> Unit,
    onRetry: () -> Unit,
    myPosition: GeoPosition? = null,
    volunteers: List<VolunteerPin> = emptyList(),
    cameraPositionState: CameraPositionState = rememberCameraPositionState {
      position = CameraPosition.fromLatLngZoom(DEFAULT_CENTER, DEFAULT_ZOOM)
    },
) {
  Column(modifier = Modifier.fillMaxSize().testTag(MapScreenTestTags.SCREEN)) {
    ShifterTopBar(
        title = title,
        onBack = onBack,
    )
    if (uiState.venue.isNotEmpty()) {
      Column(
          modifier =
              Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
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
          cameraPositionState.position = CameraPosition.fromLatLngZoom(it.toLatLng(), EVENT_ZOOM)
        }
      }
      val next = uiState.next
      val nextCaption =
          when {
            next == null -> null
            next.isOngoing -> stringResource(R.string.map_ongoing_mission, next.endTime)
            else -> stringResource(R.string.map_next_mission, next.startTime)
          }
      val resources = LocalResources.current
      // Each volunteer's marker, with whether their position is stale, rebuilt only when the
      // volunteers change. The age is part of the marker, so its drawing is redone when it changes.
      val volunteerMarkers =
          remember(volunteers, resources) {
            volunteers.associate {
              MapMarker(
                  id = VOLUNTEER_MARKER_PREFIX + it.userId,
                  position = it.position,
                  title = it.name ?: resources.getString(R.string.map_volunteer_unknown),
                  info = volunteerAge(resources, it.minutesAgo),
              ) to it.isStale
            }
          }
      ShifterMap(
          markers =
              uiState.markers.map {
                if (it.id == next?.markerId) it.copy(caption = nextCaption, highlighted = true)
                else it
              } + volunteerMarkers.keys,
          onMarkerClick = { if (it !in volunteerMarkers) onMarkerClick(it) },
          modifier = Modifier.fillMaxSize(),
          myPosition = myPosition,
          cameraPositionState = cameraPositionState,
          markerContent = {
            val isStale = volunteerMarkers[it]
            if (isStale != null) VolunteerLabel(it, isStale) else MissionLabel(it)
          },
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
                      CameraUpdateFactory.newLatLngZoom(center.toLatLng(), EVENT_ZOOM)
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

/**
 * How long ago a volunteer's position was measured, as the map says it: in minutes for the first
 * hour, then in whole hours, so an old position stays short to read.
 */
internal fun volunteerAge(resources: Resources, minutesAgo: Long): String =
    when {
      minutesAgo == 0L -> resources.getString(R.string.map_volunteer_just_now)
      minutesAgo < MINUTES_PER_HOUR -> {
        val minutes = minutesAgo.toInt()
        resources.getQuantityString(R.plurals.map_volunteer_minutes_ago, minutes, minutes)
      }
      else -> {
        val hours = (minutesAgo / MINUTES_PER_HOUR).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        resources.getQuantityString(R.plurals.map_volunteer_hours_ago, hours, hours)
      }
    }

private const val MINUTES_PER_HOUR = 60L

/**
 * A volunteer as drawn on the map: a small dot on their position, with their name and how old the
 * position is in one line above it, smaller than the missions' cards so many volunteers fit. The
 * map anchors it by its bottom centre, so the dot sits on the position. A [isStale] position is
 * greyed out, the dot left hollow, since the volunteer may have moved since.
 */
@Composable
internal fun VolunteerLabel(marker: MapMarker, isStale: Boolean) {
  val accent = if (isStale) shifter_mutedText else shifter_success
  Column(
      modifier =
          Modifier.testTag(
              if (isStale) MapScreenTestTags.STALE_VOLUNTEER else MapScreenTestTags.VOLUNTEER
          ),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
      Text(
          text = listOfNotNull(marker.title, marker.info).joinToString(" · "),
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).widthIn(max = 140.dp),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color =
              if (isStale) MaterialTheme.colorScheme.onSurfaceVariant
              else MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
    Box(
        modifier =
            Modifier.padding(top = 2.dp)
                .size(12.dp)
                .border(2.dp, accent, CircleShape)
                .then(if (isStale) Modifier else Modifier.background(accent, CircleShape))
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
