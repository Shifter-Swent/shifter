// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.location

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.ui.components.PrimaryButton

object LocationPermissionTestTags {
  const val CARD = "locationPermissionCard"
  const val MESSAGE = "locationPermissionMessage"
  const val ACTION = "locationPermissionAction"
}

/** Why a screen asks for the location, which picks the messages of the [LocationPermissionCard]. */
enum class LocationReason {
  /** The volunteer's automatic check-in when they arrive at their mission. */
  CHECK_IN,
  /** The user's own position on the map. */
  MAP,
}

/**
 * Explains why the location is needed and offers the action that unblocks it, for every
 * [LocationAccess] but [LocationAccess.GRANTED], where it shows nothing.
 *
 * @param onRequest asks for the permission again.
 * @param reason what the location is for, which the messages explain.
 * @param onOpenAppSettings opens the app's settings, where a refused permission can be granted.
 * @param onOpenLocationSettings opens the settings that turn location services on.
 * @param compact puts the message and a text button on one row, in a smaller card, for a screen
 *   where the card goes over other content, such as the map.
 */
@Composable
fun LocationPermissionCard(
    access: LocationAccess,
    onRequest: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    modifier: Modifier = Modifier,
    reason: LocationReason = LocationReason.CHECK_IN,
    compact: Boolean = false,
) {
  val message = message(access, reason) ?: return
  val content =
      when (access) {
        LocationAccess.PERMANENTLY_DENIED ->
            CardContent(message, R.string.location_open_app_settings, onOpenAppSettings)
        LocationAccess.LOCATION_OFF ->
            CardContent(message, R.string.location_open_location_settings, onOpenLocationSettings)
        else -> CardContent(message, R.string.location_allow, onRequest)
      }
  if (compact) {
    CompactCard(content, modifier)
    return
  }
  Card(
      modifier = modifier.fillMaxWidth().testTag(LocationPermissionTestTags.CARD),
      shape = MaterialTheme.shapes.medium,
      colors = cardColors(),
  ) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
          text = stringResource(content.message),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.testTag(LocationPermissionTestTags.MESSAGE),
      )
      PrimaryButton(
          text = stringResource(content.action),
          onClick = content.onClick,
          testTag = LocationPermissionTestTags.ACTION,
      )
    }
  }
}

/** The message that explains [access] for [reason]; null once granted, where nothing is shown. */
@StringRes
private fun message(access: LocationAccess, reason: LocationReason): Int? =
    when (reason) {
      LocationReason.CHECK_IN ->
          when (access) {
            LocationAccess.GRANTED -> null
            LocationAccess.NOT_REQUESTED -> R.string.location_not_requested
            LocationAccess.DENIED -> R.string.location_denied
            LocationAccess.APPROXIMATE_ONLY -> R.string.location_approximate_only
            LocationAccess.PERMANENTLY_DENIED -> R.string.location_permanently_denied
            LocationAccess.LOCATION_OFF -> R.string.location_off
          }
      LocationReason.MAP ->
          when (access) {
            LocationAccess.GRANTED -> null
            LocationAccess.NOT_REQUESTED -> R.string.map_location_not_requested
            LocationAccess.DENIED -> R.string.map_location_denied
            LocationAccess.APPROXIMATE_ONLY -> R.string.map_location_approximate_only
            LocationAccess.PERMANENTLY_DENIED -> R.string.map_location_permanently_denied
            LocationAccess.LOCATION_OFF -> R.string.map_location_off
          }
    }

/** The message and a text button on one row, so the card takes little room. */
@Composable
private fun CompactCard(content: CardContent, modifier: Modifier) {
  Card(
      modifier = modifier.fillMaxWidth().testTag(LocationPermissionTestTags.CARD),
      shape = MaterialTheme.shapes.medium,
      colors = cardColors(),
  ) {
    Row(
        modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = stringResource(content.message),
          style = MaterialTheme.typography.bodySmall,
          modifier =
              Modifier.weight(1f)
                  .padding(vertical = 8.dp)
                  .testTag(LocationPermissionTestTags.MESSAGE),
      )
      TextButton(
          onClick = content.onClick,
          modifier = Modifier.testTag(LocationPermissionTestTags.ACTION),
      ) {
        Text(stringResource(content.action))
      }
    }
  }
}

/** The background of the app's notices, such as a day with nothing planned. */
@Composable
private fun cardColors() =
    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

private class CardContent(
    @StringRes val message: Int,
    @StringRes val action: Int,
    val onClick: () -> Unit,
)
