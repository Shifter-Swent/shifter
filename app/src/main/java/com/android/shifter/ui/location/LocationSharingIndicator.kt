// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.location

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.model.location.LiveLocationSharingState
import com.swent.shifter.model.location.LiveLocationTarget
import com.swent.shifter.ui.theme.shifter_mutedText
import com.swent.shifter.ui.theme.shifter_success
import com.swent.shifter.ui.theme.shifter_warning

object LocationSharingTestTags {
  const val INDICATOR = "LocationSharingIndicator"
  const val SHEET = "LocationSharingSheet"
  const val SHEET_TITLE = "LocationSharingSheetTitle"
  const val OPEN_SETTINGS = "LocationSharingOpenSettings"

  /** The row of [target] in the sheet. */
  fun targetRow(target: LiveLocationTarget) =
      "LocationSharingTarget_${target.eventId}_${target.teamId}"
}

/**
 * What the top bars need to show the location sharing (#405): the state of each target, their
 * names, and what to do when the volunteer opens the sheet or asks for Settings.
 *
 * Provided once at the navigation root through [LocalLocationSharing], so the top bar of every
 * screen shows it without each screen passing it on.
 */
data class LocationSharingUi(
    val states: Map<LiveLocationTarget, LiveLocationSharingState>,
    val labels: Map<LiveLocationTarget, SharingTargetLabel>,
    /** Called when the sheet opens, to read the names of the targets. */
    val onOpen: () -> Unit,
    val onOpenSettings: () -> Unit,
)

/** The location sharing shown by the top bars; `null` where it is not provided, showing nothing. */
val LocalLocationSharing = compositionLocalOf<LocationSharingUi?> { null }

/**
 * The location sharing icon of the top bar, from [LocalLocationSharing]: the most urgent state of
 * the targets (see [LiveLocationSharingState]), and a sheet explaining it when tapped. It draws
 * nothing while no session runs, so the volunteer only sees it during a running event.
 */
@Composable
fun LocationSharingIndicator(modifier: Modifier = Modifier) {
  val sharing = LocalLocationSharing.current
  val state = sharing?.states?.values?.maxOrNull()
  // Not saveable: a rotation recreates the activity, which stops the publishing and with it every
  // state, so the sheet would close anyway (see below).
  var open by remember { mutableStateOf(false) }
  // Once no session runs, the sheet closes, and does not reopen with the next event.
  LaunchedEffect(state == null) { if (state == null) open = false }
  if (sharing == null || state == null) return

  val look = state.look()
  // 48 dp to touch, like the back button, around a 40 dp circle.
  Box(
      contentAlignment = Alignment.Center,
      modifier =
          modifier
              .size(48.dp)
              // A round ripple, like the back button's, rather than the whole square.
              .clip(CircleShape)
              .clickable(
                  onClickLabel = stringResource(R.string.location_sharing_show_details),
                  role = Role.Button,
              ) {
                sharing.onOpen()
                open = true
              }
              .testTag(LocationSharingTestTags.INDICATOR),
  ) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(40.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
    ) {
      LocationIcon(look, stringResource(look.description))
    }
  }
  if (open) {
    LocationSharingSheet(
        state = state,
        sharing = sharing,
        onDismiss = { open = false },
        onOpenSettings = {
          open = false
          sharing.onOpenSettings()
        },
    )
  }
}

