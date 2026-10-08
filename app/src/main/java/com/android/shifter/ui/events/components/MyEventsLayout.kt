// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.ui.events.EventCardUi
import com.swent.shifter.ui.events.EventTab
import com.swent.shifter.ui.events.MyEventsTestTags
import com.swent.shifter.ui.events.MyEventsUiState

/**
 * Layout shared by the staff and the organizer My Events screens: header, tabs, the cards of the
 * selected tab, and [floatingContent] drawn over the bottom of the list.
 *
 * @param cardAction the action link of each card, or null to hide it.
 */
@Composable
fun MyEventsLayout(
    state: MyEventsUiState,
    avatarInitial: String,
    onTabSelected: (EventTab) -> Unit,
    onAvatarClick: () -> Unit,
    cardAction: (EventCardUi) -> EventCardAction?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    floatingContent: @Composable BoxScope.() -> Unit = {},
) {
  Box(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .safeDrawingPadding()
  ) {
    Column(Modifier.fillMaxSize()) {
      MyEventsTopBar(
          avatarInitial = avatarInitial,
          onAvatarClick = onAvatarClick,
          subtitle = subtitle,
      )
      EventTabs(selected = state.selectedTab, onSelect = onTabSelected)
      Box(Modifier.fillMaxSize()) {
        when {
          // A reload keeps the cards already shown instead of replacing them with the spinner.
          state.isLoading && state.visibleEvents.isEmpty() ->
              CircularProgressIndicator(
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.align(Alignment.Center).testTag(MyEventsTestTags.LOADING),
              )
          state.errorMessage != null -> CenteredMessage(state.errorMessage, MyEventsTestTags.ERROR)
          state.visibleEvents.isEmpty() ->
              CenteredMessage(
                  stringResource(
                      if (state.selectedTab == EventTab.UPCOMING) R.string.my_events_empty_upcoming
                      else R.string.my_events_empty_past
                  ),
                  MyEventsTestTags.EMPTY_STATE,
              )
          else ->
              LazyColumn(
                  // The bottom padding keeps the last card above the floating buttons.
                  contentPadding =
                      PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                  modifier = Modifier.fillMaxSize().testTag(MyEventsTestTags.EVENT_LIST),
              ) {
                items(state.visibleEvents, key = { it.id }) { card ->
                  EventCard(card = card, action = cardAction(card))
                }
              }
        }
      }
    }
    floatingContent()
  }
}

@Composable
private fun BoxScope.CenteredMessage(text: String, testTag: String) {
  Text(
      text = text,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = 14.sp,
      textAlign = TextAlign.Center,
      modifier = Modifier.align(Alignment.Center).padding(24.dp).testTag(testTag),
  )
}
