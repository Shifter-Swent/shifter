// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events.organizer

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.ui.events.EventBadge
import com.swent.shifter.ui.events.EventTab
import com.swent.shifter.ui.events.MyEventsTestTags
import com.swent.shifter.ui.events.MyEventsUiState
import com.swent.shifter.ui.events.MyEventsViewModel
import com.swent.shifter.ui.events.SampleMyEvents
import com.swent.shifter.ui.events.components.EventCardAction
import com.swent.shifter.ui.events.components.MyEventsLayout
import com.swent.shifter.ui.theme.ShifterTheme

/** [OrgaEventsContent] driven by [viewModel], which owns the cards and the selected tab. */
@Composable
fun OrgaEventsScreen(
    viewModel: MyEventsViewModel,
    avatarInitial: String,
    onManageEvent: (eventId: String) -> Unit = {},
    onCreateEventClick: () -> Unit = {},
    onAvatarClick: () -> Unit = {},
) {
  val state by viewModel.uiState.collectAsState()
  OrgaEventsContent(
      state = state,
      avatarInitial = avatarInitial,
      onTabSelected = viewModel::selectTab,
      onManageEvent = onManageEvent,
      onCreateEventClick = onCreateEventClick,
      onAvatarClick = onAvatarClick,
  )
}

/** The events the user organizes (Figma "My Events Organizer", OR-00). Stateless. */
@Composable
fun OrgaEventsContent(
    state: MyEventsUiState,
    avatarInitial: String,
    onTabSelected: (EventTab) -> Unit,
    onManageEvent: (eventId: String) -> Unit,
    onCreateEventClick: () -> Unit,
    onAvatarClick: () -> Unit,
) {
  val manageColor = MaterialTheme.colorScheme.primary
  MyEventsLayout(
      state = state,
      avatarInitial = avatarInitial,
      subtitle = "Organizer View",
      onTabSelected = onTabSelected,
      onAvatarClick = onAvatarClick,
      cardAction = { card ->
        EventCardAction("Manage event", manageColor) { onManageEvent(card.id) }
      },
      modifier = Modifier.testTag(MyEventsTestTags.ORGANIZER_SCREEN),
  ) {
    CreateEventButton(onCreateEventClick)
  }
}

@Composable
private fun BoxScope.CreateEventButton(onClick: () -> Unit) {
  ExtendedFloatingActionButton(
      onClick = onClick,
      shape = RoundedCornerShape(28.dp),
      containerColor = MaterialTheme.colorScheme.primary,
      contentColor = MaterialTheme.colorScheme.onPrimary,
      icon = {
        Icon(
            painter = painterResource(R.drawable.ic_plus),
            contentDescription = null,
            tint = Color.Unspecified,
        )
      },
      text = { Text(text = "Create my event", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) },
      modifier =
          Modifier.align(Alignment.BottomEnd)
              .padding(end = 18.dp, bottom = 16.dp)
              .height(56.dp)
              .testTag(MyEventsTestTags.CREATE_EVENT_BUTTON),
  )
}

@Preview
@Composable
private fun OrgaEventsContentPreview() {
  ShifterTheme {
    OrgaEventsContent(
        state =
            MyEventsUiState(
                upcoming = SampleMyEvents.organizer.filter { it.badge != EventBadge.ENDED }
            ),
        avatarInitial = "J",
        onTabSelected = {},
        onManageEvent = {},
        onCreateEventClick = {},
        onAvatarClick = {},
    )
  }
}