/** The pin, crossed out when nothing is shared, with a dot of [look]'s color otherwise. */
@Composable
private fun LocationIcon(look: StateLook, contentDescription: String?) {
  val crossedOut = look.dot == null
  Box(modifier = Modifier.size(24.dp)) {
    Icon(
        imageVector = if (crossedOut) Icons.Outlined.LocationOff else Icons.Outlined.LocationOn,
        contentDescription = contentDescription,
        modifier = Modifier.size(20.dp).align(Alignment.Center),
        tint = if (crossedOut) shifter_mutedText else MaterialTheme.colorScheme.onSurface,
    )
    look.dot?.let { color ->
      Box(Modifier.size(9.dp).align(Alignment.TopEnd).background(color, CircleShape))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSharingSheet(
    state: LiveLocationSharingState,
    sharing: LocationSharingUi,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
  val look = state.look()
  val colors = MaterialTheme.colorScheme
  ModalBottomSheet(
      onDismissRequest = onDismiss,
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      containerColor = colors.surfaceContainer,
      modifier = Modifier.testTag(LocationSharingTestTags.SHEET),
  ) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp).background(colors.secondaryContainer, CircleShape),
        ) {
          LocationIcon(look, contentDescription = null)
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(look.title),
            color = colors.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(LocationSharingTestTags.SHEET_TITLE),
        )
      }
      Text(text = stringResource(look.body), color = colors.onSurfaceVariant, fontSize = 14.sp)
      // Off says the same for every target, so it lists none.
      if (state != LiveLocationSharingState.OFF) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
              text = stringResource(R.string.location_sharing_targets_header),
              color = colors.onSurface,
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
          )
          sharing.states.entries
              .sortedWith(compareBy({ it.key.eventId }, { it.key.teamId }))
              .forEach { (target, targetState) ->
                TargetRow(target, targetState, sharing.labels[target])
              }
        }
      }
      Text(
          text =
              stringResource(
                  if (state == LiveLocationSharingState.OFF) R.string.location_sharing_turn_on_hint
                  else R.string.location_sharing_turn_off_hint
              ),
          color = colors.onSurfaceVariant,
          fontSize = 14.sp,
      )
      Button(
          onClick = onOpenSettings,
          modifier =
              Modifier.fillMaxWidth().height(48.dp).testTag(LocationSharingTestTags.OPEN_SETTINGS),
      ) {
        Text(
            stringResource(R.string.location_sharing_open_settings),
            fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}

/** One team the position goes to: its event, the team and its manager, and its state. */
@Composable
private fun TargetRow(
    target: LiveLocationTarget,
    state: LiveLocationSharingState,
    label: SharingTargetLabel?,
) {
  val colors = MaterialTheme.colorScheme
  val look = state.look()
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
          Modifier.fillMaxWidth()
              .background(colors.surfaceContainerHigh, RoundedCornerShape(12.dp))
              .padding(horizontal = 14.dp, vertical = 10.dp)
              .testTag(LocationSharingTestTags.targetRow(target)),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = label?.eventTitle ?: stringResource(R.string.location_sharing_target_unknown),
          color = colors.onSurface,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      if (label != null) {
        Text(
            text =
                label.managerName?.let {
                  stringResource(
                      R.string.location_sharing_target_team_and_manager,
                      label.teamName,
                      it,
                  )
                } ?: label.teamName,
            color = colors.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Spacer(Modifier.width(8.dp))
    val dotColor = look.dot ?: shifter_mutedText
    Box(Modifier.size(7.dp).background(dotColor, CircleShape))
    Spacer(Modifier.width(6.dp))
    Text(text = stringResource(look.targetLabel), color = dotColor, fontSize = 12.sp)
  }
}

/**
 * How a state is shown: the dot of the icon (`null` for the crossed-out pin), the words of the icon
 * and of the sheet, and the label of a target in that state.
 */
private class StateLook(
    val dot: Color?,
    @StringRes val description: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
    @StringRes val targetLabel: Int,
)

@Composable
private fun LiveLocationSharingState.look(): StateLook =
    when (this) {
      LiveLocationSharingState.SHARED ->
          StateLook(
              dot = shifter_success,
              description = R.string.location_sharing_shared_description,
              title = R.string.location_sharing_shared_title,
              body = R.string.location_sharing_shared_body,
              targetLabel = R.string.location_sharing_target_shared,
          )
      LiveLocationSharingState.WAITING ->
          StateLook(
              dot = shifter_warning,
              description = R.string.location_sharing_waiting_description,
              title = R.string.location_sharing_waiting_title,
              body = R.string.location_sharing_waiting_body,
              targetLabel = R.string.location_sharing_target_waiting,
          )
      LiveLocationSharingState.REFUSED ->
          StateLook(
              dot = MaterialTheme.colorScheme.error,
              description = R.string.location_sharing_refused_description,
              title = R.string.location_sharing_refused_title,
              body = R.string.location_sharing_refused_body,
              targetLabel = R.string.location_sharing_target_refused,
          )
      LiveLocationSharingState.NO_LOCATION_ACCESS ->
          StateLook(
              dot = null,
              description = R.string.location_sharing_no_access_description,
              title = R.string.location_sharing_no_access_title,
              body = R.string.location_sharing_no_access_body,
              targetLabel = R.string.location_sharing_target_not_shared,
          )
      LiveLocationSharingState.OFF ->
          StateLook(
              dot = null,
              description = R.string.location_sharing_off_description,
              title = R.string.location_sharing_off_title,
              body = R.string.location_sharing_off_body,
              targetLabel = R.string.location_sharing_target_not_shared,
          )
    }
