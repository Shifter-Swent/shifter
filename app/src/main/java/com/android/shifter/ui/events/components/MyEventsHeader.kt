// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.ui.components.UnderlinedTab
import com.swent.shifter.ui.components.UnderlinedTabRow
import com.swent.shifter.ui.events.EventTab
import com.swent.shifter.ui.events.MyEventsTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.shifter_avatarBackground

/** "My Events" title, an optional [subtitle] such as "Organizer View", and the user's avatar. */
@Composable
fun MyEventsTopBar(
    avatarInitial: String,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
  val colors = MaterialTheme.colorScheme
  Row(
      modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
          text = stringResource(R.string.my_events_title),
          color = colors.onBackground,
          fontSize = 22.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.testTag(MyEventsTestTags.TITLE),
      )
      if (subtitle != null) {
        Text(
            text = subtitle,
            color = colors.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag(MyEventsTestTags.SUBTITLE),
        )
      }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(36.dp)
                .clip(CircleShape)
                .background(shifter_avatarBackground)
                .clickable(
                    onClickLabel = stringResource(R.string.my_events_open_profile),
                    onClick = onAvatarClick,
                )
                .testTag(MyEventsTestTags.AVATAR),
    ) {
      Text(
          text = avatarInitial,
          color = colors.onBackground,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

/** The Upcoming / Past tabs, followed by the full-width divider. */
@Composable
fun EventTabs(
    selected: EventTab,
    onSelect: (EventTab) -> Unit,
    modifier: Modifier = Modifier,
) {
  UnderlinedTabRow(modifier) {
    UnderlinedTab(
        label = stringResource(R.string.my_events_tab_upcoming),
        isSelected = selected == EventTab.UPCOMING,
        onClick = { onSelect(EventTab.UPCOMING) },
        width = 160.dp,
        modifier = Modifier.testTag(MyEventsTestTags.UPCOMING_TAB),
    )
    UnderlinedTab(
        label = stringResource(R.string.my_events_tab_past),
        isSelected = selected == EventTab.PAST,
        onClick = { onSelect(EventTab.PAST) },
        width = 150.dp,
        modifier = Modifier.testTag(MyEventsTestTags.PAST_TAB),
    )
  }
}

@Preview
@Composable
private fun MyEventsHeaderPreview() {
  ShifterTheme {
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
      MyEventsTopBar(avatarInitial = "J", onAvatarClick = {}, subtitle = "Organizer View")
      EventTabs(selected = EventTab.UPCOMING, onSelect = {})
    }
  }
}
