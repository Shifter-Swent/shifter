// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events.staff

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.ui.events.EventBadge
import com.swent.shifter.ui.events.EventTab
import com.swent.shifter.ui.events.MyEventsTestTags
import com.swent.shifter.ui.events.MyEventsUiState
import com.swent.shifter.ui.events.MyEventsViewModel
import com.swent.shifter.ui.events.SampleMyEvents
import com.swent.shifter.ui.events.components.EventCardAction
import com.swent.shifter.ui.events.components.EventCardActionType
import com.swent.shifter.ui.events.components.MyEventsLayout
import com.swent.shifter.ui.events.components.SwitchViewButton
import com.swent.shifter.ui.theme.ShifterTheme

/** [StaffEventsContent] driven by [viewModel], which owns the cards and the selected tab. */
@Composable
fun StaffEventsScreen(
    viewModel: MyEventsViewModel,
    avatarInitial: String,
    onOrganizerViewClick: () -> Unit,
    onScanQrClick: () -> Unit = {},
    onWithdraw: (eventId: String) -> Unit = {},
    onAvatarClick: () -> Unit = {},
) {
  val state by viewModel.uiState.collectAsState()
  StaffEventsContent(
      state = state,
      avatarInitial = avatarInitial,
      onTabSelected = viewModel::selectTab,
      onWithdraw = onWithdraw,
      onOrganizerViewClick = onOrganizerViewClick,
      onScanQrClick = onScanQrClick,
      onAvatarClick = onAvatarClick,
  )
}

/**
 * The events the user takes part in as a volunteer (Figma "My-Events", VM-00).
 *
 * Stateless: the Organizer View button only reports the click, switching to the organizer screen is
 * left to the navigation.
 */
@Composable
fun StaffEventsContent(
    state: MyEventsUiState,
    avatarInitial: String,
    onTabSelected: (EventTab) -> Unit,
    onWithdraw: (eventId: String) -> Unit,
    onOrganizerViewClick: () -> Unit,
    onScanQrClick: () -> Unit,
    onAvatarClick: () -> Unit,
) {
  MyEventsLayout(
      state = state,
      avatarInitial = avatarInitial,
      onTabSelected = onTabSelected,
      onAvatarClick = onAvatarClick,
      // An event that is over can no longer be left.
      cardAction = { card ->
        if (card.badge == EventBadge.ENDED) null
        else EventCardAction(EventCardActionType.WITHDRAW) { onWithdraw(card.id) }
      },
      modifier = Modifier.testTag(MyEventsTestTags.STAFF_SCREEN),
  ) {
    SwitchViewButton(
        label = stringResource(R.string.my_events_organizer_view),
        testTag = MyEventsTestTags.ORGANIZER_VIEW_BUTTON,
        onClick = onOrganizerViewClick,
    )
    ScanQrButton(onScanQrClick)
  }
}

@Composable
private fun BoxScope.ScanQrButton(onClick: () -> Unit) {
  FloatingActionButton(
      onClick = onClick,
      shape = CircleShape,
      containerColor = MaterialTheme.colorScheme.primary,
      modifier =
          Modifier.align(Alignment.BottomEnd)
              .padding(end = 18.dp, bottom = 16.dp)
              .size(56.dp)
              .testTag(MyEventsTestTags.SCAN_QR_BUTTON),
  ) {
    Icon(
        painter = painterResource(R.drawable.ic_plus),
        contentDescription = stringResource(R.string.my_events_scan_invitation),
    )
  }
}

@Preview
@Composable
private fun StaffEventsContentPreview() {
  ShifterTheme {
    StaffEventsContent(
        state =
            MyEventsUiState(
                upcoming = SampleMyEvents.staff.filter { it.badge != EventBadge.ENDED }
            ),
        avatarInitial = "J",
        onTabSelected = {},
        onWithdraw = {},
        onOrganizerViewClick = {},
        onScanQrClick = {},
        onAvatarClick = {},
    )
  }
}
