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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.ui.events.EventBadge
import com.swent.shifter.ui.events.EventTab
import com.swent.shifter.ui.events.MyEventsTestTags
import com.swent.shifter.ui.events.MyEventsUiState
import com.swent.shifter.ui.events.SampleMyEvents
import com.swent.shifter.ui.events.components.EventCardAction
import com.swent.shifter.ui.events.components.EventCardActionType
import com.swent.shifter.ui.events.components.MyEventsLayout
import com.swent.shifter.ui.events.components.SwitchViewButton
import com.swent.shifter.ui.theme.ShifterTheme

/** The events the user organizes (Figma "My Events Organizer", OR-00). Stateless. */
@Composable
fun OrgaEventsContent(
    state: MyEventsUiState,
    avatarInitial: String,
    onTabSelected: (EventTab) -> Unit,
    onManageEvent: (eventId: String) -> Unit,
    onCreateEventClick: () -> Unit,
    onVolunteerViewClick: () -> Unit,
    onAvatarClick: () -> Unit,
) {
  MyEventsLayout(
      state = state,
      avatarInitial = avatarInitial,
      subtitle = stringResource(R.string.my_events_organizer_view),
      onTabSelected = onTabSelected,
      onAvatarClick = onAvatarClick,
      cardAction = { card ->
        EventCardAction(EventCardActionType.MANAGE_EVENT) { onManageEvent(card.id) }
      },
      modifier = Modifier.testTag(MyEventsTestTags.ORGANIZER_SCREEN),
  ) {
    // Mirrors the staff screen's "Organizer View" button, so the organizer can go back.
    SwitchViewButton(
        label = stringResource(R.string.my_events_volunteer_view),
        testTag = MyEventsTestTags.VOLUNTEER_VIEW_BUTTON,
        onClick = onVolunteerViewClick,
    )
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
        )
      },
      text = {
        Text(
            text = stringResource(R.string.my_events_create_event),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
      },
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
        onVolunteerViewClick = {},
        onAvatarClick = {},
    )
  }
}
