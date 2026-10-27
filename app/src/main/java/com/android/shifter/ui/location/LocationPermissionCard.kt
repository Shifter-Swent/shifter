// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.location

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * Explains why the location is needed and offers the action that unblocks it, for every
 * [LocationAccess] but [LocationAccess.GRANTED], where it shows nothing.
 *
 * @param onRequest asks for the permission again.
 * @param onOpenAppSettings opens the app's settings, where a refused permission can be granted.
 * @param onOpenLocationSettings opens the settings that turn location services on.
 */
@Composable
fun LocationPermissionCard(
    access: LocationAccess,
    onRequest: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val content =
      when (access) {
        LocationAccess.GRANTED -> return
        LocationAccess.NOT_REQUESTED ->
            CardContent(R.string.location_not_requested, R.string.location_allow, onRequest)
        LocationAccess.DENIED ->
            CardContent(R.string.location_denied, R.string.location_allow, onRequest)
        LocationAccess.APPROXIMATE_ONLY ->
            CardContent(R.string.location_approximate_only, R.string.location_allow, onRequest)
        LocationAccess.PERMANENTLY_DENIED ->
            CardContent(
                R.string.location_permanently_denied,
                R.string.location_open_app_settings,
                onOpenAppSettings,
            )
        LocationAccess.LOCATION_OFF ->
            CardContent(
                R.string.location_off,
                R.string.location_open_location_settings,
                onOpenLocationSettings,
            )
      }
  Card(modifier = modifier.fillMaxWidth().testTag(LocationPermissionTestTags.CARD)) {
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

private class CardContent(
    @StringRes val message: Int,
    @StringRes val action: Int,
    val onClick: () -> Unit,
)
